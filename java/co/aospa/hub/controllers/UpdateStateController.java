package co.aospa.hub.controllers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;

import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import co.aospa.hub.client.ClientConnector;
import co.aospa.hub.components.ChangelogComponent;
import co.aospa.hub.components.Component;
import co.aospa.hub.components.ComponentBuilder;
import co.aospa.hub.components.OtaConfigComponent;
import co.aospa.hub.components.UpdateComponent;
import co.aospa.hub.ui.State;
import co.aospa.hub.ui.UpdateAvailableState;
import co.aospa.hub.ui.UpdateCheckingState;
import co.aospa.hub.ui.UpdateUnavailableState;
import co.aospa.hub.util.Constants;
import co.aospa.hub.util.PreferenceHelper;
import co.aospa.hub.util.Version;

public class UpdateStateController implements ClientConnector.ClientListener {

    private static final String TAG = "UpdateStateController";

    private final Context mContext;
    private final ClientConnector mClientConnector;
    private static UpdateStateController mStateController;
    private final List<StateListener> mListeners = new ArrayList<>();

    private OtaConfigComponent mOtaComponent;
    private ChangelogComponent mChangelogComponent;
    private UpdateComponent mUpdateComponent;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.equals(Constants.INTENT_ACTION_CHECK_UPDATES)) {
                getComponentFromServer(ComponentBuilder.COMPONENT_UPDATES);
            }
        }
    };

    public static synchronized UpdateStateController get(Context context) {
        if (mStateController == null) {
            mStateController = new UpdateStateController(context);
        }
        return mStateController;
    }

    public UpdateStateController(Context context) {
        mContext = context;
        mClientConnector = new ClientConnector(context);
        mClientConnector.addClientStatusListener(this);

        IntentFilter i = new IntentFilter();
        i.addAction(Constants.INTENT_ACTION_CHECK_UPDATES);
        context.registerReceiver(mReceiver, i);
    }

    public void getComponentFromServer(String component) {
        switch (component) {
            case ComponentBuilder.COMPONENT_UPDATES:
                long millis = System.currentTimeMillis();
                PreferenceHelper preferenceHelper = new PreferenceHelper(mContext);
                preferenceHelper.saveLongValue(Constants.KEY_LAST_UPDATE_CHECK, millis);
                updateState(new UpdateCheckingState());
                mClientConnector.initComponent(component);
            case ComponentBuilder.COMPONENT_CHANGELOG:
                mClientConnector.initComponent(component);
            default:
                updateState(new UpdateCheckingState());
                mClientConnector.initComponent(component);
        }
    }

    private void updateComponent(File data, int task) {
        switch (task) {
            case ClientConnector.TASK_UPDATES:
                mUpdateComponent = (UpdateComponent) ComponentBuilder.buildComponent(data, task);
                queueUpdateRequest();
                break;
            case ClientConnector.TASK_CHANGELOG:
                mChangelogComponent = (ChangelogComponent) ComponentBuilder.buildComponent(data, task);
                break;
            default:
                mOtaComponent = (OtaConfigComponent) ComponentBuilder.buildComponent(data, task);
                queueUpdateRequest();

        }
    }

    private void queueUpdateRequest() {
        if (mOtaComponent != null) {
            if (!mOtaComponent.isEnabledFromServer()) {
                updateState(new UpdateUnavailableState());
                Log.d(TAG, "Updates are disabled from server, ignoring request");
                return;
            }
            Log.d(TAG, ":queueUpdateRequest");
            UpdateComponent component =  mUpdateComponent;
            UpdateRequestTask requestTask = new UpdateRequestTask(this);
            requestTask.start(component);
        }
    }

    private void updateCurrentState() {
        State state = getState();
        notifyStateListeners(state);
    }

    private State getState() {
        PreferenceHelper preferenceHelper = new PreferenceHelper(mContext);
        String stateKey = preferenceHelper.getStringValueByKey(Constants.KEY_STATE);
        if (stateKey == null) {
            Log.d(TAG, ":getState - returned empty, setting to unavailable state");
            return new UpdateUnavailableState();
        }
        Gson gson = new Gson();
        return gson.fromJson(stateKey, State.class);
    }

    private void updateState(State state) {
        notifyStateListeners(state);
    }

    public Component getComponentForTask(int task) {
        Component component;
        if (task == ClientConnector.TASK_UPDATES) {
            component = mUpdateComponent;
        } else if (task == ClientConnector.TASK_CHANGELOG) {
            component = mChangelogComponent;
        } else {
            component = mOtaComponent;
        }
        return component;
    }

    public void addUpdateStateListener(StateListener listener) {
        mListeners.add(listener);
    }

    public void removeUpdateStateListener(StateListener listener) {
        mListeners.remove(listener);
    }

    private void notifyStateListeners(State state) {
        Handler handler = new Handler(mContext.getMainLooper());
        handler.post(() -> {
            for (StateListener listener : mListeners) {
                listener.onUpdateStateChanged(state);
            }
        });
    }

    @Override
    public void onClientStatusSuccess(File data, int task) {
        Log.d(TAG, ":onClientStatusSuccess - updating component");
        updateComponent(data, task);
    }

    public interface StateListener {
        void onUpdateStateChanged(State state);
    }

    private static final class UpdateRequestTask {

        private final UpdateStateController mController;

        public UpdateRequestTask(UpdateStateController controller) {
            mController = controller;
        }

        public void start(UpdateComponent component) {
            boolean available = isUpdateAvailable(component);
            if (available) {
                mController.updateState(new UpdateAvailableState());
            } else {
                mController.updateState(new UpdateUnavailableState());
            }
        }

        public boolean isUpdateAvailable(
                UpdateComponent component) {
            return component != null && component.getTimestamp() > Version.getCurrentTimestamp();
        }
    }
}
