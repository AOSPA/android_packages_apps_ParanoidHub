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
package com.paranoid.hub.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemProperties;
import android.util.Log;

import com.paranoid.hub.misc.Constants;
import com.paranoid.hub.misc.Utils;

public class Version {

    private Context mContext;
    private String mName;
    private String mVersion;
    private long mTimestamp;
    private int mPersistentStatus;

    private boolean mAllowDowngrading;

    public Version() {
    }

    public Version(Context context, Update update) {
        mContext = context;
        SharedPreferences prefs = context.getSharedPreferences(Utils.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        mAllowDowngrading = prefs.getBoolean(Constants.PREF_ALLOW_DOWNGRADING, 
                context.getResources().getBoolean(R.bool.config_allowDowngradingDefault));
        mName = update.getName();
        mVersion = update.getVersion();
        mTimestamp = update.getTimestamp();
        mPersistentStatus = update.getPersistentStatus();
    }

    public boolean isUpdateAvailable() {
        if (mPersistentStatus == UpdateStatus.Persistent.LOCAL_UPDATE) {
            Log.d(TAG, mName + " is available for local upgrade");
            return true;
        }

        if (isDowngrade()) {
            Log.d(TAG, mName + " is available for downgrade");
            return true;
        }

        if (isIncremental()) {
            Log.d(TAG, mName + " is available for incremental or hotfix");
            return true;
        }

        if (isNewUpdate()) {
            Log.d(TAG, mName + " is available for update");
            return true;
        }
        Log.d(TAG, mName + " Verson:" + mVersion 
                + "Build:" + Long.toString(mTimestamp) 
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
        String version = SystemProperties.get(Constants.PROP_VERSION);
        String[] split = version.split("-");
        String date = split[3];
        return Long.valueOf(date);
    }

    public static String getBuildType() {
        return SystemProperties.get(Constants.PROP_BUILD_TYPE);
    }
}
