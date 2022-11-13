package co.aospa.hub;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import co.aospa.hub.components.ComponentBuilder;
import co.aospa.hub.controllers.UpdateStateController;
import co.aospa.hub.util.Constants;

public class UpdateStateService extends Service {

    private static final String TAG = "UpdateStateService";

    private final IBinder mBinder = new LocalBinder();
    private UpdateStateController mStateController;

    @Override
    public void onCreate() {
        super.onCreate();
        mStateController = UpdateStateController.get(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Start service for intent " + intent);
        boolean shouldStartSticky = false;
        if (Constants.INTENT_ACTION_CHECK_UPDATES.equals(intent.getAction())) {
            getComponent(ComponentBuilder.COMPONENT_UPDATES, true);
        } else if (Constants.INTENT_ACTION_UPDATE_CHANGELOG.equals(intent.getAction())) {
            getComponent(ComponentBuilder.COMPONENT_CHANGELOG, false);
        } else {
            getComponent(ComponentBuilder.COMPONENT_CONFIG, false);
        }

        if (Constants.INTENT_ACTION_UPDATE.equals(intent.getAction())) {
            UpdateStateController controller = getController();
            shouldStartSticky = true;
            String action = intent.getStringExtra(Constants.EXTRA_DOWNLOAD);
            switch (action) {
                case Constants.EXTRA_DOWNLOAD_ACTION_PAUSE:
                    controller.pauseDownloadTask();
                    break;
                case Constants.EXTRA_DOWNLOAD_ACTION_RESUME:
                    controller.resumeDownloadTask();
                    break;
                default:
                    controller.startDownloadTask();
            }
        } else if (Constants.INTENT_ACTION_UPDATE_INSTALL_LEGACY.equals(intent.getAction())) {
            UpdateStateController controller = getController();
            controller.startLegacyInstallTaskIfPossible();
        }
        return shouldStartSticky ? START_STICKY : START_NOT_STICKY;
    }

    private void getComponent(String component, boolean userRequested) {
        UpdateStateController controller = getController();
        controller.getComponentFromServer(component, userRequested);
    }

    public class LocalBinder extends Binder {
        public UpdateStateService getService() {
            return UpdateStateService.this;
        }
    }

    public UpdateStateController getController() {
        return mStateController;
    }
}
