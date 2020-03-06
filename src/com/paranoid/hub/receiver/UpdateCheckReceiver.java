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
package com.paranoid.hub.receiver;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.paranoid.hub.HubActivity;
import com.paranoid.hub.R;
import com.paranoid.hub.download.DownloadClient;
import com.paranoid.hub.misc.BuildInfoUtils;
import com.paranoid.hub.misc.Constants;
import com.paranoid.hub.misc.Utils;
import com.paranoid.hub.model.Update;
import com.paranoid.hub.model.UpdatePresenter;
import com.paranoid.hub.service.UpdateService;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.UUID;

public class UpdateCheckReceiver extends BroadcastReceiver {

    private static final String TAG = "UpdateCheckReceiver";

    private static final String DAILY_CHECK_ACTION = "daily_check_action";
    private static final String ONESHOT_CHECK_ACTION = "oneshot_check_action";
    private static final String SNOOZE_DOWNLOAD_ACTION = "snooze_download_action";

    private static final String NEW_UPDATES_NOTIFICATION_CHANNEL =
            "new_updates_notification_channel";

    public static final int NOTIFICATION_ID = 9;

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Utils.cleanupDownloadsDir(context);
        }

        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Set a repeating alarm on boot to check for new updates once per day
            scheduleRepeatingUpdatesCheck(context);
        }

        if (!Utils.isNetworkAvailable(context)) {
            Log.d(TAG, "Network not available, scheduling new check");
            scheduleUpdatesCheck(context, false);
            return;
        }

        if (SNOOZE_DOWNLOAD_ACTION.equals(intent.getAction())) {
            // Set an alarm to present the update again in 1 hour
            scheduleUpdatesCheck(context, true);
            return;
        }

        final File json = Utils.getCachedUpdateList(context);
        final File jsonNew = new File(json.getAbsolutePath() + UUID.randomUUID());
        String url = Utils.getServerURL(context);
        DownloadClient.DownloadCallback callback = new DownloadClient.DownloadCallback() {
            @Override
            public void onFailure(boolean cancelled) {
                Log.e(TAG, "Could not download updates list, scheduling new check");
                scheduleUpdatesCheck(context, false);
            }

            @Override
            public void onResponse(int statusCode, String url,
                    DownloadClient.Headers headers) {
            }

            @Override
            public void onSuccess(File destination) {
                try {
                    if (json.exists() && UpdatePresenter.isNewUpdate(context, json, jsonNew)) {
                        Update update = UpdatePresenter.getUpdate();
                        showNotification(context, update.getVersion(), update.getDownloadId());
                        updateRepeatingUpdatesCheck(context);
                    }
                    jsonNew.renameTo(json);
                    long currentMillis = System.currentTimeMillis();
                    preferences.edit()
                            .putLong(Constants.PREF_LAST_UPDATE_CHECK, currentMillis)
                            .apply();
                    // In case we set a one-shot check because of a previous failure
                    cancelUpdatesCheck(context);
                } catch (IOException | JSONException e) {
                    Log.e(TAG, "Could not parse list, scheduling new check", e);
                    scheduleUpdatesCheck(context, false);
                }
            }
        };

        try {
            DownloadClient downloadClient = new DownloadClient.Builder()
                    .setUrl(url)
                    .setDestination(jsonNew)
                    .setDownloadCallback(callback)
                    .build();
            downloadClient.start();
        } catch (IOException e) {
            Log.e(TAG, "Could not fetch list, scheduling new check", e);
            scheduleUpdatesCheck(context, false);
        }
    }

    private static void showNotification(Context context, String version, String downloadId) {
        String buildInfo = String.format(
                context.getResources().getString(R.string.update_found_notification_text),
                BuildInfoUtils.getVersionFlavor(), version);
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel notificationChannel = new NotificationChannel(
                NEW_UPDATES_NOTIFICATION_CHANNEL,
                context.getString(R.string.new_updates_channel_title),
                NotificationManager.IMPORTANCE_HIGH);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                NEW_UPDATES_NOTIFICATION_CHANNEL);
        builder.setSmallIcon(R.drawable.ic_system_update);
        Intent notificationIntent = new Intent(context, HubActivity.class);
        PendingIntent intent = PendingIntent.getActivity(context, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(intent);
        builder.setContentTitle(context.getString(R.string.update_found_notification_title));
        builder.setStyle(new NotificationCompat.BigTextStyle().bigText(buildInfo));
        builder.setContentText(buildInfo);
        builder.mActions.clear();
        builder.addAction(R.drawable.ic_notification_download,
                context.getString(R.string.update_found_button_text), getDownloadPendingIntent(context, downloadId));
        builder.addAction(R.drawable.ic_notification_snooze,
                context.getString(R.string.update_found_notification_snooze), getSnoozePendingIntent(context));
        builder.setColor(ContextCompat.getColor(context, R.color.theme_accent));
        builder.setOngoing(true);
        builder.setAutoCancel(false);
        notificationManager.createNotificationChannel(notificationChannel);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private static PendingIntent getDownloadPendingIntent(Context context, String downloadId) {
        Intent intent = new Intent(context, UpdateService.class);
        intent.setAction(UpdateService.ACTION_START_DOWNLOAD);
        intent.putExtra(UpdateService.EXTRA_DOWNLOAD_ID, downloadId);
        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent getSnoozePendingIntent(Context context) {
        Intent intent = new Intent(context, UpdateCheckReceiver.class);
        intent.setAction(SNOOZE_DOWNLOAD_ACTION);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
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
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);

        Date nextCheckDate = new Date(System.currentTimeMillis() + millisToNextCheck);
        Log.d(TAG, "Setting one-shot updates check: " + nextCheckDate);
    }

    public static void cancelUpdatesCheck(Context context) {
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmMgr.cancel(getUpdatesCheckIntent(context));
        Log.d(TAG, "Cancelling pending one-shot check");
    }
}
