package co.aospa.hub.controllers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.text.format.Formatter;

import androidx.core.app.NotificationCompat;

import co.aospa.hub.R;
import co.aospa.hub.components.UpdateComponent;
import co.aospa.hub.ui.activities.HubActivity;
import co.aospa.hub.util.Constants;

public class NotificationController {

    private static final int NOTIFICATION_ID = 10;

    private final Context mContext;

    public @interface NotificationType {
        int AVAILABLE = 0;
        int COMPLETED = 1;
        int REBOOT = 2;
        int TESTERS = 2;
    }

    public NotificationController(Context context) {
        mContext = context;
    }

    public void showNotification(int notificationType, UpdateComponent component) {
        NotificationChannel notificationChannel = new NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                mContext.getString(R.string.system_update_notification_channel),
                NotificationManager.IMPORTANCE_HIGH);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext,
                Constants.NOTIFICATION_CHANNEL_ID)
                .setContentIntent(getIntent(notificationType))
                .setSmallIcon(R.drawable.ic_system_update_24dp)
                .setContentTitle(getContentTitle(notificationType, component))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        getContentTitle(notificationType, component)))
                .setContentText(getContentText(notificationType))
                .setOngoing(getOngoing(notificationType));
        NotificationManager nM = mContext.getSystemService(NotificationManager.class);
        nM.createNotificationChannel(notificationChannel);
        nM.notify(NOTIFICATION_ID, builder.build());
    }

    public void cancelNotification() {
        NotificationManager nM = mContext.getSystemService(NotificationManager.class);
        nM.cancel(NOTIFICATION_ID);
    }

    private PendingIntent getIntent(int notificationType) {
        Intent notificationIntent = new Intent(mContext, HubActivity.class);
        if (notificationType == NotificationType.TESTERS) {
            // Todo add testers activity intent
        }
        return PendingIntent.getActivity(mContext, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private String getContentTitle(
            int notificationType, UpdateComponent component) {
        String contentTitle = null;
        switch (notificationType) {
            case NotificationType.AVAILABLE:
                contentTitle = getContentTitleFromComponent(component);
                break;
            case  NotificationType.COMPLETED:
                contentTitle = mContext.getResources().getString(
                        R.string.system_update_notification_update_completed);
                break;
            case  NotificationType.REBOOT:
                contentTitle = mContext.getResources().getString(
                        R.string.system_update_notification_update_finished_reboot);
                break;
        }
        return contentTitle;
    }

    private String getContentText(int notificationType) {
        String contentText = null;
        switch (notificationType) {
            case NotificationType.AVAILABLE:
                contentText = mContext.getResources().getString(
                        R.string.system_update_notification_update_available_desc);
                break;
            case  NotificationType.COMPLETED:
                contentText = mContext.getResources().getString(
                        R.string.system_update_notification_update_completed_desc);
                break;
            case  NotificationType.REBOOT:
                contentText = mContext.getResources().getString(
                        R.string.system_update_notification_update_finished_reboot_desc);
                break;
        }
        return contentText;
    }

    private boolean getOngoing(int notificationType) {
        return notificationType == NotificationType.AVAILABLE;
    }

    private String getContentTitleFromComponent(
            UpdateComponent component) {
        return component != null ? String.format(mContext.getResources().getString(
                        R.string.system_update_notification_update_available),
                component.getVersion(),
                component.getVersionNumber())
                : mContext.getResources().getString(
                R.string.system_update_notification_update_available_default);
    }
}
