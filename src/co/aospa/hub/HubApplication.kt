/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub

import android.app.Application
import co.aospa.hub.data.repository.BatteryRepository
import co.aospa.hub.data.repository.ConnectivityRepository
import co.aospa.hub.data.repository.UpdateRepository
import co.aospa.hub.data.source.UpdateDataStore
import co.aospa.hub.data.source.UpdatePreferences
import co.aospa.hub.data.source.UpdateServerClient
import co.aospa.hub.system.SystemUpdateInfoPublisher
import co.aospa.hub.system.UpdateNotifier
import co.aospa.hub.ui.model.toUpdateState
import co.aospa.hub.work.UpdatesCheckWorker
import java.io.File
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Owns the objects that outlive any one screen. */
class HubApplication : Application() {
    private val scope = MainScope()

    /** The choices made on the settings screen. */
    val preferences by lazy { UpdatePreferences(applicationContext) }
    /** The battery level. */
    val batteryRepository by lazy { BatteryRepository(applicationContext) }
    /** Whether there is usable internet, and whether it is metered. */
    val connectivityRepository by lazy { ConnectivityRepository(applicationContext, scope) }
    /** The notifications shown while the app is closed. */
    val updateNotifier by lazy { UpdateNotifier(applicationContext) }

    /** The update the server offers and what has been done with it. */
    val repository by lazy {
        UpdateRepository(
            store = UpdateDataStore(applicationContext),
            client = UpdateServerClient(applicationContext),
            connectivityRepository = connectivityRepository,
            downloadDirectory = File(getString(R.string.config_downloadDirectory)),
        )
    }

    override fun onCreate() {
        super.onCreate()
        updateNotifier.createChannels()
        schedulePeriodicCheck()
        publishSystemUpdateInfo()
    }

    /** Applies the check interval the user picked. */
    fun schedulePeriodicCheck() {
        UpdatesCheckWorker.schedule(this, preferences.checkInterval)
    }

    private fun publishSystemUpdateInfo() {
        val publisher = SystemUpdateInfoPublisher(applicationContext)
        scope.launch {
            combine(
                repository.data,
                repository.progress,
                repository.installError,
            ) { data, progress, installError ->
                data.toUpdateState(progress, installError)
            }.collect(publisher::publish)
        }
    }
}
