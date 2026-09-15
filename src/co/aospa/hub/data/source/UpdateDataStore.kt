/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.data.source

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import co.aospa.hub.data.model.UpdateData
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private const val TAG = "UpdateDataStore"
private const val STORE_NAME = "hub"

private val Context.dataStore by preferencesDataStore(
    name = STORE_NAME,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Stores what is known about the update, as one serialized blob.
 *
 * Anything that cannot be read back is replaced with an empty record, which a check repairs.
 */
class UpdateDataStore(private val context: Context) {
    private val key = stringPreferencesKey("data")
    private val json = Json { ignoreUnknownKeys = true }

    /** Observable for what is stored. */
    val data: Flow<UpdateData> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { preferences -> preferences.read() }

    /** Returns what is stored right now. */
    suspend fun get(): UpdateData = data.first()

    /** Applies [transform] to what is stored and writes the result back. */
    suspend fun update(transform: (UpdateData) -> UpdateData) {
        context.dataStore.edit { preferences ->
            preferences[key] = json.encodeToString(transform(preferences.read()))
        }
    }

    private fun Preferences.read(): UpdateData {
        val stored = this[key] ?: return UpdateData()
        val restored = try {
            json.decodeFromString<UpdateData>(stored)
        } catch (e: SerializationException) {
            Log.w(TAG, "Could not read the stored update", e)
            UpdateData()
        }

        // A build the device is already running is not an update.
        val update = restored.update ?: return restored
        return if (update.isNewerThanDevice) {
            restored
        } else {
            UpdateData(lastCheckedAt = restored.lastCheckedAt)
        }
    }
}
