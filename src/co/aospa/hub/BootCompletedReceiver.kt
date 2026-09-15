/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.aospa.hub.util.hub

/** Reschedules the periodic check, which does not survive a reboot. */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        context.hub.schedulePeriodicCheck()
    }
}
