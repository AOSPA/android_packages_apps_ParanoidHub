/*
 * Copyright (C) 2022 Paranoid Android
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
package co.aospa.hub.controllers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import co.aospa.hub.R;
import co.aospa.hub.client.DownloadClient;
import co.aospa.hub.components.UpdateComponent;
import co.aospa.hub.util.App;
import co.aospa.hub.util.Constants;
import co.aospa.hub.util.FileUtils;
import co.aospa.hub.util.PreferenceHelper;
import co.aospa.hub.util.Update;
import co.aospa.hub.util.Version;

public class UpdateController {

    private static final String TAG = "UpdateController";

    private static final int MAX_REPORT_INTERVAL_MS = 1000;

    private final Context mContext;
    private final File mDownloadPath;
    private File mFilePath;
    private final PowerManager.WakeLock mWakeLock;
    @SuppressLint("StaticFieldLeak")
    private static UpdateController sController;

    private final List<UpdateController.UpdateListener> mListeners = new ArrayList<>();
    private final Map<String, DownloadEntry> mDownloads = new HashMap<>();
    private final Set<String> mVerifyingUpdates = new HashSet<>();
    private static final Set<PosixFilePermission> mFilePermissions = new HashSet<>();

    private int mActiveDownloads = 0;

    public @interface StatusType {
        int STARTING = 0;
        int DOWNLOAD = 1;
        int DOWNLOAD_CANCEL = 2;
        int DOWNLOAD_ERROR = 3;
        int VERIFY = 4;
        int VERIFY_ERROR = 5;
        int INSTALL = 6;
        int INSTALL_ERROR = 7;
        int FINALIZE = 8;
        int PAUSE = 9;
        int REBOOT = 10;
    }

    public static synchronized UpdateController get(Context context) {
        if (sController == null) {
            sController = new UpdateController(context);
        }
        return sController;
    }

    private UpdateController(Context context) {
        mContext = context.getApplicationContext();
        mDownloadPath = Update.getDownloadPath(context);
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hub:wakelock");
        mWakeLock.setReferenceCounted(false);
    }

    @SuppressLint("WakelockTimeout")
    public void startDownload(UpdateComponent updateComponent) {
        if (ABUpdateController.isInstalling(mContext)) {
            notifyUpdateListener(StatusType.INSTALL, -1);
            return;
        }
        String id = updateComponent.getId();
        mDownloads.put(id, new DownloadEntry(updateComponent));
        Log.d(TAG, "Starting download for " + id);
        if (!mDownloads.containsKey(id) || isDownloading(id)) {
            return;
        }
        DownloadEntry entry = mDownloads.get(id);
        if (entry == null) {
            Log.e(TAG, "Could not get download entry");
            return;
        }
        UpdateComponent component = entry.mComponent;
        File filePath = new File(mDownloadPath, component.getFileName());
        if (filePath.exists()) {
            //noinspection ResultOfMethodCallIgnored
            filePath.delete();
            filePath = new File(mDownloadPath, component.getFileName());
            Log.d(TAG, "Creating new file path with " + filePath.getName());
        }

        mFilePath = filePath;
        component.setFile(mFilePath);
        DownloadClient downloadClient;
        try {
            downloadClient = new DownloadClient.Builder()
                    .setUrl(component.getDownloadUrl())
                    .setDestination(component.getFile())
                    .setDownloadCallback(getDownloadCallback(id))
                    .setProgressListener(getProgressListener(id))
                    .setUseDuplicateLinks(true)
                    .build();
        } catch (IOException exception) {
            Log.e(TAG, "Could not build download client");
            notifyUpdateListener(StatusType.DOWNLOAD_ERROR, -1);
            return;
        }
        addDownloadClient(entry, downloadClient);
        notifyUpdateListener(StatusType.STARTING, 0);
        downloadClient.start();
        mWakeLock.acquire();
    }

    public void cancelOrPauseDownload(UpdateComponent component, boolean cancel) {
        String id = component.getId();
        Log.d(TAG, cancel ? "Cancel " : "Pause " + "download for " + id);
        if (!isDownloading(id)) {
            return;
        }

        DownloadEntry entry = mDownloads.get(id);
        if (entry != null) {
            entry.mDownloadClient.cancel();
            removeDownloadClient(entry);
            if (cancel) {
                mDownloads.remove(id);
                App.clearDownloadedFiles(mContext);
            }
            notifyUpdateListener(cancel
                    ? StatusType.DOWNLOAD_CANCEL
                    : StatusType.PAUSE, -1);
        }
    }

    @SuppressLint("WakelockTimeout")
    public void resumeDownload(UpdateComponent updateComponent) {
        if (ABUpdateController.isInstalling(mContext)) {
            notifyUpdateListener(StatusType.INSTALL, -1);
            return;
        }
        String id = updateComponent.getId();
        Log.d(TAG, "Resuming download for " + id);
        if (!mDownloads.containsKey(id) || isDownloading(id)) {
            return;
        }
        DownloadEntry entry = mDownloads.get(id);
        if (entry == null) {
            Log.e(TAG, "Could not get download entry");
            return;
        }
        UpdateComponent component = entry.mComponent;
        File file = component.getFile();
        if (file == null || !file.exists()) {
            Log.e(TAG, "The destination file of " + id + " doesn't exist, can't resume");
            notifyUpdateListener(StatusType.DOWNLOAD_ERROR, -1);
            return;
        }
        if (file.exists() && component.getFileSize() > 0 && file.length() >= component.getFileSize()) {
            Log.d(TAG, "File already downloaded, starting verification");
            if (shouldVerifyUpdate()){
                verifyUpdate(id);
                notifyUpdateListener(StatusType.VERIFY, 0);
            } else {
                Log.d(TAG, "Skipping pre-existing download verification because it is disabled");
                notifyUpdateListener(StatusType.INSTALL, 0);
                installUpdate(entry.mComponent);
            }
        } else {
            DownloadClient downloadClient;
            try {
                downloadClient = new DownloadClient.Builder()
                        .setUrl(component.getDownloadUrl())
                        .setDestination(component.getFile())
                        .setDownloadCallback(getDownloadCallback(id))
                        .setProgressListener(getProgressListener(id))
                        .setUseDuplicateLinks(true)
                        .build();
            } catch (IOException exception) {
                Log.e(TAG, "Could not build download client");
                notifyUpdateListener(StatusType.DOWNLOAD_ERROR, -1);
                return;
            }
            addDownloadClient(entry, downloadClient);
            notifyUpdateListener(StatusType.STARTING, 0);
            downloadClient.resume();
            mWakeLock.acquire();
        }
    }

    private DownloadClient.ProgressListener getProgressListener(final String downloadId) {
        return new DownloadClient.ProgressListener() {
            private long mLastUpdate = 0;
            private int mProgress = 0;

            @Override
            public void update(long bytesRead, long contentLength, long speed, long eta, boolean done) {
                DownloadEntry entry = mDownloads.get(downloadId);
                if (entry == null) {
                    return;
                }
                UpdateComponent component = entry.mComponent;
                if (contentLength <= 0) {
                    if (component.getFileSize() <= 0) {
                        return;
                    } else {
                        contentLength = component.getFileSize();
                    }
                }
                if (contentLength <= 0) {
                    return;
                }
                final long now = SystemClock.elapsedRealtime();
                int progress = Math.round(bytesRead * 100f / contentLength);
                if (progress != mProgress || mLastUpdate - now > MAX_REPORT_INTERVAL_MS) {
                    mProgress = progress;
                    mLastUpdate = now;
                    notifyUpdateListener(StatusType.DOWNLOAD, progress);
                }
            }
        };
    }

    private DownloadClient.DownloadCallback getDownloadCallback(final String id) {
        return new DownloadClient.DownloadCallback() {
            @Override
            public void onResponse(int statusCode, String url, DownloadClient.Headers headers) {
                final DownloadEntry entry = mDownloads.get(id);
                if (entry == null) {
                    return;
                }
                final UpdateComponent component = entry.mComponent;
                String contentLength = headers.get("Content-Length");
                if (contentLength != null) {
                    try {
                        long size = Long.parseLong(contentLength);
                        if (component.getFileSize() < size) {
                            component.setFileSize(size);
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Could not get content-length");
                    }
                }
                notifyUpdateListener(StatusType.DOWNLOAD, 0);
            }

            @Override
            public void onSuccess(File destination, int task) {
                Log.d(TAG, "Download complete");
                DownloadEntry entry = mDownloads.get(id);
                if (entry != null) {
                    removeDownloadClient(entry);
                    if (shouldVerifyUpdate()){
                        verifyUpdate(id);
                        notifyUpdateListener(StatusType.VERIFY, 0);
                    } else {
                        Log.d(TAG, "Skipping download verification because it is disabled");
                        notifyUpdateListener(StatusType.INSTALL, -1);
                        installUpdate(entry.mComponent);
                    }
                    tryReleaseWakelock();
                }
            }

            @Override
            public void onFailure(boolean cancelled) {
                if (cancelled) {
                    Log.d(TAG, "Download cancelled");
                } else {
                    DownloadEntry entry = mDownloads.get(id);
                    if (entry != null) {
                        Log.e(TAG, "Download failed");
                        removeDownloadClient(entry);
                        notifyUpdateListener(StatusType.DOWNLOAD_ERROR, -1);
                    }
                }
                tryReleaseWakelock();
            }
        };
    }

    private void installUpdate(UpdateComponent component) {
        try {
            if (Update.isABDevice() && Update.isABUpdate(component.getFile())) {
                ABUpdateController controller = ABUpdateController.getInstance(mContext,
                        sController);
                controller.install(component);
            } else {
                notifyUpdateListener(StatusType.REBOOT, -1);
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not install update", e);
            notifyUpdateListener(StatusType.INSTALL_ERROR, -1);
        }
    }

    public void installRecoveryUpdateIfPossible(UpdateComponent component) {
        if (Update.isABDevice()) {
            PowerManager pm = mContext.getSystemService(PowerManager.class);
            pm.reboot(null);
            return;
        }

        if (Update.isEncrypted(mContext, component.getFile())) {
            // uncrypt rewrites the file so that it can be read without mounting
            // the filesystem, so create a copy of it.
            prepareForUncryptAndInstall(component);
        } else {
            installRecoveryPackage(component.getFile());
        }
    }

    private void installRecoveryPackage(File update) {
        // Reset the update status now that the update is applied
        setUpdateStatus(-1);
        try {
            android.os.RecoverySystem.installPackage(mContext, update);
        } catch (IOException e) {
            Log.e(TAG, "Could not install update", e);
            notifyUpdateListener(StatusType.INSTALL_ERROR, -1);
        }
    }

    private boolean shouldVerifyUpdate() {
        boolean useDownloadVerification =
                mContext.getResources().getBoolean(R.bool.config_enableDownloadVerification);
        return Version.getBuildType().equals("Release") && useDownloadVerification;
    }

    @SuppressLint("SetWorldReadable")
    private void verifyUpdate(final String id) {
        Log.d(TAG, "verifying downloaded update");
        mVerifyingUpdates.add(id);
        new Thread(() -> {
            DownloadEntry entry = mDownloads.get(id);
            if (entry != null) {
                UpdateComponent component = entry.mComponent;
                File file = component.getFile();
                if (file.exists() && verifyPackage(file)) {
                    //noinspection ResultOfMethodCallIgnored
                    file.setReadable(true, false);
                    notifyUpdateListener(StatusType.INSTALL, 0);
                    installUpdate(component);
                } else {
                    notifyUpdateListener(StatusType.VERIFY_ERROR, -1);
                }
                mVerifyingUpdates.remove(id);
            }
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
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            } else {
                Log.e(TAG, "Error while verifying the file", e);
            }
            return false;
        }
    }

    private void tryReleaseWakelock() {
        if (!hasActiveDownloads()) {
            mWakeLock.release();
        }
    }

    private synchronized void prepareForUncryptAndInstall(UpdateComponent component) {
        String uncryptFilePath = component.getFile().getAbsolutePath() + Constants.UNCRYPT_FILE_EXT;
        File uncryptFile = new File(uncryptFilePath);
        Thread thread = new Thread(new Runnable() {
            private long mLastUpdate = -1;

            final FileUtils.ProgressCallBack mProgressCallBack = progress -> {
                long now = SystemClock.elapsedRealtime();
                if (mLastUpdate < 0 || now - mLastUpdate > 500) {
                    notifyUpdateListener(StatusType.INSTALL, progress);
                    mLastUpdate = now;
                }
            };

            @Override
            public void run() {
                try {
                    FileUtils.copyFile(component.getFile(), uncryptFile, mProgressCallBack);
                    try {
                        mFilePermissions.add(PosixFilePermission.OWNER_READ);
                        mFilePermissions.add(PosixFilePermission.OWNER_WRITE);
                        mFilePermissions.add(PosixFilePermission.OTHERS_READ);
                        mFilePermissions.add(PosixFilePermission.GROUP_READ);
                        Files.setPosixFilePermissions(uncryptFile.toPath(), mFilePermissions);
                    } catch (IOException ignored) {}
                    installRecoveryPackage(uncryptFile);
                } catch (IOException e) {
                    Log.e(TAG, "Could not copy update", e);
                    //noinspection ResultOfMethodCallIgnored
                    uncryptFile.delete();
                    notifyUpdateListener(StatusType.INSTALL_ERROR, -1);
                }
            }
        });
        thread.start();
        notifyUpdateListener(StatusType.INSTALL, 0);
    }

    public void showUpdateNotification(int notificationType) {
        NotificationController notificationController = new NotificationController(mContext);
        notificationController.showNotification(notificationType, null);
    }

    public boolean hasActiveDownloads() {
        return mActiveDownloads > 0;
    }

    public boolean isDownloading(String id) {
        return mDownloads.containsKey(id) &&
                Objects.requireNonNull(mDownloads.get(id)).mDownloadClient != null;
    }

    public Map<String, DownloadEntry> getDownloads() {
        return mDownloads;
    }

    public boolean isVerifyingUpdate() {
        return mVerifyingUpdates.size() > 0;
    }

    public int getUpdateStatus() {
        PreferenceHelper preferenceHelper = new PreferenceHelper(mContext);
        return preferenceHelper.getIntValueByKey(Constants.KEY_UPDATE_STATUS);
    }

    public void setUpdateStatus(int status) {
        PreferenceHelper preferenceHelper = new PreferenceHelper(mContext);
        preferenceHelper.saveIntValue(Constants.KEY_UPDATE_STATUS, status);
    }

    private void addDownloadClient(DownloadEntry entry, DownloadClient client) {
        if (entry.mDownloadClient != null) {
            return;
        }
        entry.mDownloadClient = client;
        mActiveDownloads++;
    }

    private void removeDownloadClient(DownloadEntry entry) {
        if (entry.mDownloadClient == null) {
            return;
        }
        entry.mDownloadClient = null;
        mActiveDownloads--;
    }

    public void addUpdateListener(UpdateController.UpdateListener listener) {
        mListeners.add(listener);
    }

    public void removeUpdateListener(UpdateController.UpdateListener listener) {
        mListeners.remove(listener);
    }

    public void notifyUpdateListener(int status, int progress) {
        setUpdateStatus(status);
        for (UpdateController.UpdateListener listener : mListeners) {
            listener.onUpdateStatusChanged(status, progress);
        }
    }

    public interface UpdateListener {
        void onUpdateStatusChanged(int status, int progress);
    }

    private static class DownloadEntry {
        final UpdateComponent mComponent;
        DownloadClient mDownloadClient;
        private DownloadEntry(UpdateComponent component) {
            mComponent = component;
        }
    }
}
