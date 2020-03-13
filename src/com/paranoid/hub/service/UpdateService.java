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
package com.paranoid.hub.service;

import static com.paranoid.hub.model.UpdateStatus.UNKNOWN;
import static com.paranoid.hub.model.UpdateStatus.UNAVAILABLE;
import static com.paranoid.hub.model.UpdateStatus.CHECKING;
import static com.paranoid.hub.model.UpdateStatus.AVAILABLE;
import static com.paranoid.hub.model.UpdateStatus.STARTING;
import static com.paranoid.hub.model.UpdateStatus.DOWNLOADING;
import static com.paranoid.hub.model.UpdateStatus.DOWNLOADED;
import static com.paranoid.hub.model.UpdateStatus.PAUSED;
import static com.paranoid.hub.model.UpdateStatus.PAUSED_ERROR;
import static com.paranoid.hub.model.UpdateStatus.DELETED;
import static com.paranoid.hub.model.UpdateStatus.VERIFYING;
import static com.paranoid.hub.model.UpdateStatus.VERIFIED;
import static com.paranoid.hub.model.UpdateStatus.VERIFICATION_FAILED;
import static com.paranoid.hub.model.UpdateStatus.INSTALLING;
import static com.paranoid.hub.model.UpdateStatus.INSTALLED;
import static com.paranoid.hub.model.UpdateStatus.INSTALLATION_FAILED;
import static com.paranoid.hub.model.UpdateStatus.INSTALLATION_CANCELLED;
import static com.paranoid.hub.model.UpdateStatus.INSTALLATION_SUSPENDED;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.text.format.Formatter;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.paranoid.hub.R;
import com.paranoid.hub.HubActivity;
import com.paranoid.hub.HubUpdateController;
import com.paranoid.hub.HubUpdateController.StatusListener;
import com.paranoid.hub.controller.ABUpdateController;
import com.paranoid.hub.controller.UpdateController;
import com.paranoid.hub.misc.BuildInfoUtils;
import com.paranoid.hub.misc.StringGenerator;
import com.paranoid.hub.misc.Utils;
import com.paranoid.hub.model.UpdateInfo;
import com.paranoid.hub.model.UpdatePresenter;
import com.paranoid.hub.model.UpdateStatus;
import com.paranoid.hub.receiver.UpdateReceiver;
import com.paranoid.hub.receiver.UpdateCheckReceiver;

import java.io.IOException;
import java.text.DateFormat;
import java.text.NumberFormat;

public class UpdateService extends Service implements StatusListener {

    private static final String TAG = "UpdateService";

    public static final String ACTION_START_DOWNLOAD = "action_start_download";

    public static final String ACTION_DOWNLOAD_CONTROL = "action_download_control";
    public static final String EXTRA_DOWNLOAD_ID = "extra_download_id";
    public static final String EXTRA_DOWNLOAD_CONTROL = "extra_download_control";
    public static final String ACTION_INSTALL_UPDATE = "action_install_update";
    public static final String ACTION_INSTALL_STOP = "action_install_stop";

    public static final String ACTION_INSTALL_SUSPEND = "action_install_suspend";
    public static final String ACTION_INSTALL_RESUME = "action_install_resume";

    private static final String ONGOING_NOTIFICATION_CHANNEL =
            "ongoing_notification_channel";

    public static final int DOWNLOAD_RESUME = 0;
    public static final int DOWNLOAD_PAUSE = 1;

    public static final int NOTIFICATION_ID = 10;

    private final IBinder mBinder = new LocalBinder();
    private boolean mHasClients;

    private NotificationCompat.Builder mNotificationBuilder;
    private NotificationManager mNotificationManager;

    private HubUpdateController mController;

    @Override
    public void onCreate() {
        super.onCreate();
        mController = HubUpdateController.getInstance(this);

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel notificationChannel = new NotificationChannel(
                ONGOING_NOTIFICATION_CHANNEL,
                getString(R.string.ongoing_channel_title),
                NotificationManager.IMPORTANCE_LOW);
        mNotificationManager.createNotificationChannel(notificationChannel);
        mNotificationBuilder = new NotificationCompat.Builder(this,
                ONGOING_NOTIFICATION_CHANNEL);
        mNotificationBuilder.setSmallIcon(R.drawable.ic_system_update);
        mNotificationBuilder.setColor(getColor(R.color.theme_accent));
        mNotificationBuilder.setShowWhen(false);

        Intent notificationIntent = new Intent(this, HubActivity.class);
        PendingIntent intent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        mNotificationBuilder.setContentIntent(intent);

        mController.addUpdateStatusListener(this);
    }

    public class LocalBinder extends Binder {
        public UpdateService getService() {
            return UpdateService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        mHasClients = true;
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mHasClients = false;
        tryStopSelf();
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Starting service");

        if (intent == null || intent.getAction() == null) {
            if (ABUpdateController.isInstallingUpdate(this)) {
                // The service is being restarted.
                ABUpdateController controller = ABUpdateController.getInstance(this,
                        mController);
                controller.reconnect();
            }
        } else if (ACTION_START_DOWNLOAD.equals(intent.getAction())) {
            String downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID);
            mNotificationManager.cancel(NOTIFICATION_ID);
            mController.setDownloadEntry(UpdatePresenter.getUpdate());
            mController.startDownload(downloadId);
        } else if (ACTION_DOWNLOAD_CONTROL.equals(intent.getAction())) {
            String downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID);
            int action = intent.getIntExtra(EXTRA_DOWNLOAD_CONTROL, -1);
            if (action == DOWNLOAD_RESUME) {
                mController.resumeDownload(downloadId);
            } else if (action == DOWNLOAD_PAUSE) {
                mController.pauseDownload(downloadId);
            } else {
                Log.e(TAG, "Unknown download action");
            }
        } else if (ACTION_INSTALL_STOP.equals(intent.getAction())) {
            if (UpdateController.isInstalling()) {
                UpdateController controller = UpdateController.getInstance(this,
                        mController);
                controller.cancel();
            } else if (ABUpdateController.isInstallingUpdate(this)) {
                ABUpdateController controller = ABUpdateController.getInstance(this,
                        mController);
                controller.reconnect();
                controller.cancel();
            }
        } else if (ACTION_INSTALL_SUSPEND.equals(intent.getAction())) {
            if (ABUpdateController.isInstallingUpdate(this)) {
                ABUpdateController controller = ABUpdateController.getInstance(this,
                        mController);
                controller.reconnect();
                controller.suspend();
            }
        } else if (ACTION_INSTALL_RESUME.equals(intent.getAction())) {
            if (ABUpdateController.isInstallingUpdateSuspended(this)) {
                ABUpdateController controller = ABUpdateController.getInstance(this,
                        mController);
                controller.reconnect();
                controller.resume();
            }
        }
        return ABUpdateController.isInstallingUpdate(this) ? START_STICKY : START_NOT_STICKY;
    }

    public HubUpdateController getController() {
        return mController;
    }

    private void tryStopSelf() {
        if (!mHasClients && !mController.hasActiveDownloads() &&
                !mController.isInstallingUpdate()) {
            Log.d(TAG, "Service no longer needed, stopping");
            stopSelf();
        }
    }

    @Override
    public void onUpdateStatusChanged(int state, String downloadId) {
        if (state == HubUpdateController.STATE_STATUS_CHANGED) {
            UpdateInfo update = mController.getUpdate(downloadId);
            Bundle extras = new Bundle();
            extras.putString(HubUpdateController.EXTRA_DOWNLOAD_ID, downloadId);
            mNotificationBuilder.setExtras(extras);
            handleUpdateStatusChange(update);
        } else if (state == HubUpdateController.STATE_DOWNLOAD_PROGRESS) {
            UpdateInfo update = mController.getUpdate(downloadId);
            handleDownloadProgressChange(update);
        } else if (state == HubUpdateController.STATE_INSTALL_PROGRESS) {
            UpdateInfo update = mController.getUpdate(downloadId);
            handleInstallProgress(update);
        } else if (state == HubUpdateController.STATE_UPDATE_DELETE) {
            Bundle extras = mNotificationBuilder.getExtras();
            if (extras != null && downloadId.equals(
                extras.getString(HubUpdateController.EXTRA_DOWNLOAD_ID))) {
                mNotificationBuilder.setExtras(null);
                mNotificationManager.cancel(NOTIFICATION_ID);
            }
        }
    }

    private void handleUpdateStatusChange(UpdateInfo update) {
        if (update == null) return;
        switch (update.getStatus()) {
            case DELETED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                mNotificationBuilder.setOngoing(false);
                mNotificationManager.cancel(NOTIFICATION_ID);
                tryStopSelf();
                break;
            }
            case STARTING: {
                setNotificationTitle(R.string.starting_update_notification_title);
                setNotificationDescription(R.string.starting_update_notification_text);
                mNotificationBuilder.mActions.clear();
                mNotificationBuilder.setProgress(0, 0, true);
                mNotificationBuilder.setSmallIcon(android.R.drawable.stat_sys_download);
                mNotificationBuilder.setOngoing(true);
                mNotificationBuilder.setAutoCancel(false);
                mNotificationBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
                startForeground(NOTIFICATION_ID, mNotificationBuilder.build());
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                break;
            }
            case DOWNLOADING: {
                stopForeground(STOP_FOREGROUND_DETACH);
                mNotificationManager.cancel(NOTIFICATION_ID);
                mNotificationBuilder.mActions.clear();
                mNotificationBuilder.setSmallIcon(android.R.drawable.stat_sys_download);
                mNotificationBuilder.addAction(R.drawable.ic_notification_pause,
                        getString(R.string.downloading_button_text_pause),
                        getPausePendingIntent(update.getDownloadId()));
                mNotificationBuilder.setOngoing(true);
                mNotificationBuilder.setAutoCancel(false);
                mNotificationBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                break;
            }
            case DOWNLOADED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                setNotificationTitle(R.string.install_update_notification_title);
                setNotificationDescription(R.string.install_update_notification_text);
                mNotificationBuilder.mActions.clear();
                mNotificationBuilder.setSmallIcon(R.drawable.ic_system_update);
                mNotificationBuilder.setProgress(0, 0, false);
                mNotificationBuilder.setOngoing(true);
                mNotificationBuilder.setAutoCancel(true);
                mNotificationBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
            case PAUSED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                setNotificationTitle(R.string.paused_update_notification_title);
                setNotificationDescription(R.string.paused_update_notification_text);
                // In case we pause before the first progress update
                mNotificationBuilder.setProgress(100, update.getProgress(), false);
                mNotificationBuilder.mActions.clear();
                mNotificationBuilder.addAction(R.drawable.ic_notification_resume,
                        getString(R.string.downloading_button_text_resume),
                        getResumePendingIntent(update.getDownloadId()));
                mNotificationBuilder.setOngoing(false);
                mNotificationBuilder.setAutoCancel(false);
                mNotificationBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
            case PAUSED_ERROR: {
                stopForeground(STOP_FOREGROUND_DETACH);
                int progress = update.getProgress();
                setNotificationTitle(R.string.paused_error_update_notification_title);
                setNotificationDescription(R.string.paused_error_update_notification_text);
                // In case we pause before the first progress update
                mNotificationBuilder.setProgress(progress > 0 ? 100 : 0, progress, false);
                mNotificationBuilder.mActions.clear();
                mNotificationBuilder.setSmallIcon(android.R.drawable.stat_sys_warning);
                mNotificationBuilder.addAction(R.drawable.ic_notification_resume,
                        getString(R.string.downloading_button_text_resume),
                        getResumePendingIntent(update.getDownloadId()));
                mNotificationBuilder.setOngoing(false);
                mNotificationBuilder.setAutoCancel(true);
                mNotificationBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
            case VERIFYING: {
                setNotificationTitle(R.string.verifying_update_notification_title);
                setNotificationDescription(R.string.verifying_update_notification_text);
                mNotificationBuilder.setProgress(0, 0, true);
                mNotificationBuilder.setSmallIcon(R.drawable.ic_system_update);
                mNotificationBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
                mNotificationBuilder.mActions.clear();
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                break;
            }
            case VERIFIED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                setNotificationTitle(R.string.verified_update_notification_title);
                setNotificationDescription(R.string.verified_update_notification_text);
                mNotificationBuilder.setSmallIcon(R.drawable.ic_system_update);
                mNotificationBuilder.setProgress(0, 0, false);
                mNotificationBuilder.setOngoing(false);
                mNotificationBuilder.setAutoCancel(true);
                mNotificationBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
            case VERIFICATION_FAILED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                setNotificationTitle(R.string.verifying_error_update_notification_title);
                setNotificationDescription(R.string.verifying_error_update_notification_text);
                mNotificationBuilder.setSmallIcon(android.R.drawable.stat_sys_warning);
                mNotificationBuilder.setProgress(0, 0, false);
                mNotificationBuilder.setOngoing(false);
                mNotificationBuilder.setAutoCancel(true);
                mNotificationBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
            case INSTALLING: {
                mNotificationBuilder.mActions.clear();
                mNotificationBuilder.setSmallIcon(R.drawable.ic_system_update);
                mNotificationBuilder.setProgress(0, 0, false);
                if (ABUpdateController.isInstallingUpdate(this)) {
                    mNotificationBuilder.addAction(R.drawable.ic_notification_pause,
                            getString(R.string.installing_suspended_text),
                            getSuspendInstallationPendingIntent());
                }
                mNotificationBuilder.setOngoing(true);
                mNotificationBuilder.setAutoCancel(false);
                mNotificationBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
                startForeground(NOTIFICATION_ID, mNotificationBuilder.build());
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                break;
            }
            case INSTALLED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                setNotificationTitle(R.string.installed_update_notification_title);
                setNotificationDescription(R.string.installed_update_notification_text);
                mNotificationBuilder.mActions.clear();
                mNotificationBuilder.setSmallIcon(R.drawable.ic_system_update);
                mNotificationBuilder.setProgress(0, 0, false);
                mNotificationBuilder.addAction(R.drawable.ic_notification_restart,
                        getString(R.string.restart_button_text),
                        getRebootPendingIntent());
                mNotificationBuilder.setOngoing(false);
                mNotificationBuilder.setAutoCancel(true);
                mNotificationBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
            case INSTALLATION_FAILED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                setNotificationTitle(R.string.installing_error_update_notification_title);
                setNotificationDescription(R.string.installing_error_update_notification_text);
                mNotificationBuilder.setSmallIcon(android.R.drawable.stat_sys_warning);
                mNotificationBuilder.setProgress(0, 0, false);
                mNotificationBuilder.setOngoing(false);
                mNotificationBuilder.setAutoCancel(true);
                mNotificationBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
            case INSTALLATION_CANCELLED: {
                stopForeground(true);
                tryStopSelf();
                break;
            }
            case INSTALLATION_SUSPENDED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                setNotificationTitle(R.string.installing_suspended_update_notification_title);
                setNotificationDescription(R.string.installing_suspended_update_notification_text);
                // In case we pause before the first progress update
                mNotificationBuilder.setProgress(100, update.getProgress(), false);
                mNotificationBuilder.mActions.clear();
                mNotificationBuilder.addAction(R.drawable.ic_notification_resume,
                        getString(R.string.downloading_button_text_resume),
                        getResumeInstallationPendingIntent());
                mNotificationBuilder.setOngoing(true);
                mNotificationBuilder.setAutoCancel(false);
                mNotificationBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
        }
    }

    private void handleDownloadProgressChange(UpdateInfo update) {
        if (update == null) {
			return;
	    }
        int progress = update.getProgress();
        mNotificationBuilder.setProgress(100, progress, false);
        setNotificationTitle(R.string.downloading_notification_title);

        String speed = Formatter.formatFileSize(this, update.getSpeed());
        CharSequence eta = StringGenerator.formatETA(this, update.getEta() * 1000);
        mNotificationBuilder.setContentText(getString(R.string.downloading_notification_text, eta, speed));

        mNotificationBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
        mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
    }

    private void handleInstallProgress(UpdateInfo update) {
        if (update == null) {
			return;
	    }
        int progress = update.getInstallProgress();
        mNotificationBuilder.setProgress(100, progress, false);
        String percent = NumberFormat.getPercentInstance().format(progress / 100.f);
        boolean notAB = UpdateController.isInstalling();
        mNotificationBuilder.mActions.clear();
        mNotificationBuilder.setContentTitle(notAB ? getString(R.string.installing_update_notification_title) :
                update.getFinalizing() ?
                        getString(R.string.installing_finalizing_update_notification_text) :
                        getString(R.string.installing_preparing_update_notification_text));
        mNotificationBuilder.setContentText(percent);

        mNotificationBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
        mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
    }

    private void setNotificationTitle(int res) {
        String title = getString(res);
        mNotificationBuilder.setContentTitle(title);
    }

    private void setNotificationDescription(int res) {
        String text = getString(res);
        mNotificationBuilder.setContentText(text);
    }

    private PendingIntent getResumePendingIntent(String downloadId) {
        final Intent intent = new Intent(this, UpdateService.class);
        intent.setAction(ACTION_DOWNLOAD_CONTROL);
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        intent.putExtra(EXTRA_DOWNLOAD_CONTROL, DOWNLOAD_RESUME);
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getPausePendingIntent(String downloadId) {
        final Intent intent = new Intent(this, UpdateService.class);
        intent.setAction(ACTION_DOWNLOAD_CONTROL);
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        intent.putExtra(EXTRA_DOWNLOAD_CONTROL, DOWNLOAD_PAUSE);
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getRebootPendingIntent() {
        final Intent intent = new Intent(this, UpdateReceiver.class);
        intent.setAction(UpdateReceiver.ACTION_INSTALL_REBOOT);
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getSuspendInstallationPendingIntent() {
        final Intent intent = new Intent(this, UpdateService.class);
        intent.setAction(ACTION_INSTALL_SUSPEND);
        return PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getResumeInstallationPendingIntent() {
        final Intent intent = new Intent(this, UpdateService.class);
        intent.setAction(ACTION_INSTALL_RESUME);
        return PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
