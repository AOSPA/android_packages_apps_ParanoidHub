/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.data.model

import kotlinx.serialization.Serializable

/** Everything the app remembers between launches. */
@Serializable
data class UpdateData(
    val update: Update? = null,
    val downloadState: DownloadState = DownloadState.NONE,
    val downloadedBytes: Long = 0,
    val lastCheckedAt: Long = 0,
)
