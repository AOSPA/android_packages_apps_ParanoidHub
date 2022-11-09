package co.aospa.hub.util;

public class Constants {

    // Intents
    public static final String INTENT_ACTION_CHECK_UPDATES = "action_check_updates";
    public static final String INTENT_ACTION_UPDATE_CONFIG = "action_update_config";

    // Preference Keys
    public static final String KEY_LAST_UPDATE_CHECK = "last_update_check";
    public static final String KEY_STATE = "state";

    // Properties
    public static final String AB_PAYLOAD_BIN_PATH = "payload.bin";
    public static final String AB_PAYLOAD_PROPERTIES_PATH = "payload_properties.txt";
    public static final String PROP_AB_DEVICE = "ro.build.ab_update";
    public static final String PROP_VERSION = "ro.aospa.version";
    public static final String PROP_VERSION_MAJOR = "ro.aospa.version.major";
    public static final String PROP_VERSION_MINOR = "ro.aospa.version.minor";
    public static final String PROP_DEVICE = "ro.aospa.device";
    public static final String PROP_BUILD_TYPE = "ro.aospa.build.variant";
    public static final String PROP_DEVICE_MODEL = "ro.product.model";
    public static final String PROP_BUILD_DATE = "ro.build.date.utc";
    public static final String UNCRYPT_FILE_EXT = ".uncrypt";
}
