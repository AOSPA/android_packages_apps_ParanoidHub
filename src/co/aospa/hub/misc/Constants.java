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
package co.aospa.hub.misc;

import android.app.AlarmManager;

public final class Constants {

    private Constants() {
    }

    // Update Configuration
    public static final String UPDATE_STATUS = "hub_update_status";

    // AB Update Configuration
    public static final String IS_INSTALLING_AB = "is_installing_ab";
    public static final String IS_INSTALL_SUSPENDED = "is_install_suspended";
    public static final String DOWNLOAD_ID_AB = "download_id_ab";
    public static final String NEEDS_REBOOT_AFTER_UPDATE = "needs_reboot_after_update";

    // Preferences
    public static final String PREF_AUTO_DELETE_UPDATES = "auto_delete_updates";
    public static final String PREF_AB_PERF_MODE = "ab_perf_mode";
    public static final String PREF_ALLOW_DOWNGRADING = "allow_downgrading";
    public static final String PREF_ALLOW_LOCAL_UPDATES = "allow_local_updates";
    public static final String PREF_ALLOW_BETA_UPDATES = "allow_beta_updates";

    // Rollout Configuration
    public static final boolean IS_STAGED_ROLLOUT_ENABLED = true;
    public static final String IS_ROLLOUT_READY = "is_staged_rollout_ready";
    public static final String IS_ROLLOUT_SCHEDULED = "is_staged_rollout_scheduled";

    // Matchmaker Configuration
    public static final String IS_MATCHMAKER_ENABLED = "hub_is_match_maker_enabled";

    // General Update Operations
    public static final String PREF_LAST_UPDATE_CHECK = "last_update_check";
    public static final String PREF_INSTALL_OLD_TIMESTAMP = "install_old_timestamp";
    public static final String PREF_INSTALL_NEW_TIMESTAMP = "install_new_timestamp";
    public static final String PREF_INSTALL_PACKAGE_PATH = "install_package_path";
    public static final String PREF_INSTALL_AGAIN = "install_again";
    public static final String PREF_INSTALL_NOTIFIED = "install_notified";
    public static final long UPDATE_CHECK_INTERVAL = AlarmManager.INTERVAL_HALF_DAY; // 12 hours

    // Properties
    public static final String AB_PAYLOAD_BIN_PATH = "payload.bin";
    public static final String AB_PAYLOAD_PROPERTIES_PATH = "payload_properties.txt";
    public static final String PROP_AB_DEVICE = "ro.build.ab_update";
    public static final String PROP_VERSION = "ro.aospa.version";
    public static final String PROP_VERSION_MAJOR = "ro.aospa.version.major";
    public static final String PROP_VERSION_MINOR = "ro.aospa.version.minor";
    public static final String PROP_DEVICE = "ro.aospa.device";
    public static final String PROP_BUILD_TYPE = "ro.aospa.build.variant";
    public static final String UNCRYPT_FILE_EXT = ".uncrypt";
}
