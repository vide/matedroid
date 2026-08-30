package com.matedroid.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.CarData
import com.matedroid.data.api.models.CarExterior
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.CarImageOverride
import com.matedroid.data.local.ChargeSessionStateDataStore
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.local.TripCountCache
import com.matedroid.domain.HighSocWarning
import com.matedroid.domain.LowSocWarning
import com.matedroid.domain.SinceLastChargeRepository
import com.matedroid.domain.SinceLastChargeStats
import com.matedroid.domain.TripRepository
import com.matedroid.domain.model.Trip
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.GeocodingRepository
import com.matedroid.data.repository.SentryStateRepository
import com.matedroid.data.repository.TeslamateRepository
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val cars: List<CarData> = emptyList(),
    val selectedCarId: Int? = null,
    val carStatus: CarStatus? = null,
    val units: Units? = null,
    val resolvedAddress: String? = null,
    val totalCharges: Int? = null,
    val totalDrives: Int? = null,
    val error: String? = null,
    val errorDetails: String? = null,
    val carImageOverride: CarImageOverride? = null,
    val carImageOverrides: Map<Int, CarImageOverride> = emptyMap(),
    val isCurrentChargeAvailable: Boolean = false,
    /** True while the dashboard is showing the built-in sample data rather than a real car. */
    val isDemoMode: Boolean = false,
    val sentryEventCount: Int = 0,
    val totalTrips: Int? = null,
    /** Most recent detected trip (newest first), for the dashboard's Trips hero teaser. */
    val latestTrip: Trip? = null,
    val dcFinishedPluggedIn: Boolean = false,
    /** Consumption since the last energy-adding charge; null hides the carousel page. */
    val sinceLastCharge: SinceLastChargeStats? = null,
    /** Battery level above which a parked car is flagged; see [HighSocWarning]. */
    val highSocWarningThreshold: Int = HighSocWarning.DEFAULT_THRESHOLD,
    /** Battery level below which the percentage reads as low; see [LowSocWarning]. */
    val lowSocWarningThreshold: Int = LowSocWarning.DEFAULT_THRESHOLD
) {
    private val selectedCar: CarData?
        get() = cars.find { it.carId == selectedCarId }

    val hasMultipleCars: Boolean
        get() = cars.size > 1

    val selectedCarName: String?
        get() = selectedCar?.displayName ?: carStatus?.displayName

    val selectedCarEfficiency: Double?
        get() = selectedCar?.carDetails?.efficiency

    val selectedCarModel: String?
        get() = selectedCar?.carDetails?.model

    val selectedCarTrimBadging: String?
        get() = selectedCar?.carDetails?.trimBadging

    val selectedCarExterior: CarExterior?
        get() = selectedCar?.carExterior
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: TeslamateRepository,
    private val geocodingRepository: GeocodingRepository,
    private val settingsDataStore: SettingsDataStore,
    private val sentryStateRepository: SentryStateRepository,
    private val tripRepository: TripRepository,
    private val tripCountCache: TripCountCache,
    private val chargeSessionStateDataStore: ChargeSessionStateDataStore,
    private val sinceLastChargeRepository: SinceLastChargeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null
    private var autoRefreshCarId: Int? = null
    private var lastGeocodedLocation: Pair<Double, Double>? = null

    companion object {
        private const val AUTO_REFRESH_INTERVAL_MS = 5000L
    }

    // Cache of current overrides for use when selectedCarId changes
    private var currentOverrides: Map<Int, CarImageOverride> = emptyMap()

    init {
        // Load overrides first, then load cars
        viewModelScope.launch {
            // Get initial overrides before loading cars
            currentOverrides = settingsDataStore.carImageOverrides.first()
            loadCars()
        }
        observeCarImageOverrides()
        observeSocWarningThresholds()
    }

    /** Kept live rather than read once, so a change in Settings shows on the way back. */
    private fun observeSocWarningThresholds() {
        viewModelScope.launch {
            settingsDataStore.settings
                .map { it.highSocWarningThreshold to it.lowSocWarningThreshold }
                .distinctUntilChanged()
                .collect { (high, low) ->
                    _uiState.update {
                        it.copy(highSocWarningThreshold = high, lowSocWarningThreshold = low)
                    }
                }
        }

        viewModelScope.launch {
            settingsDataStore.settings
                .map { it.isDemoMode }
                .distinctUntilChanged()
                .collect { demo -> _uiState.update { it.copy(isDemoMode = demo) } }
        }
    }

    private fun observeCarImageOverrides() {
        viewModelScope.launch {
            settingsDataStore.carImageOverrides.collect { overrides ->
                currentOverrides = overrides
                val carId = _uiState.value.selectedCarId
                _uiState.update {
                    it.copy(
                        carImageOverride = carId?.let { id -> overrides[id] },
                        carImageOverrides = overrides
                    )
                }
            }
        }
    }

    fun loadCars() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, errorDetails = null) }

            when (val result = repository.getCars()) {
                is ApiResult.Success -> {
                    val cars = result.data
                    // Try to restore last selected car, fall back to first car
                    val lastCarId = settingsDataStore.settings.first().lastSelectedCarId
                    val selectedCarId = if (lastCarId != null && cars.any { it.carId == lastCarId }) {
                        lastCarId
                    } else {
                        cars.firstOrNull()?.carId
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            cars = cars,
                            selectedCarId = selectedCarId,
                            carImageOverride = selectedCarId?.let { id -> currentOverrides[id] }
                        )
                    }
                    selectedCarId?.let { loadCarStatus(it) }
                    // Fetch and cache global settings (for Teslamate base URL)
                    fetchAndCacheGlobalSettings()
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message,
                            errorDetails = result.details
                        )
                    }
                }
            }
        }
    }

    fun selectCar(carId: Int) {
        // Reset state when switching cars
        lastGeocodedLocation = null
        _uiState.update {
            it.copy(
                selectedCarId = carId,
                carStatus = null,
                resolvedAddress = null,
                totalCharges = null,
                totalDrives = null,
                carImageOverride = currentOverrides[carId],
                isCurrentChargeAvailable = false,
                sinceLastCharge = null,
                // Clear any error from a previously-selected car so switching to a
                // working car doesn't keep showing the stale error (issue #272).
                error = null,
                errorDetails = null
            )
        }
        // Save the selected car for next app launch
        viewModelScope.launch {
            settingsDataStore.saveLastSelectedCarId(carId)
        }
        loadCarStatus(carId)
    }

    /**
     * Retry loading the currently-selected car's status after a failure.
     * Clears the error first so the UI shows a loading state while retrying.
     */
    fun retryCarStatus() {
        val carId = _uiState.value.selectedCarId
        if (carId == null) {
            // No car selected yet (e.g. the car list itself failed to load) — reload everything.
            loadCars()
            return
        }
        _uiState.update { it.copy(error = null, errorDetails = null) }
        loadCarStatus(carId)
    }

    fun refresh() {
        val carId = _uiState.value.selectedCarId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }

            // Fetch car status directly (not via loadCarStatus which launches separate coroutine)
            when (val result = repository.getCarStatus(carId)) {
                is ApiResult.Success -> {
                    val status = result.data.status
                    val dcFinishedPluggedIn = trackAndComputeDcFinishedPluggedIn(carId, status)
                    detectChargeCycleEnd(carId, status)
                    _uiState.update {
                        it.copy(
                            carStatus = status,
                            units = result.data.units,
                            dcFinishedPluggedIn = dcFinishedPluggedIn,
                            error = null
                        )
                    }
                    fetchAddressIfNeeded(status)
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(error = result.message) }
                }
            }

            // Pull-to-refresh also re-reads the trip count + latest trip.
            loadTripCount(carId)
            loadSinceLastCharge(carId)

            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun loadCarStatus(carId: Int) {
        viewModelScope.launch {
            when (val result = repository.getCarStatus(carId)) {
                is ApiResult.Success -> {
                    val status = result.data.status
                    val dcFinishedPluggedIn = trackAndComputeDcFinishedPluggedIn(carId, status)
                    detectChargeCycleEnd(carId, status)
                    _uiState.update {
                        it.copy(
                            carStatus = status,
                            units = result.data.units,
                            dcFinishedPluggedIn = dcFinishedPluggedIn,
                            error = null
                        )
                    }
                    // Fetch address if no geofence but coordinates are available
                    fetchAddressIfNeeded(status)
                    checkCurrentChargeAvailability(carId, status)
                    loadSentryEventCount(carId)
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(error = result.message) }
                }
            }
        }
        loadCounts(carId)
        loadSinceLastCharge(carId)
        startAutoRefresh(carId)
    }

    // Charging state seen by the last status update, per car — a true→false
    // transition means a charge cycle just ended and the stats must re-anchor.
    private var lastObservedCharging: Pair<Int, Boolean>? = null

    /** Reload the since-last-charge stats when a charge finishes while the dashboard polls. */
    private fun detectChargeCycleEnd(carId: Int, status: CarStatus) {
        val wasCharging = lastObservedCharging?.takeIf { it.first == carId }?.second
        lastObservedCharging = carId to status.isCharging
        if (wasCharging == true && !status.isCharging) {
            loadSinceLastCharge(carId)
        }
    }

    private fun loadSinceLastCharge(carId: Int) {
        viewModelScope.launch {
            val stats = sinceLastChargeRepository.getStats(carId)
            // Guard against a car switch while the two API calls were in flight.
            if (_uiState.value.selectedCarId == carId) {
                _uiState.update { it.copy(sinceLastCharge = stats) }
            }
        }
    }

    /**
     * Persists the in-session DC flag so we can still identify the charge type
     * after completion (when `charger_phases` is null for both AC and DC).
     * Returns whether the unplug warning should be shown for this status.
     */
    private suspend fun trackAndComputeDcFinishedPluggedIn(carId: Int, status: CarStatus): Boolean {
        if (status.isCharging && status.isDcCharging) {
            chargeSessionStateDataStore.setLastSessionDc(carId, true)
        } else if (status.pluggedIn == false) {
            chargeSessionStateDataStore.clear(carId)
        }
        return status.isChargeCompletePluggedIn && chargeSessionStateDataStore.wasLastSessionDc(carId)
    }

    private fun loadCounts(carId: Int) {
        // Use counts from the cars API response instead of fetching all drives/charges
        val selectedCar = _uiState.value.cars.find { it.carId == carId }
        _uiState.update {
            it.copy(
                totalCharges = selectedCar?.teslamateStats?.totalCharges,
                totalDrives = selectedCar?.teslamateStats?.totalDrives
            )
        }
        loadTripCount(carId)
    }

    private fun fetchAddressIfNeeded(status: CarStatus) {
        val lat = status.latitude
        val lon = status.longitude
        val hasGeofence = !status.geofence.isNullOrBlank()

        // Only fetch if no geofence, coordinates exist, and location changed
        if (!hasGeofence && lat != null && lon != null) {
            val currentLocation = Pair(lat, lon)
            // Check if we've already geocoded this location (with some tolerance)
            if (lastGeocodedLocation?.let { (lastLat, lastLon) ->
                    kotlin.math.abs(lastLat - lat) < 0.0001 && kotlin.math.abs(lastLon - lon) < 0.0001
                } == true) {
                return
            }

            lastGeocodedLocation = currentLocation
            viewModelScope.launch {
                val address = geocodingRepository.reverseGeocode(lat, lon)
                if (address != null) {
                    _uiState.update { it.copy(resolvedAddress = address) }
                }
            }
        } else if (hasGeofence) {
            // Clear resolved address if geofence is available
            _uiState.update { it.copy(resolvedAddress = null) }
            lastGeocodedLocation = null
        }
    }

    /** Resume polling after the dashboard becomes visible again (see [pauseAutoRefresh]). */
    fun resumeAutoRefresh() {
        autoRefreshCarId?.let { startAutoRefresh(it) }
    }

    /** Stop polling while the dashboard is not visible, to avoid off-screen network/CPU/battery cost. */
    fun pauseAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private fun startAutoRefresh(carId: Int) {
        autoRefreshCarId = carId
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                when (val result = repository.getCarStatus(carId)) {
                    is ApiResult.Success -> {
                        val status = result.data.status
                        val dcFinishedPluggedIn = trackAndComputeDcFinishedPluggedIn(carId, status)
                    detectChargeCycleEnd(carId, status)
                        _uiState.update {
                            it.copy(
                                carStatus = status,
                                units = result.data.units,
                                dcFinishedPluggedIn = dcFinishedPluggedIn
                            )
                        }
                        // Update address if location changed
                        fetchAddressIfNeeded(status)
                        checkCurrentChargeAvailability(carId, status)
                        loadSentryEventCount(carId)
                    }
                    is ApiResult.Error -> {
                        // Silently ignore errors during auto-refresh
                    }
                }
            }
        }
    }

    private fun checkCurrentChargeAvailability(carId: Int, status: CarStatus) {
        if (status.isCharging && !_uiState.value.isCurrentChargeAvailable) {
            viewModelScope.launch {
                val available = repository.isCurrentChargeAvailable(carId)
                _uiState.update { it.copy(isCurrentChargeAvailable = available) }
            }
        }
    }

    private fun loadSentryEventCount(carId: Int) {
        viewModelScope.launch {
            val count = sentryStateRepository.getEventCount(carId)
            _uiState.update { it.copy(sentryEventCount = count) }
        }
    }

    /** Re-read the trip count + latest trip — e.g. after returning from creating/editing a trip. */
    fun refreshTripCount() {
        _uiState.value.selectedCarId?.let { loadTripCount(it) }
    }

    private fun loadTripCount(carId: Int) {
        viewModelScope.launch {
            // Show cached value instantly
            tripCountCache.get(carId)?.let { cached ->
                _uiState.update { it.copy(totalTrips = cached) }
            }
            // Recompute in background and update cache (also auto-persists new saved trips).
            // Trips come back newest-first, so the head is the latest trip for the hero teaser.
            val trips = tripRepository.getTrips(carId)
            _uiState.update { it.copy(totalTrips = trips.size, latestTrip = trips.firstOrNull()) }
            tripCountCache.set(carId, trips.size)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, errorDetails = null) }
    }

    /**
     * Fetches global settings from the API and caches the base_url.
     * This runs silently - failures don't affect the user experience.
     */
    private fun fetchAndCacheGlobalSettings() {
        viewModelScope.launch {
            when (val result = repository.getGlobalSettings()) {
                is ApiResult.Success -> {
                    result.data.settings?.teslamateUrls?.baseUrl?.let { url ->
                        settingsDataStore.saveTeslamateBaseUrl(url.trimEnd('/'))
                    }
                }
                is ApiResult.Error -> {
                    // Silent fail - this is optional functionality
                    // Older Teslamate API versions may not have this endpoint
                }
            }
        }
    }

    /**
     * Save or clear a car image override for the current car.
     *
     * @param override The override to save, or null to reset to automatic detection
     */
    fun saveCarImageOverride(override: CarImageOverride?) {
        val carId = _uiState.value.selectedCarId ?: return
        viewModelScope.launch {
            settingsDataStore.saveCarImageOverride(carId, override)
        }
    }
}
