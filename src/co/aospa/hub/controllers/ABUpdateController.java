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
import android.os.UpdateEngine;
import android.os.UpdateEngineCallback;
import android.util.Log;

import co.aospa.hub.components.UpdateComponent;
import co.aospa.hub.util.Constants;
import co.aospa.hub.util.PreferenceHelper;
import co.aospa.hub.util.Update;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ABUpdateController extends UpdateEngineCallback {

    private static final String TAG = "ABUpdateController";

    private static ABUpdateController sInstance = null;

    private final UpdateController mController;
    private UpdateComponent mComponent;
    private final Context mContext;

    private final UpdateEngine mUpdateEngine;
    private boolean mBound;

    private int mProgress = 0;

    private ABUpdateController(Context context, UpdateController controller) {
        mController = controller;
        mContext = context.getApplicationContext();
        mUpdateEngine = new UpdateEngine();
    }

    public static synchronized ABUpdateController getInstance(
            Context context, UpdateController controller) {
        if (sInstance == null) {
            sInstance = new ABUpdateController(context, controller);
        }
        return sInstance;
    }

    @SuppressLint("SetWorldReadable")
    public void install(UpdateComponent component) {
        Log.d(TAG, "Installing a/b update");
        if (isInstalling(mContext)) {
            Log.e(TAG, "Already installing an update");
            return;
        }
        mComponent = component;
        File file = mComponent.getFile();
        if (!file.exists()) {
            Log.e(TAG, "The given update doesn't exist");
            setUpdateStatus(UpdateController.StatusType.INSTALL_ERROR, -1);
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        file.setReadable(true, false);

        long offset;
        String[] headerKeyValuePairs;
        try {
            ZipFile zipFile = new ZipFile(file);
            offset = Update.getZipEntryOffset(zipFile, Constants.AB_PAYLOAD_BIN_PATH);
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
            setUpdateStatus(UpdateController.StatusType.INSTALL_ERROR, -1);
            return;
        }

        if (!mBound) {
            mBound = mUpdateEngine.bind(this);
            if (!mBound) {
                Log.e(TAG, "Could not bind");
                setUpdateStatus(UpdateController.StatusType.INSTALL_ERROR, -1);
                return;
            }
        }

        mUpdateEngine.setPerformanceMode(Constants.USE_AB_PERFORMANCE_MODE);

        String zipFileUri = "file://" + file.getAbsolutePath();
        mUpdateEngine.applyPayload(zipFileUri, offset, 0, headerKeyValuePairs);
        setUpdateStatus(UpdateController.StatusType.INSTALL, mProgress);

    }

    private void setUpdateStatus(int status, int progress) {
        if (status == UpdateController.StatusType.REBOOT) {
            mController.getDownloads().remove(mComponent.getId());
            mController.showUpdateNotification(NotificationController.NotificationType.REBOOT);
        }
        mController.notifyUpdateListener(status, progress);
    }

    public static synchronized boolean isInstalling(Context context) {
        PreferenceHelper preferenceHelper = new PreferenceHelper(context);
        int updateStatus = preferenceHelper.getIntValueByKey(Constants.KEY_UPDATE_STATUS);
        return (updateStatus == UpdateController.StatusType.INSTALL
                || updateStatus == UpdateController.StatusType.FINALIZE);
    }

    @Override
    public void onStatusUpdate(int status, float percent) {
        switch (status) {
            case UpdateEngine.UpdateStatusConstants.DOWNLOADING: {
                mProgress = Math.round(percent * 100);
                setUpdateStatus(UpdateController.StatusType.INSTALL, mProgress);
                Log.d(TAG, "onStatusUpdate: update engine - installing");
            }
            break;
            case UpdateEngine.UpdateStatusConstants.FINALIZING: {
                mProgress = Math.round(percent * 100);
                setUpdateStatus(UpdateController.StatusType.FINALIZE, mProgress);
                Log.d(TAG, "onStatusUpdate: update engine - finalizing");
            }
            break;
            case UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT: {
                setUpdateStatus(UpdateController.StatusType.REBOOT, -1);
                Log.d(TAG, "onStatusUpdate: update engine - needs reboot");
            }
            break;
        }
    }

    @Override
    public void onPayloadApplicationComplete(int errorCode) {
        if (errorCode != UpdateEngine.ErrorCodeConstants.SUCCESS) {
            setUpdateStatus(UpdateController.StatusType.INSTALL_ERROR, -1);
        }
    }
}