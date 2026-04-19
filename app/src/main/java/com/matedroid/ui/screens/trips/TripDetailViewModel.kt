package com.matedroid.ui.screens.trips

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.GeocodeCacheDao
import com.matedroid.data.local.dao.TripCountryCacheDao
import com.matedroid.data.local.dao.TripRouteCacheDao
import com.matedroid.data.local.entity.TripCountryCache
import com.matedroid.data.local.entity.TripRouteCache
import com.matedroid.data.model.Currency
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.data.repository.countryCodeToFlag
import com.matedroid.domain.EligibleLegs
import com.matedroid.domain.LegRef
import com.matedroid.domain.RouteSimplifier
import com.matedroid.domain.TripRepository
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
import java.nio.ByteBuffer
import javax.inject.Inject

@Immutable
data class TripRoutePoint(
    val latitude: Double,
    val longitude: Double
)

@Immutable
data class TripMapMarker(
    val latitude: Double,
    val longitude: Double,
    val type: TripMapPointType,
    val label: String,
    val chargeId: Int? = null
)

enum class TripMapPointType { START, CHARGE, END }

/** One drive leg's GPS points, kept separate for alternating colors on the map. */
@Immutable
data class TripRouteSegment(
    val points: List<TripRoutePoint>
)

@Immutable
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
    val currencySymbol: String = "€",

    // Edit / merge / delete (PR 2)
    val savedTripId: Long? = null,
    val savedTripName: String? = null,
    val dcChargeIds: Set<Int> = emptySet(),
    val showAddLegSheet: Boolean = false,
    val showMergeSheet: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val showRenameDialog: Boolean = false,
    val renameDraft: String = "",
    val eligibleLegs: EligibleLegs? = null,
    val adjacentTrips: List<Pair<Long, Trip>> = emptyList(),
    val pendingMergeTarget: Pair<Long, Trip>? = null,
    val justDeleted: Boolean = false
)

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    private val aggregateDao: AggregateDao,
    private val geocodeCacheDao: GeocodeCacheDao,
    private val tripRouteCacheDao: TripRouteCacheDao,
    private val tripCountryCacheDao: TripCountryCacheDao,
    private val tripRepository: TripRepository,
    private val tripCache: TripCache,
    private val repository: TeslamateRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripDetailUiState())
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()

    private var loaded = false
    private var currentCarId: Int = -1
    private var currentStartDate: String = ""
    private var hasBeenPaused = false

    fun loadTrip(carId: Int, tripStartDate: String) {
        if (loaded) return
        loaded = true
        currentCarId = carId
        currentStartDate = tripStartDate

        viewModelScope.launch {
            // Skip these on revalidation — units and currency don't change between same-session
            // navigations, and the network call was the slowest step of the reload.
            val isFirstLoad = _uiState.value.units == null
            if (isFirstLoad) {
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
            }

            // Fast path: use cached trip from list screen (avoids a repository round trip)
            val trip = tripCache.take(tripStartDate)
                ?: tripRepository.getTrips(carId).find { it.startDate == tripStartDate }
            if (trip == null) {
                _uiState.update { it.copy(isLoading = false, isMapLoading = false) }
                return@launch
            }

            // Lookup savedTripId (for edit/merge/delete actions) and DC charge id set (for AC/DC visuals)
            val savedId = tripRepository.findSavedTripId(carId, trip)
            val savedName = savedId?.let { tripRepository.getTripName(it) }
            val dcIds = try {
                aggregateDao.getDcChargeIds(carId).toSet()
            } catch (e: Exception) { emptySet() }

            // Placeholder charge markers — only on first load. On revalidation, keep the existing
            // markers (which already include start+end) so the map doesn't flash a shorter set.
            val existingMarkers = _uiState.value.markers
            val initialMarkers = if (existingMarkers.isEmpty()) {
                trip.charges.map { charge ->
                    TripMapMarker(
                        charge.latitude, charge.longitude,
                        TripMapPointType.CHARGE, charge.address,
                        chargeId = charge.chargeId
                    )
                }
            } else existingMarkers

            // Show trip info immediately — countries and map load in background
            _uiState.update {
                it.copy(
                    isLoading = false,
                    trip = trip,
                    markers = initialMarkers,
                    savedTripId = savedId,
                    savedTripName = savedName,
                    dcChargeIds = dcIds
                )
            }

            // Resolve countries — try cache first, resolve in background if miss
            launch {
                val tripKey = computeTripKey(trip)
                val cached = tripCountryCacheDao.get(tripKey)
                val countries = if (cached != null) {
                    cached.countries.split(",")
                        .map { TripCountry(it, countryCodeToFlag(it)) }
                } else {
                    val resolved = resolveCountriesFromDriveEdges(trip)
                    if (resolved.isNotEmpty()) {
                        tripCountryCacheDao.insert(
                            TripCountryCache(
                                tripKey = tripKey,
                                countries = resolved.joinToString(",") { it.countryCode },
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                    resolved
                }
                _uiState.update { it.copy(countries = countries) }
            }

            // Fetch GPS positions for all drives in parallel (for the map)
            loadRoutePositions(carId, trip)
        }
    }

    // === Edit / merge / delete actions (PR 2) ===

    fun openAddLegSheet() {
        val tripId = _uiState.value.savedTripId ?: return
        viewModelScope.launch {
            val eligible = tripRepository.getEligibleNewLegs(tripId, currentCarId)
            _uiState.update { it.copy(showAddLegSheet = true, eligibleLegs = eligible) }
        }
    }

    fun closeAddLegSheet() {
        _uiState.update { it.copy(showAddLegSheet = false, eligibleLegs = null) }
    }

    fun pickLegs(refs: List<LegRef>) {
        if (refs.isEmpty()) return
        val tripId = _uiState.value.savedTripId ?: return
        viewModelScope.launch {
            tripRepository.extendTripWithLegs(tripId, refs)
            _uiState.update { it.copy(showAddLegSheet = false, eligibleLegs = null) }
            reloadTrip()
        }
    }

    fun openMergeSheet() {
        val tripId = _uiState.value.savedTripId ?: return
        viewModelScope.launch {
            val adjacent = tripRepository.getAdjacentTrips(tripId, currentCarId)
            _uiState.update { it.copy(showMergeSheet = true, adjacentTrips = adjacent) }
        }
    }

    fun closeMergeSheet() {
        _uiState.update { it.copy(showMergeSheet = false, adjacentTrips = emptyList()) }
    }

    fun pickMergeTarget(otherId: Long, otherTrip: Trip) {
        _uiState.update {
            it.copy(showMergeSheet = false, pendingMergeTarget = otherId to otherTrip)
        }
    }

    fun cancelMergeTarget() {
        _uiState.update { it.copy(pendingMergeTarget = null) }
    }

    fun confirmMergeTarget() {
        val keptId = _uiState.value.savedTripId ?: return
        val (otherId, _) = _uiState.value.pendingMergeTarget ?: return
        viewModelScope.launch {
            val newId = tripRepository.mergeTrips(keptId, otherId, currentCarId) ?: return@launch
            // The merged trip's startDate equals the earliest source's startDate.
            // Re-derive currentStartDate so reloadTrip finds the merged trip.
            val all = tripRepository.getTrips(currentCarId)
            val merged = all.find { trip ->
                tripRepository.findSavedTripId(currentCarId, trip) == newId
            } ?: return@launch
            currentStartDate = merged.startDate
            _uiState.update { it.copy(pendingMergeTarget = null, adjacentTrips = emptyList()) }
            reloadTrip()
        }
    }

    fun openRenameDialog() {
        _uiState.update {
            it.copy(
                showRenameDialog = true,
                renameDraft = it.savedTripName.orEmpty()
            )
        }
    }

    fun closeRenameDialog() {
        _uiState.update { it.copy(showRenameDialog = false, renameDraft = "") }
    }

    fun updateRenameDraft(draft: String) {
        _uiState.update { it.copy(renameDraft = draft) }
    }

    fun confirmRename() {
        val tripId = _uiState.value.savedTripId ?: return
        val draft = _uiState.value.renameDraft
        viewModelScope.launch {
            tripRepository.renameTrip(tripId, draft)
            val updated = tripRepository.getTripName(tripId)
            _uiState.update { it.copy(showRenameDialog = false, savedTripName = updated, renameDraft = "") }
        }
    }

    fun openDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun closeDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun confirmDelete() {
        val tripId = _uiState.value.savedTripId ?: return
        viewModelScope.launch {
            tripRepository.deleteTrip(tripId)
            _uiState.update { it.copy(showDeleteConfirm = false, justDeleted = true) }
        }
    }

    /** Called by the screen on ON_PAUSE so we know the user navigated away. */
    fun onScreenPaused() {
        hasBeenPaused = true
    }

    /**
     * Called by the screen on ON_RESUME. Reloads the trip if the user is returning from a child
     * screen (e.g. a drive/charge detail) where they might have changed the trip's composition.
     *
     * The reload is delayed a short while so the fresh composition that follows back-navigation
     * can finish its first frame (heavy children like the map/timeline/legs/donut). Without this
     * delay the reload's state emissions pile onto the same main-thread budget, which shows up
     * as "scroll is blocked for a while".
     */
    fun onScreenResumed() {
        if (loaded && hasBeenPaused) {
            hasBeenPaused = false
            viewModelScope.launch {
                kotlinx.coroutines.delay(400)
                reloadTrip()
            }
        }
    }

    /**
     * Revalidate the trip in the background. We deliberately do NOT clear existing state — the
     * user keeps seeing the current trip while fresh data is fetched. StateFlow dedupes equal
     * values, so if nothing actually changed in the DB, no emission happens and nothing in the
     * UI recomposes. Only genuine changes (leg added/removed/renamed) cause updates.
     */
    private fun reloadTrip() {
        loaded = false
        loadTrip(currentCarId, currentStartDate)
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
     * Drive aggregates store start + end coords (V9+). For aggregates computed
     * before V9, the last drive's end is fetched from the API as fallback (1 call).
     */
    private suspend fun resolveCountriesFromDriveEdges(trip: Trip): List<TripCountry> {
        // 1. Collect all edge points in chronological order
        data class EdgePoint(val lat: Double, val lon: Double, val time: String)

        val points = mutableListOf<EdgePoint>()

        val driveCoords = aggregateDao.getDriveEdgeCoordinates(trip.drives.map { it.driveId })
            .associateBy { it.driveId }

        // Drive start + end points from cached coordinates
        for (drive in trip.drives) {
            val coord = driveCoords[drive.driveId] ?: continue
            points.add(EdgePoint(coord.startLatitude, coord.startLongitude, drive.startDate))
            if (coord.endLatitude != null && coord.endLongitude != null) {
                points.add(EdgePoint(coord.endLatitude, coord.endLongitude, drive.endDate))
            }
        }

        // Fallback: if the last drive has no cached end coords, fetch from API (1 call).
        // SchemaVersion V5 triggers re-sync to populate end coords, but the sync may
        // not have completed yet when the user first opens the trip detail after update.
        val lastDrive = trip.drives.lastOrNull()
        val lastCoord = lastDrive?.let { driveCoords[it.driveId] }
        if (lastDrive != null && (lastCoord == null || lastCoord.endLatitude == null)) {
            when (val result = repository.getDriveDetail(lastDrive.carId, lastDrive.driveId)) {
                is ApiResult.Success -> {
                    result.data.positions
                        ?.lastOrNull { it.latitude != null && it.longitude != null }
                        ?.let { points.add(EdgePoint(it.latitude!!, it.longitude!!, lastDrive.endDate)) }
                }
                is ApiResult.Error -> {}
            }
        }

        // Charge locations
        for (charge in trip.charges) {
            points.add(EdgePoint(charge.latitude, charge.longitude, charge.startDate))
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
                cachedRows.map { row -> deserializeSegment(row.segmentData) }
            } else {
                val fetched = fetchRouteFromApi(carId, trip)
                if (fetched.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    tripRouteCacheDao.insertAll(
                        fetched.mapIndexed { index, segment ->
                            TripRouteCache(
                                tripKey = tripKey,
                                segmentIndex = index,
                                segmentData = serializeSegment(segment),
                                createdAt = now
                            )
                        }
                    )
                }
                fetched
            }

            // Simplify for display — keeps route shape, reduces rendering work
            val simplified = segments.map { segment ->
                TripRouteSegment(
                    RouteSimplifier.simplify(
                        segment.points,
                        epsilon = 0.0001,
                        lat = { it.latitude },
                        lon = { it.longitude }
                    )
                )
            }

            val allPoints = simplified.flatMap { it.points }

            // Rebuild markers from scratch: [start] + charges + [end]. Building from `trip` avoids
            // double-adding start/end on revalidation (previous version appended to current state).
            val markers = mutableListOf<TripMapMarker>()
            if (allPoints.isNotEmpty()) {
                val first = allPoints.first()
                markers.add(
                    TripMapMarker(
                        first.latitude, first.longitude,
                        TripMapPointType.START, trip.startAddress
                    )
                )
            }
            trip.charges.forEach { charge ->
                markers.add(
                    TripMapMarker(
                        charge.latitude, charge.longitude,
                        TripMapPointType.CHARGE, charge.address,
                        chargeId = charge.chargeId
                    )
                )
            }
            if (allPoints.isNotEmpty()) {
                val last = allPoints.last()
                markers.add(
                    TripMapMarker(
                        last.latitude, last.longitude,
                        TripMapPointType.END, trip.endAddress
                    )
                )
            }

            _uiState.update {
                it.copy(
                    routeSegments = simplified,
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

    /** SHA-256 hash of sorted drive IDs, used as cache key. Delegates to [TripRepository]. */
    private fun computeTripKey(trip: Trip): String = tripRepository.computeFingerprint(trip)

    /** Pack points as big-endian doubles: [lat0, lon0, lat1, lon1, ...] */
    private fun serializeSegment(segment: TripRouteSegment): ByteArray {
        val buf = ByteBuffer.allocate(segment.points.size * 16) // 2 doubles × 8 bytes
        for (pt in segment.points) {
            buf.putDouble(pt.latitude)
            buf.putDouble(pt.longitude)
        }
        return buf.array()
    }

    private fun deserializeSegment(data: ByteArray): TripRouteSegment {
        val buf = ByteBuffer.wrap(data)
        val count = data.size / 16
        return TripRouteSegment(
            (0 until count).map {
                TripRoutePoint(buf.getDouble(), buf.getDouble())
            }
        )
    }
}
