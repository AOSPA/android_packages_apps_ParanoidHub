/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.install

import android.content.Context
import android.os.RecoverySystem
import android.os.storage.StorageManager
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

private const val TAG = "NonAbUpdateInstaller"

private const val UNCRYPT_FILE_EXT = ".uncrypt"

/**
 * Installs by rebooting into recovery, for devices without A/B partitions.
 *
 * On an encrypted device uncrypt rewrites the package in place, so it is copied first.
 */
class NonAbUpdateInstaller(
    private val context: Context,
    private val listener: UpdateInstaller.Listener,
) : UpdateInstaller {
    @Volatile
    private var cancelled = false

    override fun install(packageFile: File) {
        cancelled = false

        val toInstall = if (isEncrypted(packageFile)) {
            val copy = File(packageFile.absolutePath + UNCRYPT_FILE_EXT)
            try {
                packageFile.copyTo(copy, overwrite = true)
                Files.setPosixFilePermissions(
                    copy.toPath(),
                    setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.GROUP_READ,
                        PosixFilePermission.OTHERS_READ,
                    ),
                )
            } catch (e: IOException) {
                Log.e(TAG, "Could not copy the update package", e)
                copy.delete()
                listener.onError(e.message ?: "Could not prepare the update package")
                return
            }
            if (cancelled) {
                copy.delete()
                return
            }
            copy
        } else {
            packageFile
        }

        try {
            RecoverySystem.installPackage(context, toInstall)
        } catch (e: IOException) {
            Log.e(TAG, "Could not install the update", e)
            listener.onError(e.message ?: "Could not hand the update to recovery")
        }
    }

    override fun cancel() {
        cancelled = true
    }

    private fun isEncrypted(file: File): Boolean =
        context.getSystemService(StorageManager::class.java).isEncrypted(file)
}
