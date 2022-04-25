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
package co.aospa.hub.controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import androidx.preference.PreferenceManager;

import co.aospa.hub.HubController;
import co.aospa.hub.misc.Constants;
import co.aospa.hub.misc.FileUtils;
import co.aospa.hub.model.UpdateInfo;
import co.aospa.hub.model.UpdateStatus;
import co.aospa.hub.model.Version;

import java.io.File;
import java.io.IOException;

public class UpdateController {

    private static final String TAG = "UpdateController";

    private static UpdateController sInstance = null;

    private Thread mPrepareUpdateThread;
    private volatile boolean mCanCancel;

    private final Context mContext;
    private final HubController mController;

    private UpdateController(Context context, HubController controller) {
        mContext = context.getApplicationContext();
        mController = controller;
    }

    public static synchronized UpdateController getInstance(Context context,
            HubController controller) {
        if (sInstance == null) {
            sInstance = new UpdateController(context, controller);
        }
        return sInstance;
    }

    public static synchronized boolean isInstalling() {
        return false;
    }

    public void install(String downloadId) {
        if (isInstalling()) {
            Log.e(TAG, "Already installing an update");
            return;
        }

        UpdateInfo update = mController.getUpdate(downloadId);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        long buildTimestamp = Version.getCurrentTimestamp();
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
        installPackage(update.getFile(), downloadId);
    }

    private void installPackage(File update, String downloadId) {
        try {
            android.os.RecoverySystem.installPackage(mContext, update);
        } catch (IOException e) {
            Log.e(TAG, "Could not install update", e);
            mController.getActualUpdate(downloadId)
                    .setStatus(UpdateStatus.INSTALLATION_FAILED, mContext);
            mController.notifyUpdateStatusChanged(mController.getActualUpdate(downloadId), HubController.STATE_STATUS_CHANGED);
        }
    }

    public synchronized void cancel() {
        if (!mCanCancel) {
            Log.d(TAG, "Nothing to cancel");
            return;
        }
        mPrepareUpdateThread.interrupt();
    }

}
