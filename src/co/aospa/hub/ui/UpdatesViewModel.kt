/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.aospa.hub.data.model.CheckResult
import co.aospa.hub.ui.model.UpdateState
import co.aospa.hub.ui.model.toUpdateState
import co.aospa.hub.util.hub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Builds what the update screen shows from what the repository holds. */
class UpdatesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.hub.repository
    private val checking = MutableStateFlow(false)
    private val checkFailure = MutableStateFlow<CheckResult?>(null)

    /** Observable for what the screen should be showing. */
    val state: StateFlow<UpdateState> =
        combine(
            repository.data,
            repository.progress,
            repository.installError,
            checking,
            checkFailure,
        ) { data, progress, installError, checking, failure ->
            when {
                checking -> UpdateState.Checking
                failure != null -> UpdateState.CheckFailed(
                    offline = failure == CheckResult.OFFLINE,
                    lastCheckedAt = data.lastCheckedAt,
                )

                else -> data.toUpdateState(progress, installError)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = UpdateState.UpToDate(0),
        )

    /** Whether work stopped with nothing left to carry it on. */
    suspend fun isWorkInterrupted(): Boolean = repository.isWorkInterrupted()

    /** Whether an install stopped with nothing following it. */
    suspend fun isInstallInterrupted(): Boolean = repository.isInstallInterrupted()

    /** Asks the server for the newest build. */
    fun check() {
        viewModelScope.launch {
            checking.value = true
            checkFailure.value = null
            val result = repository.check()
            checking.value = false
            checkFailure.value = result.takeIf {
                it == CheckResult.OFFLINE || it == CheckResult.FAILED
            }
        }
    }
}
