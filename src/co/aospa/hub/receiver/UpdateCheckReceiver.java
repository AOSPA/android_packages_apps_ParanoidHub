/*
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
package co.aospa.hub.receiver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.util.Log;

import co.aospa.hub.HubActivity;
import co.aospa.hub.HubUpdateManager;
import co.aospa.hub.RolloutContractor;
import co.aospa.hub.R;
import co.aospa.hub.download.ClientConnector;
import co.aospa.hub.download.DownloadClient;
import co.aospa.hub.misc.Constants;
import co.aospa.hub.misc.Utils;
import co.aospa.hub.model.Update;
import co.aospa.hub.model.UpdatePresenter;
import co.aospa.hub.model.Version;
import co.aospa.hub.notification.NotificationContract;
import co.aospa.hub.notification.NotificationContractor;
import co.aospa.hub.service.UpdateService;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.UUID;

public class UpdateCheckReceiver extends BroadcastReceiver implements ClientConnector.ConnectorListener {

    private static final String TAG = "UpdateCheckReceiver";

    private static final String DAILY_CHECK_ACTION = "daily_check_action";
    private static final String ONESHOT_CHECK_ACTION = "oneshot_check_action";
    private static final String SNOOZE_DOWNLOAD_ACTION = "snooze_download_action";

    private static final String NEW_UPDATES_NOTIFICATION_CHANNEL =
            "new_updates_notification_channel";

    private Context mContext;
    private ClientConnector mConnector;
    private RolloutContractor mRolloutContractor;

    @Override
    public void onReceive(final Context context, Intent intent) {
        mContext = context;
        if (mConnector == null) {
            mConnector = new ClientConnector(context);
            mConnector.addClientStatusListener(this);
        }
        mRolloutContractor = new RolloutContractor(context);
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Utils.cleanupDownloadsDir(context);
            updateConfigurations();
            // Set a repeating alarm on boot to check for new updates once per day
            scheduleRepeatingUpdatesCheck(context);
        }

        if (!Utils.isNetworkAvailable(context)) {
            Log.d(TAG, "Network not available, scheduling new check");
            scheduleUpdatesCheck(context, false);
            return;
        }

        if (RolloutContractor.ROLLOUT_ACTION.equals(intent.getAction())) {
            mRolloutContractor.setScheduled(false);
            mRolloutContractor.setReady(true);
            Log.d(TAG, "Rollout iniated, start the check again");
        }

        if (SNOOZE_DOWNLOAD_ACTION.equals(intent.getAction())) {
            // Set an alarm to present the update again in 1 hour
            scheduleUpdatesCheck(context, true);
            return;
        }
        updateDeviceConfiguration();
    }

    private static void showNotification(Context context, Update update) {
        Version version = new Version(context, update);
        boolean isBetaUpdate = version.isBetaUpdate();
        Intent notificationIntent = new Intent(context, HubActivity.class);
        PendingIntent intent = PendingIntent.getActivity(context, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        String buildInfo = String.format(isBetaUpdate ?
                context.getResources().getString(R.string.update_found_notification_text_beta) :
                context.getResources().getString(R.string.update_found_notification_text),
                Version.getCurrentFlavor(), update.getVersion());
        NotificationContractor contractor = new NotificationContractor(context);
        NotificationContract contract = contractor.create(NotificationContractor.NEW_UPDATES_NOTIFICATION_CHANNEL, true);
        contract.setTitle(context.getResources().getString(R.string.update_found_notification_title));
        contract.setTextStyle(buildInfo);
        contract.setText(buildInfo);
        contract.setIcon(R.drawable.ic_system_update);
        contract.setColor(R.color.theme_accent);
        contract.setDismissible(false);
        contract.setIntent(intent);
        contractor.present(NotificationContractor.ID);
    }

    private static PendingIntent getRepeatingUpdatesCheckIntent(Context context) {
        Intent intent = new Intent(context, UpdateCheckReceiver.class);
        intent.setAction(DAILY_CHECK_ACTION);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    public static void updateRepeatingUpdatesCheck(Context context) {
        cancelRepeatingUpdatesCheck(context);
        scheduleRepeatingUpdatesCheck(context);
    }

    public static void scheduleRepeatingUpdatesCheck(Context context) {
        PendingIntent updateCheckIntent = getRepeatingUpdatesCheckIntent(context);
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmMgr.setRepeating(AlarmManager.RTC, System.currentTimeMillis() +
                Constants.UPDATE_CHECK_INTERVAL, Constants.UPDATE_CHECK_INTERVAL,
                updateCheckIntent);

        Date nextCheckDate = new Date(System.currentTimeMillis() +
                Constants.UPDATE_CHECK_INTERVAL);
        Log.d(TAG, "Setting automatic updates check: " + nextCheckDate);
    }

    public static void cancelRepeatingUpdatesCheck(Context context) {
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmMgr.cancel(getRepeatingUpdatesCheckIntent(context));
    }

    private static PendingIntent getUpdatesCheckIntent(Context context) {
        Intent intent = new Intent(context, UpdateCheckReceiver.class);
        intent.setAction(ONESHOT_CHECK_ACTION);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    public static void scheduleUpdatesCheck(Context context, boolean isSnoozed) {
        long millisToNextCheck = isSnoozed ? AlarmManager.INTERVAL_HOUR : AlarmManager.INTERVAL_HOUR * 2;
        PendingIntent updateCheckIntent = getUpdatesCheckIntent(context);
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmMgr.set(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + millisToNextCheck,
                updateCheckIntent);
        NotificationContractor contractor = new NotificationContractor(context);
        contractor.retract(NotificationContractor.ID);

        Date nextCheckDate = new Date(System.currentTimeMillis() + millisToNextCheck);
        Log.d(TAG, "Setting one-shot updates check: " + nextCheckDate);
    }

    public static void cancelUpdatesCheck(Context context) {
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmMgr.cancel(getUpdatesCheckIntent(context));
        Log.d(TAG, "Cancelling pending one-shot check");
    }

    private void updateConfigurations() {
        mRolloutContractor.setupDevice();
        updateDeviceConfiguration();
    }

    private void updateDeviceConfiguration() {
        File oldJson = Utils.getCachedUpdateList(mContext);
        File newJson = new File(oldJson.getAbsolutePath() + UUID.randomUUID());
        String url = Utils.getServerURL(mContext) + HubUpdateManager.DEVICE_FILE;
        Log.d(TAG, "Updating ota information from " + url);
        mConnector.insert(oldJson, newJson, url);
        mConnector.start();
    }

    @Override
    public void onClientStatusFailure(boolean cancelled) {
        Log.e(TAG, "Could not download updates list, scheduling new check");
        scheduleUpdatesCheck(mContext, false);
    }

    @Override
    public void onClientStatusResponse(int statusCode, String url, DownloadClient.Headers headers) {}

    @Override
    public void onClientStatusSuccess(File oldFile, File newFile) {
        try {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            if (oldFile.exists() && UpdatePresenter.isNewUpdate(mContext, oldFile, newFile, mRolloutContractor.isReady())) {
                Update update = UpdatePresenter.getUpdate();
                showNotification(mContext, update);
                updateRepeatingUpdatesCheck(mContext);
            }
            newFile.renameTo(oldFile);
            long currentMillis = System.currentTimeMillis();
            prefs.edit()
                    .putLong(Constants.PREF_LAST_UPDATE_CHECK, currentMillis)
                    .apply();
            // In case we set a one-shot check because of a previous failure
            cancelUpdatesCheck(mContext);
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Could not parse list, scheduling new check", e);
            scheduleUpdatesCheck(mContext, false);
        }
    }
}
