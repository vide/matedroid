package com.matedroid.data.repository

import android.util.Log
import com.matedroid.data.api.TeslamateApi
import com.matedroid.data.api.models.BatteryHealth
import com.matedroid.data.api.models.CarData
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.api.models.ChargeData
import com.matedroid.data.api.models.ChargeDetail
import com.matedroid.data.api.models.DriveData
import com.matedroid.data.api.models.DriveDetail
import com.matedroid.data.api.models.GlobalSettingsData
import com.matedroid.data.api.models.Units
import com.matedroid.data.api.models.UpdateData
import com.matedroid.data.local.AppSettings
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.di.TeslamateApiFactory
import com.matedroid.domain.UnitSystem
import kotlinx.coroutines.flow.first
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import retrofit2.Response

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(
        val message: String,
        val code: Int? = null,
        val details: String? = null
    ) : ApiResult<Nothing>()
}

data class CarStatusWithUnits(
    val status: CarStatus,
    val units: Units
)

/**
 * Typed outcome of the current-charge endpoint: the server answering
 * "no active charge" is an authoritative response, distinct from errors.
 */
sealed class CurrentChargeOutcome {
    data class Active(val detail: ChargeDetail) : CurrentChargeOutcome()
    data object NoActiveCharge : CurrentChargeOutcome()
}

/**
 * Represents exceptions that should trigger a fallback to the secondary server.
 * These are network-level errors where the server is unreachable, not application-level errors.
 */
private fun Throwable.isNetworkError(): Boolean {
    return this is SocketTimeoutException ||
            this is ConnectException ||
            this is UnknownHostException ||
            this is SSLException ||
            this is java.io.IOException && message?.contains("connection", ignoreCase = true) == true
}

/**
 * Checks if an exception is a JSON parsing error.
 * These errors indicate the server returned something that isn't valid JSON
 * or doesn't match the expected schema.
 */
private fun Throwable.isJsonParsingError(): Boolean {
    return this is JsonDataException ||
            this is JsonEncodingException ||
            (this is java.io.IOException && message?.contains("JsonReader", ignoreCase = true) == true)
}

@Singleton
class TeslamateRepository @Inject constructor(
    private val apiFactory: TeslamateApiFactory,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "TeslamateRepository"
    }

    // Cache: true = endpoint exists (API 1.24+), false = 404 (older API)
    private val currentChargeApiAvailable = mutableMapOf<Int, Boolean>()

    /**
     * Check whether the current charge endpoint is available for the given car.
     * Makes a dedicated HTTP call and checks only the status code: 200 means the endpoint
     * exists, 404 means an old TM version without it. Only those two definitive answers are
     * cached for the app session — a transient failure (timeout, DNS, 5xx) must not disable
     * the live-charge UI until process death, so it leaves the cache unset and is retried.
     */
    suspend fun isCurrentChargeAvailable(carId: Int): Boolean {
        currentChargeApiAvailable[carId]?.let { return it }
        val result = executeWithFallback { api ->
            val response = api.getCurrentCharge(carId)
            if (response.code() == 200) ApiResult.Success(true)
            else ApiResult.Error("Not available", response.code())
        }
        return when {
            result is ApiResult.Success -> {
                currentChargeApiAvailable[carId] = true
                true
            }
            result is ApiResult.Error && result.code == 404 -> {
                currentChargeApiAvailable[carId] = false
                false
            }
            else -> false // transient — don't cache, probe again next time
        }
    }

    private suspend fun getSettings(): AppSettings = settingsDataStore.settings.first()

    private suspend fun getApiForUrl(url: String): TeslamateApi? {
        if (url.isBlank()) return null
        return apiFactory.create(url)
    }

    /**
     * Executes an API call with automatic fallback to the secondary server if configured.
     *
     * The fallback is triggered only for network-level errors (timeout, connection refused,
     * DNS failure, SSL errors). HTTP errors (4xx, 5xx) do NOT trigger fallback because
     * they indicate the server is reachable but returned an error.
     *
     * @param apiCall The API call to execute, given a TeslamateApi instance
     * @return The result of the API call
     */
    private suspend fun <T> executeWithFallback(
        apiCall: suspend (TeslamateApi) -> ApiResult<T>
    ): ApiResult<T> {
        val settings = getSettings()

        if (settings.serverUrl.isBlank()) {
            return ApiResult.Error("Server not configured")
        }

        // Try primary server first
        val primaryApi = getApiForUrl(settings.serverUrl)
            ?: return ApiResult.Error("Server not configured")

        val primaryResult = try {
            apiCall(primaryApi)
        } catch (e: Exception) {
            if (e.isNetworkError() && settings.hasSecondaryServer) {
                Log.d(TAG, "Primary server failed with network error, trying secondary: ${e.message}")
                null // Will try secondary
            } else {
                // Not a network error or no secondary server, return the error
                return when {
                    e is javax.net.ssl.SSLHandshakeException ->
                        ApiResult.Error("SSL certificate error. Enable 'Accept invalid certificates' for self-signed certs.")
                    e.isJsonParsingError() ->
                        ApiResult.Error(
                            message = "Invalid response from server",
                            details = "The server returned an unexpected response that could not be parsed.\n\n" +
                                    "This usually means:\n" +
                                    "• The API URL might be incorrect\n" +
                                    "• The server is returning an error page\n" +
                                    "• TeslaMate API is not properly configured\n\n" +
                                    "Technical details: ${e.message}"
                        )
                    else -> ApiResult.Error(e.message ?: "Connection failed")
                }
            }
        }

        // If primary succeeded or returned an HTTP error, return it
        if (primaryResult != null) {
            // Only fallback on network errors, not on HTTP errors
            if (primaryResult is ApiResult.Success) {
                return primaryResult
            }
            // For HTTP errors, don't fallback - the server is reachable
            if (primaryResult is ApiResult.Error && primaryResult.code != null) {
                return primaryResult
            }
        }

        // Try secondary server if available
        if (settings.hasSecondaryServer) {
            Log.d(TAG, "Trying secondary server: ${settings.secondaryServerUrl}")
            val secondaryApi = getApiForUrl(settings.secondaryServerUrl)
                ?: return primaryResult ?: ApiResult.Error("Secondary server not configured")

            return try {
                apiCall(secondaryApi)
            } catch (e: Exception) {
                Log.d(TAG, "Secondary server also failed: ${e.message}")
                // Both servers failed, return a combined error message
                when {
                    e is javax.net.ssl.SSLHandshakeException ->
                        ApiResult.Error("Both servers failed. SSL certificate error on secondary server.")
                    e.isJsonParsingError() ->
                        ApiResult.Error(
                            message = "Invalid response from secondary server",
                            details = "The secondary server returned an unexpected response.\n\n" +
                                    "Technical details: ${e.message}"
                        )
                    else -> ApiResult.Error("Both servers unreachable: ${e.message}")
                }
            }
        }

        // No secondary server, return the primary error
        return primaryResult ?: ApiResult.Error("Connection failed")
    }

    /**
     * Pings [serverUrl] with the settings the caller passes rather than the saved ones, so
     * Settings can test the form as it stands before anything is committed to disk.
     */
    suspend fun testConnection(
        serverUrl: String,
        acceptInvalidCerts: Boolean = false,
        connectTimeoutSeconds: Int? = null
    ): ApiResult<Unit> {
        return try {
            val api = apiFactory.create(serverUrl, acceptInvalidCerts, connectTimeoutSeconds)
            val response = api.ping()
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error("Server returned ${response.code()}", response.code())
            }
        } catch (e: javax.net.ssl.SSLHandshakeException) {
            ApiResult.Error("SSL certificate error. Enable 'Accept invalid certificates' for self-signed certs.")
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Connection failed")
        }
    }

    /**
     * Map a Retrofit [Response] to an [ApiResult]: on a successful HTTP status,
     * run [extract] on the (nullable) body and wrap a non-null result as Success,
     * otherwise report the missing payload; on a non-2xx status, surface the code.
     * [what] names the resource for the error messages.
     *
     * Any thrown exception propagates to [executeWithFallback], which owns the
     * network-error / secondary-server handling.
     */
    private inline fun <B, T> Response<B>.toResult(
        what: String,
        extract: (B?) -> T?
    ): ApiResult<T> =
        if (isSuccessful) {
            extract(body())?.let { ApiResult.Success(it) }
                ?: ApiResult.Error("No $what returned")
        } else {
            ApiResult.Error("Failed to fetch $what: ${code()}", code())
        }

    suspend fun getCars(): ApiResult<List<CarData>> =
        executeWithFallback { api -> api.getCars().toResult("cars") { it?.data?.cars ?: emptyList() } }

    suspend fun getCar(carId: Int): ApiResult<CarData> =
        executeWithFallback { api -> api.getCar(carId).toResult("car") { it?.data?.cars?.firstOrNull() } }

    suspend fun getCarStatus(carId: Int): ApiResult<CarStatusWithUnits> {
        val result = executeWithFallback { api ->
            api.getCarStatus(carId).toResult("status") { body ->
                body?.data?.let { data -> data.status?.let { CarStatusWithUnits(it, data.units ?: Units()) } }
            }
        }
        if (result is ApiResult.Success) {
            updateUnitSystem(result.data.units.isImperial)
        }
        return result
    }

    // Tracks what we last persisted so the DataStore write happens at most once per change.
    private var lastPersistedImperial: Boolean? = null

    /** Keep [UnitSystem] (and its persisted copy) in sync with the server's unit setting. */
    private suspend fun updateUnitSystem(imperial: Boolean) {
        UnitSystem.isImperial = imperial
        if (lastPersistedImperial != imperial) {
            lastPersistedImperial = imperial
            settingsDataStore.saveIsImperial(imperial)
        }
    }

    suspend fun getCharges(
        carId: Int,
        startDate: String? = null,
        endDate: String? = null,
        page: Int = 1,
        show: Int = 50000
    ): ApiResult<List<ChargeData>> =
        executeWithFallback { api ->
            api.getCharges(carId, startDate, endDate, page = page, show = show)
                .toResult("charges") { it?.data?.charges ?: emptyList() }
        }

    suspend fun getCurrentCharge(carId: Int): ApiResult<CurrentChargeOutcome> {
        return executeWithFallback { api ->
            val response = api.getCurrentCharge(carId)
            if (response.isSuccessful) {
                val body = response.body()
                val detail = body?.data?.charge
                when {
                    detail != null -> ApiResult.Success(CurrentChargeOutcome.Active(detail))
                    // TeslamateAPI answers 200 + {"error": "..."} (or 204) when there is
                    // no active charge — an authoritative answer, not a failure. At charge
                    // start this is returned for a short while before the charge appears.
                    body?.error != null || response.code() == 204 ->
                        ApiResult.Success(CurrentChargeOutcome.NoActiveCharge)
                    else -> ApiResult.Error("No current charge data returned")
                }
            } else {
                ApiResult.Error("Failed to fetch current charge: ${response.code()}", response.code())
            }
        }
    }

    suspend fun getChargeDetail(carId: Int, chargeId: Int): ApiResult<ChargeDetail> =
        executeWithFallback { api -> api.getChargeDetail(carId, chargeId).toResult("charge detail") { it?.data?.charge } }

    suspend fun getDrives(
        carId: Int,
        startDate: String? = null,
        endDate: String? = null,
        page: Int = 1,
        show: Int = 50000
    ): ApiResult<List<DriveData>> =
        executeWithFallback { api ->
            api.getDrives(carId, startDate, endDate, page = page, show = show)
                .toResult("drives") { it?.data?.drives ?: emptyList() }
        }

    suspend fun getDriveDetail(carId: Int, driveId: Int): ApiResult<DriveDetail> =
        executeWithFallback { api -> api.getDriveDetail(carId, driveId).toResult("drive detail") { it?.data?.drive } }

    suspend fun getBatteryHealth(carId: Int): ApiResult<BatteryHealth> =
        executeWithFallback { api -> api.getBatteryHealth(carId).toResult("battery health") { it?.data?.batteryHealth } }

    suspend fun getUpdates(carId: Int): ApiResult<List<UpdateData>> =
        executeWithFallback { api ->
            api.getUpdates(carId, page = 1, show = 50000).toResult("updates") { it?.data?.updates ?: emptyList() }
        }

    suspend fun getGlobalSettings(): ApiResult<GlobalSettingsData> =
        executeWithFallback { api -> api.getGlobalSettings().toResult("global settings") { it?.data } }
}
