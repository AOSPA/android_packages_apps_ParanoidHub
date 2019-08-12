/*
 * Copyright (C) 2017 The LineageOS Project
 * Copyright (C) 2019 ParanoidAndroid Project
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
package com.paranoid.paranoidhub.updater;

import android.os.UpdateEngine;
import android.os.UpdateEngineCallback;
import android.util.Log;

import com.paranoid.paranoidhub.utils.FileUtils;

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

    public static final int INSTALLING = 0;
    public static final int INSTALLED = 1;
    public static final int FAILED = 2;

    private static final String PAYLOAD_BIN_PATH = "payload.bin";
    private static final String PAYLOAD_PROPERTIES_PATH = "payload_properties.txt";

    private final File mUpdateFile;
    private boolean mIsInstallingUpdate;

    public interface InstallListener {
        void onInstallProgress(int status, int progress);
    }

    private ArrayList<InstallListener> mListeners = new ArrayList<InstallListener>();

    private final UpdateEngineCallback mUpdateEngineCallback = new UpdateEngineCallback() {

        @Override
        public void onStatusUpdate(int status, float percent) {
            switch (status) {
                case UpdateEngine.UpdateStatusConstants.DOWNLOADING:
                case UpdateEngine.UpdateStatusConstants.FINALIZING: {
                    int progress = Math.round(percent * 100);
                    notifySubscribers(INSTALLING, progress);
                }
                break;

                case UpdateEngine.UpdateStatusConstants.REPORTING_ERROR_EVENT: {
                    notifySubscribers(FAILED, 0);
                }
                break;
            }
        }

        @Override
        public void onPayloadApplicationComplete(int errorCode) {
            mIsInstallingUpdate = false;
            switch (errorCode) {
                case UpdateEngine.ErrorCodeConstants.SUCCESS: {
                    notifySubscribers(INSTALLED, 0);
                }
                break;

                default: {
                    notifySubscribers(FAILED, 0);
                }
                break;
            }
        }
    };

    public ABUpdateController(File updateFile) {
        mUpdateFile = updateFile;
    }

    public synchronized boolean start() {
        if (mIsInstallingUpdate) {
            return false;
        }
        mIsInstallingUpdate = startUpdate();
        return mIsInstallingUpdate;
    }

    public synchronized boolean isInstallingUpdate() {
        return mIsInstallingUpdate;
    }

    private boolean startUpdate() {
        if (!mUpdateFile.exists()) {
            Log.e(TAG, "The given update doesn't exist");
            return false;
        }

        notifySubscribers(INSTALLING, 0);

        long offset;
        String[] headerKeyValuePairs;
        try {
            ZipFile zipFile = new ZipFile(mUpdateFile);
            offset = FileUtils.getZipEntryOffset(zipFile, PAYLOAD_BIN_PATH);
            ZipEntry payloadPropEntry = zipFile.getEntry(PAYLOAD_PROPERTIES_PATH);
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
            Log.e(TAG, "Could not prepare " + mUpdateFile, e);
            notifySubscribers(FAILED, 0);
            return false;
        }

        UpdateEngine engine = new UpdateEngine();
        engine.bind(mUpdateEngineCallback);
        String zipFileUri = "file://" + mUpdateFile.getAbsolutePath();
        engine.applyPayload(zipFileUri, offset, 0, headerKeyValuePairs);

        return true;
    }

    private void notifySubscribers(int status, int progress) {
        Log.d(TAG, "Notifying subscribers");
        synchronized (mListeners) {
            for (InstallListener cb : mListeners) {
                cb.onInstallProgress(status, progress);
            }
        }
    }

    public void setListener(InstallListener listener) {
        synchronized (mListeners) {
            mListeners.add(listener);
        }
    }

    public void removeListener(InstallListener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    public static boolean isABUpdate(ZipFile zipFile) {
        return zipFile.getEntry(PAYLOAD_BIN_PATH) != null &&
                zipFile.getEntry(PAYLOAD_PROPERTIES_PATH) != null;
    }
}
