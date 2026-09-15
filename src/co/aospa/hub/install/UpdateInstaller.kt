/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.install

import android.content.Context
import co.aospa.hub.util.BuildInfo
import java.io.File

/** Applies an update package. */
interface UpdateInstaller {
    /** Reports how the install is going. */
    interface Listener {
        /** Only A/B installs report progress. */
        fun onProgress(percent: Int) = Unit

        /** The package takes effect on the next boot. */
        fun onComplete()

        /** The install did not finish, and why. */
        fun onError(reason: String)
    }

    /** Lets the install use more of the device, where the device can. */
    fun setPerformanceMode(enable: Boolean) = Unit

    /** Applies [packageFile], which must be downloaded and verified. */
    fun install(packageFile: File)

    /** Abandons the install. */
    fun cancel() = Unit

    /** Holds the install where it is. */
    fun suspend() = Unit

    /** Continues a suspended install. */
    fun resume() = Unit

    /** Drops whatever the install was holding. */
    fun release() = Unit

    companion object {
        /** Returns the installer this device can use. */
        fun forDevice(context: Context, listener: Listener): UpdateInstaller =
            if (BuildInfo.isAbDevice) {
                AbUpdateInstaller(listener)
            } else {
                NonAbUpdateInstaller(context, listener)
            }
    }
}
