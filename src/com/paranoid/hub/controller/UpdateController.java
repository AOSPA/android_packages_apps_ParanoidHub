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
package com.paranoid.hub.controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.paranoid.hub.HubUpdateController;
import com.paranoid.hub.misc.BuildInfoUtils;
import com.paranoid.hub.misc.Constants;
import com.paranoid.hub.misc.FileUtils;
import com.paranoid.hub.misc.Utils;
import com.paranoid.hub.model.UpdateInfo;
import com.paranoid.hub.model.UpdateStatus;

import java.io.File;
import java.io.IOException;

public class UpdateController {

    private static final String TAG = "UpdateController";

    private static UpdateController sInstance = null;
    private static String sInstallingUpdate = null;

    private Thread mPrepareUpdateThread;
    private volatile boolean mCanCancel;

    private final Context mContext;
    private final HubUpdateController mController;

    private UpdateController(Context context, HubUpdateController controller) {
        mContext = context.getApplicationContext();
        mController = controller;
    }

    public static synchronized UpdateController getInstance(Context context,
            HubUpdateController controller) {
        if (sInstance == null) {
            sInstance = new UpdateController(context, controller);
        }
        return sInstance;
    }

    public static synchronized boolean isInstalling() {
        return sInstallingUpdate != null;
    }

    public static synchronized boolean isInstalling(String downloadId) {
        return sInstallingUpdate != null && sInstallingUpdate.equals(downloadId);
    }

    public void install(String downloadId) {
        if (isInstalling()) {
            Log.e(TAG, "Already installing an update");
            return;
        }

        UpdateInfo update = mController.getUpdate(downloadId);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        long buildTimestamp = BuildInfoUtils.getBuildDateTimestamp();
        long lastBuildTimestamp = preferences.getLong(Constants.PREF_INSTALL_OLD_TIMESTAMP,
                buildTimestamp);
        boolean isReinstalling = buildTimestamp == lastBuildTimestamp;
        preferences.edit()
                .putLong(Constants.PREF_INSTALL_OLD_TIMESTAMP, buildTimestamp)
                .putLong(Constants.PREF_INSTALL_NEW_TIMESTAMP, update.getTimestamp())
                .putString(Constants.PREF_INSTALL_PACKAGE_PATH, update.getFile().getAbsolutePath())
                .putBoolean(Constants.PREF_INSTALL_AGAIN, isReinstalling)
                .putBoolean(Constants.PREF_INSTALL_NOTIFIED, false)
                .apply();

        if (Utils.isEncrypted(mContext, update.getFile())) {
            // uncrypt rewrites the file so that it can be read without mounting
            // the filesystem, so create a copy of it.
            prepareForUncryptAndInstall(update);
        } else {
            installPackage(update.getFile(), downloadId);
        }
    }

    private void installPackage(File update, String downloadId) {
        try {
            android.os.RecoverySystem.installPackage(mContext, update);
        } catch (IOException e) {
            Log.e(TAG, "Could not install update", e);
            mController.getActualUpdate(downloadId)
                    .setStatus(UpdateStatus.INSTALLATION_FAILED, mContext);
            mController.notifyUpdateStatusChanged(HubUpdateController.STATE_STATUS_CHANGED, downloadId);
        }
    }

    private synchronized void prepareForUncryptAndInstall(UpdateInfo update) {
        String uncryptFilePath = update.getFile().getAbsolutePath() + Constants.UNCRYPT_FILE_EXT;
        File uncryptFile = new File(uncryptFilePath);

        Runnable copyUpdateRunnable = new Runnable() {
            private long mLastUpdate = -1;

            FileUtils.ProgressCallBack mProgressCallBack = new FileUtils.ProgressCallBack() {
                @Override
                public void update(int progress) {
                    long now = SystemClock.elapsedRealtime();
                    if (mLastUpdate < 0 || now - mLastUpdate > 500) {
                        mController.getActualUpdate(update.getDownloadId())
                                .setInstallProgress(progress);
                        mController.notifyUpdateStatusChanged(HubUpdateController.STATE_INSTALL_PROGRESS, update.getDownloadId());
                        mLastUpdate = now;
                    }
                }
            };

            @Override
            public void run() {
                try {
                    mCanCancel = true;
                    FileUtils.copyFile(update.getFile(), uncryptFile, mProgressCallBack);
                    mCanCancel = false;
                    if (mPrepareUpdateThread.isInterrupted()) {
                        mController.getActualUpdate(update.getDownloadId())
                                .setStatus(UpdateStatus.INSTALLATION_CANCELLED, mContext);
                        mController.getActualUpdate(update.getDownloadId())
                                .setInstallProgress(0);
                        uncryptFile.delete();
                    } else {
                        installPackage(uncryptFile, update.getDownloadId());
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Could not copy update", e);
                    uncryptFile.delete();
                    mController.getActualUpdate(update.getDownloadId())
                            .setStatus(UpdateStatus.INSTALLATION_FAILED, mContext);
                } finally {
                    synchronized (UpdateController.this) {
                        mCanCancel = false;
                        mPrepareUpdateThread = null;
                        sInstallingUpdate = null;
                    }
                    mController.notifyUpdateStatusChanged(HubUpdateController.STATE_STATUS_CHANGED, update.getDownloadId());
                }
            }
        };

        mPrepareUpdateThread = new Thread(copyUpdateRunnable);
        mPrepareUpdateThread.start();
        sInstallingUpdate = update.getDownloadId();
        mCanCancel = false;

        mController.getActualUpdate(update.getDownloadId())
                .setStatus(UpdateStatus.INSTALLING, mContext);
        mController.notifyUpdateStatusChanged(HubUpdateController.STATE_STATUS_CHANGED, update.getDownloadId());
    }

    public synchronized void cancel() {
        if (!mCanCancel) {
            Log.d(TAG, "Nothing to cancel");
            return;
        }
        mPrepareUpdateThread.interrupt();
    }
}
