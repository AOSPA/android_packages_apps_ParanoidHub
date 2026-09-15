/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.util

import android.os.Build
import android.os.SystemProperties

/** Provides properties of the running build. */
object BuildInfo {
    private const val PROP_AB_UPDATE = "ro.build.ab_update"
    private const val PROP_DEVICE = "ro.aospa.device"
    private const val PROP_RELEASE_TYPE = "ro.aospa.build.variant"

    /** The Android release, such as "17". Not the SDK level. */
    val androidVersion: String = Build.VERSION.RELEASE

    /** When this build was made, in seconds. */
    val buildDateTimestamp: Long = Build.TIME / 1000

    /** The device to ask the server about. */
    val device: String = SystemProperties.get(PROP_DEVICE, "")

    /** Whether this device has A/B partitions. */
    val isAbDevice: Boolean = SystemProperties.getBoolean(PROP_AB_UPDATE, false)

    /** The build variant, matched against what the server offers. */
    val releaseType: String = SystemProperties.get(PROP_RELEASE_TYPE, "")
}
