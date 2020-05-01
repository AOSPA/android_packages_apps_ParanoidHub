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

import android.content.Context;
import android.annotation.NonNull;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import co.aospa.hub.HubController;
import co.aospa.hub.R;
import co.aospa.hub.misc.Constants;
import co.aospa.hub.misc.FileUtils;
import co.aospa.hub.misc.Utils;
import co.aospa.hub.model.Update;
import co.aospa.hub.model.UpdateInfo;
import co.aospa.hub.model.UpdateStatus;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class LocalUpdateController {

    private static final String TAG = "LocalUpdateController";

    private static LocalUpdateController sInstance = null;
    private static String sPreparingUpdate = null;

    private final HubController mController;
    private final Context mContext;
    private Thread mPrepareUpdateThread;

    public LocalUpdateController(Context context, HubController controller) {
        mController = controller;
        mContext = context.getApplicationContext();
    }

    public static synchronized LocalUpdateController getInstance(Context context,
            HubController controller) {
        if (sInstance == null) {
            sInstance = new LocalUpdateController(context, controller);
        }
        return sInstance;
    }

    public UpdateInfo buildUpdate(File updateFile) {
        Update update = new Update();
        update.setFile(updateFile);
        update.setName(updateFile.getName());
        update.setFileSize(updateFile.length());
        update.setTimestamp(getTimestamp(updateFile.getName()));
        update.setDownloadId(getDummyId(updateFile.getName()));
        update.setVersion(getVersion(updateFile.getName()));
        return update;
    }

    public UpdateInfo setUpdate(File updateFile) {
        UpdateInfo update = buildUpdate(updateFile);
        return update;
    }

    private String getVersion(String fileName) {
        String[] version = fileName.split("-");
        return version[2];
    }

    private Long getTimestamp(String fileName) {
        String[] timestamp = fileName.split("-");
        String[] exactTimestamp = timestamp[4].split("\\.");
        return Long.parseLong(exactTimestamp[0]);
    }

    private String getDummyId(String fileName) {
        long id = getTimestamp(fileName) * 2;
        return Long.toString(id);
    }

    public File getLocalFile(File path) {
        try {
            for (File f : path.listFiles()) {
                if (f.getName().startsWith("pa-") 
                        && f.getName().endsWith(".zip")) {
                    return f;
                }
            }
        } catch (NullPointerException e) {}
        return null;
    }

    public synchronized void copyUpdateToDir(UpdateInfo update) {
        String path = Utils.getDownloadPath(mContext) + "/" + update.getName();
        File updateFile = new File(path);
        Log.d(TAG, "Copying update to: " + path);

        Runnable copyUpdateRunnable = new Runnable() {
            private long mLastUpdate = -1;

            FileUtils.ProgressCallBack mProgressCallBack = new FileUtils.ProgressCallBack() {
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
                        Utils.setPermissions(updateFile, android.os.FileUtils.S_IRWXU | android.os.FileUtils.S_IRGRP | android.os.FileUtils.S_IROTH, -1, -1);
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
                        sPreparingUpdate = null;
                    }
                }
            }
        };

        mPrepareUpdateThread = new Thread(copyUpdateRunnable);
        mPrepareUpdateThread.start();
        sPreparingUpdate = update.getDownloadId();
    }

    public static synchronized boolean isPreparing() {
        return sPreparingUpdate != null;
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
                DocumentFile fileD = DocumentFile.fromSingleUri(context, uri);
                Log.d(TAG, "" + fileD.getName());
                Log.d(TAG, "" + fileD.getType());

                final String id = DocumentsContract.getDocumentId(uri);
                if (id != null && id.startsWith("raw:")) {
                    return id.substring(4);
                }

                String[] contentUriPrefixesToTry = new String[]{
                        "content://downloads/public_downloads",
                        "content://downloads/my_downloads"
                };

                for (String contentUriPrefix : contentUriPrefixesToTry) {
                    Uri contentUri = ContentUris.withAppendedId(Uri.parse(contentUriPrefix), Long.valueOf(id));
                    try {
                        String path = getDataColumn(context, contentUri, null, null);
                        if (path != null) {
                            return path;
                        }
                    } catch (Exception e) {
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

        if (mimeType == null && context != null) {
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
