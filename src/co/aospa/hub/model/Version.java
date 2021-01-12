/*
 * Copyright (C) 2021 Paranoid Android
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

    public static final String TYPE_ALPHA = "ALPHA";
    public static final String TYPE_BETA = "BETA";
    public static final String TYPE_DEV = "DEV";
    public static final String TYPE_RELEASE = "RELEASE";

    private Context mContext;
    private String mName;
    private String mVersion;
    public long mTimestamp;
    private int mPersistentStatus;

    private boolean mAllowBetaUpdates;
    private boolean mAllowDowngrading;

    public Version() {
    }

    public Version(Context context, Update update) {
        mContext = context;
        SharedPreferences prefs = context.getSharedPreferences(Utils.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        mAllowBetaUpdates = prefs.getBoolean(Constants.PREF_ALLOW_BETA_UPDATES, false);
        mAllowDowngrading = prefs.getBoolean(Constants.PREF_ALLOW_DOWNGRADING,
                context.getResources().getBoolean(R.bool.config_allowDowngradingDefault));
        mName = update.getName();
        mVersion = update.getVersion();
        mTimestamp = update.getTimestamp();
        mPersistentStatus = update.getPersistentStatus();
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
        Log.d(TAG, mName + " Version:" + mVersion + "Build:" + Long.toString(mTimestamp)
                + " is older than current Paranoid Android version");
        return false;
    }

    public boolean isNewUpdate() {
        return Float.valueOf(mVersion) > Float.valueOf(getCurrentVersion())
                && mTimestamp > getCurrentTimestamp();
    }

    public boolean isIncremental() {
        return Float.valueOf(mVersion) == Float.valueOf(getCurrentVersion())
                && mTimestamp > getCurrentTimestamp();
    }

    public boolean isDowngrade() {
        return mAllowDowngrading &&
                Float.valueOf(mVersion) < Float.valueOf(getCurrentVersion());
    }

    public static String getCurrentFlavor() {
        return SystemProperties.get(Constants.PROP_VERSION_FLAVOR);
    }

    public static String getCurrentVersion() {
        return SystemProperties.get(Constants.PROP_VERSION_CODE);
    }

    public static long getCurrentTimestamp() {
        String date;
        String version = SystemProperties.get(Constants.PROP_VERSION);
        String[] split = version.split("-");
        if (TYPE_BETA.toLowerCase().equals(getCurrentFlavor().toLowerCase()) {
           date = split[5];
        } else {
           date = split[4];
        }
        return Long.valueOf(date);
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
        String updateType = null;
        String[] split = mName.split("-");
        try {
            updateType = split[2];
        } catch(ArrayIndexOutOfBoundsException e) {
            return false;
        }
        String beta = TYPE_BETA.toLowerCase();
        return updateType != null && beta.equals(updateType);
    }
}
