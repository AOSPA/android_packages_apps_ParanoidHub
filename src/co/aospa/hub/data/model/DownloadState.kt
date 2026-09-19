/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.data.model

import kotlinx.serialization.Serializable

/** How far the update package on disk has got. */
@Serializable
enum class DownloadState {
    NONE,
    DOWNLOADING,
    PAUSED,
    FAILED,
    DOWNLOADED,
    VERIFYING,
    VERIFIED,
    VERIFICATION_FAILED,
    INSTALLING,
    INSTALL_FAILED,
    NEEDS_REBOOT;

    /** Whether a partly-downloaded package may be on disk. */
    val hasPartialFile: Boolean
        get() = this == DOWNLOADING || this == PAUSED || this == FAILED

    /** Whether the app must be running for this state to still be true. */
    val needsAppRunning: Boolean
        get() = this == DOWNLOADING || this == DOWNLOADED || this == VERIFYING

    /** Whether the package is being worked on, or awaits a reboot, and must be left alone. */
    val isInUse: Boolean
        get() = this == DOWNLOADING || this == VERIFYING || this == INSTALLING ||
                this == NEEDS_REBOOT
}
