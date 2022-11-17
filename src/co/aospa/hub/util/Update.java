package co.aospa.hub.util;

import android.content.Context;
import android.os.SystemProperties;
import android.os.storage.StorageManager;
import android.text.format.Formatter;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import co.aospa.hub.R;
import co.aospa.hub.client.ClientConnector;
import co.aospa.hub.components.ChangelogComponent;
import co.aospa.hub.components.UpdateComponent;

public class Update {

    private final ChangelogComponent mChangelogComponent;
    private final UpdateComponent mUpdateComponent;

    public Update(UpdateComponent updateComponent,
                  ChangelogComponent changelogComponent) {
        mChangelogComponent = changelogComponent;
        mUpdateComponent = updateComponent;

    }

    public static File getDownloadPath(Context context) {
        return new File(context.getString(R.string.system_update_download_path));
    }

    public static File getCachedUpdate(Context context) {
        return new File(context.getCacheDir(), ClientConnector.TARGET_DEVICE);
    }

    public String getUpdateDescriptionText(Context context) {
        String updateDescription = "error";
        if (mUpdateComponent != null) {
            if (isDeviceIncrementalUpdate()) {
                updateDescription = String.format(context.getResources().getString(
                                R.string.system_update_update_available_device_desc),
                        mUpdateComponent.getVersion(),
                        mUpdateComponent.getBuildType(),
                        mUpdateComponent.getVersionNumber(),
                        getDeviceChangelog(),
                        Formatter.formatShortFileSize(context, mUpdateComponent.getFileSize()));
            } else {
                updateDescription = String.format(context.getResources().getString(
                                R.string.system_update_update_available_desc),
                        mUpdateComponent.getDeviceChangelog(),
                        Formatter.formatShortFileSize(context, mUpdateComponent.getFileSize()));
            }

        }
        return updateDescription;
    }

    private String getDeviceChangelog() {
        String severText = mUpdateComponent.getDeviceChangelog();
        return severText.replaceAll(",", "\n");
    }

    public boolean isDeviceIncrementalUpdate() {
        Version version = new Version(mUpdateComponent);
        boolean isAndroidUpgrade = version.isAndroidUpgrade();
        return !isAndroidUpgrade && (Float.parseFloat(mUpdateComponent.getVersionNumber())
                > Float.parseFloat(mChangelogComponent.getVersionNumber()));
    }

    public boolean isSecurityUpdate() {
        return mUpdateComponent != null
                && !mUpdateComponent.getAndroidSpl().equals(Version.getRawAndroidSpl());
    }

    public static boolean isABDevice() {
        return SystemProperties.getBoolean(Constants.PROP_AB_DEVICE, false);
    }

    public static boolean isABUpdate(File file) throws IOException {
        ZipFile zipFile = new ZipFile(file);
        boolean isABUpdate = isABUpdate(zipFile);
        zipFile.close();
        return isABUpdate;
    }

    public static boolean isABUpdate(ZipFile zipFile) {
        return zipFile.getEntry(Constants.AB_PAYLOAD_BIN_PATH) != null &&
                zipFile.getEntry(Constants.AB_PAYLOAD_PROPERTIES_PATH) != null;
    }

    public static boolean isEncrypted(Context context, File file) {
        StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        return sm.isEncrypted(file);
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
        Log.e("Update", "Entry " + entryPath + " not found");
        throw new IllegalArgumentException("The given entry was not found");
    }
}
