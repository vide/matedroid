package com.matedroid.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tripCountStore: DataStore<Preferences> by preferencesDataStore(name = "trip_count_cache")

/**
 * Lightweight cache for trip counts per car, so the dashboard can show
 * the last-known value instantly while recomputing in the background.
 */
@Singleton
class TripCountCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun key(carId: Int) = intPreferencesKey("trip_count_$carId")

    suspend fun get(carId: Int): Int? {
        return context.tripCountStore.data.map { prefs ->
            prefs[key(carId)]
        }.first()
    }

    suspend fun set(carId: Int, count: Int) {
        context.tripCountStore.edit { prefs ->
            prefs[key(carId)] = count
        }
    }
}
