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
import kotlinx.coroutines.flow.first
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

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

    private suspend fun getSettings(): AppSettings = settingsDataStore.settings.first()

    private fun getApiForUrl(url: String): TeslamateApi? {
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

    suspend fun testConnection(serverUrl: String, acceptInvalidCerts: Boolean = false): ApiResult<Unit> {
        return try {
            val api = apiFactory.create(serverUrl, acceptInvalidCerts)
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

    suspend fun getCars(): ApiResult<List<CarData>> {
        return executeWithFallback { api ->
            try {
                val response = api.getCars()
                if (response.isSuccessful) {
                    val cars = response.body()?.data?.cars ?: emptyList()
                    ApiResult.Success(cars)
                } else {
                    ApiResult.Error("Failed to fetch cars: ${response.code()}", response.code())
                }
            } catch (e: Exception) {
                throw e // Let executeWithFallback handle it
            }
        }
    }

    suspend fun getCarStatus(carId: Int): ApiResult<CarStatusWithUnits> {
        return executeWithFallback { api ->
            try {
                val response = api.getCarStatus(carId)
                if (response.isSuccessful) {
                    val data = response.body()?.data
                    val status = data?.status
                    val units = data?.units ?: Units()
                    if (status != null) {
                        ApiResult.Success(CarStatusWithUnits(status, units))
                    } else {
                        ApiResult.Error("No status data returned")
                    }
                } else {
                    ApiResult.Error("Failed to fetch status: ${response.code()}", response.code())
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    suspend fun getCharges(
        carId: Int,
        startDate: String? = null,
        endDate: String? = null
    ): ApiResult<List<ChargeData>> {
        return executeWithFallback { api ->
            try {
                val response = api.getCharges(carId, startDate, endDate, page = 1, show = 50000)
                if (response.isSuccessful) {
                    val charges = response.body()?.data?.charges ?: emptyList()
                    ApiResult.Success(charges)
                } else {
                    ApiResult.Error("Failed to fetch charges: ${response.code()}", response.code())
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    suspend fun getChargeDetail(carId: Int, chargeId: Int): ApiResult<ChargeDetail> {
        return executeWithFallback { api ->
            try {
                val response = api.getChargeDetail(carId, chargeId)
                if (response.isSuccessful) {
                    val detail = response.body()?.data?.charge
                    if (detail != null) {
                        ApiResult.Success(detail)
                    } else {
                        ApiResult.Error("No charge detail returned")
                    }
                } else {
                    ApiResult.Error("Failed to fetch charge detail: ${response.code()}", response.code())
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    suspend fun getDrives(
        carId: Int,
        startDate: String? = null,
        endDate: String? = null
    ): ApiResult<List<DriveData>> {
        return executeWithFallback { api ->
            try {
                val response = api.getDrives(carId, startDate, endDate, page = 1, show = 50000)
                if (response.isSuccessful) {
                    val drives = response.body()?.data?.drives ?: emptyList()
                    ApiResult.Success(drives)
                } else {
                    ApiResult.Error("Failed to fetch drives: ${response.code()}", response.code())
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    suspend fun getDriveDetail(carId: Int, driveId: Int): ApiResult<DriveDetail> {
        return executeWithFallback { api ->
            try {
                val response = api.getDriveDetail(carId, driveId)
                if (response.isSuccessful) {
                    val detail = response.body()?.data?.drive
                    if (detail != null) {
                        ApiResult.Success(detail)
                    } else {
                        ApiResult.Error("No drive detail returned")
                    }
                } else {
                    ApiResult.Error("Failed to fetch drive detail: ${response.code()}", response.code())
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    suspend fun getBatteryHealth(carId: Int): ApiResult<BatteryHealth> {
        return executeWithFallback { api ->
            try {
                val response = api.getBatteryHealth(carId)
                if (response.isSuccessful) {
                    val health = response.body()?.data?.batteryHealth
                    if (health != null) {
                        ApiResult.Success(health)
                    } else {
                        ApiResult.Error("No battery health data returned")
                    }
                } else {
                    ApiResult.Error("Failed to fetch battery health: ${response.code()}", response.code())
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    suspend fun getUpdates(carId: Int): ApiResult<List<UpdateData>> {
        return executeWithFallback { api ->
            try {
                val response = api.getUpdates(carId, page = 1, show = 50000)
                if (response.isSuccessful) {
                    val updates = response.body()?.data?.updates ?: emptyList()
                    ApiResult.Success(updates)
                } else {
                    ApiResult.Error("Failed to fetch updates: ${response.code()}", response.code())
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    suspend fun getGlobalSettings(): ApiResult<GlobalSettingsData> {
        return executeWithFallback { api ->
            try {
                val response = api.getGlobalSettings()
                if (response.isSuccessful) {
                    val data = response.body()?.data
                    if (data != null) {
                        ApiResult.Success(data)
                    } else {
                        ApiResult.Error("No global settings data returned")
                    }
                } else {
                    ApiResult.Error("Failed to fetch global settings: ${response.code()}", response.code())
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }
}
