package com.matedroid.ui.screens.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.local.dao.GeocodeCacheDao
import com.matedroid.data.local.dao.TripRouteCacheDao
import com.matedroid.data.local.entity.TripRouteCache
import com.matedroid.data.model.Currency
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.data.repository.countryCodeToFlag
import com.matedroid.domain.TripDetector
import com.matedroid.domain.model.Trip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject

data class TripRoutePoint(
    val latitude: Double,
    val longitude: Double
)

data class TripMapMarker(
    val latitude: Double,
    val longitude: Double,
    val type: TripMapPointType,
    val label: String,
    val chargeId: Int? = null
)

enum class TripMapPointType { START, CHARGE, END }

/** One drive leg's GPS points, kept separate for alternating colors on the map. */
data class TripRouteSegment(
    val points: List<TripRoutePoint>
)

data class TripCountry(
    val countryCode: String,
    val flagEmoji: String
)

data class TripDetailUiState(
    val isLoading: Boolean = true,
    val trip: Trip? = null,
    val routeSegments: List<TripRouteSegment> = emptyList(),
    val markers: List<TripMapMarker> = emptyList(),
    val isMapLoading: Boolean = true,
    val countries: List<TripCountry> = emptyList(),
    val units: Units? = null,
    val currencySymbol: String = "€"
)

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    private val driveSummaryDao: DriveSummaryDao,
    private val aggregateDao: AggregateDao,
    private val geocodeCacheDao: GeocodeCacheDao,
    private val tripRouteCacheDao: TripRouteCacheDao,
    private val tripDetector: TripDetector,
    private val repository: TeslamateRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripDetailUiState())
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()

    private var loaded = false

    fun loadTrip(carId: Int, tripStartDate: String) {
        if (loaded) return
        loaded = true

        viewModelScope.launch {
            launch {
                when (val result = repository.getCarStatus(carId)) {
                    is ApiResult.Success -> _uiState.update { it.copy(units = result.data.units) }
                    is ApiResult.Error -> {}
                }
            }
            launch {
                val settings = settingsDataStore.settings.first()
                val symbol = Currency.findByCode(settings.currencyCode).symbol
                _uiState.update { it.copy(currencySymbol = symbol) }
            }

            val drives = driveSummaryDao.getAllChronological(carId)
            val dcCharges = aggregateDao.getDcChargeSummaries(carId)
            val trips = tripDetector.detectTrips(drives, dcCharges)

            val trip = trips.find { it.startDate == tripStartDate }
            if (trip == null) {
                _uiState.update { it.copy(isLoading = false, isMapLoading = false) }
                return@launch
            }

            // Build markers from known coordinates
            val markers = mutableListOf<TripMapMarker>()
            trip.charges.forEach { charge ->
                markers.add(
                    TripMapMarker(
                        charge.latitude, charge.longitude,
                        TripMapPointType.CHARGE, charge.address,
                        chargeId = charge.chargeId
                    )
                )
            }

            // Resolve countries from drive edge coordinates (fast, Room-only)
            val countries = resolveCountriesFromDriveEdges(trip)

            // Show trip info + countries immediately, map loads in background
            _uiState.update {
                it.copy(isLoading = false, trip = trip, markers = markers, countries = countries)
            }

            // Fetch GPS positions for all drives in parallel (for the map)
            loadRoutePositions(carId, trip)
        }
    }

    /**
     * Resolve the trip's country sequence: [start, ...intermediates..., end].
     *
     * Collects all leg edge points (each drive's start + end, each charge's location),
     * resolves each to a country via geocode cache, then:
     *   - first leg start  → trip start country
     *   - last leg end     → trip end country
     *   - everything else  → deduplicated intermediates (excl start & end)
     *
     * Drive aggregates only store start coords; for end coords we use the
     * next charge's location as proxy, and fetch the last drive's end from
     * the API (1 call).
     */
    private suspend fun resolveCountriesFromDriveEdges(trip: Trip): List<TripCountry> {
        // 1. Collect all edge points in chronological order
        data class EdgePoint(val lat: Double, val lon: Double, val time: String)

        val points = mutableListOf<EdgePoint>()

        val driveCoords = aggregateDao.getDriveCoordinates(trip.drives.map { it.driveId })
            .associateBy { it.driveId }

        // Drive start points
        for (drive in trip.drives) {
            val coord = driveCoords[drive.driveId] ?: continue
            points.add(EdgePoint(coord.startLatitude, coord.startLongitude, drive.startDate))
        }

        // Charge locations (= drive end / next drive start proxy)
        for (charge in trip.charges) {
            points.add(EdgePoint(charge.latitude, charge.longitude, charge.startDate))
        }

        // Last drive's actual end point (1 API call)
        val lastDrive = trip.drives.lastOrNull()
        if (lastDrive != null) {
            when (val result = repository.getDriveDetail(lastDrive.carId, lastDrive.driveId)) {
                is ApiResult.Success -> {
                    result.data.positions
                        ?.lastOrNull { it.latitude != null && it.longitude != null }
                        ?.let { points.add(EdgePoint(it.latitude!!, it.longitude!!, lastDrive.endDate)) }
                }
                is ApiResult.Error -> {}
            }
        }

        points.sortBy { it.time }

        // 2. Resolve each point to a country code
        val codes = points.mapNotNull { pt ->
            val cached = geocodeCacheDao.get((pt.lat * 100).toInt(), (pt.lon * 100).toInt())
            cached?.countryCode
        }

        if (codes.isEmpty()) return emptyList()

        return buildCountrySequence(codes).map { TripCountry(it, countryCodeToFlag(it)) }
    }

    companion object {
        /**
         * Build [start, ...intermediates..., end] from an ordered list of country codes.
         * - First code = trip start
         * - Last code = trip end
         * - Everything in between: deduplicated, excluding start & end countries
         */
        internal fun buildCountrySequence(codes: List<String>): List<String> {
            if (codes.isEmpty()) return emptyList()
            if (codes.size == 1) return listOf(codes.first())

            val startCode = codes.first()
            val endCode = codes.last()

            val intermediates = codes
                .drop(1).dropLast(1)
                .filter { it != startCode && it != endCode }
                .distinct()

            val result = mutableListOf(startCode)
            result.addAll(intermediates)
            if (endCode != startCode || intermediates.isNotEmpty()) {
                result.add(endCode)
            }

            return result
        }
    }

    private fun loadRoutePositions(carId: Int, trip: Trip) {
        viewModelScope.launch {
            val tripKey = computeTripKey(trip)

            // Try cache first
            val cachedRows = tripRouteCacheDao.getSegments(tripKey)
            val segments = if (cachedRows.isNotEmpty()) {
                cachedRows.map { row -> deserializeSegment(row.segmentJson) }
            } else {
                val fetched = fetchRouteFromApi(carId, trip)
                if (fetched.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    tripRouteCacheDao.insertAll(
                        fetched.mapIndexed { index, segment ->
                            TripRouteCache(
                                tripKey = tripKey,
                                segmentIndex = index,
                                segmentJson = serializeSegment(segment),
                                createdAt = now
                            )
                        }
                    )
                }
                fetched
            }

            val allPoints = segments.flatMap { it.points }

            // Add start/end markers from actual GPS data
            val markers = _uiState.value.markers.toMutableList()
            if (allPoints.isNotEmpty()) {
                val first = allPoints.first()
                val last = allPoints.last()
                markers.add(
                    0,
                    TripMapMarker(
                        first.latitude, first.longitude,
                        TripMapPointType.START, trip.startAddress
                    )
                )
                markers.add(
                    TripMapMarker(
                        last.latitude, last.longitude,
                        TripMapPointType.END, trip.endAddress
                    )
                )
            }

            _uiState.update {
                it.copy(
                    routeSegments = segments,
                    markers = markers,
                    isMapLoading = false
                )
            }
        }
    }

    private suspend fun fetchRouteFromApi(carId: Int, trip: Trip): List<TripRouteSegment> {
        val deferreds = trip.drives.map { drive ->
            viewModelScope.async {
                when (val result = repository.getDriveDetail(carId, drive.driveId)) {
                    is ApiResult.Success -> {
                        result.data.positions
                            ?.filter { it.latitude != null && it.longitude != null }
                            ?.map { TripRoutePoint(it.latitude!!, it.longitude!!) }
                            ?: emptyList()
                    }
                    is ApiResult.Error -> emptyList()
                }
            }
        }
        return deferreds.awaitAll()
            .map { TripRouteSegment(it) }
            .filter { it.points.isNotEmpty() }
    }

    /** SHA-256 hash of sorted drive IDs, used as cache key. */
    private fun computeTripKey(trip: Trip): String {
        val ids = trip.drives.map { it.driveId }.sorted().joinToString(",")
        val digest = MessageDigest.getInstance("SHA-256").digest(ids.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun serializeSegment(segment: TripRouteSegment): String {
        val arr = JSONArray()
        for (pt in segment.points) {
            arr.put(JSONObject().put("lat", pt.latitude).put("lon", pt.longitude))
        }
        return arr.toString()
    }

    private fun deserializeSegment(json: String): TripRouteSegment {
        val arr = JSONArray(json)
        return TripRouteSegment(
            (0 until arr.length()).map { j ->
                val obj = arr.getJSONObject(j)
                TripRoutePoint(obj.getDouble("lat"), obj.getDouble("lon"))
            }
        )
    }
}
