/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.SwitchPreferenceCompat
import co.aospa.hub.R
import co.aospa.hub.data.source.UpdatePreferences
import co.aospa.hub.util.BuildInfo
import co.aospa.hub.util.hub
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/** Shows the update settings. */
class SettingsFragment :
    SettingsBasePreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val preferences by lazy { UpdatePreferences(requireContext()) }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.hub_preferences, rootKey)

        findPreference<SwitchPreferenceCompat>(
            UpdatePreferences.KEY_PRIORITIZE_INSTALL_SPEED
        )?.isVisible = BuildInfo.isAbDevice
    }

    override fun onStart() {
        super.onStart()
        preferences.registerListener(this)
    }

    override fun onStop() {
        preferences.unregisterListener(this)
        super.onStop()
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences?, key: String?) {
        val context = context ?: return
        if (key == UpdatePreferences.KEY_CHECK_INTERVAL) {
            context.hub.schedulePeriodicCheck()
        }
    }
}
