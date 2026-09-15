/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.ui.model

import co.aospa.hub.data.model.DownloadState
import co.aospa.hub.data.model.Progress
import co.aospa.hub.data.model.UpdateData

/** Returns what the screen should show for this data. */
fun UpdateData.toUpdateState(
    progress: Progress? = null,
    installError: String? = null,
): UpdateState {
    val update = update ?: return UpdateState.UpToDate(lastCheckedAt)
    val bytes = progress?.downloadedBytes ?: downloadedBytes
    return when (downloadState) {
        DownloadState.NONE -> UpdateState.Available(update)
        DownloadState.DOWNLOADING -> UpdateState.Downloading(update, bytes)
        DownloadState.PAUSED -> UpdateState.DownloadPaused(update, downloadedBytes)
        DownloadState.FAILED -> UpdateState.DownloadPaused(update, downloadedBytes, failed = true)
        DownloadState.DOWNLOADED, DownloadState.VERIFYING -> UpdateState.Verifying(update)
        DownloadState.VERIFIED -> UpdateState.ReadyToInstall(update)
        DownloadState.VERIFICATION_FAILED -> UpdateState.VerificationFailed(update)
        DownloadState.INSTALLING -> UpdateState.Installing(update, progress?.percent ?: 0)
        DownloadState.INSTALL_FAILED -> UpdateState.InstallFailed(update, installError.orEmpty())
        DownloadState.NEEDS_REBOOT -> UpdateState.NeedsReboot(update)
    }
}
