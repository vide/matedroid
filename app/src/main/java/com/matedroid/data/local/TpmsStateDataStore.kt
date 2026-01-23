package com.matedroid.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tpmsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tpms_state")

/**
 * Represents the TPMS (Tire Pressure Monitoring System) warning state for a car.
 */
data class TpmsState(
    val warningFl: Boolean = false,
    val warningFr: Boolean = false,
    val warningRl: Boolean = false,
    val warningRr: Boolean = false,
    val lastCheckedAt: Long = 0L
) {
    /**
     * Returns true if any tire has a warning.
     */
    val hasAnyWarning: Boolean
        get() = warningFl || warningFr || warningRl || warningRr

    /**
     * Returns list of tire positions that have warnings.
     */
    fun getWarningTires(): List<TirePosition> {
        return buildList {
            if (warningFl) add(TirePosition.FL)
            if (warningFr) add(TirePosition.FR)
            if (warningRl) add(TirePosition.RL)
            if (warningRr) add(TirePosition.RR)
        }
    }
}

/**
 * Tire position enum for identifying which tires have warnings.
 */
enum class TirePosition {
    FL, FR, RL, RR
}

/**
 * Preferences DataStore for persisting TPMS warning state per car.
 * State is keyed by carId (e.g., tpms_warning_fl_1 for car 1's front left tire).
 */
@Singleton
class TpmsStateDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Key builders for car-specific preferences
    private fun warningFlKey(carId: Int) = booleanPreferencesKey("tpms_warning_fl_$carId")
    private fun warningFrKey(carId: Int) = booleanPreferencesKey("tpms_warning_fr_$carId")
    private fun warningRlKey(carId: Int) = booleanPreferencesKey("tpms_warning_rl_$carId")
    private fun warningRrKey(carId: Int) = booleanPreferencesKey("tpms_warning_rr_$carId")
    private fun lastCheckedKey(carId: Int) = longPreferencesKey("tpms_last_checked_$carId")

    /**
     * Get the current TPMS state for a specific car.
     */
    suspend fun getState(carId: Int): TpmsState {
        return context.tpmsDataStore.data.map { preferences ->
            TpmsState(
                warningFl = preferences[warningFlKey(carId)] ?: false,
                warningFr = preferences[warningFrKey(carId)] ?: false,
                warningRl = preferences[warningRlKey(carId)] ?: false,
                warningRr = preferences[warningRrKey(carId)] ?: false,
                lastCheckedAt = preferences[lastCheckedKey(carId)] ?: 0L
            )
        }.first()
    }

    /**
     * Save the TPMS state for a specific car.
     */
    suspend fun saveState(carId: Int, state: TpmsState) {
        context.tpmsDataStore.edit { preferences ->
            preferences[warningFlKey(carId)] = state.warningFl
            preferences[warningFrKey(carId)] = state.warningFr
            preferences[warningRlKey(carId)] = state.warningRl
            preferences[warningRrKey(carId)] = state.warningRr
            preferences[lastCheckedKey(carId)] = state.lastCheckedAt
        }
    }

    /**
     * Clear all TPMS states (for all cars).
     */
    suspend fun clearAllStates() {
        context.tpmsDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
