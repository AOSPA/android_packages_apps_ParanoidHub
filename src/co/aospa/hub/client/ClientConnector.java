/*
 * Copyright (C) 2022 Paranoid Android
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
package co.aospa.hub.client;

import android.content.Context;
import android.os.Handler;
import android.os.SystemProperties;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import co.aospa.hub.components.ComponentBuilder;
import co.aospa.hub.util.Constants;

public class ClientConnector implements DownloadClient.DownloadCallback {

    private static final String TAG = "ClientConnector";

    public static final int COMPONENT_DELAY_TIME_MILLIS = 4000;
    public static final String TARGET_DEVICE = SystemProperties.get(Constants.PROP_DEVICE);

    private final Context mContext;
    private DownloadClient mClient;
    private final List<ClientListener> mListeners = new ArrayList<>();

    public @interface TaskType {
        int CONFIG = 0;
        int CHANGELOG = 1;
        int UPDATES = 2;
    }

    public ClientConnector(Context context) {
        mContext = context;
    }

    public void initComponent(String component) {
        final Handler handler = new Handler();
        handler.postDelayed(() -> {
            File componentFile = Server.getComponentFile(mContext, component);
            if (componentFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                componentFile.delete();
            }
            File data = new File(componentFile.getAbsolutePath());
            final boolean updateComponent = component.contentEquals(ComponentBuilder.COMPONENT_UPDATES);
            String url = updateComponent ? Server.getUrl(mContext) + component + "/" + TARGET_DEVICE
                    : Server.getUrl(mContext) + component;
            Log.d(TAG, "Getting " + component + " from " + url + " to " + data.getName());
            try {
                mClient = new DownloadClient.Builder()
                        .setUrl(url)
                        .setDestination(data)
                        .setDownloadCallback(ClientConnector.this)
                        .setTask(ComponentBuilder.getTaskForComponent(component))
                        .build();
            } catch (IOException exception) {
                Log.d(TAG, "Could not build download client");
            }
            if (mClient != null) mClient.start();

        }, COMPONENT_DELAY_TIME_MILLIS);
    }

    public void addClientStatusListener(ClientListener listener) {
        mListeners.add(listener);
    }

    public void removeClientStatusListener(ClientListener listener) {
        mListeners.remove(listener);
    }

    private void notifyClientStatusSuccess(File destination, int task) {
        for (ClientListener listener : mListeners) {
            listener.onClientStatusSuccess(destination, task);
        }
    }

    private void notifyClientStatusFailure() {
        for (ClientListener listener : mListeners) {
            listener.onClientStatusFailure();
        }
    }

    @Override
    public void onResponse(int statusCode, String url, DownloadClient.Headers headers) {

    }

    @Override
    public void onSuccess(File destination, int task) {
        notifyClientStatusSuccess(destination, task);
    }

    @Override
    public void onFailure(boolean cancelled) {
        notifyClientStatusFailure();
    }

    public interface ClientListener {
        void onClientStatusSuccess(File destination, int task);
        void onClientStatusFailure();
    }
}
