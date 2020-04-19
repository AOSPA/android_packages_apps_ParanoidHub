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
package co.aospa.hub.notification;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;


public class NotificationContractor {

    public static final String TAG = "NotificationContractor";

    public static final String INSTALL_ERROR_NOTIFICATION_CHANNEL = "install_error_notification_channel";
    public static final String ONGOING_NOTIFICATION_CHANNEL = "ongoing_notification_channel";
    public static final String PROGRESS_NOTIFICATION_CHANNEL = "progress_notification_channel";
    public static final String NEW_UPDATES_NOTIFICATION_CHANNEL = "new_updates_notification_channel";

    public static final int ID = 10;

    private Context mContext;
    private NotificationContract mContract;
    private NotificationManager mNotificationManager;

    public NotificationContractor(Context context) {
        mContext = context;
        mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public NotificationContract create(String channel, boolean highImportance) {
        mContract = new NotificationContract(mContext, mNotificationManager, channel, highImportance);
        return mContract;
    }

    public void present(int id) {
        mNotificationManager.notify(id, mContract.write());
    }

    public void retract(int id) {
        mNotificationManager.cancel(id);
    }

    public void setExtras(Bundle extras) {
        if (mContract != null) {
            mContract.setExtras(extras);
        }
    }

    public Bundle getExtras() {
        if (mContract != null) {
            return mContract.getExtras();
        }
        return null;
    }
}
