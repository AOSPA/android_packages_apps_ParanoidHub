package co.aospa.hub.util;

import android.os.SystemProperties;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import co.aospa.hub.components.UpdateComponent;

public class Version {

    private static final String TAG = "Version";

    private final UpdateComponent mComponent;

    public Version(UpdateComponent component) {
        mComponent = component;
    }

    public boolean isUpdateAvailable() {
        if (mComponent != null) {
            if (isValidatedDowngraded()) {
                Log.d(TAG, mComponent.getFileName()
                        + " is available for downgrade");
                return true;
            }

            if (isAndroidUpgrade()) {
                Log.d(TAG, mComponent.getFileName() + " is available for update and Android upgrade");
                return true;
            }

            // Treat it as a valid update if the timestamp is newer
            if (mComponent.getTimestamp() > getCurrentTimestamp()
                    || (Float.parseFloat(mComponent.getVersionNumber())
                    > Float.parseFloat(getCurrentVersionNumber()))) {
                Log.d(TAG, mComponent.getFileName() + " is available for update");
                return true;
            } else {
                Log.d(TAG, "Current update component has no new updates");
            }
        }
        return false;
    }

    private boolean isAndroidUpgrade() {
        return mComponent != null &&
                Long.parseLong(mComponent.getAndroidVersion()) > getAndroidVersion();
    }

    private boolean isValidatedDowngraded() {
        return canUserDowngrade()
                && mComponent.getTimestamp()
                < getCurrentTimestamp();
    }

    public boolean canUserDowngrade() {
        return false;
    }

    public static long getAndroidVersion() {
        return Long.parseLong(SystemProperties.get(Constants.PROP_ANDROID_VERSION));
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

    public static String getAndroidSpl() {
        String dataIn = SystemProperties.get(Constants.PROP_ANDROID_SPL);
        String dataOut;

        SimpleDateFormat oldFormat = new SimpleDateFormat("yyyy-M-dd", Locale.US);
        SimpleDateFormat newFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.US);
        Date date = null;
        try {
            date = oldFormat.parse(dataIn);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        dataOut = newFormat.format(Objects.requireNonNull(date));
        return dataOut;
    }

    public static String getRawAndroidSpl() {
        return SystemProperties.get(Constants.PROP_ANDROID_SPL);
    }
}
