/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.download

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val TAG = "UpdateDownloader"
private const val BUFFER_SIZE = 64 * 1024
private const val CONNECT_TIMEOUT_MILLIS = 30_000
private const val READ_TIMEOUT_MILLIS = 60_000

/** Downloads an update package, resuming whatever is already on disk. */
class UpdateDownloader {
    /** Reports how much of the package is on disk. */
    fun interface ProgressListener {
        /** Called as bytes land on disk. */
        fun onProgress(downloadedBytes: Long)
    }

    @Volatile
    private var cancelled = false

    /** Stops the download at the next chunk boundary. */
    fun cancel() {
        cancelled = true
    }

    /** Appends to [destination] until complete. Returns false when cancelled. */
    @Throws(IOException::class)
    fun download(
        url: String,
        destination: File,
        totalBytes: Long,
        progress: ProgressListener,
    ): Boolean {
        cancelled = false
        destination.parentFile?.mkdirs()

        val alreadyHave = if (destination.exists()) destination.length() else 0
        if (totalBytes in 1..alreadyHave) return true

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            if (alreadyHave > 0) setRequestProperty("Range", "bytes=$alreadyHave-")
        }

        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                throw IOException("Unexpected HTTP status: $code")
            }
            // A server that ignores the range starts over, so the local copy is stale.
            val resuming = code == HttpURLConnection.HTTP_PARTIAL

            var written = if (resuming) alreadyHave else 0
            connection.inputStream.use { input ->
                FileOutputStream(destination, resuming).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (cancelled) return false
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        progress.onProgress(written)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        return true
    }

    companion object {
        /** Returns whether [file] matches the hash the server advertised. */
        fun verify(file: File, sha256: String): Boolean {
            if (!file.exists()) return false
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(sha256, ignoreCase = true)) {
                Log.w(TAG, "Hash mismatch for ${file.name}: expected $sha256, got $actual")
                return false
            }
            return true
        }
    }
}
