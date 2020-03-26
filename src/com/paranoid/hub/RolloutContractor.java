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
package com.paranoid.hub;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.paranoid.hub.R;
import com.paranoid.hub.download.ClientConnector;
import com.paranoid.hub.download.DownloadClient;
import com.paranoid.hub.misc.Constants;
import com.paranoid.hub.misc.Utils;
import com.paranoid.hub.notification.NotificationContractor;
import com.paranoid.hub.receiver.UpdateCheckReceiver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.Object;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.json.JSONException;
import org.json.JSONObject;

public class RolloutContractor implements ClientConnector.ConnectorListener {

    private static final String TAG = "RolloutContractor";

    private static final int SIM_1 = 1;

    private static final String ROLLOUT_ACTION = "rollout_action";
    private static final long INTERVAL_5_MINUTES = 300000;
    private static final long INTERVAL_15_MINUTES = 900000;
    private static final long INTERVAL_1_HOUR = 3600000;
    private static final long INTERVAL_3_HOURS = 10800000;
    private static final long INTERVAL_5_HOURS = 18000000;
    private static final long INTERVAL_7_HOURS = 25200000;

    private String[] mWhitelist;
    private static final String[] DEVICE_A = { "00", "01", "02", "03", "04", "05", "06", "07", "08", 
            "09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19" };
    private static final String[] DEVICE_B = { "20", "21", "22", "23", "24", "25", "26", "27", "28", 
            "29", "30", "31", "32", "33", "34", "35", "36", "37", "38", "39" };
    private static final String[] DEVICE_C = { "40", "41", "42", "43", "44", "45", "46", "47", "48", 
            "49", "50", "51", "52", "53", "54", "55", "56", "57", "58", "59" };
    private static final String[] DEVICE_D = { "60", "61", "62", "63", "64", "65", "66", "67", "68", 
            "69", "70", "71", "72", "73", "74", "75", "76", "77", "78", "79" };
    private static final String[] DEVICE_E = { "80", "81", "82", "83", "84", "85", "86", "87", "88", 
            "89", "90", "91", "92", "93", "94", "95", "96", "97", "98", "99" };

    private Context mContext;
    private ClientConnector mConnector;
    private final Object mLock = new Object();
    private TelephonyManager mTelephonyManager;

    private boolean mReady = false;
    private boolean mScheduled = false;
    private String mImei;

    public RolloutContractor(Context context) {
        mContext = context;
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (mConnector == null) {
            mConnector = new ClientConnector(context);
            mConnector.addClientStatusListener(this);
        }
    }

    public void setupDevice() {
        mImei = mTelephonyManager.getImei(SIM_1).toString();
        Log.d(TAG, "Device imei is: " + mImei);

        File oldWhitelist = new File(mContext.getCacheDir(), "whitelisted.json");
        File newWhitelist = new File(oldWhitelist.getAbsolutePath() + UUID.randomUUID());
        String url = Utils.getWhitelistUrl(mContext);
        Log.d(TAG, "Updating whitelisted devices for rollout from " + url);
        mConnector.insert(oldWhitelist, newWhitelist, url);
        mConnector.start();
    }

    private long getRolloutForDevice() {
        if (isDeviceWhitelisted()) {
            setReady(true);
            return 0;
        } else if (isDevice(DEVICE_A)) {
            return INTERVAL_1_HOUR;
        } else if (isDevice(DEVICE_B)) {
            return INTERVAL_3_HOURS;
        } else if (isDevice(DEVICE_C)) {
            return INTERVAL_5_HOURS;
        } else if (isDevice(DEVICE_D)) {
            return INTERVAL_7_HOURS;
        } else if (isDevice(DEVICE_E)) {
            return INTERVAL_15_MINUTES;
        }
        return INTERVAL_5_MINUTES;
    }

    private PendingIntent getRolloutIntent() {
        Intent intent = new Intent(mContext, UpdateCheckReceiver.class);
        intent.setAction(ROLLOUT_ACTION);
        return PendingIntent.getBroadcast(mContext, 0, intent, 0);
    }

    public void schedule() {
        if (mScheduled) {
            Log.d(TAG, "Rollout is already scheduled");
            return;
        }

        if (!mReady) {
            mScheduled = true;
            long millisToRollout = getRolloutForDevice();
            PendingIntent rolloutIntent = getRolloutIntent();
            AlarmManager alarmMgr = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
            alarmMgr.set(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + millisToRollout,
                    rolloutIntent);
            NotificationContractor contractor = new NotificationContractor(mContext);
            contractor.retract(NotificationContractor.ID);

            Date rolloutDate = new Date(System.currentTimeMillis() + millisToRollout);
            Log.d(TAG, "Rollout for device is: " + rolloutDate);
        }
    }

    private void matchMakeWhitelist(File newWhitelist)
            throws IOException, JSONException {
        String json = "";
        try (BufferedReader br = new BufferedReader(new FileReader(newWhitelist))) {
            for (String line; (line = br.readLine()) != null;) {
                json += line;
            }
        }
        JSONObject obj = new JSONObject(json);
        JSONObject whitelist = obj.getJSONObject("whitelisted_devices");
        Iterator keys = whitelist.keys();
        List<String> list = new ArrayList<String>();
        while(keys.hasNext()) {
            String key = (String) keys.next();
            list.add(key);
        }
        mWhitelist = list.toArray(new String[list.size()]);
        Log.d(TAG, "Whitelisted devices: " + Arrays.toString(mWhitelist));
        schedule();
    }

    private boolean isDevice(String[] imeiPrefix) {
        return Stream.of(imeiPrefix).anyMatch(mImei::startsWith);
    }

    private boolean isDeviceWhitelisted() {
        if (mWhitelist == null) {
            Log.d(TAG, "Nothing in the whitelist");
            return false;
        }
        boolean match = Stream.of(mWhitelist).anyMatch(mImei::equals);
        Log.d(TAG, "Device is apart of whitelisted rollout: " + match);
        return match;
    }

    public void setReady(boolean ready) {
        mScheduled = false;
        synchronized (mLock) {
            if (ready != mReady) {
                mReady = ready;
            }
        }
    }

    public boolean isReady() {
        synchronized(mLock) {
            if (!Utils.isDebug() && Constants.IS_STAGED_ROLLOUT_ENABLED) {
                return mReady;
            }
            if (Utils.isDebug()) Log.d(TAG, "Staged rollouts disabled on debug builds");
            return true;
        }
    }

    @Override
    public void onClientStatusFailure(boolean cancelled) {
        Log.d(TAG, "Could not download whitelist");
    }

    @Override
    public void onClientStatusResponse(int statusCode, String url, DownloadClient.Headers headers) {}

    @Override
    public void onClientStatusSuccess(File oldWhitelist, File newWhitelist) {
        try {
            matchMakeWhitelist(newWhitelist);
        } catch (IOException | JSONException e) {
            Log.d(TAG, "Could not match make device whitelist");
        }
    }
}
