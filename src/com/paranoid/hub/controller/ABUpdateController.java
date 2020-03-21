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
import android.os.UpdateEngine;
import android.os.UpdateEngineCallback;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.paranoid.hub.HubController;
import com.paranoid.hub.R;
import com.paranoid.hub.misc.Constants;
import com.paranoid.hub.misc.Utils;
import com.paranoid.hub.model.Update;
import com.paranoid.hub.model.UpdateStatus;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ABUpdateController {

    private static final String TAG = "ABUpdateController";

    private static final String PREF_INSTALLING_AB_ID = "installing_ab_id";
    private static final String PREF_INSTALLING_SUSPENDED_AB_ID = "installing_suspended_ab_id";

    private static ABUpdateController sInstance = null;

    private final HubController mController;
    private final Context mContext;
    private String mDownloadId;

    private UpdateEngine mUpdateEngine;
    private boolean mBound;

    private boolean mFinalizing;
    private int mProgress;

    private final UpdateEngineCallback mUpdateEngineCallback = new UpdateEngineCallback() {

        @Override
        public void onStatusUpdate(int status, float percent) {
            Update update = mController.getActualUpdate(mDownloadId);
            if (update == null) {
                // We read the id from a preference, the update could no longer exist
                installationDone(status == UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT);
                return;
            }

            switch (status) {
                case UpdateEngine.UpdateStatusConstants.DOWNLOADING:
                case UpdateEngine.UpdateStatusConstants.FINALIZING: {
                    if (update.getStatus() != UpdateStatus.INSTALLING) {
                        update.setStatus(UpdateStatus.INSTALLING, mContext);
                        mController.notifyUpdateStatusChanged(update, HubController.STATE_STATUS_CHANGED);
                    }
                    mProgress = Math.round(percent * 100);
                    mController.getActualUpdate(mDownloadId).setInstallProgress(mProgress);
                    mFinalizing = status == UpdateEngine.UpdateStatusConstants.FINALIZING;
                    mController.getActualUpdate(mDownloadId).setFinalizing(mFinalizing);
                    mController.notifyUpdateStatusChanged(update, HubController.STATE_INSTALL_PROGRESS);
                }
                break;

                case UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT: {
                    installationDone(true);
                    update.setInstallProgress(0);
                    update.setStatus(UpdateStatus.INSTALLED, mContext);
                    mController.notifyUpdateStatusChanged(update, HubController.STATE_STATUS_CHANGED);
                    SharedPreferences prefs = mContext.getSharedPreferences(Utils.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
                    boolean deleteUpdatesDefault = mContext.getResources().getBoolean(R.bool.config_autoDeleteUpdatesDefault);
                    boolean deleteUpdate = prefs.getBoolean(Constants.PREF_AUTO_DELETE_UPDATES,
                            deleteUpdatesDefault);
                    if (deleteUpdate) {
                        mController.deleteUpdate(mDownloadId);
                    }
                }
                break;

                case UpdateEngine.UpdateStatusConstants.IDLE: {
                    // The service was restarted because we thought we were installing an
                    // update, but we aren't, so clear everything.
                    installationDone(false);
                }
                break;
            }
        }

        @Override
        public void onPayloadApplicationComplete(int errorCode) {
            if (errorCode != UpdateEngine.ErrorCodeConstants.SUCCESS) {
                installationDone(false);
                Update update = mController.getActualUpdate(mDownloadId);
                update.setInstallProgress(0);
                update.setStatus(UpdateStatus.INSTALLATION_FAILED, mContext);
                mController.notifyUpdateStatusChanged(update, HubController.STATE_STATUS_CHANGED);
            }
        }
    };

    public static synchronized boolean isInstallingUpdate(Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        return pref.getString(ABUpdateController.PREF_INSTALLING_AB_ID, null) != null ||
                pref.getString(Constants.PREF_NEEDS_REBOOT_ID, null) != null;
    }

    public static synchronized boolean isInstallingUpdate(Context context, String downloadId) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        return downloadId.equals(pref.getString(ABUpdateController.PREF_INSTALLING_AB_ID, null)) ||
                TextUtils.equals(pref.getString(Constants.PREF_NEEDS_REBOOT_ID, null), downloadId);
    }

    public static synchronized boolean isInstallingUpdateSuspended(Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        return pref.getString(ABUpdateController.PREF_INSTALLING_SUSPENDED_AB_ID, null) != null;
    }

    public static synchronized boolean isInstallingUpdateSuspended(Context context, String downloadId) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        return downloadId.equals(pref.getString(ABUpdateController.PREF_INSTALLING_SUSPENDED_AB_ID, null));
    }

    public static synchronized boolean isWaitingForReboot(Context context, String downloadId) {
        String waitingId = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(Constants.PREF_NEEDS_REBOOT_ID, null);
        return TextUtils.equals(waitingId, downloadId);
    }

    private ABUpdateController(Context context, HubController controller) {
        mController = controller;
        mContext = context.getApplicationContext();
        mUpdateEngine = new UpdateEngine();
    }

    public static synchronized ABUpdateController getInstance(Context context,
            HubController controller) {
        if (sInstance == null) {
            sInstance = new ABUpdateController(context, controller);
        }
        return sInstance;
    }

    public boolean install(String downloadId) {
        if (isInstallingUpdate(mContext)) {
            Log.e(TAG, "Already installing an update");
            return false;
        }

        mDownloadId = downloadId;

        File file = mController.getActualUpdate(mDownloadId).getFile();
        if (!file.exists()) {
            Log.e(TAG, "The given update doesn't exist");
            mController.getActualUpdate(downloadId)
                    .setStatus(UpdateStatus.INSTALLATION_FAILED, mContext);
            mController.notifyUpdateStatusChanged(mController.getActualUpdate(downloadId), HubController.STATE_STATUS_CHANGED);
            return false;
        }

        long offset;
        String[] headerKeyValuePairs;
        try {
            ZipFile zipFile = new ZipFile(file);
            offset = Utils.getZipEntryOffset(zipFile, Constants.AB_PAYLOAD_BIN_PATH);
            ZipEntry payloadPropEntry = zipFile.getEntry(Constants.AB_PAYLOAD_PROPERTIES_PATH);
            try (InputStream is = zipFile.getInputStream(payloadPropEntry);
                 InputStreamReader isr = new InputStreamReader(is);
                 BufferedReader br = new BufferedReader(isr)) {
                List<String> lines = new ArrayList<>();
                for (String line; (line = br.readLine()) != null;) {
                    lines.add(line);
                }
                headerKeyValuePairs = new String[lines.size()];
                headerKeyValuePairs = lines.toArray(headerKeyValuePairs);
            }
            zipFile.close();
        } catch (IOException | IllegalArgumentException e) {
            Log.e(TAG, "Could not prepare " + file, e);
            mController.getActualUpdate(mDownloadId)
                    .setStatus(UpdateStatus.INSTALLATION_FAILED, mContext);
            mController.notifyUpdateStatusChanged(mController.getActualUpdate(mDownloadId), HubController.STATE_STATUS_CHANGED);
            return false;
        }

        if (!mBound) {
            mBound = mUpdateEngine.bind(mUpdateEngineCallback);
            if (!mBound) {
                Log.e(TAG, "Could not bind");
                mController.getActualUpdate(downloadId)
                        .setStatus(UpdateStatus.INSTALLATION_FAILED, mContext);
                mController.notifyUpdateStatusChanged(mController.getActualUpdate(mDownloadId), HubController.STATE_STATUS_CHANGED);
                return false;
            }
        }

        SharedPreferences prefs = mContext.getSharedPreferences(Utils.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        boolean enableABPerfModeDefault = mContext.getResources().getBoolean(R.bool.config_abPerformanceModeDefault);
        boolean enableABPerfMode = prefs.getBoolean(Constants.PREF_AB_PERF_MODE, enableABPerfModeDefault);
        mUpdateEngine.setPerformanceMode(enableABPerfMode);

        String zipFileUri = "file://" + file.getAbsolutePath();
        mUpdateEngine.applyPayload(zipFileUri, offset, 0, headerKeyValuePairs);

        mController.getActualUpdate(mDownloadId).setStatus(UpdateStatus.INSTALLING, mContext);
        mController.notifyUpdateStatusChanged(mController.getActualUpdate(mDownloadId), HubController.STATE_STATUS_CHANGED);

        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(PREF_INSTALLING_AB_ID, mDownloadId)
                .apply();

        return true;
    }

    public boolean reconnect() {
        if (!isInstallingUpdate(mContext)) {
            Log.e(TAG, "reconnect: Not installing any update");
            return false;
        }

        if (mBound) {
            return true;
        }

        mDownloadId = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getString(PREF_INSTALLING_AB_ID, null);

        // We will get a status notification as soon as we are connected
        mBound = mUpdateEngine.bind(mUpdateEngineCallback);
        if (!mBound) {
            Log.e(TAG, "Could not bind");
            return false;
        }

        return true;
    }

    private void installationDone(boolean needsReboot) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        String id = needsReboot ? prefs.getString(PREF_INSTALLING_AB_ID, null) : null;
        prefs.edit()
            .putString(Constants.PREF_NEEDS_REBOOT_ID, id)
            .remove(PREF_INSTALLING_AB_ID)
            .apply();
        prefs.edit().putBoolean(Constants.NEEDS_REBOOT_AFTER_UPDATE, needsReboot).apply();
    }

    public boolean cancel() {
        if (!isInstallingUpdate(mContext)) {
            Log.e(TAG, "cancel: Not installing any update");
            return false;
        }

        if (!mBound) {
            Log.e(TAG, "Not connected to update engine");
            return false;
        }

        mUpdateEngine.cancel();
        installationDone(false);

        mController.getActualUpdate(mDownloadId)
                .setStatus(UpdateStatus.INSTALLATION_CANCELLED, mContext);
        mController.notifyUpdateStatusChanged(mController.getActualUpdate(mDownloadId), HubController.STATE_STATUS_CHANGED);

        return true;
    }

    public void setPerformanceMode(boolean enable) {
        mUpdateEngine.setPerformanceMode(enable);
    }

    public boolean suspend() {
        if (!isInstallingUpdate(mContext)) {
            Log.e(TAG, "cancel: Not installing any update");
            return false;
        }

        if (!mBound) {
            Log.e(TAG, "Not connected to update engine");
            return false;
        }

        mUpdateEngine.suspend();

        mController.getActualUpdate(mDownloadId)
                .setStatus(UpdateStatus.INSTALLATION_SUSPENDED, mContext);
        mController.notifyUpdateStatusChanged(mController.getActualUpdate(mDownloadId), HubController.STATE_STATUS_CHANGED);

        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(PREF_INSTALLING_SUSPENDED_AB_ID, mDownloadId)
                .apply();

        return true;
    }

    public boolean resume() {
        if (!isInstallingUpdateSuspended(mContext)) {
            Log.e(TAG, "cancel: No update is suspended");
            return false;
        }

        if (!mBound) {
            Log.e(TAG, "Not connected to update engine");
            return false;
        }

        mUpdateEngine.resume();

        mController.getActualUpdate(mDownloadId).setStatus(UpdateStatus.INSTALLING, mContext);
        mController.notifyUpdateStatusChanged(mController.getActualUpdate(mDownloadId), HubController.STATE_STATUS_CHANGED);
        mController.getActualUpdate(mDownloadId).setInstallProgress(mProgress);
        mController.getActualUpdate(mDownloadId).setFinalizing(mFinalizing);
        mController.notifyUpdateStatusChanged(mController.getActualUpdate(mDownloadId), HubController.STATE_INSTALL_PROGRESS);

        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .remove(PREF_INSTALLING_SUSPENDED_AB_ID)
                .apply();

        return true;
    }
}
