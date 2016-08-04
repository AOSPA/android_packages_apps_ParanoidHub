package com.paranoid.paranoidhub.utils;

import android.util.Log;
import java.io.Serializable;

/**
 * Class to manage different versions
 * <p/>
 * Format<br>
 * pa_A-B-C.D.E-F.zip<br>
 * where<br>
 * A = device name, required<br>
 * B = extra information, not required (for gapps)<br>
 * C = major, integer from 0 to n, required<br>
 * D = minor, integer from 0 to 9, required<br>
 * E = maintenance, integer from 0 to n, not required<br>
 * F = date, YYYYMMDD, not required
 * <p/>
 * All the default values not specified above are 0
 * <p/>
 * Examples<br>
 * pa_angler-6.0.1-20160616.zip<br>
 */
public class Version implements Serializable {

    private static final String TAG = "Hub/Version";

    private int mMajor = 0;
    private int mMinor = 0;
    private int mMaintenance = 0;

    private String mDate = "0";

    public Version(String version) {
        this(version.split("-")[0], version.split("-")[1]);
    }

    public Version(String version, String date) {
        try {
            String[] parts = version.split("\\.");
            mMajor = Integer.parseInt(parts[0]);
            if (parts.length > 1) {
                mMinor = Integer.parseInt(parts[1]);
            }
            if (parts.length > 2) {
                mMaintenance = Integer.parseInt(parts[2]);
            }
            mDate = date;
            if (Constants.DEBUG) Log.d(TAG, "got version M: " + mMajor + "m: " + mMinor + "maint: " + mMaintenance);
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
        return 0;
    }

    public int getMajor() {
        return mMajor;
    }

    public int getMinor() {
        return mMinor;
    }

    public int getMaintenance() {
        return mMaintenance;
    }

    public String getDate() {
        return mDate;
    }

    public boolean isEmpty() {
        return mMajor == 0;
    }

    public String toString() {
        return mMajor + "." + mMinor + (mMaintenance > 0 ? "." + mMaintenance : "")
                + " (" + mDate + ")";
    }
}