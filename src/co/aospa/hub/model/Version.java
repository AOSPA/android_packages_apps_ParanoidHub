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

    private String mName;
    private String mVersion;
    public long mTimestamp;

    private boolean mAllowBetaUpdates;
    private boolean mAllowDowngrading;

    public Version() {
    }

    public Version(Context context, Update update) {
        SharedPreferences prefs = context.getSharedPreferences(Utils.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        mAllowBetaUpdates = prefs.getBoolean(Constants.PREF_ALLOW_BETA_UPDATES, false);
        mAllowDowngrading = prefs.getBoolean(Constants.PREF_ALLOW_DOWNGRADING, 
                context.getResources().getBoolean(R.bool.config_allowDowngradingDefault));
        mName = update.getName();
        mVersion = update.getVersion();
        mTimestamp = update.getTimestamp();
    }

    public boolean isUpdateAvailable() {
        if (isDowngrade()) {
            Log.d(TAG, mName + " is available for downgrade");
            return true;
        }

        if (isIncremental()) {
            if (isBetaUpdate() && !mAllowBetaUpdates) {
                Log.d(TAG, mName + " is a beta but the user is not opted in");
                return false;
            }
            Log.d(TAG, mName + " is available for incremental or hotfix");
            return true;
        }

        if (isNewUpdate()) {
            if (isBetaUpdate() && !mAllowBetaUpdates) {
                Log.d(TAG, mName + " is a beta but the user is not opted in");
                return false;
            }
            Log.d(TAG, mName + " is available for update");
            return true;
        }
        Log.d(TAG, mName + " Verson:" + mVersion 
                + "Build:" + mTimestamp
                + " is older than current Paranoid Android version");
        return false;
    }

    public boolean isNewUpdate() {
        return Float.parseFloat(mVersion) > Float.parseFloat(getMinor())
                && mTimestamp > getCurrentTimestamp();
    }

    public boolean isIncremental() {
        return Float.valueOf(mVersion).equals(Float.valueOf(getMinor()))
                && mTimestamp > getCurrentTimestamp();
    }

    public boolean isDowngrade() {
        return mAllowDowngrading && 
                Float.parseFloat(mVersion) < Float.parseFloat(getMinor());
    }

    public static String getMajor() {
        return SystemProperties.get(Constants.PROP_VERSION_MAJOR);
    }

    public static String getMinor() {
        return SystemProperties.get(Constants.PROP_VERSION_MINOR);
    }

    public static long getCurrentTimestamp() {
        String date;
        String version = SystemProperties.get(Constants.PROP_VERSION);
        String[] split = version.split("-");
        if (isBuild(TYPE_RELEASE)) {
            date = split[3];
        } else {
            date = split[4];
        }
        return Long.parseLong(date);
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

    public boolean isBetaUpdate() {
        String updateType;
        String[] split = mName.split("-");
        try {
            updateType = split[5];
        } catch(ArrayIndexOutOfBoundsException e) {
            return false;
        }
        String beta = TYPE_BETA.toLowerCase();
        return beta.equals(updateType);
    }
}
