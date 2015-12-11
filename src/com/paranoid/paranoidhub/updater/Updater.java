/*
 * Copyright 2014 ParanoidAndroid Project
 *
 * This file is part of Paranoid OTA.
 *
 * Paranoid OTA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Paranoid OTA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Paranoid OTA.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.paranoid.paranoidhub.updater;

import android.app.Activity;
import android.content.Context;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.paranoid.paranoidhub.utils.NotificationUtils;
import com.paranoid.paranoidhub.utils.UpdateUtils;
import com.paranoid.paranoidhub.utils.Version;

import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public abstract class Updater implements Response.Listener<JSONObject>, Response.ErrorListener {
    private Context mContext;
    private Server[] mServers;
    private PackageInfo[] mLastUpdates = new PackageInfo[0];
    private List<UpdaterListener> mListeners = new ArrayList<>();
    private RequestQueue mQueue;
    private Server mServer;
    private boolean mScanning = false;
    private boolean mFromAlarm;
    private boolean mServerWorks = false;
    private int mCurrentServer = -1;

    public Updater(Context context, Server[] servers, boolean fromAlarm) {
        mContext = context;
        mServers = servers;
        mFromAlarm = fromAlarm;
        mQueue = Volley.newRequestQueue(context);
    }

    public abstract Version getVersion();

    public abstract String getDevice();

    public abstract int getErrorStringId();

    protected Context getContext() {
        return mContext;
    }

    public PackageInfo[] getLastUpdates() {
        return mLastUpdates;
    }

    public void setLastUpdates(PackageInfo[] infos) {
        if (infos == null) {
            infos = new PackageInfo[0];
        }
        mLastUpdates = infos;
    }

    public void addUpdaterListener(UpdaterListener listener) {
        mListeners.add(listener);
    }

    public void check() {
        check(false);
    }

    public void check(boolean force) {
        if (mScanning) return;
        if (mFromAlarm && !force) return;
        mServerWorks = false;
        mScanning = true;
        fireStartChecking();
        nextServerCheck();
    }

    protected void nextServerCheck() {
        mScanning = true;
        mCurrentServer++;
        mServer = mServers[mCurrentServer];
        JsonObjectRequest jsObjRequest = new JsonObjectRequest(mServer.getUrl(
                getDevice(), getVersion()), null, this, this);
        mQueue.add(jsObjRequest);
    }

    @Override
    public void onResponse(JSONObject response) {
        mScanning = false;
        try {
            PackageInfo[] lastUpdates;
            setLastUpdates(null);
            List<PackageInfo> list = mServer.createPackageInfoList(response);
            String error = mServer.getError();

            lastUpdates = list.toArray(new PackageInfo[list.size()]);
            if (lastUpdates.length > 0) {
                mServerWorks = true;
                if (mFromAlarm) {
                    NotificationUtils.showNotification(getContext(), lastUpdates);
                }
            } else {
                if (error != null && !error.isEmpty()) {
                    if (versionError(error)) {
                        return;
                    }
                } else {
                    mServerWorks = true;
                    if (mCurrentServer < mServers.length - 1) {
                        nextServerCheck();
                        return;
                    }
                }
            }
            mCurrentServer = -1;
            setLastUpdates(lastUpdates);
            fireCheckCompleted(lastUpdates);
        } catch (Exception ex) {
            System.out.println(response.toString());
            ex.printStackTrace();
            versionError(null);
        }
    }

    @Override
    public void onErrorResponse(VolleyError ex) {
        mScanning = false;
        versionError(null);
    }

    private boolean versionError(String error) {
        if (mCurrentServer < mServers.length - 1) {
            nextServerCheck();
            return true;
        }
        if (!mFromAlarm && !mServerWorks) {
            int id = getErrorStringId();
            if (error != null) {
                UpdateUtils.showToastOnUiThread(getContext(), getContext().getResources().getString(id)
                        + ": " + error);
            } else {
                UpdateUtils.showToastOnUiThread(getContext(), id);
            }
        }
        mCurrentServer = -1;
        fireCheckCompleted(null);
        fireCheckError(error);
        return false;
    }

    public boolean isScanning() {
        return mScanning;
    }

    public void removeUpdaterListener(UpdaterListener listener) {
        mListeners.remove(listener);
    }

    protected void fireStartChecking() {
        if (mContext instanceof Activity) {
            ((Activity) mContext).runOnUiThread(new Runnable() {

                public void run() {
                    for (UpdaterListener listener : mListeners) {
                        listener.startChecking();
                    }
                }
            });
        }
    }

    protected void fireCheckCompleted(final PackageInfo[] info) {
        if (mContext instanceof Activity) {
            ((Activity) mContext).runOnUiThread(new Runnable() {

                public void run() {
                    for (UpdaterListener listener : mListeners) {
                        listener.versionFound(info);
                    }
                }
            });
        }
    }

    protected void fireCheckError(final String cause) {
        if (mContext instanceof Activity) {
            ((Activity) mContext).runOnUiThread(new Runnable() {

                public void run() {
                    for (UpdaterListener listener : mListeners) {
                        listener.checkError(cause);
                    }
                }
            });
        }
    }

    public interface PackageInfo extends Serializable {

        String getMd5();

        String getFilename();

        String getPath();

        String getHost();

        String getSize();

        Version getVersion();

        boolean isDelta();

        String getDeltaFilename();

        String getDeltaPath();

        String getDeltaMd5();
    }

    public interface UpdaterListener {

        void startChecking();

        void versionFound(PackageInfo[] info);

        void checkError(String cause);
    }
}
