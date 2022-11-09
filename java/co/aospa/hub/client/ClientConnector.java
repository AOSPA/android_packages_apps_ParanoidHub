package co.aospa.hub.client;

import android.content.Context;
import android.os.Handler;
import android.os.SystemProperties;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import co.aospa.hub.components.Component;
import co.aospa.hub.components.ComponentBuilder;
import co.aospa.hub.util.Constants;

public class ClientConnector implements DownloadClient.DownloadCallback {

    private static final String TAG = "ClientConnector";

    public static final String TARGET_DEVICE = SystemProperties.get(Constants.PROP_DEVICE);

    public static final int TASK_CONFIG = 0;
    public static final int TASK_CHANGELOG = 1;
    public static final int TASK_UPDATES = 2;

    private final Context mContext;
    private DownloadClient mClient;
    private final List<ClientListener> mListeners = new ArrayList<>();

    public ClientConnector(Context context) {
        mContext = context;
    }

    public void initComponent(String component) {
        final Handler handler = new Handler();
        handler.postDelayed(() -> {
            File componentFile = Server.getComponentFile(mContext, component);
            if (componentFile.exists()) {
                componentFile.delete();
            }
            File data = new File(componentFile.getAbsolutePath() + UUID.randomUUID());
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

        }, 4000);
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

    @Override
    public void onResponse(int statusCode, String url, DownloadClient.Headers headers) {

    }

    @Override
    public void onSuccess(File destination, int task) {
        notifyClientStatusSuccess(destination, task);
    }

    @Override
    public void onFailure(boolean cancelled) {

    }

    public interface ClientListener {
        void onClientStatusSuccess(File destination, int task);
    }
}
