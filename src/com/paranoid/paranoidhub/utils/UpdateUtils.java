package com.paranoid.paranoidhub.utils;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

public class UpdateUtils {
    public static final int TWRP = 1;
    public static final int CWM_BASED = 2;

    public static String getProp(String prop) {
        try {
            Process process = Runtime.getRuntime().exec("getprop " + prop);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(
                    process.getInputStream()));
            StringBuilder log = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                log.append(line);
            }
            return log.toString();
        } catch (IOException e) {
            // Runtime error
        }
        return null;
    }

    /**
     * Method borrowed from OpenDelta. Using reflection voodoo instead calling
     * the hidden class directly, to dev/test outside of AOSP tree.
     * <p/>
     * Jorrit "Chainfire" Jongma and The OmniROM Project
     */
    public static boolean setPermissions(String path, int mode, int uid, int gid) {
        try {
            Class<?> FileUtils = UpdateUtils.class.getClassLoader().loadClass("android.os.FileUtils");
            Method setPermissions = FileUtils.getDeclaredMethod("setPermissions", String.class,
                    int.class,
                    int.class,
                    int.class);
            return ((Integer) setPermissions.invoke(
                    null,
                    path,
                    mode,
                    uid,
                    gid) == 0);
        } catch (Exception e) {
            // A lot of voodoo could go wrong here, return failure instead of
            // crash
            e.printStackTrace();
        }
        return false;
    }

    public static boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException ignored) {
        }
        return false;
    }

    public static String exec(String command) {
        try {
            Process p = Runtime.getRuntime().exec(command);
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("sync\n");
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();
            return getStreamLines(p.getInputStream());
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private static String getStreamLines(final InputStream is) {
        String out = null;
        StringBuffer buffer = null;
        final DataInputStream dis = new DataInputStream(is);

        try {
            if (dis.available() > 0) {
                buffer = new StringBuffer(dis.readLine());
                while (dis.available() > 0) {
                    buffer.append("\n").append(dis.readLine());
                }
            }
            dis.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (buffer != null) {
            out = buffer.toString();
        }
        return out;
    }

    public static void showToastOnUiThread(final Context context, final int resourceId) {
        ((Activity) context).runOnUiThread(new Runnable() {

            public void run() {
                Toast.makeText(context, resourceId, Toast.LENGTH_LONG).show();
            }
        });
    }

    public static void showToastOnUiThread(final Context context, final String string) {
        ((Activity) context).runOnUiThread(new Runnable() {

            public void run() {
                Toast.makeText(context, string, Toast.LENGTH_LONG).show();
            }
        });
    }

}