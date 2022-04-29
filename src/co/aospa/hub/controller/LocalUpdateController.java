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
package co.aospa.hub.controller;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.NonNull;

import co.aospa.hub.HubActivity;
import co.aospa.hub.HubController;
import co.aospa.hub.misc.FileUtils;
import co.aospa.hub.misc.Utils;
import co.aospa.hub.model.Update;
import co.aospa.hub.model.UpdateInfo;
import co.aospa.hub.model.UpdateStatus;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class LocalUpdateController {

    private static final String TAG = "LocalUpdateController";

    private static final int REQUEST_PICK = 9061;
    private static final String FILE_NAME = "localUpdate.zip";
    private static final String MIME_ZIP = "application/zip";
    private static final String METADATA_PATH = "META-INF/com/android/metadata";
    private static final String METADATA_TIMESTAMP_KEY = "post-timestamp=";

    public static final int NONE = 0;
    public static final int STARTED = 1;
    public static final int COMPLETED = 2;

    private static LocalUpdateController sInstance = null;

    private final HubActivity mActivity;
    private final HubController mController;
    private final Context mContext;
    private Thread mPrepareUpdateThread;
    private final Handler mUiThread;
    private final List<ImportListener> mListeners = new ArrayList<>();

    public interface ImportListener {
        void onImportStatusChanged(UpdateInfo info, int state);
    }

    public LocalUpdateController(HubActivity activity, Context context, HubController controller) {
        mActivity = activity;
        mController = controller;
        mContext = context.getApplicationContext();
        mUiThread = new Handler(context.getMainLooper());
    }

    public static synchronized LocalUpdateController getInstance(HubActivity activity, Context context,
            HubController controller) {
        if (sInstance == null) {
            sInstance = new LocalUpdateController(activity, context, controller);
        }
        return sInstance;
    }

    public UpdateInfo buildUpdate(File updateFile) {
        final long timeStamp = getTimeStamp(updateFile);
        Update update = new Update();
        update.setFile(updateFile);
        update.setName(updateFile.getName());
        update.setFileSize(updateFile.length());
        update.setTimestamp(timeStamp);
        update.setDownloadId(getDummyId(updateFile));
        update.setVersion(getVersion(updateFile.getName()));
        return update;
    }

    public void initUpdatePicker() {
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(MIME_ZIP);
        mActivity.startActivityForResult(intent, REQUEST_PICK);
    }

    public boolean onResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || requestCode != REQUEST_PICK) {
            return false;
        }
        return onPicked(data.getData());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private boolean onPicked(Uri uri) {
        Log.d(TAG, "onPicked");
        mActivity.runOnUiThread(() -> notifyImportListener(null, STARTED));
        mPrepareUpdateThread = new Thread(() -> {
            File importedFile = null;
            try {
                boolean updateAvailable;
                importedFile = importFile(uri);
                final UpdateInfo update = buildUpdate(importedFile);
                updateAvailable = mController.isUpdateAvailable(update, true, true);
                if (updateAvailable) {}
                Log.d(TAG, updateAvailable ? "Local update: " + update.getName() + " is available" : "Local update is not available");
                mActivity.runOnUiThread(() -> notifyImportListener(update, COMPLETED));
            } catch (Exception e) {
                Log.e(TAG, "Failed to import update package", e);
                // Do not store invalid update
                if (importedFile != null) {
                    importedFile.delete();
                }
                mActivity.runOnUiThread(() -> notifyImportListener(null, NONE));
            }
        });
        mPrepareUpdateThread.start();
        return true;
    }

    @SuppressLint("SetWorldReadable")
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private File importFile(Uri uri) throws IOException {
        final ParcelFileDescriptor parcelDescriptor = mActivity.getContentResolver()
                .openFileDescriptor(uri, "r");
        if (parcelDescriptor == null) {
            throw new IOException("Failed to obtain fileDescriptor");
        }

        final String fileName = getFileName(mContext, uri);
        Log.d(TAG, "importFile: " + fileName);
        final FileInputStream iStream = new FileInputStream(parcelDescriptor
                .getFileDescriptor());
        final File downloadDir = Utils.getDownloadPath(mActivity);
        final File outFile = new File(downloadDir, fileName);
        if (outFile.exists()) {
            outFile.delete();
        }
        final FileOutputStream oStream = new FileOutputStream(outFile);

        int read;
        final byte[] buffer = new byte[4096];
        while ((read = iStream.read(buffer)) > 0) {
            oStream.write(buffer, 0, read);
        }
        oStream.flush();
        oStream.close();
        iStream.close();

        outFile.setReadable(true, false);

        return outFile;
    }

    public void notifyImportListener(UpdateInfo info, int state) {
        Log.d(TAG, "Notifying import listeners: " + state);
        mUiThread.post(() -> {
            for (ImportListener listener : mListeners) {
                listener.onImportStatusChanged(info, state);
            }
        });
    }

    public void addImportListener(ImportListener listener) {
        mListeners.add(listener);
    }

    public void removeImportListener(ImportListener listener) {
        mListeners.remove(listener);
    }

    private String getVersion(String fileName) {
        String[] version = fileName.split("-");
        return version[3];
    }

    private String getDummyId(File file) {
        long id = getTimeStamp(file) * 2;
        return Long.toString(id);
    }

    private long getTimeStamp(File file) {
        try {
            final String metadataContent = readZippedFile(file, METADATA_PATH);
            final String[] lines = metadataContent.split("\n");
            for (String line : lines) {
                if (!line.startsWith(METADATA_TIMESTAMP_KEY)) {
                    continue;
                }

                final String timeStampStr = line.replace(METADATA_TIMESTAMP_KEY, "");
                return Long.parseLong(timeStampStr);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read date from local update zip package", e);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Failed to parse timestamp number from zip metadata file", e);
        }

        Log.e(TAG, "Couldn't find timestamp in zip file, falling back to $now");
        return System.currentTimeMillis();
    }

    private String readZippedFile(File file, String path) throws IOException {
        final StringBuilder sb = new StringBuilder();
        InputStream iStream = null;

        try {
            final ZipFile zip = new ZipFile(file);
            final Enumeration<? extends ZipEntry> iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                final ZipEntry entry = iterator.nextElement();
                if (!METADATA_PATH.equals(entry.getName())) {
                    continue;
                }

                iStream = zip.getInputStream(entry);
                break;
            }

            if (iStream == null) {
                throw new FileNotFoundException("Couldn't find " + path + " in " + file.getName());
            }

            final byte[] buffer = new byte[1024];
            int read;
            while ((read = iStream.read(buffer)) > 0) {
                sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read file from zip package", e);
            throw e;
        } finally {
            if (iStream != null) {
                iStream.close();
            }
        }

        return sb.toString();
    }

    public synchronized void copyUpdateToDir(UpdateInfo update) {
        String path = Utils.getDownloadPath(mContext) + "/" + update.getName();
        File updateFile = new File(path);
        Log.d(TAG, "Copying update to: " + path);

        Runnable copyUpdateRunnable = new Runnable() {
            private long mLastUpdate = -1;

            final FileUtils.ProgressCallBack mProgressCallBack = new FileUtils.ProgressCallBack() {
                @Override
                public void update(int progress) {
                    long now = SystemClock.elapsedRealtime();
                    if (mLastUpdate < 0 || now - mLastUpdate > 500) {
                        mController.getActualUpdate(update.getDownloadId())
                                .setInstallProgress(progress);
                        mController.getActualUpdate(update.getDownloadId())
                                .setStatus(UpdateStatus.PREPARING, mContext);
                        mController.notifyUpdateStatusChanged(mController.getActualUpdate(update.getDownloadId()), HubController.STATE_INSTALL_PROGRESS);
                        mLastUpdate = now;
                    }

                    if (progress == 100) {
                        Log.d(TAG, "Copying local update completed");
                        mController.getActualUpdate(update.getDownloadId())
                                .setStatus(UpdateStatus.DOWNLOADED, mContext);
                        mController.notifyUpdateStatusChanged(mController.getActualUpdate(update.getDownloadId()), HubController.STATE_STATUS_CHANGED);
                    }
                }
            };

            @Override
            public void run() {
                try {
                    FileUtils.copyFile(update.getFile(), updateFile, mProgressCallBack);
                    mController.getActualUpdate(update.getDownloadId()).setFile(updateFile);
                    if (mPrepareUpdateThread.isInterrupted()) {
                        mController.getActualUpdate(update.getDownloadId())
                                .setStatus(UpdateStatus.UNAVAILABLE, mContext);
                        mController.getActualUpdate(update.getDownloadId())
                                .setInstallProgress(0);
                        updateFile.delete();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Could not copy update", e);
                    updateFile.delete();
                    mController.getActualUpdate(update.getDownloadId())
                            .setStatus(UpdateStatus.UNAVAILABLE, mContext);
                } finally {
                    synchronized (LocalUpdateController.this) {
                        mPrepareUpdateThread = null;
                    }
                }
            }
        };

        mPrepareUpdateThread = new Thread(copyUpdateRunnable);
        mPrepareUpdateThread.start();
    }

    public static String getRealPath(Context context, Uri uri) {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            } else if (isDownloadsDocument(uri)) { // DownloadsProvider
                final String id = DocumentsContract.getDocumentId(uri);
                if (id != null && id.startsWith("raw:")) {
                    return id.substring(4);
                }

                String[] contentUriPrefixesToTry = new String[]{
                        "content://downloads/public_downloads",
                        "content://downloads/my_downloads"
                };

                for (String contentUriPrefix : contentUriPrefixesToTry) {
                    assert id != null;
                    Uri contentUri = ContentUris.withAppendedId(Uri.parse(contentUriPrefix), Long.parseLong(id));
                    try {
                        String path = getDataColumn(context, contentUri, null, null);
                        if (path != null) {
                            return path;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // path could not be retrieved using ContentResolver, therefore copy file to accessible cache using streams
                String fileName = getFileName(context, uri);
                File cacheDir = getDocumentCacheDir(context);
                File file = generateFileName(fileName, cacheDir);
                String destinationPath = null;
                if (file != null) {
                    destinationPath = file.getAbsolutePath();
                    saveFileFromUri(context, uri, destinationPath);
                }
                return destinationPath;
            } else if (isMediaDocument(uri)) { // MediaProvider
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) { // MediaStore (and general)

            // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();

            return getDataColumn(context, uri, null, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) { // File
            return uri.getPath();
        }

        return null;
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }


    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    public static String getFileName(Context context, Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        String filename = null;

        if (mimeType == null) {
            String path = getRealPath(context, uri);
            if (path == null) {
                filename = getName(uri.toString());
            } else {
                File file = new File(path);
                filename = file.getName();
            }
        } else {
            Cursor returnCursor = context.getContentResolver().query(uri, null, null, null, null);
            if (returnCursor != null) {
                int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                returnCursor.moveToFirst();
                filename = returnCursor.getString(nameIndex);
                returnCursor.close();
            }
        }

        return filename;
    }

    public static String getName(String filename) {
        if (filename == null) {
            return null;
        }
        int index = filename.lastIndexOf('/');
        return filename.substring(index + 1);
    }

    public static File getDocumentCacheDir(@NonNull Context context) {
        File dir = new File(context.getCacheDir(), "documents");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File generateFileName(String name, File directory) {
        if (name == null) {
            return null;
        }
        File file = new File(directory, name);

        if (file.exists()) {
            String fileName = name;
            String extension = "";
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex > 0) {
                fileName = name.substring(0, dotIndex);
                extension = name.substring(dotIndex);
            }

            int index = 0;
            while (file.exists()) {
                index++;
                name = fileName + '(' + index + ')' + extension;
                file = new File(directory, name);
            }
        }

        try {
            if (!file.createNewFile()) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
        return file;
    }

    private static void saveFileFromUri(Context context, Uri uri, String destinationPath) {
        InputStream is = null;
        BufferedOutputStream bos = null;
        try {
            is = context.getContentResolver().openInputStream(uri);
            bos = new BufferedOutputStream(new FileOutputStream(destinationPath, false));
            byte[] buf = new byte[1024];
            is.read(buf);
            do {
                bos.write(buf);
            } while (is.read(buf) != -1);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null) is.close();
                if (bos != null) bos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
