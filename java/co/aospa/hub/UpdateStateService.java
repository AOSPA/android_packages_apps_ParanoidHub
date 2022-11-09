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
        if (Constants.INTENT_ACTION_UPDATE_CONFIG.equals(intent.getAction())) {
            getComponent(ComponentBuilder.COMPONENT_CONFIG);
        } else if (Constants.INTENT_ACTION_CHECK_UPDATES.equals(intent.getAction())) {
            getComponent(ComponentBuilder.COMPONENT_UPDATES);
        }
        return START_NOT_STICKY;
    }

    private void getComponent(String component) {
        UpdateStateController controller = getController();
        controller.getComponentFromServer(component);
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
