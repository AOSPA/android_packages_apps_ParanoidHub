/*
 * Copyright (C) 2022 Paranoid Android
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
package co.aospa.hub.util;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.sysprop.SetupWizardProperties;

import com.google.android.setupcompat.util.WizardManagerHelper;
import com.google.android.setupdesign.util.ThemeHelper;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import co.aospa.hub.R;

public class App {

    public static void clearDownloadedFiles(Context context) {
        File downloadPath = Update.getDownloadPath(context);
        if (downloadPath.isDirectory()) {
            File[] files = downloadPath.listFiles();
            if (files != null) {
                for (File file : files) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        }
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return !(info == null || !info.isConnected() || !info.isAvailable());
    }

    public static String getTimeLocalized(Context context, long unixTimestamp) {
        DateFormat f = DateFormat.getTimeInstance(DateFormat.SHORT, getCurrentLocale(context));
        Date date = new Date(unixTimestamp * 1000);
        return f.format(date);
    }

    public static Locale getCurrentLocale(Context context) {
        return context.getResources().getConfiguration().getLocales()
                .getFirstMatch(context.getResources().getAssets().getLocales());
    }

    public static String getThemeString(Intent intent) {
        String theme = intent.getStringExtra(WizardManagerHelper.EXTRA_THEME);
        if (theme == null) {
            theme = SetupWizardProperties.theme().orElse("");
        }
        return theme;
    }

    public static int getTheme(Context context, Intent intent) {
        String theme = getThemeString(intent);
        if (theme != null) {
            if (WizardManagerHelper.isAnySetupWizard(intent)) {
                if (ThemeHelper.isSetupWizardDayNightEnabled(context)) {
                    switch (theme) {
                        case ThemeHelper.THEME_GLIF_V4_LIGHT:
                        case ThemeHelper.THEME_GLIF_V4:
                            return R.style.GlifThemeV4_Light;
                        case ThemeHelper.THEME_GLIF_V3_LIGHT:
                        case ThemeHelper.THEME_GLIF_V3:
                            return R.style.GlifThemeV3_Light;
                    }
                } else {
                    switch (theme) {
                        case ThemeHelper.THEME_GLIF_V4_LIGHT:
                            return R.style.GlifThemeV4_Light;
                        case ThemeHelper.THEME_GLIF_V4:
                            return R.style.GlifThemeV4;
                        case ThemeHelper.THEME_GLIF_V3_LIGHT:
                            return R.style.GlifThemeV3_Light;
                        case ThemeHelper.THEME_GLIF_V3:
                            return R.style.GlifThemeV3;
                    }
                }
            } else {
                switch (theme) {
                    case ThemeHelper.THEME_GLIF_V4_LIGHT:
                    case ThemeHelper.THEME_GLIF_V4:
                        return R.style.GlifThemeV4;
                    case ThemeHelper.THEME_GLIF_V3_LIGHT:
                    case ThemeHelper.THEME_GLIF_V3:
                        return R.style.GlifThemeV3;
                }
            }
        }
        return R.style.GlifThemeV3;
    }
}
