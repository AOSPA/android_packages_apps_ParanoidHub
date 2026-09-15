/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.data.repository

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import co.aospa.hub.data.model.BatteryState
import com.android.settingslib.fuelgauge.BatteryStatus

/** Provides the battery level. */
class BatteryRepository(private val context: Context) {
    /** The battery right now, without waiting for a broadcast. */
    val currentState: BatteryState
        get() = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.let { stateOf(it) }
            ?: BatteryState.FULL

    private fun stateOf(intent: Intent): BatteryState {
        val status = BatteryStatus(intent)
        return BatteryState(
            level = status.level,
            isPluggedIn = status.isPluggedIn,
            isAcCharging = status.isPluggedInWired,
        )
    }
}
