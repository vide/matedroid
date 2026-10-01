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
    val lastEventAt: Long = 0L,
    val sessionStartedAt: Long = 0L
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
    private fun sessionStartedAtKey(carId: Int) = longPreferencesKey("sentry_session_started_$carId")

    private fun Preferences.toSentryState(carId: Int) = SentryState(
        sentryActive = this[activeKey(carId)] ?: false,
        eventCount = this[eventCountKey(carId)] ?: 0,
        lastEventAt = this[lastEventKey(carId)] ?: 0L,
        sessionStartedAt = this[sessionStartedAtKey(carId)] ?: 0L
    )

    /**
     * Get the current sentry state for a specific car.
     */
    suspend fun getState(carId: Int): SentryState {
        return context.sentryDataStore.data.map { it.toSentryState(carId) }.first()
    }

    /**
     * Save the sentry state for a specific car.
     */
    suspend fun saveState(carId: Int, state: SentryState) {
        context.sentryDataStore.edit { preferences ->
            preferences[activeKey(carId)] = state.sentryActive
            preferences[eventCountKey(carId)] = state.eventCount
            preferences[lastEventKey(carId)] = state.lastEventAt
            preferences[sessionStartedAtKey(carId)] = state.sessionStartedAt
        }
    }

    /**
     * Increment the event count and update lastEventAt for a specific car.
     *
     * The read-modify-write happens inside a single [DataStore.edit] transaction,
     * so concurrent callers can't lose increments.
     */
    suspend fun incrementEventCount(carId: Int): SentryState {
        val updated = context.sentryDataStore.edit { preferences ->
            preferences[eventCountKey(carId)] = (preferences[eventCountKey(carId)] ?: 0) + 1
            preferences[lastEventKey(carId)] = System.currentTimeMillis()
        }
        return updated.toSentryState(carId)
    }

    /**
     * Reset the sentry session for a specific car (clears event count).
     */
    suspend fun resetSession(carId: Int) {
        context.sentryDataStore.edit { preferences ->
            preferences[activeKey(carId)] = false
            preferences[eventCountKey(carId)] = 0
            preferences[lastEventKey(carId)] = 0L
            preferences[sessionStartedAtKey(carId)] = 0L
        }
    }
}
