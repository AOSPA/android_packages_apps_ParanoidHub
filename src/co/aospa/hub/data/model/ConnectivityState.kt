/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.data.model

/** What the default network offers. */
data class ConnectivityState(val isOnline: Boolean, val isMetered: Boolean) {
    companion object {
        /** Assumed until the network reports otherwise. */
        val OFFLINE = ConnectivityState(isOnline = false, isMetered = true)
    }
}
