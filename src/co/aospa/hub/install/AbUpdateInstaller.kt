/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.install

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.os.ParcelFileDescriptor
import android.os.ServiceSpecificException
import android.os.UpdateEngine
import android.os.UpdateEngineCallback
import android.util.Log
import co.aospa.hub.R
import co.aospa.hub.data.model.Update
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.math.roundToInt

private const val TAG = "AbUpdateInstaller"

private const val PAYLOAD_BIN = "payload.bin"
private const val PAYLOAD_PROPERTIES = "payload_properties.txt"

private const val LOCAL_FILE_HEADER_SIZE = 30

/** Where the payload sits in the package, and the properties update_engine needs. */
private class Payload(val offset: Long, val size: Long, val properties: Array<String>)

/** The stages update_engine reports while applying a payload, each with its share of the bar. */
private enum class Phase(val status: Int, val weight: Int) {
    WRITING(UpdateEngine.UpdateStatusConstants.DOWNLOADING, 40),
    VERIFYING(UpdateEngine.UpdateStatusConstants.VERIFYING, 5),
    FINALIZING(UpdateEngine.UpdateStatusConstants.FINALIZING, 55);

    /** Places [percent] of this stage on a single 0 to 100 bar covering every stage. */
    fun progressOf(percent: Float): Int =
        entries.take(ordinal).sumOf { it.weight } + (percent * weight).roundToInt()

    companion object {
        fun of(status: Int): Phase? = entries.find { it.status == status }
    }
}

/**
 * Installs an A/B package through update_engine.
 *
 * The payload is applied in place, so it is located by walking the zip and must be stored
 * uncompressed.
 */
class AbUpdateInstaller(
    private val context: Context,
    private val listener: UpdateInstaller.Listener,
) : UpdateInstaller {
    private val updateEngine = UpdateEngine()
    private var bound = false
    @Volatile private var reattached = false

    private val callback = object : UpdateEngineCallback() {
        override fun onStatusUpdate(status: Int, percent: Float) {
            if (reattached) {
                reattached = false
                when (status) {
                    UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT -> {
                        listener.onComplete()
                        return
                    }

                    UpdateEngine.UpdateStatusConstants.IDLE -> {
                        listener.onError(context.getString(R.string.update_install_interrupted))
                        return
                    }
                }
            }

            Phase.of(status)?.let { listener.onProgress(it.progressOf(percent)) }
        }

        override fun onPayloadApplicationComplete(errorCode: Int) {
            when (errorCode) {
                UpdateEngine.ErrorCodeConstants.SUCCESS -> listener.onComplete()

                else -> listener.onError(errorMessage(errorCode))
            }
        }
    }

    override fun setPerformanceMode(enable: Boolean) {
        try {
            updateEngine.setPerformanceMode(enable)
        } catch (e: ServiceSpecificException) {
            Log.w(TAG, "Could not set the update_engine performance mode", e)
        }
    }

    override fun install(packageFile: File) {
        val payload = try {
            readPayload(packageFile)
        } catch (e: IOException) {
            Log.e(TAG, "Could not read $packageFile", e)
            listener.onError(e.message ?: "Could not read the update package")
            return
        }

        if (!bound) {
            bound = updateEngine.bind(callback)
            if (!bound) {
                listener.onError("Could not bind to update_engine")
                return
            }
        }

        try {
            ParcelFileDescriptor.open(packageFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                updateEngine.applyPayload(
                    AssetFileDescriptor(fd, payload.offset, payload.size),
                    payload.properties,
                )
            }
        } catch (e: Exception) {
            // update_engine throws when it is already applying a payload.
            Log.e(TAG, "Could not apply the payload", e)
            listener.onError(e.message ?: "Could not apply the payload")
        }
    }

    override fun reattach() {
        reattached = true
        if (!bound) {
            bound = updateEngine.bind(callback)
            if (!bound) listener.onError("Could not bind to update_engine")
        }
    }

    override fun cancel() {
        if (bound) {
            updateEngine.cancel()
        }
    }

    override fun suspend() {
        if (bound) {
            updateEngine.suspend()
        }
    }

    override fun resume() {
        if (bound) {
            updateEngine.resume()
        }
    }

    override fun release() {
        if (bound) {
            updateEngine.unbind()
            bound = false
        }
    }

    @Throws(IOException::class)
    private fun readPayload(packageFile: File): Payload = ZipFile(packageFile).use { zip ->
        var offset = 0L
        var payloadOffset = -1L
        var payloadSize = 0L
        var properties: Array<String>? = null

        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            offset += headerSizeOf(entry)
            if (entry.isDirectory) continue

            when (entry.name) {
                PAYLOAD_BIN -> {
                    if (entry.method != ZipEntry.STORED) {
                        throw IOException(
                            "$PAYLOAD_BIN is compressed, update_engine cannot read it"
                        )
                    }
                    payloadOffset = offset
                    payloadSize = entry.size
                }

                PAYLOAD_PROPERTIES -> properties = zip.getInputStream(entry).use { input ->
                    String(input.readBytes(), StandardCharsets.UTF_8)
                        .lines()
                        .filter { it.isNotBlank() }
                        .toTypedArray()
                }
            }
            offset += entry.compressedSize
        }

        if (payloadOffset < 0) throw IOException("No $PAYLOAD_BIN in the update package")
        val headers = properties
            ?: throw IOException("No $PAYLOAD_PROPERTIES in the update package")
        Payload(payloadOffset, payloadSize, headers)
    }

    private fun headerSizeOf(entry: ZipEntry): Long =
        LOCAL_FILE_HEADER_SIZE + entry.name.length + (entry.extra?.size ?: 0).toLong()

    private fun errorMessage(errorCode: Int): String = when (errorCode) {
        UpdateEngine.ErrorCodeConstants.PAYLOAD_TIMESTAMP_ERROR ->
            "The update is older than the installed system"

        UpdateEngine.ErrorCodeConstants.DOWNLOAD_PAYLOAD_VERIFICATION_ERROR ->
            "Failed to verify the signature of the payload"

        UpdateEngine.ErrorCodeConstants.PAYLOAD_SIZE_MISMATCH_ERROR -> "Payload size mismatch"
        UpdateEngine.ErrorCodeConstants.PAYLOAD_MISMATCHED_TYPE_ERROR -> "Payload type mismatch"
        UpdateEngine.ErrorCodeConstants.PAYLOAD_HASH_MISMATCH_ERROR -> "Payload hash mismatch"
        UpdateEngine.ErrorCodeConstants.POST_INSTALL_RUNNER_ERROR -> "Post-install step failed"
        UpdateEngine.ErrorCodeConstants.INSTALL_DEVICE_OPEN_ERROR -> "Could not open the partitions"
        UpdateEngine.ErrorCodeConstants.DOWNLOAD_TRANSFER_ERROR -> "Could not read the payload"
        UpdateEngine.ErrorCodeConstants.UPDATED_BUT_NOT_ACTIVE ->
            "The update was installed, but the device was not switched to it"
        else -> "Update failed (error $errorCode)"
    }
}
