/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import co.aospa.hub.data.model.BatteryState
import com.android.settingslib.fuelgauge.BatteryStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Provides the battery level. */
class BatteryRepository(private val context: Context) {
    /** Observable for the battery, starting with how it is right now. */
    val state: Flow<BatteryState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(stateOf(intent))
            }
        }

        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: trySend(BatteryState.FULL)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    private fun stateOf(intent: Intent): BatteryState {
        val status = BatteryStatus(intent)
        return BatteryState(
            level = status.level,
            isPluggedIn = status.isPluggedIn,
            isAcCharging = status.isPluggedInWired,
        )
    }
}
