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
package co.aospa.hub;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceFragment.OnPreferenceStartFragmentCallback;
import androidx.preference.PreferenceFragment.OnPreferenceStartScreenCallback;
import androidx.preference.PreferenceGroup.PreferencePositionCallback;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import co.aospa.hub.R;
import co.aospa.hub.misc.Constants;
import co.aospa.hub.misc.PreferenceHighlighter;
import co.aospa.hub.misc.Utils;

public class HubPreferencesActivity extends Activity implements
        OnPreferenceStartFragmentCallback, OnPreferenceStartScreenCallback {

    public static final String EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key";
    public static final String EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args";
    private static final int DELAY_HIGHLIGHT_DURATION_MILLIS = 600;
    public static final String SAVE_HIGHLIGHTED_KEY = "android:preference_highlighted";

    public static final String PREF_AB_PERF_MODE = Constants.PREF_AB_PERF_MODE;
    public static final String PREF_AUTO_DELETE_UPDATES = Constants.PREF_AUTO_DELETE_UPDATES;
    public static final String PREF_ALLOW_DOWNGRADING = Constants.PREF_ALLOW_DOWNGRADING;
    public static final String PREF_ALLOW_LOCAL_UPDATES = Constants.PREF_ALLOW_LOCAL_UPDATES;
    public static final String PREF_ALLOW_BETA_UPDATES = Constants.PREF_ALLOW_BETA_UPDATES;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState == null) {
            Bundle args = new Bundle();
            String prefKey = getIntent().getStringExtra(EXTRA_FRAGMENT_ARG_KEY);
            if (!TextUtils.isEmpty(prefKey)) {
                args.putString(EXTRA_FRAGMENT_ARG_KEY, prefKey);
            }

            Fragment f = Fragment.instantiate(
                    this, getString(R.string.app_preferences_class), args);
            // Display the fragment as the main content.
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, f)
                    .commit();
        }
    }

    private boolean startFragment(String fragment, Bundle args, String key) {
        if (getFragmentManager().isStateSaved()) {
            // Sometimes onClick can come after onPause because of being posted on the handler.
            // Skip starting new fragments in that case.
            return false;
        }
        Fragment f = Fragment.instantiate(this, fragment, args);
        getFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, f)
                .addToBackStack(key)
                .commit();
        return true;
    }

    @Override
    public boolean onPreferenceStartFragment(
            PreferenceFragment preferenceFragment, Preference pref) {
        return startFragment(pref.getFragment(), pref.getExtras(), pref.getKey());
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragment caller, PreferenceScreen pref) {
        Bundle args = new Bundle();
        args.putString(PreferenceFragment.ARG_PREFERENCE_ROOT, pref.getKey());
        return startFragment(getString(R.string.app_preferences_class), args, pref.getKey());
    }

    @Override
    public boolean onNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        switch (requestCode) {
            case 1:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Utils.getExportPath(getApplicationContext());
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                break;
        }
    }

    /**
     * This fragment shows the hub preferences.
     */
    public static class HubSettingsFragment extends PreferenceFragment {

        private String mHighLightKey;
        private boolean mPreferenceHighlighted = false;
        private SharedPreferences mPrefs;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            final Bundle args = getArguments();
            mHighLightKey = args == null ? null : args.getString(EXTRA_FRAGMENT_ARG_KEY);
            if (rootKey == null && !TextUtils.isEmpty(mHighLightKey)) {
                rootKey = getParentKeyForPref(mHighLightKey);
            }

            if (savedInstanceState != null) {
                mPreferenceHighlighted = savedInstanceState.getBoolean(SAVE_HIGHLIGHTED_KEY);
            }

            getPreferenceManager().setSharedPreferencesName(Utils.SHARED_PREFERENCES_KEY);
            setPreferencesFromResource(R.xml.hub_preferences, rootKey);

            PreferenceScreen screen = getPreferenceScreen();
            for (int i = screen.getPreferenceCount() - 1; i >= 0; i--) {
                Preference preference = screen.getPreference(i);
                if (!initPreference(preference)) {
                    screen.removePreference(preference);
                }
            }

            mPrefs = HubPreferencesActivity.this.getApplicationContext().getSharedPreferences(Utils.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
            mPrefs.registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putBoolean(SAVE_HIGHLIGHTED_KEY, mPreferenceHighlighted);
        }

        protected String getParentKeyForPref(String key) {
            return null;
        }

        /**
         * Initializes a preference. This is called for every preference. Returning false here
         * will remove that preference from the list.
         */
        protected boolean initPreference(Preference preference) {
            switch (preference.getKey()) {
                case PREF_ALLOW_LOCAL_UPDATES:
                    return true;
                case PREF_AUTO_DELETE_UPDATES:
                    return true;
                case PREF_AB_PERF_MODE:
                    return Utils.isABDevice();
                case PREF_ALLOW_DOWNGRADING:
                    return true;
                case PREF_ALLOW_BETA_UPDATES:
                    return true;
            }
            return true;
        }

        private void checkPermsAndBegin() {
            if (ContextCompat.checkSelfPermission(HubPreferencesActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                        != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(HubPreferencesActivity.this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, 
                        Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        1);
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences pref, String key) {
            if (PREF_ALLOW_LOCAL_UPDATES.equals(key)) {
                boolean enabled = mPrefs.getBoolean(PREF_ALLOW_LOCAL_UPDATES, false);
                if (enabled) {
                    checkStoragePermissions();
                }
            }
        }

        @Override
        public void onResume() {
            super.onResume();

            if (isAdded() && !mPreferenceHighlighted) {
                PreferenceHighlighter highlighter = createHighlighter();
                if (highlighter != null) {
                    getView().postDelayed(highlighter, DELAY_HIGHLIGHT_DURATION_MILLIS);
                    mPreferenceHighlighted = true;
                }
            }
        }

        private PreferenceHighlighter createHighlighter() {
            if (TextUtils.isEmpty(mHighLightKey)) {
                return null;
            }

            PreferenceScreen screen = getPreferenceScreen();
            if (screen == null) {
                return null;
            }

            RecyclerView list = getListView();
            PreferencePositionCallback callback = (PreferencePositionCallback) list.getAdapter();
            int position = callback.getPreferenceAdapterPosition(mHighLightKey);
            return position >= 0 ? new PreferenceHighlighter(list, position) : null;
        }
    }
}
