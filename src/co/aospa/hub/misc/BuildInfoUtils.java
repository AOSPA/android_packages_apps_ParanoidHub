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
package co.aospa.hub.misc;

import android.os.SystemProperties;

import java.util.HashMap;

import co.aospa.hub.model.UpdateInfo;

public final class BuildInfoUtils {

    private BuildInfoUtils() {
    }

    public static long getBuildDateTimestamp() {
        return SystemProperties.getLong(Constants.PROP_BUILD_DATE, 0);
    }

    public static String getBuildVersion() {
        String majorVersion = SystemProperties.get(Constants.PROP_BUILD_VERSION);
        String versionCode = SystemProperties.get(Constants.PROP_VERSION_CODE);
        String variant = SystemProperties.get(Constants.PROP_RELEASE_TYPE);
        return majorVersion + " " + versionCode + " " + variant;
    }

    public static String getUpdateVersion(UpdateInfo update) {
        String majorVersion = SystemProperties.get(Constants.PROP_BUILD_VERSION);
        HashMap<String, String> variants = new HashMap<>();
        variants.put("beta", "Beta");
        variants.put("alpha", "Alpha");
        variants.put("release", "Release");
        String[] upgradeVersion = update.getVersion().split("-");
        return majorVersion + " " + upgradeVersion[1] + " " + variants.get(upgradeVersion[4]);
    }
}
