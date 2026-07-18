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
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manual override for car image selection.
 *
 * @param variant The model variant (e.g., "my", "myj", "myjs", "myjp")
 * @param wheelCode The wheel code (e.g., "WY18P", "WY19P")
 */
data class CarImageOverride(
    val variant: String,
    val wheelCode: String
) {
    fun toJson(): String = """{"variant":"$variant","wheelCode":"$wheelCode"}"""

    companion object {
        fun fromJson(json: String): CarImageOverride? {
            return try {
                val obj = JSONObject(json)
                CarImageOverride(
                    variant = obj.getString("variant"),
                    wheelCode = obj.getString("wheelCode")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "matedroid_settings")

data class AppSettings(
    val serverUrl: String = "",
    val secondaryServerUrl: String = "",
    val apiToken: String = "",
    val httpBasicAuthUsername: String = "",
    val httpBasicAuthPassword: String = "",
    val acceptInvalidCerts: Boolean = false,
    val currencyCode: String = "EUR",
    val showShortDrivesCharges: Boolean = false,
    val teslamateBaseUrl: String = "",
    val unitOfLength: String = "km",
    val lastSelectedCarId: Int? = null,
    /**
     * Home / AC utility rate (per kWh) in the user's chosen [currencyCode].
     * Null when the user has not configured a rate; MateDroid then falls back
     * to TeslaMate's recorded cost only.
     */
    val homeUtilityRatePerKwh: Double? = null,
    /**
     * Optional DC / public utility rate (per kWh). When null, DC estimates
     * fall back to [homeUtilityRatePerKwh].
     */
    val dcUtilityRatePerKWh: Double? = null,
    /**
     * User assumption for an equivalent gasoline car's fuel economy.
     * Interpreted in L/100 km when [iceFuelEconomyIsMpg] is false, US mpg otherwise.
     * Null when unset — the gasoline comparison stays hidden in that case.
     */
    val iceFuelEconomyValue: Double? = null,
    val iceFuelEconomyIsMpg: Boolean = false,
    /**
     * User assumption for the price of a unit of gasoline. Interpreted per
     * liter when [iceFuelPriceIsPerGallon] is false, per US gallon otherwise.
     * Stored in the same currency as [currencyCode].
     */
    val iceFuelPrice: Double? = null,
    val iceFuelPriceIsPerGallon: Boolean = false,
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
    private val httpBasicAuthUsernameKey = stringPreferencesKey("http_basic_auth_username")
    private val httpBasicAuthPasswordKey = stringPreferencesKey("http_basic_auth_password")
    private val acceptInvalidCertsKey = booleanPreferencesKey("accept_invalid_certs")
    private val currencyCodeKey = stringPreferencesKey("currency_code")
    private val showShortDrivesChargesKey = booleanPreferencesKey("show_short_drives_charges")
    private val teslamateBaseUrlKey = stringPreferencesKey("teslamate_base_url")
    private val unitOfLengthKey = stringPreferencesKey("unit_of_length")
    private val lastSelectedCarIdKey = intPreferencesKey("last_selected_car_id")
    private val carImageOverridesKey = stringPreferencesKey("car_image_overrides")
    private val notificationPermissionAskedKey = booleanPreferencesKey("notification_permission_asked")
    private val homeUtilityRateKey = stringPreferencesKey("home_utility_rate_per_kwh")
    private val dcUtilityRateKey = stringPreferencesKey("dc_utility_rate_per_kwh")
    private val iceFuelEconomyValueKey = stringPreferencesKey("ice_fuel_economy_value")
    private val iceFuelEconomyIsMpgKey = booleanPreferencesKey("ice_fuel_economy_is_mpg")
    private val iceFuelPriceKey = stringPreferencesKey("ice_fuel_price")
    private val iceFuelPriceIsPerGallonKey = booleanPreferencesKey("ice_fuel_price_is_per_gallon")

    val notificationPermissionAsked: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[notificationPermissionAskedKey] ?: false
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            serverUrl = preferences[serverUrlKey] ?: "",
            secondaryServerUrl = preferences[secondaryServerUrlKey] ?: "",
            apiToken = preferences[apiTokenKey] ?: "",
            httpBasicAuthUsername = preferences[httpBasicAuthUsernameKey] ?: "",
            httpBasicAuthPassword = preferences[httpBasicAuthPasswordKey] ?: "",
            acceptInvalidCerts = preferences[acceptInvalidCertsKey] ?: false,
            currencyCode = preferences[currencyCodeKey] ?: "EUR",
            showShortDrivesCharges = preferences[showShortDrivesChargesKey] ?: false,
            teslamateBaseUrl = preferences[teslamateBaseUrlKey] ?: "",
            unitOfLength = preferences[unitOfLengthKey] ?: "km",
            lastSelectedCarId = preferences[lastSelectedCarIdKey],
            homeUtilityRatePerKwh = preferences[homeUtilityRateKey]?.toDoubleOrNull(),
            dcUtilityRatePerKWh = preferences[dcUtilityRateKey]?.toDoubleOrNull(),
            iceFuelEconomyValue = preferences[iceFuelEconomyValueKey]?.toDoubleOrNull(),
            iceFuelEconomyIsMpg = preferences[iceFuelEconomyIsMpgKey] ?: false,
            iceFuelPrice = preferences[iceFuelPriceKey]?.toDoubleOrNull(),
            iceFuelPriceIsPerGallon = preferences[iceFuelPriceIsPerGallonKey] ?: false,
        )
    }

    val showShortDrivesCharges: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[showShortDrivesChargesKey] ?: false
    }

    /**
     * Flow of car image overrides, keyed by car ID.
     */
    val carImageOverrides: Flow<Map<Int, CarImageOverride>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[carImageOverridesKey] ?: "{}"
        parseOverridesJson(jsonString)
    }

    private fun parseOverridesJson(jsonString: String): Map<Int, CarImageOverride> {
        return try {
            val result = mutableMapOf<Int, CarImageOverride>()
            val obj = JSONObject(jsonString)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val carId = key.toIntOrNull() ?: continue
                val overrideJson = obj.getJSONObject(key)
                val override = CarImageOverride(
                    variant = overrideJson.getString("variant"),
                    wheelCode = overrideJson.getString("wheelCode")
                )
                result[carId] = override
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun overridesToJson(overrides: Map<Int, CarImageOverride>): String {
        val obj = JSONObject()
        for ((carId, override) in overrides) {
            val overrideObj = JSONObject()
            overrideObj.put("variant", override.variant)
            overrideObj.put("wheelCode", override.wheelCode)
            obj.put(carId.toString(), overrideObj)
        }
        return obj.toString()
    }

    suspend fun saveSettings(
        serverUrl: String,
        secondaryServerUrl: String,
        apiToken: String,
        httpBasicAuthUsername: String,
        httpBasicAuthPassword: String,
        acceptInvalidCerts: Boolean,
        currencyCode: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[serverUrlKey] = serverUrl
            preferences[secondaryServerUrlKey] = secondaryServerUrl
            preferences[apiTokenKey] = apiToken
            preferences[httpBasicAuthUsernameKey] = httpBasicAuthUsername
            preferences[httpBasicAuthPasswordKey] = httpBasicAuthPassword
            preferences[acceptInvalidCertsKey] = acceptInvalidCerts
            preferences[currencyCodeKey] = currencyCode
        }
    }

    suspend fun saveHttpBasicAuth(username: String, password: String) {
        context.dataStore.edit { preferences ->
            preferences[httpBasicAuthUsernameKey] = username
            preferences[httpBasicAuthPasswordKey] = password
        }
    }

    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[serverUrlKey] = url
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

    /**
     * Cache the user's unit-of-length preference (km/mi) fetched from TeslaMate
     * global settings. Consumed by widgets so they can format distances offline
     * without hitting the API.
     */
    suspend fun saveUnitOfLength(unitOfLength: String) {
        context.dataStore.edit { preferences ->
            preferences[unitOfLengthKey] = unitOfLength
        }
    }

    suspend fun saveLastSelectedCarId(carId: Int) {
        context.dataStore.edit { preferences ->
            preferences[lastSelectedCarIdKey] = carId
        }
    }

    /**
     * Save or clear a car image override.
     *
     * @param carId The car ID to save the override for
     * @param override The override to save, or null to clear
     */
    suspend fun saveCarImageOverride(carId: Int, override: CarImageOverride?) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[carImageOverridesKey] ?: "{}"
            val currentMap = parseOverridesJson(currentJson).toMutableMap()

            if (override != null) {
                currentMap[carId] = override
            } else {
                currentMap.remove(carId)
            }

            preferences[carImageOverridesKey] = overridesToJson(currentMap)
        }
    }

    /**
     * Save (or clear when null / non-positive) the home utility rate. Rates are
     * stored as strings so an empty preference cleanly means "not set". Any
     * non-finite or non-positive value is treated as "clear the rate".
     */
    suspend fun saveHomeUtilityRate(ratePerKwh: Double?) {
        context.dataStore.edit { preferences ->
            val valid = ratePerKwh?.takeIf { it.isFinite() && it > 0.0 }
            if (valid == null) {
                preferences.remove(homeUtilityRateKey)
            } else {
                preferences[homeUtilityRateKey] = valid.toString()
            }
        }
    }

    /**
     * Save (or clear when null / non-positive) the optional DC utility rate.
     */
    suspend fun saveDcUtilityRate(ratePerKwh: Double?) {
        context.dataStore.edit { preferences ->
            val valid = ratePerKwh?.takeIf { it.isFinite() && it > 0.0 }
            if (valid == null) {
                preferences.remove(dcUtilityRateKey)
            } else {
                preferences[dcUtilityRateKey] = valid.toString()
            }
        }
    }

    /**
     * Save (or clear when null / non-positive) the ICE fuel-economy assumption.
     * [isMpg] records the unit the user typed the value in so we don't lose it
     * on reload.
     */
    suspend fun saveIceFuelEconomy(value: Double?, isMpg: Boolean) {
        context.dataStore.edit { preferences ->
            val valid = value?.takeIf { it.isFinite() && it > 0.0 }
            if (valid == null) {
                preferences.remove(iceFuelEconomyValueKey)
            } else {
                preferences[iceFuelEconomyValueKey] = valid.toString()
            }
            preferences[iceFuelEconomyIsMpgKey] = isMpg
        }
    }

    /**
     * Save (or clear when null / non-positive) the ICE fuel-price assumption
     * in the user's chosen currency. [isPerGallon] records the entered unit.
     */
    suspend fun saveIceFuelPrice(value: Double?, isPerGallon: Boolean) {
        context.dataStore.edit { preferences ->
            val valid = value?.takeIf { it.isFinite() && it > 0.0 }
            if (valid == null) {
                preferences.remove(iceFuelPriceKey)
            } else {
                preferences[iceFuelPriceKey] = valid.toString()
            }
            preferences[iceFuelPriceIsPerGallonKey] = isPerGallon
        }
    }

    /** Update just the ICE fuel-economy unit (leaves the value untouched). */
    suspend fun saveIceFuelEconomyUnit(isMpg: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[iceFuelEconomyIsMpgKey] = isMpg
        }
    }

    /** Update just the ICE fuel-price unit (leaves the value untouched). */
    suspend fun saveIceFuelPriceUnit(isPerGallon: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[iceFuelPriceIsPerGallonKey] = isPerGallon
        }
    }

    suspend fun saveNotificationPermissionAsked() {
        context.dataStore.edit { preferences ->
            preferences[notificationPermissionAskedKey] = true
        }
    }

    suspend fun clearSettings() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
