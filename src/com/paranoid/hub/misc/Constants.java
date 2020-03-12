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
package com.paranoid.hub.misc;

import android.app.AlarmManager;

public final class Constants {

    private Constants() {
    }

    public static final String AB_PAYLOAD_BIN_PATH = "payload.bin";
    public static final String AB_PAYLOAD_PROPERTIES_PATH = "payload_properties.txt";

    public static final String UPDATE_STATUS = "hub_update_status";
    public static final String IS_UPDATE_DOWNLOADING = "hub_is_update_downloading";

    public static final long UPDATE_CHECK_INTERVAL = AlarmManager.INTERVAL_HALF_DAY; // 12 hours

    public static final String PREF_LAST_UPDATE_CHECK = "last_update_check";
    public static final String PREF_AUTO_DELETE_UPDATES = "auto_delete_updates";
    public static final String PREF_AB_PERF_MODE = "ab_perf_mode";
    public static final String PREF_ALLOW_DOWNGRADING = "allow_downgrading";
    public static final String PREF_NEEDS_REBOOT_ID = "needs_reboot_id";

    public static final String UNCRYPT_FILE_EXT = ".uncrypt";

    public static final String PROP_AB_DEVICE = "ro.build.ab_update";
    public static final String PROP_VERSION = "ro.pa.version";
    public static final String PROP_VERSION_FLAVOR = "ro.pa.version.flavor";
    public static final String PROP_VERSION_CODE = "ro.pa.version.code";
    public static final String PROP_DEVICE = "ro.pa.device";

    public static final String PREF_INSTALL_OLD_TIMESTAMP = "install_old_timestamp";
    public static final String PREF_INSTALL_NEW_TIMESTAMP = "install_new_timestamp";
    public static final String PREF_INSTALL_PACKAGE_PATH = "install_package_path";
    public static final String PREF_INSTALL_AGAIN = "install_again";
    public static final String PREF_INSTALL_NOTIFIED = "install_notified";
}
