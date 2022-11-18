/*
 * Copyright (C) 2022 Paranoid Android
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
package co.aospa.hub.util;

public class Constants {

    // Intents
    public static final String INTENT_ACTION_CHECK_UPDATES = "action_check_updates";
    public static final String INTENT_ACTION_UPDATE_CONFIG = "action_update_config";
    public static final String INTENT_ACTION_UPDATE_CHANGELOG = "action_update_changelog";

    // Update intent w/ Extras
    public static final String INTENT_ACTION_UPDATE = "action_update";
    public static final String INTENT_ACTION_UPDATE_INSTALL_LEGACY = "action_update_install";
    public static final String EXTRA_DOWNLOAD = "extra_download";
    public static final String EXTRA_DOWNLOAD_ACTION_START = "extra_download_start";
    public static final String EXTRA_DOWNLOAD_ACTION_PAUSE = "extra_download_pause";
    public static final String EXTRA_DOWNLOAD_ACTION_RESUME = "extra_download_resume";
    public static final String EXTRA_DOWNLOAD_ACTION_CANCEL = "extra_download_cancel";

    // Preference Keys
    public static final boolean USE_AB_PERFORMANCE_MODE = true;
    public static final String KEY_LAST_UPDATE_CHECK = "last_update_check";
    public static final String KEY_UPDATE_STATUS = "update_status";
    public static final String NOTIFICATION_CHANNEL_ID = "system_updates_notification_channel";

    // Properties
    public static final String AB_PAYLOAD_BIN_PATH = "payload.bin";
    public static final String AB_PAYLOAD_PROPERTIES_PATH = "payload_properties.txt";
    public static final String PROP_AB_DEVICE = "ro.build.ab_update";
    public static final String PROP_ANDROID_VERSION = "ro.build.version.release";
    public static final String PROP_ANDROID_SPL = "ro.build.version.security_patch";
    public static final String PROP_VERSION = "ro.aospa.version";
    public static final String PROP_VERSION_MAJOR = "ro.aospa.version.major";
    public static final String PROP_VERSION_MINOR = "ro.aospa.version.minor";
    public static final String PROP_DEVICE = "ro.aospa.device";
    public static final String PROP_BUILD_TYPE = "ro.aospa.build.variant";
    public static final String PROP_DEVICE_MODEL = "ro.product.model";
    public static final String PROP_BUILD_DATE = "ro.build.date.utc";
    public static final String UNCRYPT_FILE_EXT = ".uncrypt";
}
