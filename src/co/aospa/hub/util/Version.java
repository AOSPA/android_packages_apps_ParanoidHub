package co.aospa.hub.util;

import android.os.SystemProperties;
import android.util.Log;

import co.aospa.hub.components.UpdateComponent;

public class Version {

    private static final String TAG = "Version";

    private final UpdateComponent mComponent;

    public Version(UpdateComponent component) {
        mComponent = component;
    }

    public boolean isUpdateAvailable() {
        if (isValidatedDowngraded()) {
            Log.d(TAG, mComponent.getFileName()
                    + " is available for downgrade");
            return true;
        }

        if (mComponent != null) {
            // Treat it as a valid update if the timestamp is newer
            if (mComponent.getTimestamp() > getCurrentTimestamp()) {
                Log.d(TAG, mComponent.getFileName() + " is available for update");
                return true;
            } else {
                Log.d(TAG, "Update timestamp: " + mComponent.getTimestamp()
                        + " is older than current timestamp: " + getCurrentTimestamp());
            }
        }
        return false;
    }

    private boolean isValidatedDowngraded() {
        return canUserDowngrade()
                && mComponent.getTimestamp()
                < getCurrentTimestamp();
    }

    public boolean canUserDowngrade() {
        return false;
    }

    public static String getCurrentVersion() {
        return SystemProperties.get(Constants.PROP_VERSION_MAJOR);
    }

    public static String getCurrentVersionNumber() {
        return SystemProperties.get(Constants.PROP_VERSION_MINOR);
    }

    public static long getCurrentTimestamp() {
        return Long.parseLong(SystemProperties.get(Constants.PROP_BUILD_DATE));
    }

    public static String getBuildType() {
        return SystemProperties.get(Constants.PROP_BUILD_TYPE);
    }
}
