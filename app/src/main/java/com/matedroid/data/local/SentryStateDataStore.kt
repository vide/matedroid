package com.matedroid.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sentryDataStore: DataStore<Preferences> by preferencesDataStore(name = "sentry_state")

/**
 * Represents the sentry event detection state for a car.
 */
data class SentryState(
    val sentryActive: Boolean = false,
    val eventCount: Int = 0,
    val lastEventAt: Long = 0L
)

/**
 * Preferences DataStore for persisting sentry event state per car.
 * State is keyed by carId (e.g., sentry_active_1 for car 1).
 */
@Singleton
class SentryStateDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun activeKey(carId: Int) = booleanPreferencesKey("sentry_active_$carId")
    private fun eventCountKey(carId: Int) = intPreferencesKey("sentry_event_count_$carId")
    private fun lastEventKey(carId: Int) = longPreferencesKey("sentry_last_event_$carId")

    /**
     * Get the current sentry state for a specific car.
     */
    suspend fun getState(carId: Int): SentryState {
        return context.sentryDataStore.data.map { preferences ->
            SentryState(
                sentryActive = preferences[activeKey(carId)] ?: false,
                eventCount = preferences[eventCountKey(carId)] ?: 0,
                lastEventAt = preferences[lastEventKey(carId)] ?: 0L
            )
        }.first()
    }

    /**
     * Save the sentry state for a specific car.
     */
    suspend fun saveState(carId: Int, state: SentryState) {
        context.sentryDataStore.edit { preferences ->
            preferences[activeKey(carId)] = state.sentryActive
            preferences[eventCountKey(carId)] = state.eventCount
            preferences[lastEventKey(carId)] = state.lastEventAt
        }
    }

    /**
     * Increment the event count and update lastEventAt for a specific car.
     */
    suspend fun incrementEventCount(carId: Int): SentryState {
        val current = getState(carId)
        val updated = current.copy(
            eventCount = current.eventCount + 1,
            lastEventAt = System.currentTimeMillis()
        )
        saveState(carId, updated)
        return updated
    }

    /**
     * Reset the sentry session for a specific car (clears event count).
     */
    suspend fun resetSession(carId: Int) {
        context.sentryDataStore.edit { preferences ->
            preferences[activeKey(carId)] = false
            preferences[eventCountKey(carId)] = 0
            preferences[lastEventKey(carId)] = 0L
        }
    }
}
