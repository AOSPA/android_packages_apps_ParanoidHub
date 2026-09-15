/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.data.repository

import android.util.Log
import co.aospa.hub.data.model.CheckResult
import co.aospa.hub.data.model.DownloadState
import co.aospa.hub.data.model.Progress
import co.aospa.hub.data.model.Update
import co.aospa.hub.data.model.UpdateData
import co.aospa.hub.data.source.UpdateDataStore
import co.aospa.hub.data.source.UpdateServerClient
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val TAG = "UpdateRepository"

/** Provides the update the server offers and what has been done with it. */
class UpdateRepository(
    private val store: UpdateDataStore,
    private val client: UpdateServerClient,
    private val connectivityRepository: ConnectivityRepository,
    private val downloadDirectory: File,
) {
    /** Observable for everything remembered about the update. */
    val data: Flow<UpdateData> = store.data.map { data ->
        val update = data.update
        if (update != null && data.downloadState.hasPartialFile) {
            data.copy(downloadedBytes = fileFor(update).length())
        } else {
            data
        }
    }

    private val _progress = MutableStateFlow<Progress?>(null)
    /** Observable for the running download or install, null when nothing runs. */
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    private val _installError = MutableStateFlow<String?>(null)
    /** Observable for why the last install failed, null when none has. */
    val installError: StateFlow<String?> = _installError.asStateFlow()

    /** Asks the server what it has and remembers the answer. */
    suspend fun check(): CheckResult {
        if (!connectivityRepository.currentState.isOnline) return CheckResult.OFFLINE

        val update = try {
            withContext(Dispatchers.IO) { client.fetchLatest() }
        } catch (e: IOException) {
            Log.e(TAG, "Could not fetch updates", e)
            return CheckResult.FAILED
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Bad update server response", e)
            return CheckResult.FAILED
        }

        val now = System.currentTimeMillis()
        if (update == null) {
            withContext(Dispatchers.IO) {
                store.update { previous ->
                    previous.update?.let { fileFor(it).delete() }
                    UpdateData(lastCheckedAt = now)
                }
            }
            return CheckResult.UP_TO_DATE
        }

        withContext(Dispatchers.IO) {
            store.update { previous ->
                if (previous.update?.id == update.id) {
                    previous.copy(update = update, lastCheckedAt = now)
                } else {
                    previous.update?.let { fileFor(it).delete() }
                    UpdateData(update = update, lastCheckedAt = now)
                }
            }
        }
        return CheckResult.UPDATE_AVAILABLE
    }

    /** Returns where [update] is, or will be, on disk. */
    fun fileFor(update: Update): File = update.fileIn(downloadDirectory)

    /** Sets how far the running work has got. */
    fun reportProgress(progress: Progress?) {
        _progress.value = progress
    }

    /** Sets why an install failed. */
    fun reportInstallError(reason: String) {
        _installError.value = reason
    }

    /** Whether work stopped with nothing left to carry it on. */
    suspend fun isWorkInterrupted(): Boolean =
        _progress.value == null && store.get().downloadState.needsAppRunning

    /** Sets how far the package has got. */
    suspend fun setDownloadState(state: DownloadState) {
        if (state != DownloadState.DOWNLOADING && state != DownloadState.INSTALLING) {
            _progress.value = null
        }
        if (state == DownloadState.INSTALLING) {
            _installError.value = null
        }
        store.update { it.copy(downloadState = state) }
    }

    /** Deletes what has been downloaded, keeping the update itself. */
    suspend fun discardDownload() {
        withContext(Dispatchers.IO) {
            store.get().update?.let { fileFor(it).delete() }
        }
        _progress.value = null
        store.update { it.copy(downloadState = DownloadState.NONE, downloadedBytes = 0) }
    }

    /** Returns the update being worked on, or null when there is none. */
    suspend fun currentUpdate(): Update? = store.get().update
}
