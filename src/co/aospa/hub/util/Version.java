package co.aospa.hub.util;

import android.content.Context;
import android.os.SystemProperties;
import android.util.Log;

import co.aospa.hub.components.UpdateComponent;

public class Version {

    private static final String TAG = "Version";

    private Context mContext;
    private UpdateComponent mComponent;

    public Version(Context context, UpdateComponent component) {
        mContext = context;
        mComponent = component;
    }

    public boolean isUpdateAvailable() {
        if (isDowngrade()) {
            Log.d(TAG, mComponent.getFileName()
                    + " is available for downgrade");
            return true;
        }

        /** Treat it as a valid update if the timestamp is newer **/
        if (mComponent.getTimestamp() > getCurrentTimestamp()) {
            Log.d(TAG, mComponent.getFileName() + " is available for update");
            return true;
        }

        Log.d(TAG, mComponent.getFileName() + " Version: " + mComponent.getVersion()
                + " " + mComponent.getVersionNumber() + " Build: " + mComponent.getTimestamp()
                + " is older than current Paranoid Android version");
        return false;
    }

    private boolean isDowngrade() {
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
