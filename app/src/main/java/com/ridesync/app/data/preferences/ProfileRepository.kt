package com.ridesync.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "profile")

/**
 * Identity of this device/rider. The rider id is a UUID generated once and
 * reused forever, which is what lets a rider drop off Wi-Fi and rejoin the
 * same ride slot.
 */
class ProfileRepository(private val context: Context) {

    private object Keys {
        val RIDER_ID = stringPreferencesKey("rider_id")
        val RIDER_NAME = stringPreferencesKey("rider_name")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    private val data = context.profileDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val riderName: Flow<String> = data.map { it[Keys.RIDER_NAME] ?: "" }
    val onboardingDone: Flow<Boolean> = data.map { it[Keys.ONBOARDING_DONE] ?: false }

    /** Returns the stable rider id, creating it on first use. */
    suspend fun riderId(): String {
        val existing = data.first()[Keys.RIDER_ID]
        if (existing != null) return existing
        val fresh = UUID.randomUUID().toString()
        context.profileDataStore.edit { it[Keys.RIDER_ID] = fresh }
        return fresh
    }

    suspend fun currentRiderName(): String = riderName.first()

    suspend fun setRiderName(name: String) {
        context.profileDataStore.edit { it[Keys.RIDER_NAME] = name.trim().take(24) }
    }

    suspend fun setOnboardingDone() {
        context.profileDataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    }
}
