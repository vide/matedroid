package com.matedroid.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "matedroid_settings")

data class AppSettings(
    val serverUrl: String = "",
    val secondaryServerUrl: String = "",
    val apiToken: String = "",
    val acceptInvalidCerts: Boolean = false,
    val currencyCode: String = "EUR",
    val showShortDrivesCharges: Boolean = false,
    val teslamateBaseUrl: String = "",
    val lastSelectedCarId: Int? = null
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank()

    val hasSecondaryServer: Boolean
        get() = secondaryServerUrl.isNotBlank()
}

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val serverUrlKey = stringPreferencesKey("server_url")
    private val secondaryServerUrlKey = stringPreferencesKey("secondary_server_url")
    private val apiTokenKey = stringPreferencesKey("api_token")
    private val acceptInvalidCertsKey = booleanPreferencesKey("accept_invalid_certs")
    private val currencyCodeKey = stringPreferencesKey("currency_code")
    private val showShortDrivesChargesKey = booleanPreferencesKey("show_short_drives_charges")
    private val teslamateBaseUrlKey = stringPreferencesKey("teslamate_base_url")
    private val lastSelectedCarIdKey = intPreferencesKey("last_selected_car_id")

    val settings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            serverUrl = preferences[serverUrlKey] ?: "",
            secondaryServerUrl = preferences[secondaryServerUrlKey] ?: "",
            apiToken = preferences[apiTokenKey] ?: "",
            acceptInvalidCerts = preferences[acceptInvalidCertsKey] ?: false,
            currencyCode = preferences[currencyCodeKey] ?: "EUR",
            showShortDrivesCharges = preferences[showShortDrivesChargesKey] ?: false,
            teslamateBaseUrl = preferences[teslamateBaseUrlKey] ?: "",
            lastSelectedCarId = preferences[lastSelectedCarIdKey]
        )
    }

    val showShortDrivesCharges: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[showShortDrivesChargesKey] ?: false
    }

    suspend fun saveSettings(
        serverUrl: String,
        secondaryServerUrl: String,
        apiToken: String,
        acceptInvalidCerts: Boolean,
        currencyCode: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[serverUrlKey] = serverUrl
            preferences[secondaryServerUrlKey] = secondaryServerUrl
            preferences[apiTokenKey] = apiToken
            preferences[acceptInvalidCertsKey] = acceptInvalidCerts
            preferences[currencyCodeKey] = currencyCode
        }
    }

    suspend fun saveCurrency(currencyCode: String) {
        context.dataStore.edit { preferences ->
            preferences[currencyCodeKey] = currencyCode
        }
    }

    suspend fun saveShowShortDrivesCharges(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[showShortDrivesChargesKey] = show
        }
    }

    suspend fun saveTeslamateBaseUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[teslamateBaseUrlKey] = url
        }
    }

    suspend fun saveLastSelectedCarId(carId: Int) {
        context.dataStore.edit { preferences ->
            preferences[lastSelectedCarIdKey] = carId
        }
    }

    suspend fun clearSettings() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
