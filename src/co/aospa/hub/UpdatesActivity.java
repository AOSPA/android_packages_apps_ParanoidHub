/*
 * Copyright (C) 2017-2020 The LineageOS Project
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
package co.aospa.hub;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.icu.text.DateFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.res.ResourcesCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;

import org.json.JSONException;
import co.aospa.hub.controller.UpdaterController;
import co.aospa.hub.controller.UpdaterService;
import co.aospa.hub.download.DownloadClient;
import co.aospa.hub.misc.BuildInfoUtils;
import co.aospa.hub.misc.Constants;
import co.aospa.hub.misc.StringGenerator;
import co.aospa.hub.misc.Utils;
import co.aospa.hub.model.UpdateInfo;
import co.aospa.hub.model.UpdateStatus;
import co.aospa.hub.ui.UpdateProgressView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UpdatesActivity extends AppCompatActivity {

    private enum Action {
        DOWNLOAD,
        PAUSE,
        RESUME,
        INSTALL,
        INFO,
        RETRY_DOWNLOAD,
        RETRY_INSTALL,
        CANCEL_INSTALLATION,
        REBOOT,
    }

    private static final String TAG = "UpdatesActivity";
    private UpdaterService mUpdaterService;
    private BroadcastReceiver mBroadcastReceiver;

    private View mRefreshIconView;
    private RotateAnimation mRefreshAnimation;

    private UpdaterController mUpdaterController;
    private MaterialButton mControlButton;
    private UpdateProgressView mProgressView;
    private View mIdleGroupIcon;
    private TextView mChangelog;
    private TextView mHeaderMsg;
    private TextView mProgressText;
    private TextView mUpgradeVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_updates);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mControlButton = findViewById(R.id.control_button);
        mHeaderMsg = findViewById(R.id.header_msg);
        mProgressText = findViewById(R.id.progress_text);
        mIdleGroupIcon = findViewById(R.id.idle_placeholder);
        mProgressView = findViewById(R.id.progress);
        TextView versionText = findViewById(R.id.version_text);
        versionText.setText(BuildInfoUtils.getBuildVersion());
        mUpgradeVersion = findViewById(R.id.upgrade_version);
        mChangelog = findViewById(R.id.changelog);

        mControlButton.setVisibility(View.GONE);
        mUpgradeVersion.setVisibility(View.GONE);
        mChangelog.setVisibility(View.GONE);
        findViewById(R.id.changelog_strip).setVisibility(View.GONE);

        mProgressView.setVisibility(View.INVISIBLE);
        mProgressText.setVisibility(View.INVISIBLE);
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (UpdaterController.ACTION_UPDATE_STATUS.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                    handleUpdateStatusChange(downloadId);
                } else if (UpdaterController.ACTION_DOWNLOAD_PROGRESS.equals(intent.getAction()) ||
                        UpdaterController.ACTION_INSTALL_PROGRESS.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                    handleProgressUpdate(downloadId);
                }
            }
        };


        mRefreshAnimation = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        mRefreshAnimation.setInterpolator(new LinearInterpolator());
        mRefreshAnimation.setDuration(1000);
    }

    private void handleProgressUpdate(String downloadId) {
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        switch (update.getStatus()) {
            case DOWNLOADING:
                mProgressView.setProgress(update.getProgress()/100.f);
                mProgressText.setText(update.getProgress() + "%");
                break;
            case INSTALLING:
                boolean notAB = !mUpdaterController.isInstallingABUpdate();
                mHeaderMsg.setText(notAB ? R.string.dialog_prepare_zip_message :
                        update.getFinalizing() ?
                                R.string.finalizing_package :
                                R.string.preparing_ota_first_boot);
                mProgressView.setProgress(update.getInstallProgress()/100.f);
                mProgressText.setText(update.getInstallProgress() + "%");
                break;
        }
    }


    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(this, UpdaterService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_STATUS);
        intentFilter.addAction(UpdaterController.ACTION_DOWNLOAD_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_INSTALL_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_REMOVED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, intentFilter);
    }

    @Override
    public void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        if (mUpdaterService != null) {
            unbindService(mConnection);
        }
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_toolbar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh: {
                downloadUpdatesList();
                return true;
            }
            case R.id.menu_preferences: {
                showPreferencesDialog();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            UpdaterService.LocalBinder binder = (UpdaterService.LocalBinder) service;
            mUpdaterService = binder.getService();
            mUpdaterController = mUpdaterService.getUpdaterController();
            getUpdatesList();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mUpdaterController = null;
            mUpdaterService = null;
        }
    };

    private void loadUpdate(File jsonFile)
            throws IOException, JSONException {
        Log.d(TAG, "Adding remote updates");
        boolean newUpdates = false;

        List<UpdateInfo> updates = Utils.parseJson(jsonFile, true);
        Log.d(TAG, updates.toString());
        List<String> updatesOnline = new ArrayList<>();
        for (UpdateInfo update : updates) {
            newUpdates |= mUpdaterController.addUpdate(update);
            updatesOnline.add(update.getDownloadId());
        }
        mUpdaterController.setUpdatesAvailableOnline(updatesOnline, true);

        List<UpdateInfo> sortedUpdates = mUpdaterController.getUpdates();
        if (sortedUpdates.isEmpty()) {
            mHeaderMsg.setText("Your system is up to date.");
            mControlButton.setVisibility(View.GONE);
            mChangelog.setVisibility(View.GONE);
            findViewById(R.id.changelog_strip).setVisibility(View.GONE);
        } else {
            sortedUpdates.sort((u1, u2) -> Long.compare(u2.getTimestamp(), u1.getTimestamp()));
            mUpgradeVersion.setVisibility(View.VISIBLE);
            mUpgradeVersion.setText("Upgrade version: " + BuildInfoUtils.getUpdateVersion(sortedUpdates.get(0)));
            mControlButton.setVisibility(View.VISIBLE);
            mChangelog.setVisibility(View.VISIBLE);
            findViewById(R.id.changelog_strip).setVisibility(View.VISIBLE);
            getChangelog();
            UpdateInfo update = sortedUpdates.get(0);
            boolean activeLayout;
            switch (update.getPersistentStatus()) {
                case UpdateStatus.Persistent.UNKNOWN:
                    activeLayout = update.getStatus() == UpdateStatus.STARTING;
                    break;
                case UpdateStatus.Persistent.VERIFIED:
                    activeLayout = update.getStatus() == UpdateStatus.INSTALLING;
                    break;
                case UpdateStatus.Persistent.INCOMPLETE:
                    activeLayout = true;
                    break;
                default:
                    throw new RuntimeException("Unknown update status");
            }
            if (activeLayout) {
                handleActiveStatus(update);
            } else {
                handleNotActiveStatus(update);
            }
        }
    }

    private void getChangelog() {
        Context context = getApplicationContext();
        String url = context.getString(R.string.menu_changelog_url);
        final File changelogTmp = new File(context.getExternalCacheDir().getAbsolutePath() + "/" + UUID.randomUUID());
        DownloadClient.DownloadCallback callback = new DownloadClient.DownloadCallback() {
            @Override
            public void onFailure(final boolean cancelled) {
                Log.e(TAG, "Could not download changelog");
            }

            @Override
            public void onResponse(int statusCode, String url,
                                   DownloadClient.Headers headers) {
            }

            @Override
            public void onSuccess(File destination) {
                Log.d(TAG, "Changelog downloaded");
                runOnUiThread(() -> {
                        mChangelog.setText(Utils.parseChangelogJson(destination));
                });
            }
        };

        final DownloadClient downloadClient;
        try {
            downloadClient = new DownloadClient.Builder()
                    .setUrl(url)
                    .setDestination(changelogTmp)
                    .setDownloadCallback(callback)
                    .build();
        } catch (IOException exception) {
            Log.e(TAG, "Could not build download client");
            mChangelog.setText("No changelog available.");
            return;
        }
        downloadClient.start();
    }

    private void getUpdatesList() {
        File jsonFile = Utils.getCachedUpdateList(this);
        if (jsonFile.exists()) {
            try {
                loadUpdate(jsonFile);
                Log.d(TAG, "Cached list parsed");
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Error while parsing json list", e);
            }
        } else {
            downloadUpdatesList();
        }
    }

    private void processNewJson(File json, File jsonNew) {
        try {
            loadUpdate(jsonNew);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            long millis = System.currentTimeMillis();
            preferences.edit().putLong(Constants.PREF_LAST_UPDATE_CHECK, millis).apply();
            if (json.exists() && Utils.isUpdateCheckEnabled(this) &&
                    Utils.checkForNewUpdates(json, jsonNew)) {
                UpdatesCheckReceiver.updateRepeatingUpdatesCheck(this);
            }
            // In case we set a one-shot check because of a previous failure
            UpdatesCheckReceiver.cancelUpdatesCheck(this);
            jsonNew.renameTo(json);
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Could not read json", e);
            mHeaderMsg.setText(R.string.snack_updates_check_failed);
        }
    }

    private void downloadUpdatesList() {
        final File jsonFile = Utils.getCachedUpdateList(this);
        final File jsonFileTmp = new File(jsonFile.getAbsolutePath() + UUID.randomUUID());
        String url = Utils.getServerURL(this);
        Log.d(TAG, "Checking " + url);

        DownloadClient.DownloadCallback callback = new DownloadClient.DownloadCallback() {
            @Override
            public void onFailure(final boolean cancelled) {
                Log.e(TAG, "Could not download updates list");
                runOnUiThread(() -> {
                    if (!cancelled) {
                        mHeaderMsg.setText(R.string.snack_updates_check_failed);
                    }
                    refreshAnimationStop();
                });
            }

            @Override
            public void onResponse(int statusCode, String url,
                                   DownloadClient.Headers headers) {
            }

            @Override
            public void onSuccess(File destination) {
                runOnUiThread(() -> {
                    Log.d(TAG, "List downloaded");
                    processNewJson(jsonFile, jsonFileTmp);
                    refreshAnimationStop();
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
            mHeaderMsg.setText(R.string.snack_updates_check_failed);
            return;
        }

        refreshAnimationStart();
        downloadClient.start();
    }

    private void handleUpdateStatusChange(String downloadId) {
        if (mUpdaterController == null) {
            mUpdaterController = UpdaterController.getInstance();
        }
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        switch (update.getStatus()) {
            case INSTALLED:
                mHeaderMsg.setText("Reboot to finish applying update");
                mProgressView.setVisibility(View.INVISIBLE);
                mProgressText.setVisibility(View.INVISIBLE);
                mIdleGroupIcon.setVisibility(View.VISIBLE);
                setButtonAction(mControlButton, Action.REBOOT, downloadId, true);
                break;
            case PAUSED:
                mHeaderMsg.setText("Update download paused.");
                mProgressView.setVisibility(View.INVISIBLE);
                mProgressText.setVisibility(View.INVISIBLE);
                mIdleGroupIcon.setVisibility(View.VISIBLE);
                setButtonAction(mControlButton, Action.RESUME, downloadId, true);
                break;
            case PAUSED_ERROR:
                mHeaderMsg.setText(R.string.snack_download_failed);
                mProgressView.setVisibility(View.INVISIBLE);
                mIdleGroupIcon.setVisibility(View.VISIBLE);
                setButtonAction(mControlButton, Action.RETRY_DOWNLOAD, downloadId, true);
                break;
            case VERIFICATION_FAILED:
                mHeaderMsg.setText(R.string.snack_download_verification_failed);
                mProgressView.setVisibility(View.INVISIBLE);
                mIdleGroupIcon.setVisibility(View.VISIBLE);
                setButtonAction(mControlButton, Action.RETRY_DOWNLOAD, downloadId, true);
                break;
            case VERIFIED:
                mHeaderMsg.setText(R.string.snack_download_verified);
                mProgressView.setVisibility(View.INVISIBLE);
                mProgressText.setVisibility(View.INVISIBLE);
                mIdleGroupIcon.setVisibility(View.VISIBLE);
                setButtonAction(mControlButton, Action.INSTALL, downloadId, true);
                break;
        }
    }

    private void refreshAnimationStart() {
        if (mRefreshIconView == null) {
            mRefreshIconView = findViewById(R.id.menu_refresh);
        }
        if (mRefreshIconView != null) {
            mRefreshAnimation.setRepeatCount(Animation.INFINITE);
            mRefreshIconView.startAnimation(mRefreshAnimation);
            mRefreshIconView.setEnabled(false);
        }
    }

    private void refreshAnimationStop() {
        if (mRefreshIconView != null) {
            mRefreshAnimation.setRepeatCount(0);
            mRefreshIconView.setEnabled(true);
        }
    }

    private void showPreferencesDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.preferences_dialog, null);
        Spinner autoCheckInterval =
                view.findViewById(R.id.preferences_auto_updates_check_interval);
        Switch autoDelete = view.findViewById(R.id.preferences_auto_delete_updates);
        Switch dataWarning = view.findViewById(R.id.preferences_mobile_data_warning);
        Switch abPerfMode = view.findViewById(R.id.preferences_ab_perf_mode);
        Switch updateRecovery = view.findViewById(R.id.preferences_update_recovery);

        if (!Utils.isABDevice()) {
            abPerfMode.setVisibility(View.GONE);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        autoCheckInterval.setSelection(Utils.getUpdateCheckSetting(this));
        autoDelete.setChecked(prefs.getBoolean(Constants.PREF_AUTO_DELETE_UPDATES, false));
        dataWarning.setChecked(prefs.getBoolean(Constants.PREF_MOBILE_DATA_WARNING, true));
        abPerfMode.setChecked(prefs.getBoolean(Constants.PREF_AB_PERF_MODE, false));

        if (getResources().getBoolean(R.bool.config_hideRecoveryUpdate)) {
            // Hide the update feature if explicitly requested.
            // Might be the case of A-only devices using prebuilt vendor images.
            updateRecovery.setVisibility(View.GONE);
        } else if (Utils.isRecoveryUpdateExecPresent()) {
            updateRecovery.setChecked(
                    SystemProperties.getBoolean(Constants.UPDATE_RECOVERY_PROPERTY, false));
        } else {
            // There is no recovery updater script in the device, so the feature is considered
            // forcefully enabled, just to avoid users to be confused and complain that
            // recovery gets overwritten. That's the case of A/B and recovery-in-boot devices.
            updateRecovery.setChecked(true);
            updateRecovery.setOnTouchListener(new View.OnTouchListener() {
                private Toast forcedUpdateToast = null;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (forcedUpdateToast != null) {
                        forcedUpdateToast.cancel();
                    }
                    forcedUpdateToast = Toast.makeText(getApplicationContext(),
                            getString(R.string.toast_forced_update_recovery), Toast.LENGTH_SHORT);
                    forcedUpdateToast.show();
                    return true;
                }
            });
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_preferences)
                .setView(view)
                .setOnDismissListener(dialogInterface -> {
                    prefs.edit()
                            .putInt(Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL,
                                    autoCheckInterval.getSelectedItemPosition())
                            .putBoolean(Constants.PREF_AUTO_DELETE_UPDATES,
                                    autoDelete.isChecked())
                            .putBoolean(Constants.PREF_MOBILE_DATA_WARNING,
                                    dataWarning.isChecked())
                            .putBoolean(Constants.PREF_AB_PERF_MODE,
                                    abPerfMode.isChecked())
                            .apply();

                    if (Utils.isUpdateCheckEnabled(this)) {
                        UpdatesCheckReceiver.scheduleRepeatingUpdatesCheck(this);
                    } else {
                        UpdatesCheckReceiver.cancelRepeatingUpdatesCheck(this);
                        UpdatesCheckReceiver.cancelUpdatesCheck(this);
                    }

                    if (Utils.isABDevice()) {
                        boolean enableABPerfMode = abPerfMode.isChecked();
                        mUpdaterService.getUpdaterController().setPerformanceMode(enableABPerfMode);
                    }
                    if (Utils.isRecoveryUpdateExecPresent()) {
                        boolean enableRecoveryUpdate = updateRecovery.isChecked();
                        SystemProperties.set(Constants.UPDATE_RECOVERY_PROPERTY,
                                String.valueOf(enableRecoveryUpdate));
                    }
                })
                .show();
    }

    private void startDownloadWithWarning(final String downloadId) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean warn = preferences.getBoolean(Constants.PREF_MOBILE_DATA_WARNING, true);
        if (Utils.isOnWifiOrEthernet(this) || !warn) {
            mHeaderMsg.setText(R.string.downloading_notification);
            mUpdaterController.startDownload(downloadId);
            setButtonAction(mControlButton, Action.PAUSE, downloadId, true);
            return;
        }

        View checkboxView = LayoutInflater.from(this).inflate(R.layout.checkbox_view, null);
        CheckBox checkbox = (CheckBox) checkboxView.findViewById(R.id.checkbox);
        checkbox.setText(R.string.checkbox_mobile_data_warning);

        new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                .setTitle(R.string.update_on_mobile_data_title)
                .setMessage(R.string.update_on_mobile_data_message)
                .setView(checkboxView)
                .setPositiveButton(R.string.action_download,
                        (dialog, which) -> {
                            if (checkbox.isChecked()) {
                                preferences.edit()
                                        .putBoolean(Constants.PREF_MOBILE_DATA_WARNING, false)
                                        .apply();
                                this.supportInvalidateOptionsMenu();
                            }
                            mHeaderMsg.setText(R.string.downloading_notification);
                            setButtonAction(mControlButton, Action.PAUSE, downloadId, true);
                            mUpdaterController.startDownload(downloadId);
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private AlertDialog.Builder getInstallDialog(final String downloadId) {
        if (!Utils.isBatteryLevelOk(this)) {
            Resources resources = getResources();
            String message = resources.getString(R.string.dialog_battery_low_message_pct,
                    resources.getInteger(R.integer.battery_ok_percentage_discharging),
                    resources.getInteger(R.integer.battery_ok_percentage_charging));
            return new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                    .setTitle(R.string.dialog_battery_low_title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null);
        }
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        int resId;
        try {
            if (Utils.isABUpdate(update.getFile())) {
                resId = R.string.apply_update_dialog_message_ab;
            } else {
                resId = R.string.apply_update_dialog_message;
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not determine the type of the update");
            return null;
        }

        String buildDate = StringGenerator.getDateLocalizedUTC(this,
                DateFormat.MEDIUM, update.getTimestamp());
        String buildInfoText = getString(R.string.list_build_version_date,
                BuildInfoUtils.getUpdateVersion(update), buildDate);
        return new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                .setTitle(R.string.apply_update_dialog_title)
                .setMessage(getString(resId, buildInfoText,
                        getString(android.R.string.ok)))
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> {
                            mProgressText.setVisibility(View.VISIBLE);
                            mProgressView.setVisibility(View.VISIBLE);
                            mIdleGroupIcon.setVisibility(View.INVISIBLE);
                            setButtonAction(mControlButton, Action.CANCEL_INSTALLATION, downloadId, true);
                            Utils.triggerUpdate(this, downloadId);
                            boolean notAB = !mUpdaterController.isInstallingABUpdate();
                            mHeaderMsg.setText(notAB ? R.string.dialog_prepare_zip_message :
                                    update.getFinalizing() ?
                                            R.string.finalizing_package :
                                            R.string.preparing_ota_first_boot);
                        })
                .setNegativeButton(android.R.string.cancel, null);
    }

    private AlertDialog.Builder getCancelInstallationDialog(final String downloadId) {
        return new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                .setMessage(R.string.cancel_installation_dialog_message)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> {
                            mHeaderMsg.setText("Install update");
                            setButtonAction(mControlButton, Action.INSTALL, downloadId, true);
                            mProgressText.setVisibility(View.INVISIBLE);
                            mProgressView.setVisibility(View.INVISIBLE);
                            mIdleGroupIcon.setVisibility(View.VISIBLE);
                            Intent intent = new Intent(this, UpdaterService.class);
                            intent.setAction(UpdaterService.ACTION_INSTALL_STOP);
                            startService(intent);
                        })
                .setNegativeButton(android.R.string.cancel, null);
    }

    private void setButtonAction(MaterialButton button, Action action, final String downloadId,
                                 boolean enabled) {
        final View.OnClickListener clickListener;
        switch (action) {
            case DOWNLOAD:
                button.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_download, getTheme()));
                button.setEnabled(enabled);
                button.setStrokeColorResource(R.color.default_button_color);
                button.setTextColor(getResources().getColor(R.color.default_button_color));
                button.setIconTintResource(R.color.default_button_color);
                clickListener = enabled ? view -> {
                    mProgressText.setVisibility(View.VISIBLE);
                    mProgressView.setVisibility(View.VISIBLE);
                    mIdleGroupIcon.setVisibility(View.INVISIBLE);
                    startDownloadWithWarning(downloadId);
                } : null;
                break;
            case PAUSE:
                button.setText("Pause download");
                button.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_pause, getTheme()));
                button.setStrokeColorResource(R.color.ic_background);
                button.setTextColor(getResources().getColor(R.color.ic_background));
                button.setIconTintResource(R.color.ic_background);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> mUpdaterController.pauseDownload(downloadId)
                        : null;
                break;
            case RESUME: {
                button.setText("Resume");
                button.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_resume, getTheme()));
                button.setEnabled(enabled);
                button.setStrokeColorResource(R.color.default_button_color);
                button.setTextColor(getResources().getColor(R.color.default_button_color));
                button.setIconTintResource(R.color.default_button_color);
                UpdateInfo update = mUpdaterController.getUpdate(downloadId);
                final boolean canInstall = Utils.canInstall(update) ||
                        update.getFile().length() == update.getFileSize();
                clickListener = enabled ? view -> {
                    if (canInstall) {
                        mUpdaterController.resumeDownload(downloadId);
                        setButtonAction(button, Action.PAUSE, downloadId, true);
                    } else {
                        mHeaderMsg.setText(R.string.snack_update_not_installable);
                    }
                } : null;
            }
            break;
            case INSTALL: {
                button.setText("Install update");
                Drawable install = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_install, getTheme());
                button.setIcon(install);
                button.setEnabled(enabled);
                button.setStrokeColorResource(R.color.default_button_color);
                button.setTextColor(getResources().getColor(R.color.default_button_color));
                button.setIconTintResource(R.color.default_button_color);
                UpdateInfo update = mUpdaterController.getUpdate(downloadId);
                final boolean canInstall = Utils.canInstall(update);
                clickListener = enabled ? view -> {
                    if (canInstall) {
                        getInstallDialog(downloadId).show();
                    } else {
                        mHeaderMsg.setText(R.string.snack_update_not_installable);
                    }
                } : null;
            }
            break;
            case CANCEL_INSTALLATION: {
                button.setText("Cancel");
                Drawable cancel = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_cancel, getTheme());
                button.setIcon(cancel);
                button.setStrokeColorResource(R.color.ic_background);
                button.setTextColor(getResources().getColor(R.color.ic_background));
                button.setIconTintResource(R.color.ic_background);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> getCancelInstallationDialog(downloadId).show() : null;
            }
            break;
            case REBOOT: {
                button.setText(R.string.reboot);
                Drawable reboot = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_restart, getTheme());
                button.setIcon(reboot);
                button.setEnabled(enabled);
                button.setStrokeColorResource(R.color.default_button_color);
                button.setTextColor(getResources().getColor(R.color.default_button_color));
                button.setIconTintResource(R.color.default_button_color);
                clickListener = enabled ? view -> {
                    PowerManager pm =
                            (PowerManager) getSystemService(Context.POWER_SERVICE);
                    pm.reboot(null);
                } : null;
            }
            break;
            case RETRY_DOWNLOAD:
                button.setText(R.string.retry);
                Drawable retry = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_retry, getTheme());
                button.setStrokeColorResource(R.color.ic_background);
                button.setTextColor(getResources().getColor(R.color.ic_background));
                button.setIconTintResource(R.color.ic_background);
                button.setIcon(retry);
                clickListener = enabled ? view -> {
                    mProgressText.setVisibility(View.VISIBLE);
                    mProgressView.setVisibility(View.VISIBLE);
                    startDownloadWithWarning(downloadId);
                } : null;
            break;
            case RETRY_INSTALL:
                button.setText(R.string.retry);
                Drawable retryInstall = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_retry, getTheme());
                button.setStrokeColorResource(android.R.color.holo_red_light);
                button.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                button.setIconTintResource(android.R.color.holo_red_light);
                button.setIcon(retryInstall);
                clickListener = enabled ? view -> {
                    mProgressText.setVisibility(View.VISIBLE);
                    mProgressView.setVisibility(View.VISIBLE);
                    getInstallDialog(downloadId);
                } : null;
            break;
            default:
                clickListener = null;
        }

        // Disable action mode when a button is clicked
        button.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onClick(v);
            }
        });
    }

    private boolean isBusy() {
        return mUpdaterController.hasActiveDownloads() || mUpdaterController.isVerifyingUpdate()
                || mUpdaterController.isInstallingUpdate();
    }

    private void handleNotActiveStatus(UpdateInfo update) {
        final String downloadId = update.getDownloadId();
        if (mUpdaterController.isWaitingForReboot(downloadId)) {
            mHeaderMsg.setText("Reboot to finish applying update.");
            mProgressText.setVisibility(View.INVISIBLE);
            mProgressView.setVisibility(View.INVISIBLE);
            mIdleGroupIcon.setVisibility(View.VISIBLE);
            setButtonAction(mControlButton, Action.REBOOT, downloadId, true);
        } else if (update.getPersistentStatus() == UpdateStatus.Persistent.VERIFIED) {
            mProgressText.setVisibility(View.INVISIBLE);
            mProgressView.setVisibility(View.INVISIBLE);
            mIdleGroupIcon.setVisibility(View.VISIBLE);
            setButtonAction(mControlButton,
                    Utils.canInstall(update) ? Action.INSTALL : Action.RETRY_DOWNLOAD,
                    downloadId, !isBusy());
        } else if (!Utils.canInstall(update)) {
            setButtonAction(mControlButton, Action.INFO, downloadId, !isBusy());
        } else if (update.getStatus() == UpdateStatus.INSTALLATION_FAILED) {
            mProgressText.setVisibility(View.INVISIBLE);
            mProgressView.setVisibility(View.INVISIBLE);
            mIdleGroupIcon.setVisibility(View.VISIBLE);
            setButtonAction(mControlButton, Action.RETRY_INSTALL, downloadId, true);
        } else {
            setButtonAction(mControlButton, Action.DOWNLOAD, downloadId, !isBusy());
        }
    }

    private void handleActiveStatus(UpdateInfo update) {
        final String downloadId = update.getDownloadId();
        if (mUpdaterController.isWaitingForReboot(downloadId)) {
            mHeaderMsg.setText("Reboot to finish applying update.");
            mProgressText.setVisibility(View.INVISIBLE);
            mProgressView.setVisibility(View.INVISIBLE);
            mIdleGroupIcon.setVisibility(View.VISIBLE);
            setButtonAction(mControlButton, Action.REBOOT, downloadId, true);
        } else if (mUpdaterController.isDownloading(downloadId)) {
            String percentage = update.getProgress() + "%";
            setButtonAction(mControlButton, Action.PAUSE, downloadId, true);
            mProgressView.setVisibility(View.VISIBLE);
            mProgressView.setProgress(update.getProgress()/100.f);
            mProgressText.setVisibility(View.VISIBLE);
            mProgressText.setText(percentage);
            mIdleGroupIcon.setVisibility(View.INVISIBLE);
        } else if (mUpdaterController.isInstallingUpdate(downloadId)) {
            setButtonAction(mControlButton, Action.CANCEL_INSTALLATION, downloadId, true);
            boolean notAB = !mUpdaterController.isInstallingABUpdate();
            mHeaderMsg.setText(notAB ? R.string.dialog_prepare_zip_message :
                    update.getFinalizing() ?
                            R.string.finalizing_package :
                            R.string.preparing_ota_first_boot);
            mProgressView.setVisibility(View.VISIBLE);
            mProgressView.setProgress(update.getInstallProgress()/100.f);
            mProgressText.setVisibility(View.VISIBLE);
            mProgressText.setText(update.getInstallProgress() + "%");
            mIdleGroupIcon.setVisibility(View.INVISIBLE);
        } else if (mUpdaterController.isVerifyingUpdate(downloadId)) {
            mHeaderMsg.setText("Verifying update...");
            mProgressText.setVisibility(View.INVISIBLE);
            mProgressView.setVisibility(View.INVISIBLE);
            mIdleGroupIcon.setVisibility(View.VISIBLE);
            setButtonAction(mControlButton, Action.INSTALL, downloadId, false);
        } else {
            mHeaderMsg.setText("Update download paused.");
            setButtonAction(mControlButton, Action.RESUME, downloadId, !isBusy());
            mProgressView.setVisibility(View.VISIBLE);
            mProgressView.setProgress(update.getProgress()/100.f);
            mProgressText.setVisibility(View.VISIBLE);
            mProgressText.setText(update.getProgress() + "%");
            mIdleGroupIcon.setVisibility(View.INVISIBLE);
        }
    }
}