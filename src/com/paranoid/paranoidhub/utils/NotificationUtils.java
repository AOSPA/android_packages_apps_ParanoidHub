package com.paranoid.paranoidhub.utils;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.support.v4.app.NotificationCompat;

import com.paranoid.paranoidhub.R;
import com.paranoid.paranoidhub.activities.SystemActivity;
import com.paranoid.paranoidhub.updater.Updater;

import java.io.Serializable;

public class NotificationUtils {
    public static final String FILES_INFO = "com.paranoid.paranoidhub.Utils.FILES_INFO";
    public static final int NOTIFICATION_ID = 122303235;
    private static Updater.PackageInfo[] sPackageInfosRom = new Updater.PackageInfo[0];

    public static void showNotification(Context context, Updater.PackageInfo[] infosRom) {
        Resources resources = context.getResources();

        if (infosRom != null) {
            sPackageInfosRom = infosRom;
        } else {
            infosRom = sPackageInfosRom;
        }

        Intent intent = new Intent(context, SystemActivity.class);
        NotificationInfo fileInfo = new NotificationInfo();
        fileInfo.mNotificationId = NOTIFICATION_ID;
        fileInfo.mPackageInfosRom = infosRom;
        intent.putExtra(FILES_INFO, fileInfo);
        PendingIntent pIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setContentTitle(resources.getString(R.string.update_found_title))
                .setSmallIcon(R.drawable.ic_launcher_mono)
                .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_launcher))
                .setContentIntent(pIntent)
                .setOngoing(true);

        builder.setContentText(resources.getString(R.string.rom_name) + " "
                + infosRom[0].getVersion().toString());

        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    public static class NotificationInfo implements Serializable {
        public int mNotificationId;
        public Updater.PackageInfo[] mPackageInfosRom;
    }
}
