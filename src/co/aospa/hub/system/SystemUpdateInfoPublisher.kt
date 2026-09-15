/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.system

import android.content.Context
import android.os.PersistableBundle
import android.os.SystemUpdateManager
import android.util.Log
import co.aospa.hub.ui.model.UpdateState

private const val TAG = "SystemUpdateInfoPublisher"

/** Publishes the update status to the platform for Settings to read. */
class SystemUpdateInfoPublisher(context: Context) {
    private val manager: SystemUpdateManager? by lazy {
        context.getSystemService(SystemUpdateManager::class.java)
    }

    /** Reports [state], doing nothing where the service is unavailable. */
    fun publish(state: UpdateState) {
        val service = manager ?: return

        val status = when (state) {
            is UpdateState.UpToDate, is UpdateState.Checking, is UpdateState.CheckFailed ->
                SystemUpdateManager.STATUS_IDLE

            is UpdateState.Available -> SystemUpdateManager.STATUS_WAITING_DOWNLOAD

            is UpdateState.Downloading,
            is UpdateState.DownloadPaused,
            is UpdateState.Verifying,
            is UpdateState.Installing -> SystemUpdateManager.STATUS_IN_PROGRESS

            is UpdateState.ReadyToInstall -> SystemUpdateManager.STATUS_WAITING_INSTALL
            is UpdateState.NeedsReboot -> SystemUpdateManager.STATUS_WAITING_REBOOT

            is UpdateState.VerificationFailed, is UpdateState.InstallFailed ->
                SystemUpdateManager.STATUS_WAITING_DOWNLOAD
        }

        val info = PersistableBundle().apply {
            putInt(SystemUpdateManager.KEY_STATUS, status)
            state.update?.let { update ->
                putString(SystemUpdateManager.KEY_TITLE, update.version)
                if (update.securityPatch.isNotEmpty()) {
                    putString(
                        SystemUpdateManager.KEY_TARGET_SECURITY_PATCH_LEVEL,
                        update.securityPatch,
                    )
                }
            }
        }

        try {
            service.updateSystemUpdateInfo(info)
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to publish the system update status", e)
        }
    }
}
