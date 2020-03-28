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

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.os.SystemProperties;

import androidx.preference.PreferenceManager;

import com.paranoid.hub.R;
import com.paranoid.hub.HubActivity;
import com.paranoid.hub.misc.BuildInfoUtils;
import com.paranoid.hub.misc.Constants;
import com.paranoid.hub.misc.StringGenerator;
import com.paranoid.hub.notification.NotificationContract;
import com.paranoid.hub.notification.NotificationContractor;

import java.text.DateFormat;

public class UpdateReceiver extends BroadcastReceiver {

    public static final String ACTION_INSTALL_REBOOT =
            "com.paranoid.hub.action.INSTALL_REBOOT";

    private static boolean shouldShowUpdateFailedNotification(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // We can't easily detect failed re-installations
        if (prefs.getBoolean(Constants.PREF_INSTALL_AGAIN, false) ||
                prefs.getBoolean(Constants.PREF_INSTALL_NOTIFIED, false)) {
            return false;
        }
        long buildTimestamp = BuildInfoUtils.getBuildDateTimestamp();
        long lastBuildTimestamp = prefs.getLong(Constants.PREF_INSTALL_OLD_TIMESTAMP, -1);
        return buildTimestamp == lastBuildTimestamp;
    }

    private static void showUpdateFailedNotification(Context context) {
        NotificationContractor contractor = new NotificationContractor(context);
        Intent notificationIntent = new Intent(context, HubActivity.class);
        PendingIntent intent = PendingIntent.getActivity(context, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationContract contract = contractor.create(NotificationContractor.INSTALL_ERROR_NOTIFICATION_CHANNEL, true);
        contract.setTitle(context.getString(R.string.installing_error_update_notification_title));
        contract.setText(""); //Todo: Add a message here
        contract.setIcon(R.drawable.ic_system_update);
        contract.setDismissible(true);
        contract.setIntent(intent);

        contractor.present(0);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_INSTALL_REBOOT.equals(intent.getAction())) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            pm.reboot(null);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (shouldShowUpdateFailedNotification(context)) {
                prefs.edit().putBoolean(Constants.PREF_INSTALL_NOTIFIED, true).apply();
                showUpdateFailedNotification(context);
            }
        }
    }
}
