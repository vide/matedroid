package com.matedroid.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.chargeSessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "charge_session_state"
)

/**
 * Persists whether a car's most recent *active* charging session was DC.
 *
 * TeslaMate only reports `charger_phases = 0` while actively DC charging; after
 * completion `charger_phases` goes to null regardless of charge type, so we
 * can't distinguish an AC completion from a DC completion from a single
 * status snapshot. This store remembers the last active session's type so
 * the "DC finished, cable plugged in" warning only fires for real DC sessions.
 *
 * Callers should:
 *  - set `dcActive = true` when a status shows `isCharging && isDcCharging`
 *  - set `dcActive = false` (clear) once the cable is unplugged
 */
@Singleton
class ChargeSessionStateDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun wasDcKey(carId: Int) = booleanPreferencesKey("was_dc_$carId")

    suspend fun wasLastSessionDc(carId: Int): Boolean {
        return context.chargeSessionDataStore.data
            .map { it[wasDcKey(carId)] ?: false }
            .first()
    }

    suspend fun setLastSessionDc(carId: Int, isDc: Boolean) {
        context.chargeSessionDataStore.edit { it[wasDcKey(carId)] = isDc }
    }

    suspend fun clear(carId: Int) {
        context.chargeSessionDataStore.edit { it.remove(wasDcKey(carId)) }
    }
}
