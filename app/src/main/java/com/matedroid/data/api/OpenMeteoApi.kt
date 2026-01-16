package com.matedroid.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo Historical Weather API response models.
 *
 * WMO Weather interpretation codes (WW):
 * - 0: Clear sky
 * - 1, 2, 3: Mainly clear, partly cloudy, and overcast
 * - 45, 48: Fog and depositing rime fog
 * - 51, 53, 55: Drizzle: Light, moderate, and dense intensity
 * - 56, 57: Freezing Drizzle: Light and dense intensity
 * - 61, 63, 65: Rain: Slight, moderate and heavy intensity
 * - 66, 67: Freezing Rain: Light and heavy intensity
 * - 71, 73, 75: Snow fall: Slight, moderate, and heavy intensity
 * - 77: Snow grains
 * - 80, 81, 82: Rain showers: Slight, moderate, and violent
 * - 85, 86: Snow showers slight and heavy
 * - 95: Thunderstorm: Slight or moderate
 * - 96, 99: Thunderstorm with slight and heavy hail
 */
@JsonClass(generateAdapter = true)
data class OpenMeteoResponse(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val elevation: Double? = null,
    @Json(name = "generationtime_ms") val generationTimeMs: Double? = null,
    @Json(name = "utc_offset_seconds") val utcOffsetSeconds: Int? = null,
    val timezone: String? = null,
    @Json(name = "timezone_abbreviation") val timezoneAbbreviation: String? = null,
    val hourly: OpenMeteoHourly? = null,
    @Json(name = "hourly_units") val hourlyUnits: OpenMeteoHourlyUnits? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoHourly(
    val time: List<String>? = null,
    @Json(name = "temperature_2m") val temperature2m: List<Double?>? = null,
    @Json(name = "weather_code") val weatherCode: List<Int?>? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoHourlyUnits(
    val time: String? = null,
    @Json(name = "temperature_2m") val temperature2m: String? = null,
    @Json(name = "weather_code") val weatherCode: String? = null
)

/**
 * Open-Meteo Historical Weather API interface.
 * Documentation: https://open-meteo.com/en/docs/historical-weather-api
 *
 * The Archive API provides historical weather data for any location worldwide,
 * with data available from 1940 to present (with 2-5 day delay).
 */
interface OpenMeteoApi {

    /**
     * Fetches historical weather data for a specific location and time range.
     *
     * @param latitude WGS84 latitude of the location
     * @param longitude WGS84 longitude of the location
     * @param startDate Start date in ISO8601 format (yyyy-MM-dd)
     * @param endDate End date in ISO8601 format (yyyy-MM-dd)
     * @param hourly Comma-separated list of hourly weather variables (e.g., "temperature_2m,weather_code")
     * @param timezone Timezone for the response (default: "auto" uses location timezone)
     * @return Historical weather data response
     */
    @GET("v1/archive")
    suspend fun getHistoricalWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("hourly") hourly: String = "temperature_2m,weather_code",
        @Query("timezone") timezone: String = "auto"
    ): Response<OpenMeteoResponse>
}
