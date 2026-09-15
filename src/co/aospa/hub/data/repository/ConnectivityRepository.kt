/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import co.aospa.hub.data.model.ConnectivityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/** Provides whether there is usable internet, and whether it is metered. */
class ConnectivityRepository(context: Context, scope: CoroutineScope) {
    private val connectivityManager by lazy {
        context.getSystemService(ConnectivityManager::class.java)
    }

    /** The network right now, without waiting for a callback. */
    val currentState: ConnectivityState
        get() = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            ?.let { stateOf(it) }
            ?: ConnectivityState.OFFLINE

    /** Observable for the network as it changes. */
    val state: StateFlow<ConnectivityState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                trySend(stateOf(networkCapabilities))
            }

            override fun onLost(network: Network) {
                trySend(ConnectivityState.OFFLINE)
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)
        trySend(currentState)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = ConnectivityState.OFFLINE,
    )

    private fun stateOf(capabilities: NetworkCapabilities) = ConnectivityState(
        // Validated, not merely connected: a captive portal is not usable internet.
        isOnline = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
    )
}
