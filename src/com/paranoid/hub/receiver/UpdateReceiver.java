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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.os.SystemProperties;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import com.paranoid.hub.R;
import com.paranoid.hub.HubActivity;
import com.paranoid.hub.misc.BuildInfoUtils;
import com.paranoid.hub.misc.Constants;
import com.paranoid.hub.misc.StringGenerator;

import java.text.DateFormat;

public class UpdateReceiver extends BroadcastReceiver {

    public static final String ACTION_INSTALL_REBOOT =
            "com.paranoid.hub.action.INSTALL_REBOOT";

    private static final String INSTALL_ERROR_NOTIFICATION_CHANNEL =
            "install_error_notification_channel";

    private static boolean shouldShowUpdateFailedNotification(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // We can't easily detect failed re-installations
        if (preferences.getBoolean(Constants.PREF_INSTALL_AGAIN, false) ||
                preferences.getBoolean(Constants.PREF_INSTALL_NOTIFIED, false)) {
            return false;
        }

        long buildTimestamp = BuildInfoUtils.getBuildDateTimestamp();
        long lastBuildTimestamp = preferences.getLong(Constants.PREF_INSTALL_OLD_TIMESTAMP, -1);
        return buildTimestamp == lastBuildTimestamp;
    }

    private static void showUpdateFailedNotification(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        Intent notificationIntent = new Intent(context, HubActivity.class);
        PendingIntent intent = PendingIntent.getActivity(context, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationChannel notificationChannel = new NotificationChannel(
                INSTALL_ERROR_NOTIFICATION_CHANNEL,
                context.getString(R.string.update_failed_channel_title),
                NotificationManager.IMPORTANCE_LOW);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                INSTALL_ERROR_NOTIFICATION_CHANNEL)
                .setContentIntent(intent)
                .setSmallIcon(R.drawable.ic_system_update)
                .setContentTitle(context.getString(R.string.installing_error_update_notification_title))
                .setContentText(""); //Todo: Add a message here

        NotificationManager nm = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(notificationChannel);
        nm.notify(0, builder.build());
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_INSTALL_REBOOT.equals(intent.getAction())) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            pm.reboot(null);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            pref.edit().remove(Constants.PREF_NEEDS_REBOOT_ID).apply();

            if (shouldShowUpdateFailedNotification(context)) {
                pref.edit().putBoolean(Constants.PREF_INSTALL_NOTIFIED, true).apply();
                showUpdateFailedNotification(context);
            }
        }
    }
}
