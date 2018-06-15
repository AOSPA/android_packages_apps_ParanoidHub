package com.paranoid.paranoidhub.utils;

import android.util.Log;
import java.io.Serializable;

/**
 * Class to manage different versions
 * <p/>
 * Format<br>
 * pa_A-B-CDE-F-G.zip<br>
 * where<br>
 * A = device name, required<br>
 * B = extra information, not required (for gapps)<br>
 * C = major, first letter of Android version we are based on, required<br>
 * D = minor, letter, from A to Z, required<br>
 * E = maintenance, integer(String) from 1 to 9, required<br>
 * F = tag. Expected value is BETA, DEV, PRESS <br>
 * G = date, YYYYMMDD, not required
 * Any additions past G will be ignored
 * <p/>
 * All the default values not specified above are 0
 * <p/>
 * Examples<br>
 * pa_oneplus5-OA1-20180618-signed.zip<br>
 */
public class Version implements Serializable {

    private static final String TAG = "Hub/Version";

    private static final String BETA_RELEASE_TAG = "BETA";
    private static final String DEV_RELEASE_TAG = "DEV";
    private static final String PRESS_RELEASE_TAG = "PRESS";

    private char mMajor = '';
    private char mMinor = '';
    private int mMaintenance = 0;

    private String mTag = "";
    private String mDate = "";

    public Version(String version) {
        String[] versionSplit = version.split("-");
        if (versionSplit.length == 3) {
            parseVersion(versionSplit[0], versionSplit[1], versionSplit[2]);
        } else if (versionSplit.length == 2) {
            parseVersion(versionSplit[0], "", versionSplit[1]);
        } else {
            Log.d(TAG, "Malformed version: " + version);
        }
    }

    public Version(String version, String date) {
        parseVersion(version, "", date);
    }

    private void parseVersion(String version, String tag, String date) {
        try {
            char[] parts = version.toCharArray();
            mMajor = charAt(parts[0]);
            if (parts.length > 1) {
                mMinor = charAt(parts[1]);
            }
            if (parts.length > 2) {
                mMaintenance = charAt(parts[2]);
            }
            mTag = tag;
            mDate = date;
            if (Constants.DEBUG) Log.d(TAG, "got version: " + mMajor + "." + mMinor + "." + mMaintenance);
            if (Constants.DEBUG) Log.d(TAG, "got date: " + mDate);
        } catch (NumberFormatException ex) {
            // malformed version, write the log and continue
            // C derped something for sure
            ex.printStackTrace();
            Log.d(TAG, "wtf???");
        }
    }

    public static int compare(Version v1, Version v2) {
        if (v1.getMajor() != v2.getMajor()) {
            return v1.getMajor() < v2.getMajor() ? -1 : 1;
        }
        if (v1.getMinor() != v2.getMinor()) {
            return v1.getMinor() < v2.getMinor() ? -1 : 1;
        }
        if (v1.getMaintenance() != v2.getMaintenance()) {
            return v1.getMaintenance() < v2.getMaintenance() ? -1 : 1;
        }
        if (!v1.getDate().equals(v2.getDate())) {
            return v1.getDate().compareTo(v2.getDate());
        }
        if (v1.getTag() != v2.getTag()) {
            return v1.getTag().equals(PRESS_RELEASE_TAG) &&
                   v2.getTag().equals(BETA_RELEASE_TAG) ? -1 : 1;
        }
        return 0;
    }

    public char getMajor() {
        return mMajor.charAt(0);
    }

    public char getMinor() {
        return mMinor.charAt(0);
    }

    public int getMaintenance() {
        return Integer.parseInt(mMaintenance);
    }

    public String getTag() {
        return mTag;
    }

    public String getDate() {
        return mDate;
    }

    public boolean isEmpty() {
        return mMajor.isEmpty();
    }

    public String toString() {
        return mMajor + "." + mMinor + mMaintenance + " (" + mDate + ")";
    }
}
