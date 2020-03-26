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

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.paranoid.hub.R;

public class RolloutContractor {

    private static final String TAG = "RolloutContractor";

    private Context mContext;
    private TelephonyManager mTelephonyManager;

    private boolean mReady = false;
    private String mImei;

    public RolloutContractor(Context context) {
        mContext = context;
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        int simSlot = mTelephonyManager.getPhoneCount() == 1;
        mImei = mTelephonyManager.getImei(simSlot).toString();
        Log.d(TAG, "Device imei is: " + mImei);
    }

    public boolean isReady() {
        return mReady;
    }
}
