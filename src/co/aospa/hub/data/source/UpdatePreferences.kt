/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.data.source

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import co.aospa.hub.data.model.CheckInterval

/** Provides the choices made on the settings screen. */
class UpdatePreferences(context: Context) {
    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    /** How often the background check runs. */
    val checkInterval: CheckInterval
        get() = CheckInterval.forValue(preferences.getString(KEY_CHECK_INTERVAL, null))

    /** Whether the install may use more of the device to finish sooner. */
    val prioritizeInstallSpeed: Boolean
        get() = preferences.getBoolean(KEY_PRIORITIZE_INSTALL_SPEED, true)

    /** Starts reporting changes to [listener]. */
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    /** Stops reporting changes to [listener]. */
    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        /** Preference key for the check interval. */
        const val KEY_CHECK_INTERVAL = "check_interval"
        /** Preference key for the update_engine performance mode. */
        const val KEY_PRIORITIZE_INSTALL_SPEED = "prioritize_install_speed"
    }
}
