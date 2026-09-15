/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import co.aospa.hub.R
import co.aospa.hub.ui.UpdatesActivity

/** Posts the notifications shown while the app is closed. */
class UpdateNotifier(private val context: Context) {
    private val notificationManager by lazy {
        context.getSystemService(NotificationManager::class.java)
    }

    /** Creates the channels, once per process, before anything posts. */
    fun createChannels() {
        notificationManager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_ONGOING,
                    context.getString(R.string.notification_channel_ongoing),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    CHANNEL_NEW_UPDATE,
                    context.getString(R.string.notification_channel_new_update),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    CHANNEL_FAILED,
                    context.getString(R.string.notification_channel_update_failed),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        )
    }

    /** Says a build is on offer. */
    fun showNewUpdate() {
        notificationManager.notify(
            ID_NEW_UPDATE,
            notification(CHANNEL_NEW_UPDATE)
                .setCategory(Notification.CATEGORY_SYSTEM)
                .setContentTitle(context.getString(R.string.notification_new_update_title))
                .setContentText(context.getString(R.string.notification_new_update_text))
                .build(),
        )
    }

    /** Says the install did not finish, and why. */
    fun showInstallFailed(reason: String) {
        notificationManager.notify(
            ID_FAILED,
            notification(CHANNEL_FAILED)
                .setCategory(Notification.CATEGORY_ERROR)
                .setContentTitle(context.getString(R.string.notification_install_failed_title))
                .setContentText(reason)
                .setStyle(Notification.BigTextStyle().bigText(reason))
                .build(),
        )
    }

    private fun notification(channel: String) =
        Notification.Builder(context, channel)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, UpdatesActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )

    companion object {
        /** Carries the foreground notification while work runs. */
        const val CHANNEL_ONGOING = "ongoing"

        private const val CHANNEL_NEW_UPDATE = "new_update"
        private const val CHANNEL_FAILED = "update_failed"

        private const val ID_NEW_UPDATE = 1
        private const val ID_FAILED = 2
    }
}
