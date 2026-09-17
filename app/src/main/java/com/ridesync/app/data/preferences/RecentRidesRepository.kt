package com.ridesync.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ridesync.app.domain.model.RideSummary
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.recentRidesDataStore: DataStore<Preferences> by preferencesDataStore(name = "recent_rides")

@Serializable
data class RecentRide(
    val name: String,
    val endedAtMs: Long,
    val durationMs: Long,
    val riderCount: Int,
    val interruptions: Int = 0,
    val musicSyncedMs: Long = 0,
    val talkSeconds: Long = 0,
)

/** Small local-only history of finished rides. No cloud, no accounts. */
class RecentRidesRepository(private val context: Context) {

    private companion object {
        val KEY = stringPreferencesKey("rides_json")
        const val MAX_ENTRIES = 8
        val json = Json { ignoreUnknownKeys = true }
    }

    val recentRides: Flow<List<RecentRide>> = context.recentRidesDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            prefs[KEY]?.let {
                runCatching { json.decodeFromString<List<RecentRide>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
        }

    suspend fun add(summary: RideSummary) {
        val entry = RecentRide(
            name = summary.rideName,
            endedAtMs = summary.startedAtMs + summary.durationMs,
            durationMs = summary.durationMs,
            riderCount = summary.riderCount,
            interruptions = summary.interruptions,
            musicSyncedMs = summary.musicSyncedMs,
            talkSeconds = summary.talkSeconds,
        )
        val current = recentRides.first()
        val updated = (listOf(entry) + current).take(MAX_ENTRIES)
        context.recentRidesDataStore.edit { it[KEY] = json.encodeToString(updated) }
    }
}
