/*
 * Copyright (C) 2017 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.paranoid.hub;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import com.paranoid.hub.controller.ABUpdateInstaller;
import com.paranoid.hub.controller.UpdateInstaller;
import com.paranoid.hub.download.DownloadClient;
import com.paranoid.hub.misc.Constants;
import com.paranoid.hub.misc.Utils;
import com.paranoid.hub.model.Update;
import com.paranoid.hub.model.UpdateInfo;
import com.paranoid.hub.model.UpdateStatus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HubUpdateController {

    private final String TAG = "HubUpdateController";

    public static final int STATE_STATUS_CHANGED = 0;
    public static final int STATE_DOWNLOAD_PROGRESS = 1;
    public static final int STATE_INSTALL_PROGRESS = 2;
    public static final int STATE_UPDATE_DELETE = 3;
    public static final String EXTRA_DOWNLOAD_ID = "extra_download_id";

    public static final boolean IS_DEBUG = true;

    private static HubUpdateController sController;

    private static final int MAX_REPORT_INTERVAL_MS = 1000;

    private final Context mContext;
    private Handler mHandler;
    private final LocalBroadcastManager mBroadcastManager;

    private final PowerManager.WakeLock mWakeLock;

    private final File mDownloadRoot;

    private boolean mIsUpdatesOnline;
    private int mActiveDownloads = 0;
    private List<StatusListener> mListeners = new ArrayList<>();
    private Map<String, DownloadEntry> mDownloads = new HashMap<>();
    private Set<String> mVerifyingUpdates = new HashSet<>();
    private SharedPreferences mPrefs;

    public interface StatusListener {
        void onUpdateStatusChanged(int state, String downloadId);
    }

    public static synchronized HubUpdateController getInstance() {
        return sController;
    }

    public static synchronized HubUpdateController getInstance(Context context) {
        if (sController == null) {
            sController = new HubUpdateController(context);
        }
        return sController;
    }

    private HubUpdateController(Context context) {
        mBroadcastManager = LocalBroadcastManager.getInstance(context);
        mHandler = new Handler(context.getMainLooper());
        mDownloadRoot = Utils.getDownloadPath(context);
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Updater");
        mWakeLock.setReferenceCounted(false);
        mContext = context.getApplicationContext();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        Utils.cleanupDownloadsDir(context);
    }

    private class DownloadEntry {
        final Update mUpdate;
        DownloadClient mDownloadClient;
        private DownloadEntry(Update update) {
            mUpdate = update;
        }
    }

    public void notifyUpdateStatusChanged(int state, String downloadId) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for (StatusListener listener : mListeners) {
                        listener.onUpdateStatusChanged(state, downloadId);
                    }
                }
            });
    }

    private void tryReleaseWakelock() {
        if (!hasActiveDownloads()) {
            mWakeLock.release();
        }
    }

    public void addUpdateStatusListener(StatusListener listener) {
        mListeners.add(listener);
    }

    public void removeUpdateStatusListener(StatusListener listener) {
        mListeners.remove(listener);
    }

    private DownloadClient.DownloadCallback getDownloadCallback(final String downloadId) {
        return new DownloadClient.DownloadCallback() {

            @Override
            public void onResponse(int statusCode, String url, DownloadClient.Headers headers) {
                final Update update = mDownloads.get(downloadId).mUpdate;
                String contentLength = headers.get("Content-Length");
                if (contentLength != null) {
                    try {
                        long size = Long.parseLong(contentLength);
                        if (update.getFileSize() < size) {
                            update.setFileSize(size);
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Could not get content-length");
                    }
                }
                update.setStatus(UpdateStatus.DOWNLOADING, mContext);
                update.setPersistentStatus(UpdateStatus.Persistent.INCOMPLETE);
                notifyUpdateStatusChanged(STATE_STATUS_CHANGED, downloadId);
            }

            @Override
            public void onSuccess(File destination) {
                Log.d(TAG, "Download complete");
                Update update = mDownloads.get(downloadId).mUpdate;
                update.setStatus(UpdateStatus.DOWNLOADED, mContext);
                setDownloading(false);
                if (!IS_DEBUG) verifyUpdateAsync(update, downloadId);
                notifyUpdateStatusChanged(STATE_STATUS_CHANGED, downloadId);
                tryReleaseWakelock();
            }

            @Override
            public void onFailure(boolean cancelled) {
                Update update = mDownloads.get(downloadId).mUpdate;
                if (cancelled) {
                    Log.d(TAG, "Download cancelled");
                    // Already notified
                } else {
                    Log.e(TAG, "Download failed");
                    setDownloading(false);
                    update.setStatus(UpdateStatus.PAUSED_ERROR, mContext);
                    notifyUpdateStatusChanged(STATE_STATUS_CHANGED, downloadId);
                }
                tryReleaseWakelock();
            }
        };
    }

    private DownloadClient.ProgressListener getProgressListener(final String downloadId) {
        return new DownloadClient.ProgressListener() {
            private long mLastUpdate = 0;
            private int mProgress = 0;

            @Override
            public void update(long bytesRead, long contentLength, long speed, long eta,
                    boolean done) {
                Update update = mDownloads.get(downloadId).mUpdate;
                if (contentLength <= 0) {
                    if (update.getFileSize() <= 0) {
                        return;
                    } else {
                        contentLength = update.getFileSize();
                    }
                }
                if (contentLength <= 0) {
                    return;
                }
                final long now = SystemClock.elapsedRealtime();
                int progress = Math.round(bytesRead * 100 / contentLength);
                if (progress != mProgress || mLastUpdate - now > MAX_REPORT_INTERVAL_MS) {
                    mProgress = progress;
                    mLastUpdate = now;
                    update.setProgress(progress);
                    update.setEta(eta);
                    update.setSpeed(speed);
                    notifyUpdateStatusChanged(STATE_DOWNLOAD_PROGRESS, downloadId);
                }
            }
        };
    }

    private void verifyUpdateAsync(Update update, final String downloadId) {
        update.setStatus(UpdateStatus.VERIFYING, mContext);
        mVerifyingUpdates.add(downloadId);
        new Thread(() -> {
            File file = update.getFile();
            if (file.exists() && verifyPackage(file)) {
                file.setReadable(true, false);
                update.setPersistentStatus(UpdateStatus.Persistent.VERIFIED);
                update.setStatus(UpdateStatus.VERIFIED, mContext);
            } else {
                update.setPersistentStatus(UpdateStatus.Persistent.UNKNOWN);
                update.setProgress(0);
                update.setStatus(UpdateStatus.VERIFICATION_FAILED, mContext);
            }
            mVerifyingUpdates.remove(downloadId);
            notifyUpdateStatusChanged(STATE_STATUS_CHANGED, downloadId);
        }).start();
    }

    private boolean verifyPackage(File file) {
        try {
            android.os.RecoverySystem.verifyPackage(file, null, null);
            Log.e(TAG, "Verification successful");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Verification failed", e);
            if (file.exists()) {
                file.delete();
            } else {
                // The download was probably stopped. Exit silently
                Log.e(TAG, "Error while verifying the file", e);
            }
            return false;
        }
    }

    private boolean fixUpdateStatus(Update update) {
        switch (update.getPersistentStatus()) {
            case UpdateStatus.Persistent.VERIFIED:
            case UpdateStatus.Persistent.INCOMPLETE:
                if (update.getFile() == null || !update.getFile().exists()) {
                    update.setStatus(UpdateStatus.UNKNOWN, mContext);
                    return false;
                } else if (update.getFileSize() > 0) {
                    update.setStatus(UpdateStatus.PAUSED, mContext);
                    int progress = Math.round(
                            update.getFile().length() * 100 / update.getFileSize());
                    update.setProgress(progress);
                }
                break;
        }
        return true;
    }

    public void setUpdatesNotAvailableOnline(List<String> downloadIds) {
        for (String downloadId : downloadIds) {
            DownloadEntry update = mDownloads.get(downloadId);
            if (update != null) {
                update.mUpdate.setAvailableOnline(false);
            }
        }
    }

    public void setUpdatesAvailableOnline(List<String> downloadIds, boolean purgeList) {
        List<String> toRemove = new ArrayList<>();
        for (DownloadEntry entry : mDownloads.values()) {
            boolean online = downloadIds.contains(entry.mUpdate.getDownloadId());
            if (online != mIsUpdatesOnline) mIsUpdatesOnline = online;
            entry.mUpdate.setAvailableOnline(online);
            if (!online && purgeList &&
                    entry.mUpdate.getPersistentStatus() == UpdateStatus.Persistent.UNKNOWN) {
                toRemove.add(entry.mUpdate.getDownloadId());
            }
        }
        for (String downloadId : toRemove) {
            Log.d(TAG, downloadId + " no longer available online, removing");
            mDownloads.remove(downloadId);
            notifyUpdateStatusChanged(STATE_UPDATE_DELETE, downloadId);
        }
    }

    public boolean isUpdatesOnline() {
        return mIsUpdatesOnline;
    }

    public boolean isUpdateAvailable(UpdateInfo info) {
        Update update = new Update(info);
        int status = mPrefs.getInt(Constants.UPDATE_STATUS, -1);
        if (status == UpdateStatus.INSTALLING) {
            update.setStatus(UpdateStatus.INSTALLING, mContext);
            notifyUpdateStatusChanged(STATE_INSTALL_PROGRESS, update.getDownloadId());
            return false;
        } else if (status == UpdateStatus.DOWNLOADING) {
            update.setStatus(UpdateStatus.DOWNLOADING, mContext);
            notifyUpdateStatusChanged(STATE_DOWNLOAD_PROGRESS, update.getDownloadId());
            return false;
        } else if (status == UpdateStatus.DOWNLOADED) {
            update.setStatus(UpdateStatus.DOWNLOADED, mContext);
            notifyUpdateStatusChanged(STATE_STATUS_CHANGED, update.getDownloadId());
            return false;
        }

        if (!Utils.isCompatible(mContext, update)) {
            Log.d(TAG, update.getName() + " already installed, up to date");
            update.setStatus(UpdateStatus.UNAVAILABLE, mContext);
            notifyUpdateStatusChanged(STATE_STATUS_CHANGED, update.getDownloadId());
            return false;
        }

        File file = new File(Utils.getDownloadPath(mContext), update.getName());
        if (file.exists() && !isDownloading()) {
            Log.d(TAG, file.getName() + " exists, next step is install");
            update.setStatus(UpdateStatus.DOWNLOADED, mContext);
            notifyUpdateStatusChanged(STATE_STATUS_CHANGED, update.getDownloadId());
            return false;
        } else if (isDownloading()) {
            Log.d(TAG, file.getName() + " is downloading");
            update.setStatus(UpdateStatus.DOWNLOADING, mContext);
            notifyUpdateStatusChanged(STATE_STATUS_CHANGED, update.getDownloadId());
            return false;
        }
        if (!fixUpdateStatus(update) && !update.getAvailableOnline()) {
            update.setPersistentStatus(UpdateStatus.Persistent.UNKNOWN);
            deleteUpdateAsync(update);
            Log.d(TAG, update.getDownloadId() + " had an invalid status and is not online");
            return false;
        }

        if (!mDownloads.containsKey(update.getDownloadId())) {
            mDownloads.put(update.getDownloadId(), new DownloadEntry(update));
            update.setStatus(UpdateStatus.AVAILABLE, mContext);
            update.setAvailableOnline(true);
            notifyUpdateStatusChanged(STATE_STATUS_CHANGED, update.getDownloadId());
            return true;
        }
        update.setAvailableOnline(true && update.getAvailableOnline());
        update.setDownloadUrl(update.getDownloadUrl());
        update.setStatus(status, mContext);
        notifyUpdateStatusChanged(STATE_STATUS_CHANGED, update.getDownloadId());
        return false;
    }

    public boolean startDownload(String downloadId) {
        Log.d(TAG, "Starting " + downloadId);
        if (!mDownloads.containsKey(downloadId) || isDownloading()) {
            return false;
        }
        Update update = mDownloads.get(downloadId).mUpdate;
        File destination = new File(mDownloadRoot, update.getName());
        if (destination.exists()) {
            destination = Utils.appendSequentialNumber(destination);
            Log.d(TAG, "Changing name with " + destination.getName());
        }
        update.setFile(destination);
        DownloadClient downloadClient;
        try {
            downloadClient = new DownloadClient.Builder()
                    .setUrl(update.getDownloadUrl())
                    .setDestination(update.getFile())
                    .setDownloadCallback(getDownloadCallback(downloadId))
                    .setProgressListener(getProgressListener(downloadId))
                    .setUseDuplicateLinks(true)
                    .build();
        } catch (IOException exception) {
            Log.e(TAG, "Could not build download client");
            update.setStatus(UpdateStatus.PAUSED_ERROR, mContext);
            notifyUpdateStatusChanged(STATE_STATUS_CHANGED, downloadId);
            return false;
        }
        setDownloading(true);
        update.setStatus(UpdateStatus.STARTING, mContext);
        notifyUpdateStatusChanged(STATE_STATUS_CHANGED, downloadId);
        downloadClient.start();
        mWakeLock.acquire();
        return true;
    }

    public boolean resumeDownload(String downloadId) {
        Log.d(TAG, "Resuming " + downloadId);
        if (!mDownloads.containsKey(downloadId) || isDownloading()) {
            return false;
        }
        Update update = mDownloads.get(downloadId).mUpdate;
        File file = update.getFile();
        if (file == null || !file.exists()) {
            Log.e(TAG, "The destination file of " + downloadId + " doesn't exist, can't resume");
            update.setStatus(UpdateStatus.PAUSED_ERROR, mContext);
            notifyUpdateStatusChanged(STATE_STATUS_CHANGED, downloadId);
            return false;
        }
        if (file.exists() && update.getFileSize() > 0 && file.length() >= update.getFileSize() && !IS_DEBUG) {
            Log.d(TAG, "File already downloaded, starting verification");
            verifyUpdateAsync(update, downloadId);
            notifyUpdateStatusChanged(STATE_STATUS_CHANGED, downloadId);
        } else {
            DownloadClient downloadClient;
            try {
                downloadClient = new DownloadClient.Builder()
                        .setUrl(update.getDownloadUrl())
                        .setDestination(update.getFile())
                        .setDownloadCallback(getDownloadCallback(downloadId))
                        .setProgressListener(getProgressListener(downloadId))
                        .setUseDuplicateLinks(true)
                        .build();
            } catch (IOException exception) {
                Log.e(TAG, "Could not build download client");
                update.setStatus(UpdateStatus.PAUSED_ERROR, mContext);
                notifyUpdateStatusChanged(STATE_STATUS_CHANGED, downloadId);
                return false;
            }
            setDownloading(true);
            update.setStatus(UpdateStatus.STARTING, mContext);
            notifyUpdateStatusChanged(STATE_STATUS_CHANGED, downloadId);
            downloadClient.resume();
            mWakeLock.acquire();
        }
        return true;
    }

    public boolean pauseDownload(String downloadId) {
        Log.d(TAG, "Pausing " + downloadId);
        if (!isDownloading() || mDownloadClient == null) {
            return false;
        }

        DownloadEntry entry = mDownloads.get(downloadId);
        entry.mDownloadClient.cancel();
        setDownloading(false);
        entry.mUpdate.setStatus(UpdateStatus.PAUSED, mContext);
        entry.mUpdate.setEta(0);
        entry.mUpdate.setSpeed(0);
        notifyUpdateStatusChanged(STATE_STATUS_CHANGED, downloadId);
        return true;
    }

    public void startInstall(String downloadId) {
        UpdateInfo update = getUpdate(downloadId);
        if (update.getPersistentStatus() != UpdateStatus.Persistent.VERIFIED 
                    && !IS_DEBUG) {
            throw new IllegalArgumentException(update.getDownloadId() + " is not verified");
        }
        try {
            if (Utils.isABUpdate(update.getFile())) {
                ABUpdateInstaller abInstaller = ABUpdateInstaller.getInstance(mContext, this);
                abInstaller.install(downloadId);
            } else {
                UpdateInstaller updateInstaller = UpdateInstaller.getInstance(mContext, this);
                updateInstaller.install(downloadId);
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not install update", e);
            getActualUpdate(downloadId).setStatus(UpdateStatus.INSTALLATION_FAILED, mContext);
            notifyUpdateStatusChanged(STATE_STATUS_CHANGED, downloadId);
        }
    }

    private void deleteUpdateAsync(final Update update) {
        new Thread(() -> {
            File file = update.getFile();
            if (file.exists() && !file.delete()) {
                Log.e(TAG, "Could not delete " + file.getAbsolutePath());
            }
        }).start();
    }

    public boolean deleteUpdate(String downloadId) {
        Log.d(TAG, "Cancelling " + downloadId);
        if (!mDownloads.containsKey(downloadId) || isDownloading()) {
            return false;
        }
        Update update = mDownloads.get(downloadId).mUpdate;
        update.setStatus(UpdateStatus.DELETED, mContext);
        update.setProgress(0);
        update.setPersistentStatus(UpdateStatus.Persistent.UNKNOWN);
        deleteUpdateAsync(update);

        if (!update.getAvailableOnline()) {
            Log.d(TAG, "Download no longer available online, removing");
            mDownloads.remove(downloadId);
            notifyUpdateStatusChanged(STATE_UPDATE_DELETE, downloadId);
        } else {
            notifyUpdateStatusChanged(STATE_STATUS_CHANGED, downloadId);
        }

        return true;
    }

    public Set<String> getIds() {
        return mDownloads.keySet();
    }

    public List<UpdateInfo> getUpdates() {
        List<UpdateInfo> updates = new ArrayList<>();
        for (DownloadEntry entry : mDownloads.values()) {
            updates.add(entry.mUpdate);
        }
        return updates;
    }

    public UpdateInfo getUpdate(String downloadId) {
        DownloadEntry entry = mDownloads.get(downloadId);
        return entry != null ? entry.mUpdate : null;
    }

    public Update getActualUpdate(String downloadId) {
        DownloadEntry entry = mDownloads.get(downloadId);
        return entry != null ? entry.mUpdate : null;
    }

    public boolean isDownloading() {
        boolean isDownloading = mPrefs.getBoolean(Constants.IS_UPDATE_DOWNLOADING, false);
        return isDownloading;
    }

    public boolean hasActiveDownloads() {
        return mActiveDownloads > 0;
    }

    public boolean isVerifyingUpdate() {
        return mVerifyingUpdates.size() > 0;
    }

    public boolean isVerifyingUpdate(String downloadId) {
        return mVerifyingUpdates.contains(downloadId);
    }

    public boolean isInstallingUpdate() {
        return UpdateInstaller.isInstalling() ||
                ABUpdateInstaller.isInstallingUpdate(mContext);
    }

    public boolean isInstallingUpdate(String downloadId) {
        return UpdateInstaller.isInstalling(downloadId) ||
                ABUpdateInstaller.isInstallingUpdate(mContext, downloadId);
    }

    public boolean isInstallingABUpdate() {
        return ABUpdateInstaller.isInstallingUpdate(mContext);
    }

    public boolean isWaitingForReboot(String downloadId) {
        return ABUpdateInstaller.isWaitingForReboot(mContext, downloadId);
    }

    private void setDownloading(boolean isDownloading) {
        mPrefs.edit().putBoolean(Constants.IS_UPDATE_DOWNLOADING, isDownloading).apply();
        if (isDownloading) {
            mActiveDownloads++;
        } else {
            mActiveDownloads--;
        }
    }

    public void setDownloadEntry(Update update) {
        if (!mDownloads.containsKey(update.getDownloadId())) {
            mDownloads.put(update.getDownloadId(), new DownloadEntry(update));
        }
    }

    public void setPerformanceMode(boolean enable) {
        if (!Utils.isABDevice()) {
            return;
        }
        ABUpdateInstaller.getInstance(mContext, this).setPerformanceMode(enable);
    }

    public void resetHub() {
        ((ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE))
                .clearApplicationUserData();
    }
}
