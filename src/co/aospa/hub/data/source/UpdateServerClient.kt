/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.data.source

import android.content.Context
import co.aospa.hub.R
import co.aospa.hub.data.model.Update
import co.aospa.hub.util.BuildInfo
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TIMEOUT_MILLIS = 30_000

@Serializable
private data class Feed(val updates: List<Entry> = emptyList())

@Serializable
private data class Entry(
    @SerialName("android_spl") val securityPatch: String? = null,
    @SerialName("build_type") val buildType: String,
    @SerialName("changelog_device") val changelog: String? = null,
    @SerialName("datetime") val timestamp: String,
    @SerialName("filename") val filename: String,
    @SerialName("recovery_sha256") val sha256: String,
    @SerialName("size") val size: String,
    @SerialName("url") val url: String,
    @SerialName("version") val version: String,
    @SerialName("version_code") val versionCode: String,
) {
    fun toUpdate() = Update(
        id = sha256,
        name = filename,
        version = version,
        versionCode = versionCode,
        timestamp = timestamp.toLong(),
        size = size.toLong(),
        url = url,
        sha256 = sha256,
        buildType = buildType,
        changelog = changelog.orEmpty(),
        securityPatch = securityPatch.orEmpty(),
    )
}

/** Fetches the build list the server publishes for this device. */
class UpdateServerClient(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private val url: String
        get() = context.getString(R.string.config_updateServerUrl, BuildInfo.device)

    /** Returns the newest build that applies to this device, or null if none. */
    @Throws(IOException::class)
    fun fetchLatest(): Update? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
        }

        val body = try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Unexpected HTTP status: ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }

        return json.decodeFromString<Feed>(body).updates
            .map { it.toUpdate() }
            .filter { it.matchesDeviceFlavour && it.isNewerThanDevice }
            .maxByOrNull { it.timestamp }
    }
}
