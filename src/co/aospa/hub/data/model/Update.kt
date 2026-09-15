/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.data.model

import co.aospa.hub.util.BuildInfo
import java.io.File
import kotlinx.serialization.Serializable

/** A build the server offers for this device. */
@Serializable
data class Update(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: String,
    val timestamp: Long,
    val size: Long,
    val url: String,
    val sha256: String,
    val buildType: String,
    val changelog: String = "",
    val securityPatch: String = "",
) {
    /** Whether this build is newer than the running one. */
    val isNewerThanDevice: Boolean
        get() = timestamp > BuildInfo.buildDateTimestamp

    /** Whether this build is the same flavour as the running one. */
    val matchesDeviceFlavour: Boolean
        get() = buildType.equals(BuildInfo.releaseType, ignoreCase = true)

    /** Returns where this build sits inside [directory]. */
    fun fileIn(directory: File) = File(directory, File(name).name)
}
