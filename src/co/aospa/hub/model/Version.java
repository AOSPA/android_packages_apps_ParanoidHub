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
    private String mVersionMajor;
    private String mVersionMinor;
    private String mVariant;
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
        mVersionMajor = update.getVersionMajor();
        mVersionMinor = update.getVersionMinor();
        mBuildVariant = update.getBuildVariant();
    }

    public boolean isUpdateAvailable() {
        if (isDowngrade()) {
            Log.d(TAG, mName + " is available for downgrade");
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
        Log.d(TAG, mName + " Version: " + mVersionMajor + " " + mVersionMinor
                + " Build: " + mTimestamp
                + " is older than current Paranoid Android version");
        return false;
    }

    public boolean isNewUpdate() {
        if (Float.parseFloat(mVersionMinor) > Float.parseFloat(getMinor())) {
            return true;
        }
    }

    public boolean isDowngrade() {
        return mAllowDowngrading && 
                Float.parseFloat(mVersionMinor) < Float.parseFloat(getMinor());
    }

    public static String getMajor() {
        return SystemProperties.get(Constants.PROP_VERSION_MAJOR);
    }

    public static String getMinor() {
        return SystemProperties.get(Constants.PROP_VERSION_MINOR);
    }

    public static String getBuildVariant() {
        return SystemProperties.get(Constants.PROP_BUILD_VARIANT);
    }

    public static boolean isBuild(String variant) {
        String buildVariant = getBuildVariant();
        if ((variant).equals(buildVariant)) {
            Log.d(TAG, "Current build type is: " + variant);
            return true;
        }
        return false;
    }

    public boolean isBetaUpdate() {
        return mBuildVariant.equals(TYPE_BETA);
    }
}
