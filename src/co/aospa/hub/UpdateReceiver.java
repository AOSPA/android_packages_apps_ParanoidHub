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
package co.aospa.hub;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.util.Date;

import co.aospa.hub.client.ClientConnector;
import co.aospa.hub.components.ComponentBuilder;
import co.aospa.hub.components.UpdateComponent;
import co.aospa.hub.controllers.NotificationController;
import co.aospa.hub.controllers.UpdateController;
import co.aospa.hub.util.App;
import co.aospa.hub.util.Constants;
import co.aospa.hub.util.PreferenceHelper;
import co.aospa.hub.util.Version;

public class UpdateReceiver extends BroadcastReceiver implements ClientConnector.ClientListener {

    private static final String TAG = "UpdateReceiver";

    private static final long UPDATE_REQUEST_INTERVAL = 300000L; // 5 minutes
    private static final long UPDATE_REQUEST_INTERVAL_ONGOING = AlarmManager.INTERVAL_HALF_DAY;

    private static final String ACTION_UPDATE_REQUEST = "action_update_request";
    private static final String ACTION_UPDATE_REQUEST_ONGOING = "action_update_request_ongoing";

    private Context mContext;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "onReceive for " + Intent.ACTION_BOOT_COMPLETED);
            mContext = context;
            App.clearDownloadedFiles(context);
            if (wasUpdatePreviouslyApplied(context)) {
                Log.d(TAG, "Showing notification for previously applied update");
                // Reset the update status now that the update is applied
                PreferenceHelper preferenceHelper = new PreferenceHelper(context);
                preferenceHelper.saveIntValue(Constants.KEY_UPDATE_STATUS, -1);
                NotificationController notificationController = new NotificationController(context);
                notificationController.showNotification(
                        NotificationController.NotificationType.COMPLETED, null);
                scheduleRequestTask(context, true);
                return;
            }

            if (!App.isNetworkAvailable(context)) {
                Log.d(TAG, "Network not available, scheduling new check");
                scheduleRequestTask(context, false);
            } else {
                Log.d(TAG, "Checking for updates from boot completed intent");
                startUpdateRequestTask(context);
            }
        }
        boolean requestTask = (ACTION_UPDATE_REQUEST_ONGOING.equals(intent.getAction())
                || ACTION_UPDATE_REQUEST.equals(intent.getAction()));
        if (requestTask) {
            Log.d(TAG, "Checking for updates from alarm intent");
            startUpdateRequestTask(context);
        }
    }

    private void startUpdateRequestTask(Context context) {
        Intent checkUpdatesIntent = new Intent(context, UpdateStateService.class);
        checkUpdatesIntent.setAction(Constants.INTENT_ACTION_CHECK_UPDATES);
        context.startService(checkUpdatesIntent);
    }

    public static void cancelPendingRequestTasks(Context context) {
        AlarmManager am = context.getSystemService(AlarmManager.class);
        am.cancel(getRequestTaskIntent(context, ACTION_UPDATE_REQUEST));
        am.cancel(getRequestTaskIntent(context, ACTION_UPDATE_REQUEST_ONGOING));
    }

    public static void scheduleRequestTask(Context context, boolean ongoing) {
        cancelPendingRequestTasks(context);
        long millisToNextRequest = ongoing ? UPDATE_REQUEST_INTERVAL_ONGOING : UPDATE_REQUEST_INTERVAL;
        String requestTaskAction = ongoing ? ACTION_UPDATE_REQUEST_ONGOING : ACTION_UPDATE_REQUEST;
        PendingIntent requestTaskIntent = getRequestTaskIntent(context, requestTaskAction);
        AlarmManager am = context.getSystemService(AlarmManager.class);
        am.set(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + millisToNextRequest,
                requestTaskIntent);
        Date nextCheckDate = new Date(System.currentTimeMillis() + millisToNextRequest);
        Log.d(TAG, "Scheduling update request for action: "
                + requestTaskAction + " for time: " + nextCheckDate);
    }

    private static PendingIntent getRequestTaskIntent(Context context, String action) {
        Intent intent = new Intent(context, UpdateReceiver.class);
        intent.setAction(action);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private boolean wasUpdatePreviouslyApplied(Context context) {
        PreferenceHelper preferenceHelper = new PreferenceHelper(context);
        return (preferenceHelper.getIntValueByKey(Constants.KEY_UPDATE_STATUS)
                == UpdateController.StatusType.REBOOT);
    }

    @Override
    public void onClientStatusSuccess(File data, int task) {
        UpdateComponent component = (UpdateComponent) ComponentBuilder.buildComponent(data, task);
        Version version = new Version(component);
        if (component != null && version.isUpdateAvailable()) {
            Log.d(TAG, "Update available from receiver, show notification");
            NotificationController notificationController = new NotificationController(mContext);
            notificationController.showNotification(
                    NotificationController.NotificationType.AVAILABLE, component);
        } else {
            Log.d(TAG, "No update available from receiver");
        }
        scheduleRequestTask(mContext, true);
    }

    @Override
    public void onClientStatusFailure() {
        scheduleRequestTask(mContext, true);
    }
}
