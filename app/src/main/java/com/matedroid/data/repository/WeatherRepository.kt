package com.matedroid.data.repository

import android.util.Log
import com.matedroid.data.api.OpenMeteoApi
import com.matedroid.data.api.OpenMeteoHourly
import com.matedroid.data.api.models.DrivePosition
import com.matedroid.util.formatTime
import com.matedroid.util.parseIsoDateTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Represents a weather data point along a drive route.
 *
 * @property time The time of day as HH:mm string
 * @property distanceKm Distance from start of drive in kilometers
 * @property temperatureCelsius Temperature at this point in Celsius
 * @property weatherCode WMO weather interpretation code
 * @property weatherCondition Human-readable weather condition
 */
data class WeatherPoint(
    val time: String,
    val distanceKm: Double,
    val temperatureCelsius: Double,
    val weatherCode: Int,
    val weatherCondition: WeatherCondition
)

/**
 * Weather conditions derived from WMO weather codes.
 */
enum class WeatherCondition {
    CLEAR,           // Code 0
    PARTLY_CLOUDY,   // Codes 1, 2, 3
    FOG,             // Codes 45, 48
    DRIZZLE,         // Codes 51, 53, 55, 56, 57
    RAIN,            // Codes 61, 63, 65, 66, 67, 80, 81, 82
    SNOW,            // Codes 71, 73, 75, 77, 85, 86
    THUNDERSTORM;    // Codes 95, 96, 99

    companion object {
        fun fromWmoCode(code: Int): WeatherCondition = when (code) {
            0 -> CLEAR
            1, 2, 3 -> PARTLY_CLOUDY
            45, 48 -> FOG
            51, 53, 55, 56, 57 -> DRIZZLE
            61, 63, 65, 66, 67, 80, 81, 82 -> RAIN
            71, 73, 75, 77, 85, 86 -> SNOW
            95, 96, 99 -> THUNDERSTORM
            else -> PARTLY_CLOUDY // Default for unknown codes
        }
    }
}

/**
 * Repository for fetching weather data along a drive route.
 */
@Singleton
class WeatherRepository @Inject constructor(
    private val openMeteoApi: OpenMeteoApi
) {
    companion object {
        private const val TAG = "WeatherRepository"

        // Distance thresholds in kilometers for weather point selection
        private const val THRESHOLD_SINGLE_POINT = 10.0  // Under 10km: only end
        private const val THRESHOLD_TWO_POINTS = 30.0    // Under 30km: start and end
        private const val THRESHOLD_MEDIUM_DRIVE = 150.0 // Under 150km: every 25km
        private const val INTERVAL_MEDIUM = 25.0         // Interval for medium drives
        private const val INTERVAL_LONG = 35.0           // Interval for long drives (>150km)

        private const val DAY_CACHE_SIZE = 128

        /** Bounded, access-ordered LRU map (synchronized — callers hop threads). */
        private fun <K, V> lruCache(maxSize: Int): MutableMap<K, V> =
            Collections.synchronizedMap(object : LinkedHashMap<K, V>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > maxSize
            })
    }

    // Historical weather never changes, so cache each (place, day) response. One entry
    // holds all 24 hours of the day, so every same-day sample near the same spot shares
    // a single network call — and reopening a drive costs zero fetches.
    private val dayCache = lruCache<String, OpenMeteoHourly>(DAY_CACHE_SIZE)

    /** Cache key: coordinates rounded to ~1km grid plus the ISO date. */
    private fun dayCacheKey(lat: Double, lon: Double, dateStr: String): String =
        "%.2f,%.2f,%s".format(Locale.US, lat, lon, dateStr)

    /**
     * Returns the hourly weather series for [dateStr] at ([lat], [lon]), fetching from
     * Open-Meteo on cache miss. Returns null on API failure (failures are not cached).
     */
    private suspend fun getHourlyForDay(lat: Double, lon: Double, dateStr: String): OpenMeteoHourly? {
        val key = dayCacheKey(lat, lon, dateStr)
        dayCache[key]?.let { return it }

        val response = openMeteoApi.getHistoricalWeather(
            latitude = lat,
            longitude = lon,
            startDate = dateStr,
            endDate = dateStr
        )
        if (!response.isSuccessful) {
            Log.w(TAG, "Weather API returned ${response.code()}: ${response.message()}")
            return null
        }
        val hourly = response.body()?.hourly ?: return null
        dayCache[key] = hourly
        return hourly
    }

    /**
     * Temperature (°C, as returned by Open-Meteo) and WMO weather code at the hour of
     * [dateTime] for the given coordinates, or null if unavailable.
     */
    private suspend fun fetchHourlyWeather(
        lat: Double,
        lon: Double,
        dateTime: LocalDateTime
    ): Pair<Double, Int>? {
        val dateStr = dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val hourly = getHourlyForDay(lat, lon, dateStr) ?: return null

        val hour = dateTime.hour
        val timeIndex = hourly.time?.indexOfFirst { timeStr ->
            try {
                LocalDateTime.parse(timeStr).hour == hour
            } catch (_: Exception) {
                false
            }
        } ?: -1

        if (timeIndex < 0) {
            Log.w(TAG, "Could not find matching hour $hour in weather response")
            return null
        }

        val temperature = hourly.temperature2m?.getOrNull(timeIndex) ?: return null
        val weatherCode = hourly.weatherCode?.getOrNull(timeIndex) ?: 0
        return temperature to weatherCode
    }

    /**
     * Fetches weather data for a drive based on its positions.
     *
     * The number and spacing of weather points depends on total distance:
     * - Under 10km: only the end point
     * - Under 30km: start and end points
     * - Under 150km: weather point every 25km
     * - Over 150km: weather point every 35km
     *
     * @param positions List of drive positions with coordinates and timestamps
     * @param totalDistanceKm Total drive distance in kilometers
     * @return List of weather points along the route, or empty list on failure
     */
    suspend fun getWeatherAlongDrive(
        positions: List<DrivePosition>,
        totalDistanceKm: Double
    ): List<WeatherPoint> {
        if (positions.isEmpty()) return emptyList()

        // Filter positions with valid coordinates and timestamps
        val validPositions = positions.filter {
            it.latitude != null && it.longitude != null && it.date != null
        }

        if (validPositions.isEmpty()) return emptyList()

        // Calculate cumulative distances for each position
        val positionsWithDistance = calculateCumulativeDistances(validPositions)

        // Select positions for weather queries based on total distance
        val selectedPositions = selectWeatherPositions(
            positionsWithDistance,
            totalDistanceKm
        )

        if (selectedPositions.isEmpty()) return emptyList()

        // Fetch weather for all selected positions
        return fetchWeatherForPositions(selectedPositions)
    }

    /**
     * Calculates cumulative distance from start for each position.
     */
    private fun calculateCumulativeDistances(
        positions: List<DrivePosition>
    ): List<Pair<DrivePosition, Double>> {
        val result = mutableListOf<Pair<DrivePosition, Double>>()
        var cumulativeDistance = 0.0

        positions.forEachIndexed { index, position ->
            if (index > 0) {
                val prevPosition = positions[index - 1]
                val segmentDistance = haversineDistance(
                    prevPosition.latitude!!,
                    prevPosition.longitude!!,
                    position.latitude!!,
                    position.longitude!!
                )
                cumulativeDistance += segmentDistance
            }
            result.add(Pair(position, cumulativeDistance))
        }

        return result
    }

    /**
     * Selects positions for weather queries based on total drive distance.
     */
    private fun selectWeatherPositions(
        positionsWithDistance: List<Pair<DrivePosition, Double>>,
        totalDistanceKm: Double
    ): List<Pair<DrivePosition, Double>> {
        if (positionsWithDistance.isEmpty()) return emptyList()

        val first = positionsWithDistance.first()
        val last = positionsWithDistance.last()

        return when {
            // Under 10km: only the end
            totalDistanceKm < THRESHOLD_SINGLE_POINT -> {
                listOf(last)
            }

            // Under 30km: start and end
            totalDistanceKm < THRESHOLD_TWO_POINTS -> {
                listOf(first, last)
            }

            // Medium or long drive: select at intervals
            else -> {
                val interval = if (totalDistanceKm <= THRESHOLD_MEDIUM_DRIVE) {
                    INTERVAL_MEDIUM
                } else {
                    INTERVAL_LONG
                }

                selectAtIntervals(positionsWithDistance, interval, totalDistanceKm)
            }
        }
    }

    /**
     * Selects positions at regular distance intervals.
     * Always includes start and end positions.
     */
    private fun selectAtIntervals(
        positionsWithDistance: List<Pair<DrivePosition, Double>>,
        intervalKm: Double,
        totalDistanceKm: Double
    ): List<Pair<DrivePosition, Double>> {
        val selected = mutableListOf<Pair<DrivePosition, Double>>()

        // Always include start
        selected.add(positionsWithDistance.first())

        // Add intermediate points at intervals
        var nextTarget = intervalKm
        while (nextTarget < totalDistanceKm - intervalKm / 2) {
            // Find the position closest to the target distance
            val closest = positionsWithDistance.minByOrNull {
                kotlin.math.abs(it.second - nextTarget)
            }

            if (closest != null && closest != selected.lastOrNull()) {
                selected.add(closest)
            }

            nextTarget += intervalKm
        }

        // Always include end if not already added
        val last = positionsWithDistance.last()
        if (selected.lastOrNull() != last) {
            selected.add(last)
        }

        return selected
    }

    /**
     * Fetches weather data from Open-Meteo for the selected positions, in parallel.
     * Failed positions are skipped; the rest are returned in route order.
     */
    private suspend fun fetchWeatherForPositions(
        positions: List<Pair<DrivePosition, Double>>
    ): List<WeatherPoint> = coroutineScope {
        positions.map { (position, distanceKm) ->
            async {
                try {
                    fetchWeatherForPosition(position, distanceKm)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch weather for position at ${distanceKm}km", e)
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    /**
     * Fetches weather for a single position from Open-Meteo.
     */
    private suspend fun fetchWeatherForPosition(
        position: DrivePosition,
        distanceKm: Double
    ): WeatherPoint? {
        val dateTime = parseDateTime(position.date!!) ?: return null

        return try {
            val (temperature, weatherCode) = fetchHourlyWeather(
                lat = position.latitude!!,
                lon = position.longitude!!,
                dateTime = dateTime
            ) ?: return null

            WeatherPoint(
                time = dateTime.formatTime(Locale.getDefault()),
                distanceKm = distanceKm,
                temperatureCelsius = temperature,
                weatherCode = weatherCode,
                weatherCondition = WeatherCondition.fromWmoCode(weatherCode)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching weather from Open-Meteo", e)
            null
        }
    }

    /**
     * Parses a date string into LocalDateTime.
     * Supports both ISO 8601 with offset and without.
     */
    private fun parseDateTime(dateStr: String): LocalDateTime? =
        parseIsoDateTime(dateStr)

    /**
     * Calculates the distance between two points using the Haversine formula.
     *
     * @return Distance in kilometers
     */
    private fun haversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadiusKm = 6371.0

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadiusKm * c
    }

    // ========================================================================
    // Trip-level weather sampling (used by TripDetailScreen)
    // ========================================================================

    /**
     * Fetches weather samples along a trip's drive timeline.
     *
     * Sampling: ~1 per hour of drive time, clamped to [3, 12]. Stationary periods
     * (parking / charging) are skipped — we only sample actual driving.
     *
     * Timestamps for the route points are interpolated linearly between each drive's
     * startDate/endDate since the cached route points don't carry per-point times.
     *
     * @param drives The trip's drives (chronological order, same order as route segments)
     * @param routeSegments One segment per drive (may be empty on cache miss)
     * @return Samples with fetched weather, or empty list on total failure.
     */
    suspend fun getWeatherAlongTrip(
        drives: List<com.matedroid.data.local.entity.DriveSummary>,
        routeSegments: List<com.matedroid.ui.screens.trips.TripRouteSegment>
    ): List<TripWeatherPoint> {
        if (drives.isEmpty() || routeSegments.isEmpty()) return emptyList()

        // Build a (timestamp, lat, lon) list across all drive points, with timestamps
        // interpolated evenly between each drive's startDate/endDate.
        data class TimedPoint(val epochMillis: Long, val lat: Double, val lon: Double)
        val allPoints = mutableListOf<TimedPoint>()
        drives.forEachIndexed { driveIdx, drive ->
            val segment = routeSegments.getOrNull(driveIdx) ?: return@forEachIndexed
            val pts = segment.points
            if (pts.isEmpty()) return@forEachIndexed
            val ds = parseDateTime(drive.startDate)?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                ?: return@forEachIndexed
            val de = parseDateTime(drive.endDate)?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                ?: return@forEachIndexed
            val dur = (de - ds).coerceAtLeast(1L)
            val n = pts.size
            pts.forEachIndexed { i, pt ->
                val t = if (n > 1) ds + (dur.toDouble() * i / (n - 1)).toLong() else ds
                allPoints.add(TimedPoint(t, pt.latitude, pt.longitude))
            }
        }
        if (allPoints.isEmpty()) return emptyList()

        // Sample count: ~1 per hour of drive, clamped [3, 12]
        val totalDriveMin = drives.sumOf { it.durationMin }
        val hours = totalDriveMin / 60.0
        val sampleCount = hours.toInt().coerceIn(3, 12)

        // Evenly sample indices across allPoints
        val samples = (0 until sampleCount).map { i ->
            val denom = (sampleCount - 1).coerceAtLeast(1).toDouble()
            val idx = ((i / denom) * (allPoints.size - 1)).toInt().coerceIn(0, allPoints.size - 1)
            allPoints[idx]
        }

        // Fetch weather for each sample in parallel
        return coroutineScope {
            val deferreds = samples.map { sample ->
                async { fetchTripWeatherAtSample(sample.epochMillis, sample.lat, sample.lon) }
            }
            deferreds.awaitAll().filterNotNull()
        }
    }

    private suspend fun fetchTripWeatherAtSample(
        epochMillis: Long,
        lat: Double,
        lon: Double
    ): TripWeatherPoint? {
        val dt = java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
        return try {
            val (temp, code) = fetchHourlyWeather(lat, lon, dt) ?: return null
            TripWeatherPoint(
                timestamp = dt,
                latitude = lat,
                longitude = lon,
                temperatureCelsius = temp,
                weatherCode = code,
                weatherCondition = WeatherCondition.fromWmoCode(code)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Trip weather fetch failed at $lat,$lon", e)
            null
        }
    }
}

/**
 * A weather sample along a trip: when, where, temperature, and condition.
 */
@androidx.compose.runtime.Immutable
data class TripWeatherPoint(
    val timestamp: LocalDateTime,
    val latitude: Double,
    val longitude: Double,
    val temperatureCelsius: Double,
    val weatherCode: Int,
    val weatherCondition: WeatherCondition
)
