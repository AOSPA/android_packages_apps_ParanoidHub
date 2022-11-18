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
package co.aospa.hub.controllers;

import static co.aospa.hub.client.ClientConnector.TaskType.CHANGELOG;
import static co.aospa.hub.client.ClientConnector.TaskType.UPDATES;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import co.aospa.hub.UpdateStateService;
import co.aospa.hub.client.ClientConnector;
import co.aospa.hub.components.ChangelogComponent;
import co.aospa.hub.components.Component;
import co.aospa.hub.components.ComponentBuilder;
import co.aospa.hub.components.OtaConfigComponent;
import co.aospa.hub.components.UpdateComponent;
import co.aospa.hub.controllers.UpdateController.StatusType;
import co.aospa.hub.ui.State;
import co.aospa.hub.ui.state.UpdateAvailableState;
import co.aospa.hub.ui.state.UpdateCheckingState;
import co.aospa.hub.ui.state.UpdateDownloadErrorState;
import co.aospa.hub.ui.state.UpdateDownloadInstallState;
import co.aospa.hub.ui.state.UpdateDownloadPausedState;
import co.aospa.hub.ui.state.UpdateDownloadVerificationState;
import co.aospa.hub.ui.state.UpdateInstallErrorState;
import co.aospa.hub.ui.state.UpdateRebootState;
import co.aospa.hub.ui.state.UpdateUnavailableState;
import co.aospa.hub.ui.state.UpdateVerificationErrorState;
import co.aospa.hub.util.Constants;
import co.aospa.hub.util.PreferenceHelper;
import co.aospa.hub.util.Update;
import co.aospa.hub.util.Version;

public class UpdateStateController implements ClientConnector.ClientListener, UpdateController.UpdateListener {

    private static final String TAG = "UpdateStateController";

    private final Context mContext;
    private final ClientConnector mClientConnector;
    private final UpdateController mUpdateController;
    private static UpdateStateController sStateController;
    private final List<StateListener> mListeners = new ArrayList<>();

    private OtaConfigComponent mOtaComponent;
    private ChangelogComponent mChangelogComponent;
    private UpdateComponent mUpdateComponent;

    public static synchronized UpdateStateController get(Context context) {
        if (sStateController == null) {
            sStateController = new UpdateStateController(context);
        }
        return sStateController;
    }

    private UpdateStateController(Context context) {
        mContext = context.getApplicationContext();
        mClientConnector = new ClientConnector(mContext);
        mClientConnector.addClientStatusListener(this);

        mUpdateController = UpdateController.get(mContext);
        mUpdateController.addUpdateListener(this);
    }

    public void getComponentFromServer(String component, boolean userRequested) {
        switch (component) {
            case ComponentBuilder.COMPONENT_UPDATES:
                if (userRequested) {
                    long millis = System.currentTimeMillis();
                    PreferenceHelper preferenceHelper = new PreferenceHelper(mContext);
                    preferenceHelper.saveLongValue(Constants.KEY_LAST_UPDATE_CHECK, millis);
                    State state = new UpdateCheckingState();
                    updateState(state);
                    mClientConnector.initComponent(component);
                }
                break;
            case ComponentBuilder.COMPONENT_CHANGELOG:
                mClientConnector.initComponent(component);
                break;
            default:
                State state = new UpdateCheckingState();
                updateState(state);
                mClientConnector.initComponent(component);
                break;
        }
        if (!userRequested) {
            queueCachedUpdateRequest();
        }
    }

    private void updateComponent(File data, int task) {
        switch (task) {
            case UPDATES:
                mUpdateComponent = (UpdateComponent) ComponentBuilder.buildComponent(data, task);
                queueUpdateRequestState();
                break;
            case CHANGELOG:
                mChangelogComponent = (ChangelogComponent) ComponentBuilder.buildComponent(data, task);
                queueUpdateRequestStateInCycle();
                break;
            default:
                mOtaComponent = (OtaConfigComponent) ComponentBuilder.buildComponent(data, task);
                updateComponentInCycle();
                break;

        }
    }

    private void queueUpdateRequestState() {
        if (mOtaComponent != null) {
            if (!mOtaComponent.isEnabledFromServer()) {
                State state = new UpdateUnavailableState();
                updateState(state);
                Log.d(TAG, "Updates are disabled from server, ignoring request");
                return;
            }
            Log.d(TAG, ":queueUpdateRequest");
            UpdateRequestStateTask requestTask = new UpdateRequestStateTask(this);
            requestTask.start(mUpdateComponent != null ? mUpdateComponent : null, false);
        }
    }

    private void queueCachedUpdateRequest() {
        if (mUpdateComponent == null) {
            UpdateRequestStateTask requestTask = new UpdateRequestStateTask(this);
            requestTask.start(null, true);
        }
    }

    private void queueUpdateRequestStateInCycle() {
        queueUpdateRequestState();
    }

    private void updateComponentInCycle() {
        Intent intent = new Intent(mContext, UpdateStateService.class);
        intent.setAction(Constants.INTENT_ACTION_UPDATE_CHANGELOG);
        mContext.startService(intent);
    }

    private void updateState(State state, int progress) {
        notifyStateListeners(state, progress);
    }

    private void updateState(State state) {
        notifyStateListeners(state, -1);
    }

    public Component getComponentForTask(int task) {
        Component component;
        if (task == UPDATES) {
            component = mUpdateComponent;
        } else if (task == CHANGELOG) {
            component = mChangelogComponent;
        } else {
            component = mOtaComponent;
        }
        return component;
    }

    public void startDownloadTask() {
        UpdateComponent component = (UpdateComponent)
                getComponentForTask(UPDATES);
        if (mUpdateController != null) {
            mUpdateController.startDownload(component);
            return;
        }
        Log.d(TAG, "Could start download task because update component is null");
    }

    public void cancelOrPauseDownloadTask(boolean cancel) {
        UpdateComponent component = (UpdateComponent)
                getComponentForTask(UPDATES);
        if (mUpdateController != null) {
            mUpdateController.cancelOrPauseDownload(component, cancel);
            return;
        }
        Log.d(TAG, "Could pause download task because update component is null");
    }

    public void resumeDownloadTask() {
        UpdateComponent component = (UpdateComponent)
                getComponentForTask(UPDATES);
        if (mUpdateController != null) {
            mUpdateController.resumeDownload(component);
            return;
        }
        Log.d(TAG, "Could resume download task because update component is null");
    }

    public void startLegacyInstallTaskIfPossible() {
        UpdateComponent component = (UpdateComponent)
                getComponentForTask(UPDATES);
        mUpdateController.installRecoveryUpdateIfPossible(component);
    }

    public UpdateController getUpdateController() {
        return mUpdateController;
    }

    public void addUpdateStateListener(StateListener listener) {
        mListeners.add(listener);
    }

    public void removeUpdateStateListener(StateListener listener) {
        mListeners.remove(listener);
    }

    private void notifyStateListeners(State state, int progress) {
        Handler handler = new Handler(mContext.getMainLooper());
        handler.post(() -> {
            for (StateListener listener : mListeners) {
                listener.onUpdateStateChanged(state, progress);
            }
        });
    }

    @Override
    public void onClientStatusSuccess(File data, int task) {
        Log.d(TAG, "onClientStatusSuccess - updating component");
        updateComponent(data, task);
    }

    @Override
    public void onClientStatusFailure() {
        Log.d(TAG, "onClientStatusFailure");
    }

    @Override
    public void onUpdateStatusChanged(int status, int progress) {
        ChangelogComponent changelogComponent = (ChangelogComponent)
                getComponentForTask(CHANGELOG);
        UpdateComponent updateComponent = (UpdateComponent)
                getComponentForTask(UPDATES);
        State state = null;
        switch (status) {
            case StatusType.FINALIZE:
            case StatusType.DOWNLOAD:
            case StatusType.INSTALL:
                state = new UpdateDownloadInstallState(updateComponent, changelogComponent);
                break;
            case StatusType.DOWNLOAD_CANCEL:
                state = new UpdateAvailableState(updateComponent, changelogComponent);
                break;
            case StatusType.VERIFY:
                state = new UpdateDownloadVerificationState(updateComponent, changelogComponent);
                break;
            case StatusType.PAUSE:
                state = new UpdateDownloadPausedState(updateComponent, changelogComponent);
                break;
            case StatusType.DOWNLOAD_ERROR:
                state = new UpdateDownloadErrorState(updateComponent, changelogComponent);
                break;
            case StatusType.VERIFY_ERROR:
                state = new UpdateVerificationErrorState(updateComponent, changelogComponent);
                break;
            case StatusType.INSTALL_ERROR:
                state = new UpdateInstallErrorState(updateComponent, changelogComponent);
                break;
            case StatusType.REBOOT:
                state = new UpdateRebootState();
                break;
        }
        updateState(state, progress);
    }

    public interface StateListener {
        void onUpdateStateChanged(State state, int progress);
    }

    private static final class UpdateRequestStateTask {
        private final UpdateStateController mController;


        public UpdateRequestStateTask(UpdateStateController controller) {
            mController = controller;
        }

        public void start(UpdateComponent component, boolean cachedRequest) {
            if (cachedRequest) {
                File data = Update.getCachedUpdate(mController.mContext);
                if (data.exists()) {
                    mController.updateComponent(data, UPDATES);
                    Log.d(TAG, "Updating update component with cached update");
                } else {
                    Log.d(TAG, "No cached updates found, setting unavailable state");
                    compileState(new UpdateUnavailableState());
                }
                return;
            }

            boolean available = isUpdateAvailable(component);
            int updateStatus = getUpdateStatus();
            State state;
            ChangelogComponent changelogComponent = (ChangelogComponent)
                    mController.getComponentForTask(CHANGELOG);
            switch (updateStatus) {
                case StatusType.REBOOT:
                    state = new UpdateRebootState();
                    break;
                case StatusType.VERIFY:
                    state = component != null
                            ? new UpdateDownloadVerificationState(component, changelogComponent)
                            : new UpdateUnavailableState();
                    break;
                case StatusType.PAUSE:
                    state = component != null
                            ? new UpdateDownloadPausedState(component, changelogComponent)
                            : new UpdateUnavailableState();
                    break;
                case StatusType.FINALIZE:
                case StatusType.INSTALL:
                case StatusType.DOWNLOAD:
                    state = component != null
                            ? new UpdateDownloadInstallState(component, changelogComponent)
                            : new UpdateUnavailableState();
                    break;
                default:
                    state = available
                            ? new UpdateAvailableState(component, changelogComponent)
                            : new UpdateUnavailableState();

            }
            compileState(state);
            Log.d(TAG, "UpdateRequestStateTask - state: " + state);
        }

        private void compileState(State state) {
            mController.updateState(state);
        }

        public boolean isUpdateAvailable(
                UpdateComponent component) {
            Version version = new Version(component);
            return component != null & version.isUpdateAvailable();
        }

        public int getUpdateStatus() {
            UpdateController updateController = mController.getUpdateController();
            return updateController.getUpdateStatus();
        }
    }
}
