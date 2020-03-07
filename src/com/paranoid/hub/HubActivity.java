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

import android.animation.LayoutTransition;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
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
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;
import com.paranoid.hub.HubUpdateController;
import com.paranoid.hub.HubUpdateController.StatusListener;
import com.paranoid.hub.download.DownloadClient;
import com.paranoid.hub.misc.BuildInfoUtils;
import com.paranoid.hub.misc.Constants;
import com.paranoid.hub.misc.StringGenerator;
import com.paranoid.hub.misc.Utils;
import com.paranoid.hub.model.ChangeLog;
import com.paranoid.hub.model.UpdateInfo;
import com.paranoid.hub.model.UpdatePresenter;
import com.paranoid.hub.model.UpdateStatus;
import com.paranoid.hub.receiver.UpdateCheckReceiver;
import com.paranoid.hub.service.UpdateService;
import com.paranoid.hub.ui.DonutView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HubActivity extends AppCompatActivity implements View.OnClickListener, StatusListener {

    private static final String TAG = "HubActivity";

    private int mState;
    private static final int STATE_CHECK = UNAVAILABLE;
    private static final int STATE_CHECKING = CHECKING;
    private static final int STATE_FOUND = AVAILABLE;
    private static final int STATE_DOWNLOADING = DOWNLOADING;
    private static final int STATE_DOWNLOAD_PAUSED = PAUSED;
    private static final int STATE_VERIFYING = VERIFYING;
    private static final int STATE_INSTALL = DOWNLOADED;
    private static final int STATE_INSTALLING = INSTALLING;
    private static final int STATE_INSTALL_SUSPENDED = INSTALLATION_SUSPENDED;
    private static final int STATE_INSTALL_CANCELLED = INSTALLATION_CANCELLED;
    private static final int STATE_INSTALL_FAILED = INSTALLATION_FAILED;
    private static final int STATE_RESTART = INSTALLED;
    private static final int STATE_ERROR_RESET = 1000;

    private boolean mIsDownloading;
    private int mProgress = -1;
    private String mDownloadId;

    private UpdateService mUpdateService;

    private Handler mHandler = new Handler();

    private ChangeLog mChangelog;

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
            HubUpdateController controller = mUpdateService.getController();
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
            HubUpdateController controller = mUpdateService.getController();
            controller.addUpdateStatusListener(HubActivity.this);
            downloadUpdates(true, false); // Download Changelog only
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mUpdateService = null;
        }
    };

    @Override
    public void onUpdateStatusChanged(int state, String downloadId) {
        HubUpdateController controller = mUpdateService.getController();
        mDownloadId = downloadId;
        mIsDownloading = controller.isDownloading();
        UpdateInfo update = controller.getUpdate(downloadId);
        if (state == HubUpdateController.STATE_DOWNLOAD_PROGRESS 
                || state == HubUpdateController.STATE_INSTALL_PROGRESS) {
            mProgress = state == HubUpdateController.STATE_INSTALL_PROGRESS 
                    ? update.getInstallProgress() : update.getProgress();
        }
        if (update != null) {
            updateStates(update.getDownloadId());
            updateProgress();
        }
    }

    private void updateStatusAndInfo(UpdateInfo update) {
        if (update != null && (mState != STATE_CHECK || mState != STATE_CHECKING)) {
            if (mUpdateStatus.getVisibility() != View.VISIBLE) {
                mUpdateStatus.setVisibility(View.VISIBLE);
            }
            mUpdateStatus.setText(String.format(
                    getResources().getString(R.string.update_found_text),
                    BuildInfoUtils.getVersionFlavor(), update.getVersion(), 
                    Formatter.formatShortFileSize(this, update.getFileSize())));
            if (mChangelog != null) {
                mUpdateDescription.setText(String.format(
                        getResources().getString(R.string.update_found_changelog), mChangelog.get()));
            }
        }
    }

    private void updateMessages(UpdateInfo update) {
        switch (mState) {
            default:
            case STATE_CHECK:
                mHeaderStatus.setText(getResources().getString(R.string.no_updates_title));
                mButton.setText(R.string.no_updates_button_text);
                break;
            case STATE_CHECKING:
                mHeaderStatus.setText(getResources().getString(R.string.update_checking_title));
                break;
            case STATE_ERROR_RESET:
                mHeaderStatus.setText(getResources().getString(R.string.error_update_refreshing_hub));
                mHeaderStatusError.setText(getResources().getString(R.string.error_update_refreshing_hub_header_status));
                mInfoDescription.setText(getResources().getString(R.string.error_update_refreshing_hub_desc));
                mProgressBar.setVisibility(View.VISIBLE);
                mProgressBar.setIndeterminate(true);
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        HubUpdateController controller = mUpdateService.getController();
                        controller.resetHub();
                    }
                }, 10000);
                break;
            case STATE_FOUND:
                if (update != null) {
                    mHeaderStatus.setText(getResources().getString(R.string.update_found_title));
                    mButton.setText(R.string.update_found_button_text);
                    mInfoDescription.setText(getResources().getString(R.string.update_found_warning_and_desc));
                }
                break;
            case STATE_DOWNLOADING:
                mHeaderStatus.setText(getResources().getString(R.string.downloading_title));
                mButton.setText(R.string.downloading_button_text_pause);
                break;
            case STATE_DOWNLOAD_PAUSED:
                mHeaderStatus.setText(getResources().getString(R.string.downloading_paused_title));
                mButton.setText(R.string.downloading_button_text_resume);
                break;
            case STATE_VERIFYING:
                mHeaderStatus.setText(getResources().getString(R.string.verifying_update_title));
                mProgressBar.setIndeterminate(true);
                break;
            case STATE_INSTALL_CANCELLED:
            case STATE_INSTALL_FAILED:
            case STATE_INSTALL:
                mHeaderStatus.setText(getResources().getString(R.string.install_title));
                mButton.setText(R.string.install_button_text);
                mInfoDescription.setText(getResources().getString(Utils.isABDevice() 
                        ? R.string.install_warning_and_desc_ab 
                        : R.string.install_warning_and_desc));
                mProgressBar.setIndeterminate(false);
                break;
            case STATE_INSTALLING:
                mHeaderStatus.setText(getResources().getString(R.string.installing_title));
                mButton.setText(R.string.installing_suspend_button_text);
                mInfoDescription.setText(getResources().getString(R.string.installing_warning_and_desc_ab));
                mProgressBar.setIndeterminate(false);
                break;
            case STATE_INSTALL_SUSPENDED:
                mHeaderStatus.setText(getResources().getString(R.string.installing_suspended_text));
                mButton.setText(R.string.installing_suspended_button_text);
                mInfoDescription.setText(getResources().getString(R.string.installing_suspended_warning_and_desc_ab));
                mProgressBar.setIndeterminate(false);
                break;
            case STATE_RESTART:
                mHeaderStatus.setText(getResources().getString(R.string.restart_title));
                mButton.setText(R.string.restart_button_text);
                break;
        }
        boolean buttonAllowed = (mState != STATE_VERIFYING || mState != STATE_CHECKING || mState != STATE_ERROR_RESET);
        boolean infoAllowed = (mState == STATE_FOUND || mState == STATE_INSTALL 
                || mState == STATE_INSTALLING || mState == STATE_ERROR_RESET);
        mButton.setVisibility(buttonAllowed ? View.VISIBLE : View.GONE);
        mSecondaryButton.setText(R.string.preferences_button_text);
        mSecondaryButton.setVisibility(mState != STATE_INSTALLING ? View.VISIBLE : View.GONE);

        mChangelogHeader.setVisibility((mState != STATE_CHECK || mState != STATE_ERROR_RESET) ? View.VISIBLE : View.GONE);
        mInfoIcon.setVisibility(infoAllowed ? View.VISIBLE : View.GONE);
        mInfoDescription.setVisibility(infoAllowed ? View.VISIBLE : View.GONE);
        mUpdateDescription.setVisibility(mState != STATE_CHECK ? View.VISIBLE : View.GONE);
        mHeaderStatusError.setVisibility(mState == STATE_ERROR_RESET ? View.VISIBLE : View.GONE);
        updateStatusAndInfo(update);
        updateProgress();
        updateSystemStatus();
    }

    private void loadUpdates(File jsonFile, boolean changelog, boolean manualRefresh)
            throws IOException, JSONException {
        Log.d(TAG, "Adding remote updates");
        HubUpdateController controller = mUpdateService.getController();
        boolean newUpdate = false;

        if (!changelog) {
            UpdateInfo update = UpdatePresenter.matchMakeJson(jsonFile);
            newUpdate = controller.isUpdateAvailable(update);
            if (newUpdate) {};

            if (manualRefresh) {
                showSnackbar(
                        newUpdate ? R.string.update_found_snack : R.string.no_updates_found_snack,
                        Snackbar.LENGTH_SHORT);
            }
            updateStates(update.getDownloadId());
        } else {
           mChangelog = UpdatePresenter.matchMakeChangelog(jsonFile);
           getUpdates();
        }
    }

    private void getUpdates() {
        if (mIsDownloading) {
            mState = STATE_DOWNLOADING;
            return;
        }
        if (mState >= STATE_INSTALL) {
            return;
        }
        File jsonFile = Utils.getCachedUpdateList(this);
        if (jsonFile.exists()) {
            try {
                loadUpdates(jsonFile, false, false);
                Log.d(TAG, "Cached list parsed");
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Error while parsing json list", e);
            }
        } else {
            downloadUpdates(false, false);
        }
    }

    private void processNewJson(File json, File jsonNew, boolean changelog, boolean manualRefresh) {
        try {
            loadUpdates(jsonNew, changelog, manualRefresh);
            if (!changelog) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                long millis = System.currentTimeMillis();
                preferences.edit().putLong(Constants.PREF_LAST_UPDATE_CHECK, millis).apply();
                updateSystemStatus();
                if (json.exists() && UpdatePresenter.isNewUpdate(getApplicationContext(), json, jsonNew)) {
                    UpdateCheckReceiver.updateRepeatingUpdatesCheck(this);
                }
                // In case we set a one-shot check because of a previous failure
                UpdateCheckReceiver.cancelUpdatesCheck(this);
            }
            jsonNew.renameTo(json);
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Could not read json", e);
            showSnackbar(R.string.error_update_check_failed_snack, Snackbar.LENGTH_LONG);
        }
    }

    private void downloadUpdates(boolean changelog, boolean manualRefresh) {
        final File jsonFile = changelog ? Utils.getCachedChangelog(this) : Utils.getCachedUpdateList(this);
        final File jsonFileTmp = new File(jsonFile.getAbsolutePath() + UUID.randomUUID());
        String url = changelog ? Utils.getChangelogURL(this) : Utils.getServerURL(this);
        Log.d(TAG, "Checking " + url);

        DownloadClient.DownloadCallback callback = new DownloadClient.DownloadCallback() {
            @Override
            public void onFailure(final boolean cancelled) {
                Log.e(TAG, "Could not download updates");
                runOnUiThread(() -> {
                    if (!cancelled) {
                        showSnackbar(R.string.error_update_check_failed_snack, Snackbar.LENGTH_LONG);
                    }
                    mProgressBar.setVisibility(View.GONE);
                    mProgressBar.setIndeterminate(false);
                });
            }

            @Override
            public void onResponse(int statusCode, String url,
                    DownloadClient.Headers headers) {
            }

            @Override
            public void onSuccess(File destination) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Updates downloaded");
                    processNewJson(jsonFile, jsonFileTmp, changelog, manualRefresh);
                    mProgressBar.setVisibility(View.GONE);
                    mProgressBar.setIndeterminate(false);
                });
            }
        };

        final DownloadClient downloadClient;
        try {
            downloadClient = new DownloadClient.Builder()
                    .setUrl(url)
                    .setDestination(jsonFileTmp)
                    .setDownloadCallback(callback)
                    .build();
        } catch (IOException exception) {
            Log.e(TAG, "Could not build download client");
            showSnackbar(R.string.error_update_check_failed_snack, Snackbar.LENGTH_LONG);
            return;
        }

        if (manualRefresh) {
            mProgressBar.setVisibility(View.VISIBLE);
            mProgressBar.setIndeterminate(true);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    downloadClient.start();
                }
            }, 5000);
        } else {
           downloadClient.start();
        }
    }

    private void updateSystemStatus() {
        if (mState == STATE_CHECKING) {
            mUpdateStatus.setVisibility(View.GONE);
            Log.d(TAG, "Not showing system status because we are checking for updates");
            return;
        }
        if (mState == STATE_CHECK) {
            mUpdateStatus.setVisibility(View.VISIBLE);
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            long lastChecked = prefs.getLong(Constants.PREF_LAST_UPDATE_CHECK, -1) / 1000;
            mUpdateStatus.setText(String.format(
                    getResources().getString(R.string.no_updates_text), 
                    BuildInfoUtils.getVersionFlavor(), BuildInfoUtils.getVersionCode(), 
                    StringGenerator.getTimeLocalized(this, lastChecked)));
        }
    }

    private void updateProgress() {
        boolean progressAllowed = mState == STATE_CHECKING || mState == STATE_DOWNLOADING 
                || mState == STATE_DOWNLOAD_PAUSED || mState == STATE_VERIFYING 
                || mState == STATE_INSTALLING || mState == STATE_ERROR_RESET;
        mProgressBar.setVisibility(progressAllowed ? View.VISIBLE : View.GONE);
        if (mProgress != -1) {
            mProgressBar.setIndeterminate(false);
            mProgressBar.setProgress(mProgress);
        }
    }

    private void updateStates(String downloadId) {
        mDownloadId = downloadId;
        UpdateInfo update = mUpdateService.getController().getUpdate(downloadId);
        int abState = Utils.isABDevice() ? STATE_INSTALL : STATE_RESTART;
        if (update == null) {
            Log.d(TAG, "Update is null, resetting application");
            mState = STATE_ERROR_RESET;
            updateMessages(null);
            return;
        }

        switch (update.getStatus()) {
            case UNAVAILABLE:
                mState = STATE_CHECK;
                break;
            case AVAILABLE:
                mState = STATE_FOUND;
                break;
            case DOWNLOADING:
                mState = STATE_DOWNLOADING;
                break;
            case DOWNLOADED:
                mState = HubUpdateController.IS_DEBUG ? abState : STATE_VERIFYING;
                break;
            case PAUSED:
                mState = STATE_DOWNLOAD_PAUSED;
                break;
            case VERIFIED:
                mState = abState;
                showSnackbar(R.string.verified_download_snack, Snackbar.LENGTH_LONG);
                break;
            case VERIFYING:
                mState = STATE_VERIFYING;
                break;
            case INSTALLING:
                mState = STATE_INSTALLING;
                break;
            case INSTALLATION_SUSPENDED:
                mState = STATE_INSTALL_SUSPENDED;
                break;
            case INSTALLATION_CANCELLED:
                mState = STATE_INSTALL_CANCELLED;
                break;
            case INSTALLATION_FAILED:
                mState = STATE_INSTALL_FAILED;
                showSnackbar(R.string.installing_error_update_notification_title, Snackbar.LENGTH_LONG);
                break;
            case INSTALLED:
                mState = STATE_RESTART;
                break;
            case PAUSED_ERROR:
                showSnackbar(R.string.error_download_failed_snack, Snackbar.LENGTH_LONG);
                break;
            case VERIFICATION_FAILED:
                mState = STATE_CHECK;
                showSnackbar(R.string.error_verification_failed_snack, Snackbar.LENGTH_LONG);
                break;
        }
        updateMessages(update);
    }

    private AlertDialog.Builder getCancelInstallationDialog() {
        return new AlertDialog.Builder(HubActivity.this)
                .setMessage(R.string.installing_cancel_dialog_message)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> {
                            Intent intent = new Intent(HubActivity.this, UpdateService.class);
                            intent.setAction(UpdateService.ACTION_INSTALL_STOP);
                            HubActivity.this.startService(intent);
                        })
                .setNegativeButton(android.R.string.cancel, null);
    }

    private AlertDialog.Builder getInstallDialog(final String downloadId) {
        HubUpdateController controller = mUpdateService.getController();
        if (!isBatteryLevelOk()) {
            Resources resources = getResources();
            String message = resources.getString(R.string.install_low_battery_warning_text,
                    resources.getInteger(R.integer.battery_ok_percentage_discharging),
                    resources.getInteger(R.integer.battery_ok_percentage_charging));
            return new AlertDialog.Builder(HubActivity.this)
                    .setTitle(R.string.install_low_battery_warning_title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null);
        }
        UpdateInfo update = controller.getUpdate(downloadId);
        int resId;
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

        String updateInfo = getResources().getString(R.string.install_update_dialog_message_info,
                BuildInfoUtils.getVersionFlavor(), update.getVersion(), update.getTimestamp());
        return new AlertDialog.Builder(HubActivity.this)
                .setTitle(R.string.install_update_dialog_title)
                .setMessage(getResources().getString(resId, updateInfo,
                        getResources().getString(android.R.string.ok)))
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> controller.startInstall(downloadId))
                .setNegativeButton(android.R.string.cancel, null);
    }

    @Override
    public void onClick(View v) {
        HubUpdateController controller = mUpdateService.getController();
        switch (mState) {
            default:
            case STATE_CHECK:
                mState = STATE_CHECKING;
                updateMessages(null);
                downloadUpdates(false, true);
                break;
            case STATE_FOUND:
                controller.startDownload(mDownloadId);
                break;
            case STATE_DOWNLOADING:
                controller.pauseDownload(mDownloadId);
                break;
            case STATE_DOWNLOAD_PAUSED:
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
            case STATE_INSTALL:
                UpdateInfo update = controller.getUpdate(mDownloadId);
                final boolean canInstall = Utils.canInstall(getApplicationContext(), update);
                if (canInstall) {
                    getInstallDialog(mDownloadId).show();
                } else {
                    showSnackbar(R.string.error_update_not_installable_snack,
                            Snackbar.LENGTH_LONG);
                }
                break;
            case STATE_INSTALLING:
                getCancelInstallationDialog().show();
                break;
            case STATE_RESTART:
                PowerManager pm =
                        (PowerManager) HubActivity.this.getSystemService(Context.POWER_SERVICE);
                pm.reboot(null);
                break;
        }
    }

    private void showSnackbar(int stringId, int duration) {
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
}