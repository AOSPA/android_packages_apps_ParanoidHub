/*
 * Copyright (C) 2017 The LineageOS Project
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
package com.paranoid.hub.misc;

import android.app.AlarmManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.os.SystemProperties;
import android.os.storage.StorageManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.ColorInt;

import com.paranoid.hub.R;
import com.paranoid.hub.model.Update;
import com.paranoid.hub.model.UpdateDbHelper;
import com.paranoid.hub.model.UpdateBaseInfo;
import com.paranoid.hub.model.UpdateInfo;
import com.paranoid.hub.service.UpdateService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Utils {

    private static final String TAG = "Utils";

    private Utils() {
    }

    public static File getDownloadPath(Context context) {
        return new File(context.getString(R.string.download_path));
    }

    public static File getExportPath(Context context) {
        File dir = new File(Environment.getExternalStorageDirectory(),
                context.getString(R.string.export_path));
        if (!dir.isDirectory()) {
            if (dir.exists() || !dir.mkdirs()) {
                throw new RuntimeException("Could not create directory");
            }
        }
        return dir;
    }

    public static File getCachedUpdateList(Context context) {
        return new File(context.getCacheDir(), "updates.json");
    }

    public static File getCachedChangelog(Context context) {
        return new File(context.getCacheDir(), "changelog.json");
    }

    public static boolean isCompatible(Context context, Update update) {
        boolean allowDowngradingDefault = context.getResources().getBoolean(R.bool.config_allowDowngradingDefault);
        boolean allowDowngrading = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(Constants.PREF_ALLOW_DOWNGRADING, allowDowngradingDefault);
        int jsonVersion = Integer.valueOf(update.getVersion());
        int currentVersion = Integer.valueOf(SystemProperties.get(Constants.PROP_VERSION_CODE));
        if ((allowDowngrading || jsonVersion > currentVersion) && update.getTimestamp() > 
                    BuildInfoUtils.getBuildDateTimestamp()) {
            Log.d(TAG, update.getName() + " is compatible");
            return true;
        }
        Log.d(TAG, update.getName() + " is older than current Paranoid Android version");
        return false;
    }

    public static boolean canInstall(Context context, UpdateBaseInfo update) {
        boolean allowDowngradingDefault = context.getResources().getBoolean(R.bool.config_allowDowngradingDefault);
        boolean allowDowngrading = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(Constants.PREF_ALLOW_DOWNGRADING, allowDowngradingDefault);
        return (allowDowngrading || update.getTimestamp() > BuildInfoUtils.getBuildDateTimestamp());
    }

    public static String getServerURL(Context context) {
        String device = SystemProperties.get(Constants.PROP_DEVICE);
        String url = String.format(context.getResources().getString(
                R.string.updater_server_url), device);
        return url;
    }

    public static String getChangelogURL(Context context) {
        String url = context.getResources().getString(R.string.updater_changelog_url);
        return url;
    }

    public static void triggerUpdate(Context context, String downloadId) {
        final Intent intent = new Intent(context, UpdateService.class);
        intent.setAction(UpdateService.ACTION_INSTALL_UPDATE);
        intent.putExtra(UpdateService.EXTRA_DOWNLOAD_ID, downloadId);
        context.startService(intent);
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return !(info == null || !info.isConnected() || !info.isAvailable());
    }

    public static boolean isOnWifiOrEthernet(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return (info != null && (info.getType() == ConnectivityManager.TYPE_ETHERNET
                || info.getType() == ConnectivityManager.TYPE_WIFI));
    }

    /**
     * Get the offset to the compressed data of a file inside the given zip
     *
     * @param zipFile input zip file
     * @param entryPath full path of the entry
     * @return the offset of the compressed, or -1 if not found
     * @throws IllegalArgumentException if the given entry is not found
     */
    public static long getZipEntryOffset(ZipFile zipFile, String entryPath) {
        // Each entry has an header of (30 + n + m) bytes
        // 'n' is the length of the file name
        // 'm' is the length of the extra field
        final int FIXED_HEADER_SIZE = 30;
        Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
        long offset = 0;
        while (zipEntries.hasMoreElements()) {
            ZipEntry entry = zipEntries.nextElement();
            int n = entry.getName().length();
            int m = entry.getExtra() == null ? 0 : entry.getExtra().length;
            int headerSize = FIXED_HEADER_SIZE + n + m;
            offset += headerSize;
            if (entry.getName().equals(entryPath)) {
                return offset;
            }
            offset += entry.getCompressedSize();
        }
        Log.e(TAG, "Entry " + entryPath + " not found");
        throw new IllegalArgumentException("The given entry was not found");
    }

    public static void removeUncryptFiles(File downloadPath) {
        File[] uncryptFiles = downloadPath.listFiles(
                (dir, name) -> name.endsWith(Constants.UNCRYPT_FILE_EXT));
        if (uncryptFiles == null) {
            return;
        }
        for (File file : uncryptFiles) {
            file.delete();
        }
    }

    /**
     * Cleanup the download directory, which is assumed to be a privileged location
     * the user can't access and that might have stale files. This can happen if
     * the data of the application are wiped.
     *
     * @param context
     */
    public static void cleanupDownloadsDir(Context context) {
        File downloadPath = getDownloadPath(context);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        removeUncryptFiles(downloadPath);

        long buildTimestamp = BuildInfoUtils.getBuildDateTimestamp();
        long prevTimestamp = preferences.getLong(Constants.PREF_INSTALL_OLD_TIMESTAMP, 0);
        String lastUpdatePath = preferences.getString(Constants.PREF_INSTALL_PACKAGE_PATH, null);
        boolean reinstalling = preferences.getBoolean(Constants.PREF_INSTALL_AGAIN, false);
        boolean deleteUpdatesDefault = context.getResources().getBoolean(R.bool.config_autoDeleteUpdatesDefault);
        boolean deleteUpdates = preferences.getBoolean(Constants.PREF_AUTO_DELETE_UPDATES, deleteUpdatesDefault);
        if ((buildTimestamp != prevTimestamp || reinstalling) && deleteUpdates &&
                lastUpdatePath != null) {
            File lastUpdate = new File(lastUpdatePath);
            if (lastUpdate.exists()) {
                lastUpdate.delete();
                // Remove the pref not to delete the file if re-downloaded
                preferences.edit().remove(Constants.PREF_INSTALL_PACKAGE_PATH).apply();
            }
        }

        final String DOWNLOADS_CLEANUP_DONE = "cleanup_done";
        if (preferences.getBoolean(DOWNLOADS_CLEANUP_DONE, false)) {
            return;
        }

        Log.d(TAG, "Cleaning " + downloadPath);
        if (!downloadPath.isDirectory()) {
            return;
        }
        File[] files = downloadPath.listFiles();
        if (files == null) {
            return;
        }

        // Ideally the database is empty when we get here
        UpdateDbHelper dbHelper = new UpdateDbHelper(context);
        List<String> knownPaths = new ArrayList<>();
        for (UpdateInfo update : dbHelper.getUpdates()) {
            knownPaths.add(update.getFile().getAbsolutePath());
        }
        for (File file : files) {
            if (!knownPaths.contains(file.getAbsolutePath())) {
                Log.d(TAG, "Deleting " + file.getAbsolutePath());
                file.delete();
            }
        }

        preferences.edit().putBoolean(DOWNLOADS_CLEANUP_DONE, true).apply();
    }

    public static File appendSequentialNumber(final File file) {
        String name;
        String extension;
        int extensionPosition = file.getName().lastIndexOf(".");
        if (extensionPosition > 0) {
            name = file.getName().substring(0, extensionPosition);
            extension = file.getName().substring(extensionPosition);
        } else {
            name = file.getName();
            extension = "";
        }
        final File parent = file.getParentFile();
        for (int i = 1; i < Integer.MAX_VALUE; i++) {
            File newFile = new File(parent, name + "-" + i + extension);
            if (!newFile.exists()) {
                return newFile;
            }
        }
        throw new IllegalStateException();
    }

    public static boolean isABDevice() {
        return SystemProperties.getBoolean(Constants.PROP_AB_DEVICE, false);
    }

    public static boolean isABUpdate(ZipFile zipFile) {
        return zipFile.getEntry(Constants.AB_PAYLOAD_BIN_PATH) != null &&
                zipFile.getEntry(Constants.AB_PAYLOAD_PROPERTIES_PATH) != null;
    }

    public static boolean isABUpdate(File file) throws IOException {
        ZipFile zipFile = new ZipFile(file);
        boolean isAB = isABUpdate(zipFile);
        zipFile.close();
        return isAB;
    }

    public static boolean hasTouchscreen(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
    }

    public static void addToClipboard(Context context, String label, String text, String toastMessage) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(
                Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show();
    }

    public static boolean isEncrypted(Context context, File file) {
        StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        return sm.isEncrypted(file);
    }

    public static int getColorAccent(Context context) {
        return getAttrColor(context, android.R.attr.colorAccent);
    }

    public static int getAttrColor(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        int colorAccent = ta.getColor(0, 0);
        ta.recycle();
        return colorAccent;
    }

    @ColorInt
    public static int setColorAlphaBound(int color, int alpha) {
        if (alpha < 0) {
            alpha = 0;
        } else if (alpha > 255) {
            alpha = 255;
        }
        return (color & 0x00ffffff) | (alpha << 24);
    }
}
