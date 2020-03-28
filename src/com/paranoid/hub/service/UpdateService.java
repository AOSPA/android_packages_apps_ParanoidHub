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

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.text.format.Formatter;
import android.util.Log;

import com.paranoid.hub.R;
import com.paranoid.hub.HubActivity;
import com.paranoid.hub.HubController;
import com.paranoid.hub.HubController.StatusListener;
import com.paranoid.hub.controller.ABUpdateController;
import com.paranoid.hub.controller.UpdateController;
import com.paranoid.hub.misc.BuildInfoUtils;
import com.paranoid.hub.misc.StringGenerator;
import com.paranoid.hub.misc.Utils;
import com.paranoid.hub.model.Update;
import com.paranoid.hub.model.UpdateInfo;
import com.paranoid.hub.model.UpdatePresenter;
import com.paranoid.hub.model.UpdateStatus;
import com.paranoid.hub.notification.NotificationContract;
import com.paranoid.hub.notification.NotificationContractor;
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

    public static final int DOWNLOAD_RESUME = 0;
    public static final int DOWNLOAD_PAUSE = 1;

    private final IBinder mBinder = new LocalBinder();
    private boolean mHasClients;

    private HubController mController;
    private NotificationContractor mNotificationContractor;

    @Override
    public void onCreate() {
        super.onCreate();
        mController = HubController.getInstance(this);
        mNotificationContractor = new NotificationContractor(this);
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
            if (mController.isInstalling(this, true)) {
                // The service is being restarted.
                ABUpdateController controller = ABUpdateController.getInstance(this,
                        mController);
                controller.reconnect();
            }
        } else if (ACTION_START_DOWNLOAD.equals(intent.getAction())) {
            String downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID);
            mNotificationContractor.retract(NotificationContractor.ID);
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
        } else if (ACTION_INSTALL_UPDATE.equals(intent.getAction())) {
            String downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID);
            UpdateInfo update = mController.getUpdate(downloadId);
            if (!Utils.isDebug() && update.getPersistentStatus() != UpdateStatus.Persistent.VERIFIED) {
                throw new IllegalArgumentException(update.getDownloadId() + " is not verified");
            }
            try {
                if (Utils.isABUpdate(update.getFile())) {
                    ABUpdateController controller = ABUpdateController.getInstance(this,
                            mController);
                    controller.install(downloadId);
                } else {
                    UpdateController controller = UpdateController.getInstance(this,
                            mController);
                    controller.install(downloadId);
                }
            } catch (IOException e) {
                Log.e(TAG, "Could not install update", e);
                mController.getActualUpdate(downloadId)
                        .setStatus(UpdateStatus.INSTALLATION_FAILED, this);
                mController.notifyUpdateStatusChanged(mController.getActualUpdate(downloadId), HubController.STATE_STATUS_CHANGED);
            }
        } else if (ACTION_INSTALL_STOP.equals(intent.getAction())) {
            if (mController.isInstalling(this, false)) {
                UpdateController controller = UpdateController.getInstance(this,
                        mController);
                controller.cancel();
            } else if (mController.isInstalling(this, true)) {
                ABUpdateController controller = ABUpdateController.getInstance(this,
                        mController);
                controller.reconnect();
                controller.cancel();
            }
        } else if (ACTION_INSTALL_SUSPEND.equals(intent.getAction())) {
            if (mController.isInstalling(this, true)) {
                ABUpdateController controller = ABUpdateController.getInstance(this,
                        mController);
                controller.reconnect();
                controller.suspend();
            }
        } else if (ACTION_INSTALL_RESUME.equals(intent.getAction())) {
            if (HubController.isInstallSuspended(this)) {
                ABUpdateController controller = ABUpdateController.getInstance(this,
                        mController);
                controller.reconnect();
                controller.resume();
            }
        }
        return mController.isInstalling(this, true) ? START_STICKY : START_NOT_STICKY;
    }

    public HubController getController() {
        return mController;
    }

    private void tryStopSelf() {
        if (!mHasClients && !mController.hasActiveDownloads() &&
                !mController.isInstalling(this, false) && !mController.isInstalling(this, true)) {
            Log.d(TAG, "Service no longer needed, stopping");
            stopSelf();
        }
    }

    @Override
    public void onUpdateStatusChanged(Update update, int state) {
        if (update != null) {
            if (state == HubController.STATE_STATUS_CHANGED) {
                Bundle extras = new Bundle();
                extras.putString(HubController.EXTRA_DOWNLOAD_ID, update.getDownloadId());
                mNotificationContractor.setExtras(extras);
                handleUpdateStatusChange(update);
            } else if (state == HubController.STATE_UPDATE_DELETE) {
                Bundle extras = mNotificationContractor.getExtras();
                if (extras != null && update.getDownloadId().equals(
                    extras.getString(HubController.EXTRA_DOWNLOAD_ID))) {
                    mNotificationContractor.setExtras(null);
                    mNotificationContractor.retract(NotificationContractor.ID);
                }
            }
        }
    }

    private void handleUpdateStatusChange(Update update) {
        if (update == null) return;
        switch (update.getStatus()) {
            case DELETED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                NotificationContract contract = mNotificationContractor.create(NotificationContractor.ONGOING_NOTIFICATION_CHANNEL, false);
                mNotificationContractor.retract(NotificationContractor.ID);
                tryStopSelf();
                break;
            }
            case DOWNLOADED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                NotificationContract contract = mNotificationContractor.create(NotificationContractor.ONGOING_NOTIFICATION_CHANNEL, true);
                contract.setTitle(getString(R.string.install_update_notification_title));
                contract.setText(getString(R.string.install_update_notification_text));
                contract.setProgress(0, 0, false);
                contract.setIcon(R.drawable.ic_system_update);
                contract.setDismissible(true);
                if (!Utils.isABDevice()) mNotificationContractor.present(NotificationContractor.ID);
                tryStopSelf();
                break;
            }
            case VERIFICATION_FAILED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                NotificationContract contract = mNotificationContractor.create(NotificationContractor.ONGOING_NOTIFICATION_CHANNEL, true);
                contract.setTitle(getString(R.string.updating_failed_title));
                contract.setText(getString(R.string.verifying_error_update_notification_title));
                contract.setProgress(0, 0, false);
                contract.setIcon(R.drawable.ic_system_update);
                contract.setDismissible(true);
                mNotificationContractor.present(NotificationContractor.ID);
                tryStopSelf();
                break;
            }
            case VERIFIED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                NotificationContract contract = mNotificationContractor.create(NotificationContractor.ONGOING_NOTIFICATION_CHANNEL, true);
                contract.setTitle(getString(R.string.verified_update_notification_title));
                contract.setText(getString(R.string.verified_update_notification_text));
                contract.setProgress(0, 0, false);
                contract.setIcon(R.drawable.ic_system_update);
                contract.setDismissible(false);
                if (!Utils.isABDevice()) mNotificationContractor.present(NotificationContractor.ID);
                tryStopSelf();
                break;
            }
            case INSTALLATION_FAILED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                NotificationContract contract = mNotificationContractor.create(NotificationContractor.ONGOING_NOTIFICATION_CHANNEL, true);
                contract.setTitle(getString(R.string.updating_failed_title));
                contract.setText(getString(R.string.installing_error_update_notification_title));
                contract.setProgress(0, 0, false);
                contract.setIcon(R.drawable.ic_system_update);
                contract.setDismissible(true);
                mNotificationContractor.present(NotificationContractor.ID);
                tryStopSelf();
                break;
            }
            case INSTALLED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                NotificationContract contract = mNotificationContractor.create(NotificationContractor.ONGOING_NOTIFICATION_CHANNEL, true);
                contract.setTitle(getString(R.string.installed_update_notification_title));
                contract.setText(getString(R.string.installed_update_notification_text));
                contract.setProgress(0, 0, false);
                contract.setIcon(R.drawable.ic_system_update);
                contract.setDismissible(false);
                mNotificationContractor.present(NotificationContractor.ID);
                tryStopSelf();
                break;
            }
        }
    }
}
