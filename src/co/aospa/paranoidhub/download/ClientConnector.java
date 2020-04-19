/*
 * Copyright (C) 2020 Paranoid Android
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
package co.aospa.paranoidhub.download;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClientConnector implements DownloadClient.DownloadCallback {

    private static final String TAG = "ClientConnector";

    private List<ConnectorListener> mListeners = new ArrayList<>();

    private Context mContext;
    private DownloadClient mClient;
    private File mJson;

    public interface ConnectorListener {
        void onClientStatusFailure(boolean cancelled);
        void onClientStatusResponse(int statusCode, String url, DownloadClient.Headers headers);
        void onClientStatusSuccess(File oldJson, File newJson);
    }

    public ClientConnector(Context context) {
        mContext = context;
    }

    public void insert(File oldJson, File newJson, String url) {
        Log.d(TAG, "Old update table: " + oldJson.getName() + "New update table: " + newJson.getName());
        mJson = oldJson;
        mClient = null;
        try {
            mClient = new DownloadClient.Builder()
                    .setUrl(url)
                    .setDestination(newJson)
                    .setDownloadCallback(this)
                    .build();
        } catch (IOException exception) {
            Log.d(TAG, "Could not build download client");
            return;
        }
    }

    public void start() {
        mClient.start();
    }

    public void addClientStatusListener(ConnectorListener listener) {
        mListeners.add(listener);
    }

    public void removeClientStatusListener(ConnectorListener listener) {
        mListeners.remove(listener);
    }

    private void notifyClientStatusFailure(boolean cancelled) {
        for (ConnectorListener listener : mListeners) {
            listener.onClientStatusFailure(cancelled);
        }
    }

    private void notifyClientStatusResponse(int statusCode, String url,
        DownloadClient.Headers headers) {
        for (ConnectorListener listener : mListeners) {
            listener.onClientStatusResponse(statusCode, url, headers);
        }
    }

    private void notifyClientStatusSuccess(File oldJson, File newJson) {
        for (ConnectorListener listener : mListeners) {
            listener.onClientStatusSuccess(oldJson, newJson);
        }
    }

    @Override
    public void onFailure(final boolean cancelled) {
        Log.e(TAG, "Could not download updates");
        notifyClientStatusFailure(cancelled);
    }

    @Override
    public void onResponse(int statusCode, String url,
        DownloadClient.Headers headers) {
        notifyClientStatusResponse(statusCode, url, headers);
    }

    @Override
    public void onSuccess(File destination) {
        notifyClientStatusSuccess(mJson, destination);
    }
    
}
