package com.matedroid.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.CarData
import com.matedroid.data.api.models.CarExterior
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.CarImageOverride
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.GeocodingRepository
import com.matedroid.data.repository.TeslamateRepository
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val carImageOverride: CarImageOverride? = null
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
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null
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
    }

    private fun observeCarImageOverrides() {
        viewModelScope.launch {
            settingsDataStore.carImageOverrides.collect { overrides ->
                currentOverrides = overrides
                val carId = _uiState.value.selectedCarId
                _uiState.update { it.copy(carImageOverride = carId?.let { id -> overrides[id] }) }
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
                carImageOverride = currentOverrides[carId]
            )
        }
        // Save the selected car for next app launch
        viewModelScope.launch {
            settingsDataStore.saveLastSelectedCarId(carId)
        }
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
                    _uiState.update {
                        it.copy(
                            carStatus = status,
                            units = result.data.units,
                            error = null
                        )
                    }
                    fetchAddressIfNeeded(status)
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(error = result.message) }
                }
            }

            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun loadCarStatus(carId: Int) {
        viewModelScope.launch {
            when (val result = repository.getCarStatus(carId)) {
                is ApiResult.Success -> {
                    val status = result.data.status
                    _uiState.update {
                        it.copy(
                            carStatus = status,
                            units = result.data.units,
                            error = null
                        )
                    }
                    // Fetch address if no geofence but coordinates are available
                    fetchAddressIfNeeded(status)
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(error = result.message) }
                }
            }
        }
        loadCounts(carId)
        startAutoRefresh(carId)
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

    private fun startAutoRefresh(carId: Int) {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                when (val result = repository.getCarStatus(carId)) {
                    is ApiResult.Success -> {
                        val status = result.data.status
                        _uiState.update {
                            it.copy(
                                carStatus = status,
                                units = result.data.units
                            )
                        }
                        // Update address if location changed
                        fetchAddressIfNeeded(status)
                    }
                    is ApiResult.Error -> {
                        // Silently ignore errors during auto-refresh
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, errorDetails = null) }
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
