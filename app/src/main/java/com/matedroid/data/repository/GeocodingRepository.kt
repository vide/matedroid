package com.matedroid.data.repository

import android.util.Log
import com.matedroid.data.api.NominatimApi
import com.matedroid.data.api.NominatimAddress
import com.matedroid.data.local.dao.GeocodeCacheDao
import com.matedroid.data.local.dao.GeocodeProgressDao
import com.matedroid.data.local.dao.GeocodeQueueDao
import com.matedroid.data.local.entity.GeocodeCache
import com.matedroid.data.local.entity.GeocodeProgress
import com.matedroid.data.local.entity.GeocodeQueueItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of reverse geocoding with full location details.
 */
data class GeocodedLocation(
    val address: String?,
    val countryCode: String?,
    val countryName: String?,
    val regionName: String?,
    val city: String?
)

/**
 * Progress info for geocoding UI display.
 */
data class GeocodeProgressInfo(
    val processed: Int,
    val total: Int,
    val percentage: Float
)

/**
 * Country boundary as a list of polygon rings.
 * Each ring is a list of [latitude, longitude] pairs.
 * For countries with multiple polygons (islands), this contains all of them.
 */
data class CountryBoundary(
    val countryCode: String,
    val polygons: List<List<Pair<Double, Double>>>  // List of polygons, each polygon is list of lat/lon pairs
)

@Singleton
class GeocodingRepository @Inject constructor(
    private val nominatimApi: NominatimApi,
    private val geocodeCacheDao: GeocodeCacheDao,
    private val geocodeQueueDao: GeocodeQueueDao,
    private val geocodeProgressDao: GeocodeProgressDao
) {
    companion object {
        // Grid precision: 0.01° ≈ 1.1km at equator
        private const val GRID_PRECISION = 100
    }

    // Legacy in-memory caches (kept for backward compatibility with reverseGeocode)
    private val addressCache = mutableMapOf<String, String>()
    private val locationCache = mutableMapOf<String, GeocodedLocation>()

    /**
     * Convert coordinate to grid cell.
     */
    fun toGridCoord(coord: Double): Int = (coord * GRID_PRECISION).toInt()

    /**
     * Get cached location data for a grid cell.
     * Returns null if not cached.
     */
    suspend fun getFromCache(lat: Double, lon: Double): GeocodeCache? {
        val gridLat = toGridCoord(lat)
        val gridLon = toGridCoord(lon)
        return geocodeCacheDao.get(gridLat, gridLon)
    }

    /**
     * Get cached location data by grid coordinates directly.
     * Returns null if not cached.
     */
    suspend fun getFromCacheByGrid(gridLat: Int, gridLon: Int): GeocodeCache? {
        return geocodeCacheDao.get(gridLat, gridLon)
    }

    /**
     * Get the next batch of items to geocode.
     */
    suspend fun getNextBatch(limit: Int = 1): List<GeocodeQueueItem> {
        return geocodeQueueDao.getNextBatch(limit)
    }

    /**
     * Enqueue multiple locations for background geocoding.
     * Filters out already cached locations and deduplicates by grid cell.
     */
    suspend fun enqueueLocationsForCar(
        carId: Int,
        locations: List<Pair<Double, Double>>
    ): Int {
        val items = locations.map { (lat, lon) ->
            val gridLat = toGridCoord(lat)
            val gridLon = toGridCoord(lon)
            GeocodeQueueItem(
                gridLat = gridLat,
                gridLon = gridLon,
                carId = carId,
                latitude = lat,
                longitude = lon,
                addedAt = System.currentTimeMillis()
            )
        }

        // Deduplicate by grid cell (same location = same grid)
        val uniqueItems = items.distinctBy { it.gridLat to it.gridLon }

        // Filter out already cached locations
        val uncachedItems = uniqueItems.filter { item ->
            geocodeCacheDao.get(item.gridLat, item.gridLon) == null
        }

        if (uncachedItems.isNotEmpty()) {
            geocodeQueueDao.enqueueAll(uncachedItems)

            // Update progress tracking with actual unique count
            val progress = geocodeProgressDao.get(carId)
            if (progress == null) {
                geocodeProgressDao.upsert(
                    GeocodeProgress(
                        carId = carId,
                        totalLocations = uncachedItems.size,
                        processedLocations = 0,
                        lastUpdatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                geocodeProgressDao.incrementTotal(carId, uncachedItems.size, System.currentTimeMillis())
            }
        }

        return uncachedItems.size
    }

    /**
     * Perform actual geocoding API call and cache result.
     * Called by background worker only.
     */
    suspend fun geocodeAndCache(item: GeocodeQueueItem): GeocodeCache? {
        return try {
            val response = nominatimApi.reverseGeocode(item.latitude, item.longitude)
            if (!response.isSuccessful) {
                geocodeQueueDao.markAttempt(item.gridLat, item.gridLon, System.currentTimeMillis())
                return null
            }

            val result = response.body()
            val address = result?.address

            val cache = GeocodeCache(
                gridLat = item.gridLat,
                gridLon = item.gridLon,
                countryCode = address?.countryCode?.uppercase(),
                countryName = address?.country,
                regionName = address?.state,
                city = address?.city
                    ?: address?.town
                    ?: address?.village
                    ?: address?.municipality,
                cachedAt = System.currentTimeMillis()
            )

            geocodeCacheDao.upsert(cache)
            geocodeQueueDao.remove(item.gridLat, item.gridLon)

            cache
        } catch (e: Exception) {
            geocodeQueueDao.markAttempt(item.gridLat, item.gridLon, System.currentTimeMillis())
            null
        }
    }

    /**
     * Mark a location as successfully geocoded (for progress tracking).
     */
    suspend fun markGeocoded(carId: Int) {
        geocodeProgressDao.incrementProcessed(carId, System.currentTimeMillis())
    }

    /**
     * Get count of pending geocode requests.
     */
    suspend fun getPendingCount(): Int = geocodeQueueDao.countPending()

    /**
     * Get count of total items in queue (including failed).
     */
    suspend fun getTotalQueueCount(): Int = geocodeQueueDao.countTotal()

    /**
     * Get count of failed items (attempts >= 3).
     */
    suspend fun getFailedCount(): Int = geocodeQueueDao.countFailed()

    /**
     * Reset all failed items to retry them.
     */
    suspend fun resetFailedItems() = geocodeQueueDao.resetFailed()

    /**
     * Get count of cached geocoded locations.
     */
    suspend fun getCachedCount(): Int = geocodeCacheDao.count()

    /**
     * Sync progress with actual cache count.
     * Called when queue is empty but progress shows incomplete work.
     * This fixes stale progress data from interrupted/cleared geocoding.
     */
    suspend fun syncProgressWithCache(cachedCount: Int) {
        // Update all car progress records to match reality
        // When queue is empty and we have cached items, those are the completed ones
        geocodeProgressDao.syncWithCache(cachedCount)
    }

    /**
     * Observe geocoding progress for a car.
     */
    fun observeGeocodeProgress(carId: Int): Flow<GeocodeProgressInfo?> {
        return geocodeProgressDao.observe(carId).map { progress ->
            if (progress == null || progress.totalLocations == 0) {
                null
            } else {
                GeocodeProgressInfo(
                    processed = progress.processedLocations,
                    total = progress.totalLocations,
                    percentage = progress.processedLocations.toFloat() / progress.totalLocations
                )
            }
        }
    }

    /**
     * Reset progress tracking for a car (for full resync).
     */
    suspend fun resetProgress(carId: Int) {
        geocodeProgressDao.reset(carId)
        geocodeQueueDao.clearForCar(carId)
    }

    // === Legacy methods for backward compatibility ===

    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? {
        // Round coordinates to 4 decimal places for caching (~11m accuracy)
        val cacheKey = "%.4f,%.4f".format(latitude, longitude)

        // Return cached result if available
        addressCache[cacheKey]?.let { return it }

        return try {
            val response = nominatimApi.reverseGeocode(latitude, longitude)
            if (response.isSuccessful) {
                val result = response.body()
                val address = formatAddress(result?.address)
                    ?: result?.displayName?.split(",")?.take(3)?.joinToString(", ")

                address?.also { addressCache[cacheKey] = it }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reverse geocode with full location details including country.
     * Used for extracting country information from drive positions.
     */
    suspend fun reverseGeocodeWithCountry(latitude: Double, longitude: Double): GeocodedLocation? {
        // Round coordinates to 4 decimal places for caching (~11m accuracy)
        val cacheKey = "%.4f,%.4f".format(latitude, longitude)

        // Return cached result if available
        locationCache[cacheKey]?.let { return it }

        return try {
            val response = nominatimApi.reverseGeocode(latitude, longitude)
            if (response.isSuccessful) {
                val result = response.body()
                val address = result?.address
                val location = GeocodedLocation(
                    address = formatAddress(address)
                        ?: result?.displayName?.split(",")?.take(3)?.joinToString(", "),
                    countryCode = address?.countryCode?.uppercase(),
                    countryName = address?.country,
                    regionName = address?.state,
                    city = address?.city
                        ?: address?.town
                        ?: address?.village
                        ?: address?.municipality
                )
                locationCache[cacheKey] = location
                location
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun formatAddress(address: NominatimAddress?): String? {
        if (address == null) return null

        val parts = mutableListOf<String>()

        // Street with house number
        val street = listOfNotNull(address.road, address.house_number).joinToString(" ")
        if (street.isNotBlank()) parts.add(street)

        // City/town/village
        val city = address.city ?: address.town ?: address.village ?: address.municipality
        if (city != null) parts.add(city)

        return if (parts.isNotEmpty()) parts.joinToString(", ") else null
    }

    // === Country Boundary Methods ===

    // Cache for country boundaries (in-memory, cleared on app restart)
    private val boundaryCache = mutableMapOf<String, CountryBoundary>()

    /**
     * Fetch country boundary polygon from Nominatim.
     * Returns null if the boundary cannot be fetched.
     * Results are cached in memory.
     */
    suspend fun getCountryBoundary(countryCode: String): CountryBoundary? {
        Log.d("GeocodingRepository", "getCountryBoundary called for $countryCode")

        // Check cache first
        boundaryCache[countryCode]?.let {
            Log.d("GeocodingRepository", "Returning cached boundary for $countryCode with ${it.polygons.size} polygons")
            return it
        }

        return try {
            Log.d("GeocodingRepository", "Fetching boundary from API for $countryCode")
            val response = nominatimApi.searchCountryBoundary(countryCode)
            Log.d("GeocodingRepository", "API response: success=${response.isSuccessful}, code=${response.code()}")

            if (!response.isSuccessful || response.body().isNullOrEmpty()) {
                Log.w("GeocodingRepository", "Failed to fetch boundary for $countryCode: ${response.errorBody()?.string()}")
                return null
            }

            val result = response.body()?.firstOrNull()
            if (result == null) {
                Log.w("GeocodingRepository", "No results in response for $countryCode")
                return null
            }

            Log.d("GeocodingRepository", "Got result: displayName=${result.displayName}, hasGeoJson=${result.geojson != null}")

            val geoJson = result.geojson
            if (geoJson == null) {
                Log.w("GeocodingRepository", "No geojson in result for $countryCode")
                return null
            }

            Log.d("GeocodingRepository", "GeoJSON type: ${geoJson.type}, coordinates class: ${geoJson.coordinates?.javaClass?.name}")

            val polygons = parseGeoJsonToPolygons(geoJson.type, geoJson.coordinates)
            Log.d("GeocodingRepository", "Parsed ${polygons.size} polygons for $countryCode")

            if (polygons.isEmpty()) {
                Log.w("GeocodingRepository", "No polygons parsed for $countryCode")
                return null
            }

            val boundary = CountryBoundary(countryCode, polygons)
            boundaryCache[countryCode] = boundary
            Log.d("GeocodingRepository", "Successfully cached boundary for $countryCode")
            boundary
        } catch (e: Exception) {
            Log.e("GeocodingRepository", "Error fetching boundary for $countryCode", e)
            null
        }
    }

    /**
     * Parse GeoJSON coordinates to list of polygon rings.
     * Handles both Polygon and MultiPolygon types.
     * GeoJSON uses [longitude, latitude] order; we convert to [latitude, longitude].
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseGeoJsonToPolygons(type: String, coordinates: Any?): List<List<Pair<Double, Double>>> {
        if (coordinates == null) return emptyList()

        return try {
            when (type) {
                "Polygon" -> {
                    // Polygon: [[[lon, lat], [lon, lat], ...]]
                    // First ring is the outer boundary, others are holes (we only need the outer)
                    val rings = coordinates as? List<*> ?: return emptyList()
                    val outerRing = rings.firstOrNull() as? List<*> ?: return emptyList()
                    val points = parseRing(outerRing)
                    if (points.isNotEmpty()) listOf(points) else emptyList()
                }
                "MultiPolygon" -> {
                    // MultiPolygon: [[[[lon, lat], ...]], [[[lon, lat], ...]]]
                    val polygonsList = coordinates as? List<*> ?: return emptyList()
                    polygonsList.mapNotNull { polygon ->
                        val rings = polygon as? List<*> ?: return@mapNotNull null
                        val outerRing = rings.firstOrNull() as? List<*> ?: return@mapNotNull null
                        val points = parseRing(outerRing)
                        points.takeIf { it.isNotEmpty() }
                    }
                }
                else -> {
                    Log.w("GeocodingRepository", "Unsupported GeoJSON type: $type")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("GeocodingRepository", "Error parsing GeoJSON", e)
            emptyList()
        }
    }

    /**
     * Parse a single ring of coordinates.
     * Input: [[lon, lat], [lon, lat], ...]
     * Output: [(lat, lon), (lat, lon), ...]
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseRing(ring: List<*>): List<Pair<Double, Double>> {
        return ring.mapNotNull { point ->
            val coords = point as? List<*> ?: return@mapNotNull null
            if (coords.size < 2) return@mapNotNull null
            val lon = (coords[0] as? Number)?.toDouble() ?: return@mapNotNull null
            val lat = (coords[1] as? Number)?.toDouble() ?: return@mapNotNull null
            // Convert from GeoJSON [lon, lat] to our [lat, lon] convention
            lat to lon
        }
    }
}
