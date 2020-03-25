/*
 * Copyright (C) 2017 The LineageOS Project
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
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.paranoid.hub.misc.Constants;
import com.paranoid.hub.misc.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
/**
 * This class handles all updates fetched from the servers/local json
 **/
public class UpdatePresenter {

    private static final String TAG = "UpdatePresenter";
    private static Update mUpdate = null;

    // This should really return an UpdateBaseInfo object, but currently this only
    // used to initialize UpdateInfo objects
    private static UpdateInfo buildUpdate(Context context, JSONObject object) throws JSONException {
        Update update = new Update(context);
        update.setName(object.getString("name"));
        update.setVersion(object.getString("version"));
        update.setTimestamp(object.getLong("build"));
        update.setFileSize(object.getLong("size"));
        update.setDownloadUrl(object.getString("url"));
        update.setDownloadId(object.getString("md5"));
        return update;
    }

    private static ChangeLog buildChangelog(JSONObject object) throws JSONException {
        ChangeLog changelog = new ChangeLog();
        changelog.set(object.getString("info"));
        return changelog;
    }

    public static UpdateInfo matchMakeJson(Context context, File file)
            throws IOException, JSONException {
        UpdateInfo update = null;
        String json = "";
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            for (String line; (line = br.readLine()) != null;) {
                json += line;
            }
        }
        JSONObject obj = null;
        try {
            obj = new JSONObject(json);
        } catch (JSONException e) {
            return null;
        }
        JSONArray updates = obj.getJSONArray("updates");
        for (int i = 0; i < updates.length(); i++) {
            if (updates.isNull(i)) {
                continue;
            }
            try {
                update = buildUpdate(context, updates.getJSONObject(i));
            } catch (JSONException e) {
                Log.d(TAG, "Could not parse update object, index=" + i, e);
            }
        }
        return update;
    }

    public static ChangeLog matchMakeChangelog(File file)
            throws IOException, JSONException {
        ChangeLog changelog = null;
        String json = "";
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            for (String line; (line = br.readLine()) != null;) {
                json += line;
            }
        }
        JSONObject obj = new JSONObject(json);
        JSONArray changes = obj.getJSONArray("changelog");
        for (int i = 0; i < changes.length(); i++) {
            if (changes.isNull(i)) {
                continue;
            }
            try {
                changelog = buildChangelog(changes.getJSONObject(i));
            } catch (JSONException e) {
                Log.d(TAG, "Could not parse changelog object, index=" + i, e);
            }
        }
        return changelog;
    }

    /**
     * Compares two json formatted updates list files
     *
     * @param oldJson old update list
     * @param newJson new update list
     * @return true if old/new has at least a compatible update
     * @throws IOException
     * @throws JSONException
     */
    public static boolean isNewUpdate(Context context, File oldJson, File newJson)
            throws IOException, JSONException {
        UpdateInfo oldListInfo = matchMakeJson(context, oldJson);
        UpdateInfo newListInfo = matchMakeJson(context, newJson);
        if (oldListInfo == null || newListInfo == null) {
            return false;
        }

        Update oldUpdateList = new Update(oldListInfo);
        Update newUpdateList = new Update(newListInfo);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int status = prefs.getInt(Constants.UPDATE_STATUS, -1);
        if (status == UpdateStatus.DOWNLOADING 
                || status == UpdateStatus.DOWNLOADED) {
            Log.d(TAG, "Update is downloading or already downloaded");
            return false;
        }

        if (Utils.isCompatible(context, oldUpdateList)) {
            mUpdate = oldUpdateList;
            Log.d(TAG, "Update available via old (cached) list");
            return true;
        }

        if (Utils.isCompatible(context, newUpdateList)) {
            mUpdate = newUpdateList;
            Log.d(TAG, "Update available via new list");
            return true;
        }
        return false;
    }

    public static Update getUpdate() {
        return mUpdate;
    }
}
