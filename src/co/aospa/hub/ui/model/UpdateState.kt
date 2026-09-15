/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.ui.model

import co.aospa.hub.data.model.Update

/** What the update screen is showing. */
sealed interface UpdateState {
    /** The build this state is about, if any. */
    val update: Update?
        get() = null

    /** A state with no update on offer. */
    sealed interface Idle : UpdateState {
        /** When the server was last reached, or 0 if never. */
        val lastCheckedAt: Long
    }

    /** The server has nothing newer. */
    data class UpToDate(override val lastCheckedAt: Long) : Idle

    /** The server is being asked what it has. */
    data object Checking : UpdateState

    /** The server could not be reached. */
    data class CheckFailed(
        val offline: Boolean,
        override val lastCheckedAt: Long,
    ) : Idle

    /** A build is on offer, with nothing downloaded. */
    data class Available(override val update: Update) : UpdateState

    /** A state with a partly or wholly downloaded package. */
    sealed interface Downloaded : UpdateState {
        override val update: Update
        /** How much of the package is on disk. */
        val downloadedBytes: Long

        /** How much of the package is on disk, as a percentage. */
        val percent: Int
            get() = if (update.size > 0) {
                ((downloadedBytes * 100) / update.size).toInt().coerceIn(0, 100)
            } else {
                0
            }
    }

    /** The package is being fetched. */
    data class Downloading(
        override val update: Update,
        override val downloadedBytes: Long,
    ) : Downloaded

    /** The package is partly on disk and not being fetched. */
    data class DownloadPaused(
        override val update: Update,
        override val downloadedBytes: Long,
        val failed: Boolean = false,
    ) : Downloaded

    /** The package is being checked against its hash. */
    data class Verifying(override val update: Update) : UpdateState

    /** The package did not match its hash. */
    data class VerificationFailed(override val update: Update) : UpdateState

    /** The package is downloaded and verified. */
    data class ReadyToInstall(override val update: Update) : UpdateState

    /** The package is being written to the other slot. */
    data class Installing(override val update: Update, val percent: Int) : UpdateState

    /** The install did not finish. */
    data class InstallFailed(override val update: Update, val reason: String) : UpdateState

    /** The package takes effect on the next boot. */
    data class NeedsReboot(override val update: Update) : UpdateState
}
