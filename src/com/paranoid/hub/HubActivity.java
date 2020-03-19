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
import android.icu.text.DateFormat;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.PowerManager;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;
import com.paranoid.hub.HubController;
import com.paranoid.hub.HubController.StatusListener;
import com.paranoid.hub.download.DownloadClient;
import com.paranoid.hub.misc.BuildInfoUtils;
import com.paranoid.hub.misc.Constants;
import com.paranoid.hub.misc.StringGenerator;
import com.paranoid.hub.misc.Utils;
import com.paranoid.hub.model.ChangeLog;
import com.paranoid.hub.model.Update;
import com.paranoid.hub.model.UpdateInfo;
import com.paranoid.hub.model.UpdatePresenter;
import com.paranoid.hub.model.UpdateStatus;
import com.paranoid.hub.receiver.UpdateCheckReceiver;
import com.paranoid.hub.service.UpdateService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HubActivity extends AppCompatActivity implements View.OnClickListener, StatusListener {

    private static final String TAG = "HubActivity";

    private static final int CHECK_NONE = -1;
    private static final int CHECK_NORMAL = 0;
    private static final int CHECK_LOCAL = 1;

    private boolean mIsDownloading;
    private int mProgress = -1;
    private String mDownloadId;

    private HubUpdateManager mManager;
    private UpdateService mUpdateService;

    private Handler mHandler = new Handler();

    private Button mButton;
    private Button mSecondaryButton;
    private ImageView mInfoIcon;
    private ProgressBar mProgressBar;
    private TextView mChangelogHeader;
    private TextView mHeaderStatus;
    private TextView mInfoDescription;
    private TextView mUpdateDescription;
    private TextView mUpdateStatus;
    private TextView mHeaderStatusError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hub_activity);

        mChangelogHeader = (TextView) findViewById(R.id.system_update_changelog_header);
        mHeaderStatus = (TextView) findViewById(R.id.header_system_update_status);
        mProgressBar = (ProgressBar) findViewById(R.id.system_update_progress);
        mUpdateDescription = (TextView) findViewById(R.id.system_update_desc);
        mUpdateStatus = (TextView) findViewById(R.id.system_update_status);
        mHeaderStatusError = (TextView) findViewById(R.id.system_update_error_message);

        mInfoIcon = (ImageView) findViewById(R.id.system_update_info_icon);
        mInfoDescription = (TextView) findViewById(R.id.system_update_info_desc);

        mButton = (Button) findViewById(R.id.system_update_primary_button);
        mButton.setOnClickListener(this);

        mSecondaryButton = (Button) findViewById(R.id.system_update_secondary_button);
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
        ((ViewGroup) findViewById(R.id.system_update_info)).getLayoutTransition()
                .enableTransitionType(LayoutTransition.CHANGING);

        if (ContextCompat.checkSelfPermission(HubActivity.this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(HubActivity.this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, 
                    Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);
        }
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
        mManager.setMatchMaker(true);
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
        mDownloadId = update.getDownloadId();
        runOnUiThread(() -> {
            updateMessages(update, CHECK_NONE);
            updateProgressForState(update, state);
        });
    }

    private void updateStatusAndInfo(Update update, int checkForUpdates) {
        boolean isChecking = (checkForUpdates == CHECK_LOCAL || checkForUpdates == CHECK_NORMAL);
        if (update != null && (update.getStatus() != UNAVAILABLE || isChecking)) {
            if (update.getStatus() != UNAVAILABLE || isChecking) {
                if (mUpdateStatus.getVisibility() != View.VISIBLE) {
                    mUpdateStatus.setVisibility(View.VISIBLE);
                }
                mUpdateStatus.setText(String.format(
                        getResources().getString(R.string.update_found_text),
                        BuildInfoUtils.getVersionFlavor(), update.getVersion(), 
                        Formatter.formatShortFileSize(this, update.getFileSize())));
                ChangeLog changelog = mManager.getChangelog();
                if (changelog != null) {
                    mUpdateDescription.setText(String.format(
                            getResources().getString(R.string.update_found_changelog), changelog.get()));
                } else {
                    mUpdateDescription.setText(getResources().getString(R.string.update_found_changelog_default));
                }
            }
        }
    }

    private void beginHubReset() {
        mHeaderStatus.setText(getResources().getString(R.string.error_update_refreshing_hub));
        mHeaderStatusError.setText(getResources().getString(R.string.error_update_refreshing_hub_header_status));
        mHeaderStatusError.setVisibility(View.VISIBLE);
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
        mChangelogHeader.setVisibility(View.GONE);
        HubController controller = mUpdateService.getController();
        if (checkForUpdates == CHECK_LOCAL) {
            mHeaderStatus.setText(getResources().getString(R.string.update_checking_local_title));
            mChangelogHeader.setVisibility(View.GONE);
            mUpdateStatus.setVisibility(View.GONE);
            updateStatusAndInfo(update, checkForUpdates);
            updateProgress(update, checkForUpdates);
            updateSystemStatus(update, checkForUpdates);
            return;
        } else if (checkForUpdates == CHECK_NORMAL) {
            mHeaderStatus.setText(getResources().getString(R.string.update_checking_title));
            mChangelogHeader.setVisibility(View.GONE);
            mUpdateStatus.setVisibility(View.GONE);
            updateStatusAndInfo(update, checkForUpdates);
            updateProgress(update, checkForUpdates);
            updateSystemStatus(update, checkForUpdates);
            return;
        }

        if (controller.getUpdateStatus() != UNAVAILABLE && update == null) {
            beginHubReset();
            return;
        } else if (update == null) {
            mHeaderStatus.setText(getResources().getString(R.string.no_updates_title));
            mButton.setText(R.string.no_updates_button_text);
            mButton.setVisibility(View.VISIBLE);
            Log.d(TAG, "Update is null");
            return;
        }

        int status = update.getStatus();
        Log.d(TAG, "Current update status: " + status);
        switch (status) {
            default:
            case UNAVAILABLE:
                mHeaderStatus.setText(getResources().getString(R.string.no_updates_title));
                mButton.setText(R.string.no_updates_button_text);
                mButton.setVisibility(View.VISIBLE);
                break;
            case AVAILABLE:
                if (update != null) {
                    mHeaderStatus.setText(getResources().getString(R.string.update_found_title));
                    mChangelogHeader.setVisibility(View.VISIBLE);
                    mButton.setText(R.string.update_found_button_text);
                    mButton.setVisibility(View.VISIBLE);
                    mInfoDescription.setText(getResources().getString(R.string.update_found_warning_and_desc));
                }
                break;
            case STARTING:
                mHeaderStatus.setText(getResources().getString(R.string.starting_update_title));
                mChangelogHeader.setVisibility(View.VISIBLE);
                mProgressBar.setIndeterminate(true);
                break;
            case DOWNLOADING:
                mHeaderStatus.setText(getResources().getString(R.string.downloading_title));
                mChangelogHeader.setVisibility(View.VISIBLE);
                mButton.setText(R.string.downloading_button_text_pause);
                mButton.setVisibility(View.VISIBLE);
                break;
            case PAUSED:
                mHeaderStatus.setText(getResources().getString(R.string.downloading_paused_title));
                mChangelogHeader.setVisibility(View.VISIBLE);
                mButton.setText(R.string.downloading_button_text_resume);
                mButton.setVisibility(View.VISIBLE);
                break;
            case VERIFYING:
                mHeaderStatus.setText(getResources().getString(R.string.verifying_update_title));
                mChangelogHeader.setVisibility(View.VISIBLE);
                mProgressBar.setIndeterminate(true);
                break;
            case INSTALLATION_CANCELLED:
            case INSTALLATION_FAILED:
                showSnackbar(R.string.installing_error_update_notification_title, Snackbar.LENGTH_LONG);
            case DOWNLOADED:
                mHeaderStatus.setText(getResources().getString(R.string.install_title));
                mChangelogHeader.setVisibility(View.VISIBLE);
                mButton.setText(R.string.install_button_text);
                mButton.setVisibility(View.VISIBLE);
                mInfoDescription.setText(getResources().getString(Utils.isABDevice() 
                        ? R.string.install_warning_and_desc_ab 
                        : R.string.install_warning_and_desc));
                mProgressBar.setIndeterminate(false);
                showSnackbar(R.string.verified_download_snack, Snackbar.LENGTH_LONG);
                break;
            case INSTALLING:
                if (update != null) {
                    mHeaderStatus.setText(update.getFinalizing() ? 
                            getResources().getString(R.string.installing_finalizing_update_title) :
                            getResources().getString(R.string.installing_title));
                    mChangelogHeader.setVisibility(View.VISIBLE);
                    mButton.setText(R.string.installing_suspend_button_text);
                    mButton.setVisibility(View.VISIBLE);
                    mInfoDescription.setText(getResources().getString(R.string.installing_warning_and_desc_ab));
                    mProgressBar.setIndeterminate(false);
                }
                break;
            case INSTALLATION_SUSPENDED:
                mHeaderStatus.setText(getResources().getString(R.string.installing_suspended_text));
                mChangelogHeader.setVisibility(View.VISIBLE);
                mButton.setText(R.string.installing_suspended_button_text);
                mButton.setVisibility(View.VISIBLE);
                mInfoDescription.setText(getResources().getString(R.string.installing_suspended_warning_and_desc_ab));
                mProgressBar.setIndeterminate(false);
                break;
            case INSTALLED:
                mHeaderStatus.setText(getResources().getString(R.string.restart_title));
                mChangelogHeader.setVisibility(View.VISIBLE);
                mButton.setText(R.string.restart_button_text);
                mButton.setVisibility(View.VISIBLE);
                showSnackbar(R.string.verified_download_snack, Snackbar.LENGTH_LONG);
                break;
        }
        boolean infoAllowed = (status == AVAILABLE || status == DOWNLOADED || status == INSTALLING);
        mSecondaryButton.setText(R.string.preferences_button_text);
        mSecondaryButton.setVisibility(status != INSTALLING ? View.VISIBLE : View.GONE);
        mInfoIcon.setVisibility(infoAllowed ? View.VISIBLE : View.GONE);
        mInfoDescription.setVisibility(infoAllowed ? View.VISIBLE : View.GONE);
        mUpdateDescription.setVisibility(status != UNAVAILABLE ? View.VISIBLE : View.GONE);
        updateStatusAndInfo(update, checkForUpdates);
        updateProgress(update, checkForUpdates);
        updateSystemStatus(update, checkForUpdates);
    }

    private MaterialAlertDialogBuilder checkForUpdates() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this, R.style.HubTheme_Dialog);
        dialog.setTitle(getResources().getString(R.string.no_updates_button_text));
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
                    mManager.setMatchMaker(true);
                    updateMessages(null, CHECK_LOCAL);
                    mManager.beginLocalMatchMaker();
                }
            });
        return dialog;
    }

    public void updateSystemStatus(Update update, int checkForUpdates) {
        if (checkForUpdates == CHECK_LOCAL || checkForUpdates == CHECK_NORMAL) {
            mUpdateStatus.setVisibility(View.GONE);
            Log.d(TAG, "Not showing system status because we are checking for updates");
            return;
        }
        boolean updateUnavailable = update != null && update.getStatus() == UNAVAILABLE;
        if (updateUnavailable) {
            mUpdateStatus.setVisibility(View.VISIBLE);
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            long lastChecked = prefs.getLong(Constants.PREF_LAST_UPDATE_CHECK, -1) / 1000;
            mUpdateStatus.setText(String.format(
                    getResources().getString(R.string.no_updates_text), 
                    BuildInfoUtils.getVersionFlavor(), BuildInfoUtils.getVersionCode(), 
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

    private AlertDialog.Builder createDialog(int reason, String downloadId) {
        Context context = getApplicationContext();
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        LayoutInflater inflater = LayoutInflater.from(dialog.getContext());
        View view = inflater.inflate(R.layout.hub_dialog_header, null);
        ((ImageView) view.findViewById(R.id.hub_dialog_header_icon))
                .setImageResource(R.drawable.header_system_update);
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
                        BuildInfoUtils.getVersionFlavor(), update.getVersion());
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
                        controller.startInstall(downloadId);
                    }
                };
            }
        }
        ((TextView) view.findViewById(R.id.hub_dialog_header_title)).setText(titleRes);

        dialog.setCustomTitle(view);
        dialog.setMessage(message);
        dialog.setPositiveButton(android.R.string.ok, listener);
        if (listener != null) {
            dialog.setNegativeButton(android.R.string.cancel, null);
        }
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

        switch (update.getStatus()) {
            default:
            case UNAVAILABLE:
                warmUpCheckForUpdates();
                break;
            case AVAILABLE:
                controller.startDownload(mDownloadId);
                break;
            case DOWNLOADING:
                controller.pauseDownload(mDownloadId);
                break;
            case PAUSED:
                UpdateInfo pausedUpdate = controller.getUpdate(mDownloadId);
                final boolean canResume = Utils.canInstall(getApplicationContext(), pausedUpdate) ||
                        pausedUpdate.getFile().length() == pausedUpdate.getFileSize();
                if (canResume) {
                    controller.resumeDownload(mDownloadId);
                } else {
                    showSnackbar(R.string.error_update_not_installable_snack,
                            Snackbar.LENGTH_LONG);
                }
                break;
            case INSTALLATION_CANCELLED:
            case INSTALLATION_FAILED:
            case DOWNLOADED:
                final boolean canInstall = Utils.canInstall(getApplicationContext(), update);
                if (canInstall) {
                    createDialog(1, mDownloadId).show();
                } else {
                    showSnackbar(R.string.error_update_not_installable_snack,
                            Snackbar.LENGTH_LONG);
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

    public void showSnackbar(int stringId, int duration) {
        Snackbar.make(findViewById(R.id.main_container), stringId, duration).show();
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
