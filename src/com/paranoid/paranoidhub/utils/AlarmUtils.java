package com.paranoid.paranoidhub.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.paranoid.paranoidhub.receivers.NotificationAlarm;

public class AlarmUtils {
    public static final int DEFAULT_CHECK_TIME = 18000000; // five hours
    private static final int ROM_ALARM_ID = 122303221;

    public static void setAlarm(Context context, boolean trigger) {
        setAlarm(context, DEFAULT_CHECK_TIME, trigger);
    }

    private static void setAlarm(Context context, long time, boolean trigger) {
        Intent i = new Intent(context, NotificationAlarm.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pi = PendingIntent.getBroadcast(context,
                ROM_ALARM_ID, i,
                PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
        if (time > 0) {
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, trigger ? 0 : time, time, pi);
        }
    }

    public static boolean alarmExists(Context context) {
        return (PendingIntent.getBroadcast(context, ROM_ALARM_ID,
                new Intent(context, NotificationAlarm.class),
                PendingIntent.FLAG_NO_CREATE) != null);
    }
}
