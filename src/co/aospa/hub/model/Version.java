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
package co.aospa.hub.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemProperties;
import android.util.Log;

import co.aospa.hub.R;
import co.aospa.hub.misc.Constants;
import co.aospa.hub.misc.Utils;

import java.lang.ArrayIndexOutOfBoundsException;

public class Version {

    private static final String TAG = "Version";

    public static final String TYPE_ALPHA = "Alpha";
    public static final String TYPE_BETA = "Beta";
    public static final String TYPE_RELEASE = "Release";
    public static final String TYPE_UNOFFICIAL = "Unofficial";

    private String mName;
    private String mAndroidVersion;
    private String mVersion;
    private String mVersionNumber;
    private String mBuildType;
    public long mTimestamp;

    private boolean mAllowTestersUpdates;
    private boolean mAllowDowngrading;

    public Version() {
    }

    public Version(Context context, Update update) {
        SharedPreferences prefs = context.getSharedPreferences(Utils.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        mAllowTestersUpdates = prefs.getBoolean(Constants.PREF_ALLOW_TESTERS_UPDATES, false);
        mAllowDowngrading = prefs.getBoolean(Constants.PREF_ALLOW_DOWNGRADING, 
                context.getResources().getBoolean(R.bool.config_allowDowngradingDefault));
        mName = update.getName();
        mAndroidVersion = update.getAndroidVersion();
        mVersion = update.getVersion();
        mVersionNumber = update.getVersionNumber();
        mBuildType = update.getBuildType();
        mTimestamp = update.getTimestamp();
    }

    public boolean isUpdateAvailable() {
        if (isDowngrade()) {
            Log.d(TAG, mName + " is available for downgrade");
            return true;
        }

        if (isAndroidUpgrade()) {
            Log.d(TAG, mName + " is available for upgrade");
            return true;
        }

        // IF SAME TYPE UPDATE DIRECTLY
        if (isSameBuildType()) return isNewUpdate();

        // IF IT'S ALPHA OR BETA BUT WE DON'T HAVE THE TOGGLE ENABLED DON'T UPDATE
        // THIS AVOID FLASHING ALPHAS OVER BETAS AND OTHERWISE.
        if (isTestersUpdate() && !mAllowTestersUpdates) {
            Log.d(TAG, mName + " is a testers update but the user is not opted in");
            return false;
        }

        // IF WE ARE ON A RELEASE BUILD, THE TOGGLE IS ENABLED AND IS AN ALPHA OR BETA THEN UPDATE.
        if (isTestersUpdate() && mAllowTestersUpdates && getBuildType().equals(TYPE_RELEASE)) {
            return isNewUpdate();
        }

        // IF THE NEXT BUILD IS STABLE AND WE HAVE THE TOGGLE DISABLED THEN FLASH IT.
        if (!isTestersUpdate() && !mAllowTestersUpdates) {
            return true;
        }
    
        Log.d(TAG, mName + " Version: " + mVersion + " " + mVersionNumber
                + " Build: " + mTimestamp
                + " is older than current Paranoid Android version");
        return false;
    }

    public boolean isNewUpdate() {
        if (Float.parseFloat(mAndroidVersion) < getAndroidVersion()) return false;

        return (Float.parseFloat(mVersionNumber) > Float.parseFloat(getMinor()) || (mTimestamp > getCurrentTimestamp()));
    }

    public boolean isDowngrade() {
        return mAllowDowngrading && 
                Float.parseFloat(mVersionNumber) < Float.parseFloat(getMinor())
                && mTimestamp < getCurrentTimestamp();
    }

    public boolean isAndroidUpgrade() {
        return (Float.parseFloat(mAndroidVersion) > getAndroidVersion() && mBuildType.equals(getBuildType()))
                || (Float.parseFloat(mAndroidVersion) > getAndroidVersion() && !mBuildType.equals(getBuildType()) && mAllowTestersUpdates);
    }

    public static float getAndroidVersion() {
        return Float.parseFloat(SystemProperties.get(Constants.PROP_ANDROID_VERSION));
    }

    public static String getMajor() {
        return SystemProperties.get(Constants.PROP_VERSION_MAJOR);
    }

    public static String getMinor() {
        return SystemProperties.get(Constants.PROP_VERSION_MINOR);
    }

    public static long getCurrentTimestamp() {
        return Long.parseLong(SystemProperties.get(Constants.PROP_BUILD_DATE));
    }

    public static String getBuildType() {
        return SystemProperties.get(Constants.PROP_BUILD_TYPE);
    }

    public static boolean isBuild(String type) {
        String buildType = getBuildType();
        if ((type).equals(buildType)) {
            Log.d(TAG, "Current build type is: " + type);
            return true;
        }
        return false;
    }

    public boolean isTestersUpdate() {
        return mBuildType.equals(TYPE_ALPHA) || mBuildType.equals(TYPE_BETA);
    }

    public boolean isReleaseUpdate() {
        return mBuildType.equals(TYPE_RELEASE);
    }

    public boolean isSameBuildType() {
        return mBuildType.equals(getBuildType())
    }
}
