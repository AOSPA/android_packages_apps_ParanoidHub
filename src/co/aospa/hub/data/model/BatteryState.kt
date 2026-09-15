/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.data.model

private const val MIN_LEVEL_CHARGING = 20
private const val MIN_LEVEL_DISCHARGING = 30

/** What the battery is doing. */
data class BatteryState(
    val level: Int,
    val isPluggedIn: Boolean,
    val isAcCharging: Boolean,
) {
    /** Whether there is enough charge to start an install. */
    val hasEnoughCharge: Boolean
        get() = level >= if (isPluggedIn) MIN_LEVEL_CHARGING else MIN_LEVEL_DISCHARGING

    companion object {
        /** Assumed where the device reports no battery. */
        val FULL = BatteryState(level = 100, isPluggedIn = true, isAcCharging = true)
    }
}
