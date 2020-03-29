/*
 * Copyright (C) 2020 Paranoid Android
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
package com.paranoid.hub;

import static com.paranoid.hub.model.UpdateStatus.UNKNOWN;
import static com.paranoid.hub.model.UpdateStatus.UNAVAILABLE;
import static com.paranoid.hub.model.UpdateStatus.CHECKING;
import static com.paranoid.hub.model.UpdateStatus.AVAILABLE;
import static com.paranoid.hub.model.UpdateStatus.STARTING;
import static com.paranoid.hub.model.UpdateStatus.DOWNLOADING;
import static com.paranoid.hub.model.UpdateStatus.DOWNLOAD_FAILED;
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

import android.Manifest;
import android.animation.LayoutTransition;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.icu.text.DateFormat;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.PowerManager;
import android.text.Html;
import android.text.Spanned;
import android.text.format.Formatter;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.paranoid.hub.HubController;
import com.paranoid.hub.HubController.StatusListener;
import com.paranoid.hub.download.DownloadClient;
import com.paranoid.hub.misc.Constants;
import com.paranoid.hub.misc.StringGenerator;
import com.paranoid.hub.misc.Utils;
import com.paranoid.hub.model.Configuration;
import com.paranoid.hub.model.Update;
import com.paranoid.hub.model.UpdateInfo;
import com.paranoid.hub.model.UpdatePresenter;
import com.paranoid.hub.model.UpdateStatus;
import com.paranoid.hub.model.Version;
import com.paranoid.hub.receiver.UpdateCheckReceiver;
import com.paranoid.hub.service.UpdateService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.json.JSONException;

public class HubActivity extends AppCompatActivity implements View.OnClickListener, StatusListener {

    private static final String TAG = "HubActivity";

    private static final int CHECK_NONE = -1;
    private static final int CHECK_NORMAL = 0;
    private static final int CHECK_LOCAL = 1;

    private boolean mRefreshUi = false;
    private int mProgress = -1;
    private String mDownloadId;

    private HubUpdateManager mManager;
    private UpdateService mUpdateService;

    private Handler mHandler = new Handler();

    private Button mButton;
    private ImageView mSecondaryButton;
    private ImageView mInfoIcon;
    private ProgressBar mProgressBar;
    private TextView mVersionHeader;
    private TextView mVersionHeaderInfo;
    private TextView mHeaderStatus;
    private TextView mInfoDescription;
    private TextView mUpdateDescription;
    private TextView mUpdateSize;
    private TextView mHeaderStatusMessage;
    private TextView mHeaderStatusStep;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hub_activity);

        mVersionHeader = (TextView) findViewById(R.id.system_update_version_header);
        mVersionHeaderInfo = (TextView) findViewById(R.id.system_update_version_header_info);
        mHeaderStatus = (TextView) findViewById(R.id.header_system_update_status);
        mProgressBar = (ProgressBar) findViewById(R.id.system_update_progress);
        mUpdateDescription = (TextView) findViewById(R.id.system_update_desc);
        mUpdateSize = (TextView) findViewById(R.id.system_update_size);
        mHeaderStatusMessage = (TextView) findViewById(R.id.header_system_update_status_message);
        mHeaderStatusStep = (TextView) findViewById(R.id.header_system_update_step);

        mInfoIcon = (ImageView) findViewById(R.id.system_update_info_icon);
        mInfoDescription = (TextView) findViewById(R.id.system_update_info_desc);

        mButton = (Button) findViewById(R.id.system_update_primary_button);
        mButton.setOnClickListener(this);

        mSecondaryButton = (ImageView) findViewById(R.id.system_update_secondary_button);
        mSecondaryButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent preferences = new Intent(HubActivity.this, HubPreferencesActivity.class);
                        startActivity(preferences);
                    }
                });

        ((ViewGroup) findViewById(R.id.system_update_header)).getLayoutTransition()
                .enableTransitionType(LayoutTransition.CHANGING);
        ((ViewGroup) findViewById(R.id.version_header)).getLayoutTransition()
                .enableTransitionType(LayoutTransition.CHANGING);
        ((ViewGroup) findViewById(R.id.system_update_footer)).getLayoutTransition()
                .enableTransitionType(LayoutTransition.CHANGING);

        /*if (ContextCompat.checkSelfPermission(HubActivity.this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(HubActivity.this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, 
                    Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);
        }*/
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(this, UpdateService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        if (mUpdateService != null) {
            HubController controller = mUpdateService.getController();
            controller.removeUpdateStatusListener(HubActivity.this);
            unbindService(mConnection);
        }
        super.onStop();
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            mRefreshUi = true;
            UpdateService.LocalBinder binder = (UpdateService.LocalBinder) service;
            mUpdateService = binder.getService();
            HubController controller = mUpdateService.getController();
            controller.addUpdateStatusListener(HubActivity.this);
            mManager = new HubUpdateManager(getApplicationContext(), controller, HubActivity.this);
            mManager.warmUpLogMatchMaker();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mManager = null;
            mUpdateService = null;
        }
    };

    @Override
    public void onUpdateStatusChanged(Update update, int state) {
        mDownloadId = update != null ? update.getDownloadId() : "0";
        runOnUiThread(() -> {
            if (state == HubController.STATE_DOWNLOAD_PROGRESS 
                    || state == HubController.STATE_INSTALL_PROGRESS) {
                if (mRefreshUi) {
                    updateMessages(update, CHECK_NONE);
                    mRefreshUi = false;
                }
                updateProgressForState(update, state);
            } else {
                updateMessages(update, CHECK_NONE);
                updateProgressForState(update, state);
            }
        });
    }

    private void updateStatusAndInfo(Update update, int checkForUpdates) {
        boolean isChecking = (checkForUpdates == CHECK_LOCAL || checkForUpdates == CHECK_NORMAL);
        if (update != null && (update.getStatus() != UNAVAILABLE || isChecking)) {
            if (update.getStatus() != UNAVAILABLE || isChecking) {
                if (mVersionHeader.getVisibility() != View.VISIBLE) {
                    mVersionHeader.setVisibility(View.VISIBLE);
                }
                mVersionHeaderInfo.setVisibility(View.VISIBLE);
                mUpdateSize.setVisibility(View.VISIBLE);

                mVersionHeader.setTypeface(mVersionHeader.getTypeface(), Typeface.BOLD);
                mVersionHeader.setText(String.format(
                        getResources().getString(R.string.update_found_text),
                        Version.getCurrentFlavor(), update.getVersion()));

                mVersionHeaderInfo.setMovementMethod(LinkMovementMethod.getInstance());
                mVersionHeaderInfo.setText(Html.fromHtml(getResources().getString(R.string.update_found_text_info), Html.FROM_HTML_MODE_COMPACT));

                mUpdateSize.setText(String.format(
                        getResources().getString(R.string.update_found_size),
                        Formatter.formatShortFileSize(this, update.getFileSize())));
                Configuration config = mManager.getConfiguration();
                if (config != null) {
                    mUpdateDescription.setText(String.format(
                            getResources().getString(R.string.update_found_changelog), config.getChangelog()));
                } else {
                    mUpdateDescription.setText(getResources().getString(R.string.update_found_changelog_default));
                }
            }
        }
    }

    private void beginHubReset() {
        mHeaderStatus.setText(getResources().getString(R.string.error_update_refreshing_hub));
        reportMessage(R.string.error_update_refreshing_hub_header_status);
        mInfoIcon.setVisibility(View.VISIBLE);
        mInfoDescription.setVisibility(View.VISIBLE);
        mInfoDescription.setText(getResources().getString(R.string.error_update_refreshing_hub_desc));
        mSecondaryButton.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.VISIBLE);
        mProgressBar.setIndeterminate(true);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                HubController controller = mUpdateService.getController();
                controller.resetHub();
            }
        }, 10000);
    }

    private void updateMessages(Update update, int checkForUpdates) {
        mButton.setVisibility(View.GONE);
        mVersionHeader.setVisibility(View.GONE);
        mSecondaryButton.setVisibility(View.VISIBLE);
        mHeaderStatusStep.setVisibility(View.GONE);
        HubController controller = mUpdateService.getController();
        if (checkForUpdates == CHECK_LOCAL) {
            mHeaderStatus.setText(getResources().getString(R.string.update_checking_local_title));
            mVersionHeader.setVisibility(View.GONE);
            mVersionHeaderInfo.setVisibility(View.GONE);
            mUpdateSize.setVisibility(View.GONE);
            mSecondaryButton.setVisibility(View.GONE);
            updateStatusAndInfo(update, checkForUpdates);
            updateProgress(update, checkForUpdates);
            updateSystemStatus(update, checkForUpdates, false);
            return;
        } else if (checkForUpdates == CHECK_NORMAL) {
            mHeaderStatus.setText(getResources().getString(R.string.update_checking_title));
            mVersionHeader.setVisibility(View.GONE);
            mVersionHeaderInfo.setVisibility(View.GONE);
            mUpdateSize.setVisibility(View.GONE);
            mSecondaryButton.setVisibility(View.GONE);
            updateStatusAndInfo(update, checkForUpdates);
            updateProgress(update, checkForUpdates);
            updateSystemStatus(update, checkForUpdates, false);
            return;
        }

        if (controller.getUpdateStatus() == AVAILABLE && update == null) {
            beginHubReset();
            return;
        } else if (update == null) {
            mHeaderStatus.setText(getResources().getString(R.string.no_updates_title));
            mButton.setText(R.string.button_check_for_update);
            mButton.setVisibility(View.VISIBLE);
            updateSystemStatus(update, checkForUpdates, true);
            Log.d(TAG, "Update is null");
            return;
        }

        int status = update.getStatus();
        Log.d(TAG, "Current update status: " + status);
        switch (status) {
            default:
            case UNAVAILABLE:
                mHeaderStatus.setText(getResources().getString(R.string.no_updates_title));
                mButton.setText(R.string.button_check_for_update);
                mButton.setVisibility(View.VISIBLE);
                break;
            case AVAILABLE:
                if (update != null) {
                    mHeaderStatus.setText(getResources().getString(R.string.update_found_title));
                    mVersionHeader.setVisibility(View.VISIBLE);
                    mButton.setText(R.string.button_update_found);
                    mButton.setVisibility(View.VISIBLE);
                    mInfoDescription.setText(getResources().getString(R.string.update_found_warning_and_desc));
                }
                break;
            case STARTING:
                mHeaderStatus.setText(getResources().getString(R.string.starting_update_title));
                mVersionHeader.setVisibility(View.VISIBLE);
                mProgressBar.setIndeterminate(true);
                break;
            case DOWNLOADING:
                mHeaderStatus.setText(getResources().getString(R.string.downloading_title));
                mHeaderStatusStep.setText(Utils.isDebug() ? 
                        getResources().getString(R.string.updating_step_downloading_title) :
                        getResources().getString(R.string.updating_step_downloading_verify_title));
                mVersionHeader.setVisibility(View.VISIBLE);
                mButton.setText(R.string.button_pause_update);
                mButton.setVisibility(View.VISIBLE);
                mInfoDescription.setText(getResources().getString(R.string.downloading_performance_mode_warning_and_desc));
                break;
            case DOWNLOAD_FAILED:
                mHeaderStatus.setText(getResources().getString(R.string.updating_failed_title));
                mVersionHeader.setVisibility(View.VISIBLE);
                mButton.setText(R.string.button_try_again);
                mButton.setVisibility(View.VISIBLE);
                reportMessage(R.string.downloading_error_update_title);
                break;
            case PAUSED:
                mHeaderStatus.setText(getResources().getString(R.string.downloading_paused_title));
                mHeaderStatusStep.setText(Utils.isDebug() ? 
                        getResources().getString(R.string.updating_step_downloading_paused_title) :
                        getResources().getString(R.string.updating_step_downloading_paused_verify_title));
                mVersionHeader.setVisibility(View.VISIBLE);
                mButton.setText(R.string.button_resume_update);
                mButton.setVisibility(View.VISIBLE);
                break;
            case VERIFYING:
                mHeaderStatus.setText(getResources().getString(R.string.verifying_update_title));
                mVersionHeader.setVisibility(View.VISIBLE);
                mProgressBar.setIndeterminate(true);
                break;
            case VERIFICATION_FAILED:
                mHeaderStatus.setText(getResources().getString(R.string.updating_failed_title));
                mVersionHeader.setVisibility(View.VISIBLE);
                mButton.setText(R.string.button_try_again);
                mButton.setVisibility(View.VISIBLE);
                reportMessage(R.string.verifying_error_update_notification_title);
                break;
            case VERIFIED:
                if (Utils.isABDevice()) {
                    Log.d(TAG, "This is a A/B update, starting install");
                    Utils.triggerUpdate(getApplicationContext(), update.getDownloadId());
                } else {
                    mHeaderStatus.setText(getResources().getString(R.string.install_title));
                    mVersionHeader.setVisibility(View.VISIBLE);
                    mButton.setText(R.string.button_install_update);
                    mButton.setVisibility(View.VISIBLE);
                    mInfoDescription.setText(Utils.isABDevice() ? 
                            getResources().getString(R.string.install_warning_and_desc_ab) :
                            getResources().getString(R.string.install_warning_and_desc));
                    mProgressBar.setIndeterminate(false);
                    reportMessage(R.string.verified_download_snack);
                }
                break;
            case INSTALLATION_CANCELLED:
            case INSTALLATION_FAILED:
                mHeaderStatus.setText(getResources().getString(R.string.updating_failed_title));
                mVersionHeader.setVisibility(View.VISIBLE);
                mButton.setText(R.string.button_try_again);
                mButton.setVisibility(View.VISIBLE);
                reportMessage(R.string.installing_error_update_notification_title);
            case DOWNLOADED:
                if (Utils.isABDevice()) {
                    Log.d(TAG, "This is a A/B update, starting install");
                    Utils.triggerUpdate(getApplicationContext(), update.getDownloadId());
                } else {
                    mHeaderStatus.setText(getResources().getString(R.string.install_title));
                    mVersionHeader.setVisibility(View.VISIBLE);
                    mButton.setText(R.string.button_install_update);
                    mButton.setVisibility(View.VISIBLE);
                    mInfoDescription.setText(getResources().getString(Utils.isABDevice() 
                            ? R.string.install_warning_and_desc_ab 
                            : R.string.install_warning_and_desc));
                    mProgressBar.setIndeterminate(false);
                }
                break;
            case INSTALLING:
                if (update != null) {
                    mHeaderStatus.setText(getResources().getString(R.string.installing_title));
                    mHeaderStatusStep.setText(Utils.isDebug() ? 
                            getResources().getString(update.getFinalizing() ? 
                            R.string.updating_step_installing_finalizing_title : 
                            R.string.updating_step_installing_title) : 
                            getResources().getString(update.getFinalizing() ? 
                            R.string.updating_step_installing_finalizing_verify_title : 
                            R.string.updating_step_installing_verify_title));
                    mVersionHeader.setVisibility(View.VISIBLE);
                    mButton.setText(R.string.button_pause_update);
                    mButton.setVisibility(View.VISIBLE);
                    mInfoDescription.setText(getResources().getString(R.string.installing_warning_and_desc_ab));
                    mSecondaryButton.setVisibility(View.GONE);
                    mProgressBar.setIndeterminate(false);
                }
                break;
            case INSTALLATION_SUSPENDED:
                mHeaderStatus.setText(getResources().getString(R.string.installing_title));
                mHeaderStatusStep.setText(Utils.isDebug() ? 
                        getResources().getString(R.string.updating_step_installing_paused_title) :
                        getResources().getString(R.string.updating_step_installing_paused_verify_title));
                mVersionHeader.setVisibility(View.VISIBLE);
                mButton.setText(R.string.button_resume_update);
                mButton.setVisibility(View.VISIBLE);
                mInfoDescription.setText(getResources().getString(R.string.installing_suspended_warning_and_desc_ab));
                mProgressBar.setIndeterminate(false);
                break;
            case INSTALLED:
                mHeaderStatus.setText(getResources().getString(R.string.restart_title));
                mHeaderStatusStep.setText(getResources().getString(R.string.updating_step_installed_title));
                mVersionHeader.setVisibility(View.VISIBLE);
                mButton.setText(R.string.button_restart);
                mButton.setVisibility(View.VISIBLE);
                reportMessage(R.string.verified_download_snack);
                break;
        }

        SharedPreferences prefs = getApplicationContext().getSharedPreferences(Utils.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        boolean isUpdatingInPerfMode = (status == DOWNLOADING && prefs.getBoolean(Constants.PREF_AB_PERF_MODE, 
                getResources().getBoolean(R.bool.config_abPerformanceModeDefault)));
        boolean infoAllowed = (status == AVAILABLE || status == DOWNLOADED || status == INSTALLING || isUpdatingInPerfMode);
        boolean stepsAllowed = (status == DOWNLOADING || status == PAUSED || status == INSTALLING 
                || status == INSTALLATION_SUSPENDED || status == INSTALLED);
        mInfoIcon.setVisibility(infoAllowed ? View.VISIBLE : View.GONE);
        mInfoDescription.setVisibility(infoAllowed ? View.VISIBLE : View.GONE);
        mUpdateDescription.setVisibility(status != UNAVAILABLE ? View.VISIBLE : View.GONE);
        mVersionHeaderInfo.setVisibility(status != UNAVAILABLE ? View.VISIBLE : View.GONE);
        mHeaderStatusStep.setVisibility(stepsAllowed ? View.VISIBLE : View.GONE);
        updateStatusAndInfo(update, checkForUpdates);
        updateProgress(update, checkForUpdates);
        updateSystemStatus(update, checkForUpdates, false);
    }

    private MaterialAlertDialogBuilder checkForUpdates() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this, R.style.HubTheme_Dialog);
        dialog.setTitle(getResources().getString(R.string.button_check_for_update));
        dialog.setMessage(getResources().getString(R.string.no_updates_check_dialog_message));
        dialog.setPositiveButton(R.string.no_updates_check_dialog_button_text, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    updateMessages(null, CHECK_NORMAL);
                    mManager.warmUpMatchMaker(true);
                    mManager.beginMatchMaker();
                }
            });
        dialog.setNegativeButton(R.string.no_updates_check_local_dialog_button_text, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    updateMessages(null, CHECK_LOCAL);
                    mManager.beginLocalMatchMaker();
                }
            });
        return dialog;
    }

    public void updateSystemStatus(Update update, int checkForUpdates, boolean forceUnavailable) {
        if (checkForUpdates == CHECK_LOCAL || checkForUpdates == CHECK_NORMAL) {
            mVersionHeader.setVisibility(View.GONE);
            Log.d(TAG, "Not showing system status because we are checking for updates");
            return;
        }
        boolean updateUnavailable = update != null && update.getStatus() == UNAVAILABLE;
        if (updateUnavailable || forceUnavailable) {
            mVersionHeader.setVisibility(View.VISIBLE);
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            long lastChecked = prefs.getLong(Constants.PREF_LAST_UPDATE_CHECK, -1) / 1000;
            mVersionHeader.setTypeface(mVersionHeader.getTypeface(), Typeface.NORMAL);
            mVersionHeader.setText(String.format(
                    getResources().getString(R.string.no_updates_text), 
                    Version.getCurrentFlavor(), Version.getCurrentVersion(), 
                    StringGenerator.getTimeLocalized(this, lastChecked)));
        }
    }

    private void updateProgressForState(Update update, int state) {
        if (state == HubController.STATE_DOWNLOAD_PROGRESS 
                || state == HubController.STATE_INSTALL_PROGRESS) {
            mProgress = state == HubController.STATE_INSTALL_PROGRESS 
                    ? update.getInstallProgress() : update.getProgress();
        }
        updateProgress(update, CHECK_NONE);
    }

    private void updateProgress(Update update, int checkForUpdates) {
        boolean isChecking = (checkForUpdates == CHECK_LOCAL || checkForUpdates == CHECK_NORMAL);
        boolean progressAllowed = update != null && (update.getStatus() == DOWNLOADING 
                || update.getStatus() == STARTING || update.getStatus() == PAUSED 
                || update.getStatus() == VERIFYING || update.getStatus() == INSTALLING);
        mProgressBar.setVisibility((isChecking || progressAllowed) ? View.VISIBLE : View.GONE);
        if (mProgress != -1) {
            mProgressBar.setIndeterminate(false);
            mProgressBar.setProgress(mProgress, true);
        }
    }

    private MaterialAlertDialogBuilder createDialog(int reason, String downloadId) {
        int titleRes = 0;
        String message = null;
        DialogInterface.OnClickListener listener = null;
        if (reason == 2) {
            titleRes = R.string.install_update_dialog_cancel;
            message = getResources().getString(R.string.installing_cancel_dialog_message);
            listener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog,int id) {
                    Intent intent = new Intent(HubActivity.this, UpdateService.class);
                    intent.setAction(UpdateService.ACTION_INSTALL_STOP);
                    HubActivity.this.startService(intent);
                }
            };
        } else if (reason == 1) {
            if (!isBatteryLevelOk()) {
                titleRes = R.string.install_low_battery_warning_title;
                String warning = getResources().getString(R.string.install_low_battery_warning_text,
                        getResources().getInteger(R.integer.battery_ok_percentage_discharging),
                        getResources().getInteger(R.integer.battery_ok_percentage_charging));
                message = warning;
                listener = null;
            } else {
                HubController controller = mUpdateService.getController();
                UpdateInfo update = controller.getUpdate(downloadId);
                int resId;
                titleRes = R.string.install_update_dialog_title;
                String updateInfo = getResources().getString(R.string.install_update_dialog_message_info,
                        Version.getCurrentFlavor(), update.getVersion());
                try {
                    if (Utils.isABUpdate(update.getFile())) {
                        resId = R.string.install_update_dialog_message_ab;
                    } else {
                        resId = R.string.install_update_dialog_message;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Could not determine the type of the update");
                    return null;
                }
                message = getResources().getString(resId, updateInfo,
                        getResources().getString(android.R.string.ok));
                listener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,int id) {
                        Utils.triggerUpdate(getApplicationContext(), downloadId);
                    }
                };
            }
        }

        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this, R.style.HubTheme_Dialog);
        dialog.setTitle(getResources().getString(titleRes));
        dialog.setMessage(message);
        dialog.setPositiveButton(android.R.string.ok, listener);
        if (listener != null) {
            dialog.setNegativeButton(android.R.string.cancel, null);
        }
        return dialog;
    }

    private MaterialAlertDialogBuilder enforceBatteryReq() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this, R.style.HubTheme_Dialog);
        String requirements = getResources().getString(R.string.install_low_battery_warning_text,
                    getResources().getInteger(R.integer.battery_ok_percentage_discharging),
                    getResources().getInteger(R.integer.battery_ok_percentage_charging));
        dialog.setTitle(getResources().getString(R.string.install_low_battery_warning_title));
        dialog.setMessage(requirements);
        dialog.setPositiveButton(android.R.string.ok, null);
        return dialog;
    }

    private void warmUpCheckForUpdates() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(Utils.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        boolean allowLocalUpdates = prefs.getBoolean(Constants.PREF_ALLOW_LOCAL_UPDATES, false);
        if (allowLocalUpdates) {
            checkForUpdates().show();
        } else {
            updateMessages(null, CHECK_NORMAL);
            mManager.warmUpMatchMaker(true);
            mManager.beginMatchMaker();
        }
    }

    @Override
    public void onClick(View v) {
        HubController controller = mUpdateService.getController();
        Update update = controller.getActualUpdate(mDownloadId);
        if (update == null) {
            warmUpCheckForUpdates();
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean needsReboot = prefs.getBoolean(Constants.NEEDS_REBOOT_AFTER_UPDATE, false);
        if (needsReboot) {
            PowerManager pm = (PowerManager) HubActivity.this.getSystemService(Context.POWER_SERVICE);
            update.setStatus(UpdateStatus.UNAVAILABLE, getApplicationContext());
            prefs.edit().putBoolean(Constants.NEEDS_REBOOT_AFTER_UPDATE, false).apply();
            pm.reboot(null);
            return;
        }

        switch (update.getStatus()) {
            default:
            case UNAVAILABLE:
                warmUpCheckForUpdates();
                break;
            case AVAILABLE:
                try {
                    if (Utils.isABUpdate(update.getFile())) {
                        if (!isBatteryLevelOk()) {
                            enforceBatteryReq().show();
                        } else {
                            controller.startDownload(update.getDownloadId());
                        }
                    } else {
                        controller.startDownload(update.getDownloadId());
                    }
                } catch (IOException e) {
                }
                break;
            case DOWNLOADING:
                controller.pauseDownload(update.getDownloadId());
                break;
            case DOWNLOAD_FAILED:
                controller.startDownload(update.getDownloadId());
                break;
            case VERIFICATION_FAILED:
                controller.startDownload(update.getDownloadId());
            case PAUSED:
                UpdateInfo pausedUpdateInfo = controller.getUpdate(update.getDownloadId());
                Update pausedUpdate = new Update(pausedUpdateInfo);
                final boolean canResume = Utils.canInstall(getApplicationContext(), pausedUpdate) ||
                        pausedUpdateInfo.getFile().length() == pausedUpdateInfo.getFileSize();
                if (canResume) {
                    controller.resumeDownload(update.getDownloadId());
                } else {
                    reportMessage(R.string.error_update_not_installable_snack);
                }
                break;
            case INSTALLATION_CANCELLED:
            case INSTALLATION_FAILED:
                Utils.triggerUpdate(getApplicationContext(), update.getDownloadId());
            case DOWNLOADED:
                final boolean canInstall = Utils.canInstall(getApplicationContext(), update);
                if (canInstall) {
                    createDialog(1, update.getDownloadId()).show();
                } else {
                    reportMessage(R.string.error_update_not_installable_snack);
                }
                break;
            case INSTALLING:
                createDialog(2, null).show();
                break;
            case INSTALLED:
                PowerManager pm =
                        (PowerManager) HubActivity.this.getSystemService(Context.POWER_SERVICE);
                pm.reboot(null);
                break;
        }
    }

    public void reportMessage(int message) {
        mHeaderStatusMessage.setText(getResources().getString(message));
        mHeaderStatusMessage.setVisibility(View.VISIBLE);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mHeaderStatusMessage.setVisibility(View.GONE);
            }
        }, 2000);
    }

    private boolean isBatteryLevelOk() {
        Intent intent = HubActivity.this.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (!intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)) {
            return true;
        }
        int percent = Math.round(100.f * intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) /
                intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int required = (plugged & BatteryManager.BATTERY_PLUGGED_ANY) != 0 ?
                getResources().getInteger(R.integer.battery_ok_percentage_charging) :
                getResources().getInteger(R.integer.battery_ok_percentage_discharging);
        return percent >= required;
    }

    public ProgressBar getProgressBar() {
        return mProgressBar;
    }
}
