package com.matedroid.ui.screens.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.CountryBoundary
import com.matedroid.data.repository.StatsRepository
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.domain.model.ChargeLocation
import com.matedroid.domain.model.CountryRecord
import com.matedroid.domain.model.DriveLocation
import com.matedroid.domain.model.RegionRecord
import com.matedroid.domain.model.YearFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sorting options for the regions list.
 */

/**
 * Map view mode for switching between charges and drives.
 */
enum class MapViewMode {
    CHARGES,
    DRIVES
}

/**
 * Filter for charge type on the map.
 */
enum class ChargeTypeFilter {
    ALL,        // Show all charges
    AC_ONLY,    // Show only AC charges
    DC_ONLY     // Show only DC charges
}

data class RegionsVisitedUiState(
    val isLoading: Boolean = true,
    val countryRecord: CountryRecord? = null,
    val regions: List<RegionRecord> = emptyList(),
    val units: Units? = null,
    val chargeLocations: List<ChargeLocation> = emptyList(),
    val driveLocations: List<DriveLocation> = emptyList(),
    val countryBoundary: CountryBoundary? = null,
    val mapViewMode: MapViewMode = MapViewMode.CHARGES,
    val chargeTypeFilter: ChargeTypeFilter = ChargeTypeFilter.ALL,
    val availableYears: List<Int> = emptyList(),    // Years with data in this country
    val selectedMapYear: Int? = null,                // null = all years
    val sortOrder: GeoSortOrder = GeoSortOrder.FIRST_VISIT,
    val error: String? = null
) {
    /** Charges filtered by type and year for map display */
    val filteredChargeLocations: List<ChargeLocation>
        get() {
            var filtered = chargeLocations

            // Filter by year if selected
            if (selectedMapYear != null) {
                filtered = filtered.filter { charge ->
                    charge.date.take(4).toIntOrNull() == selectedMapYear
                }
            }

            // Filter by charge type
            filtered = when (chargeTypeFilter) {
                ChargeTypeFilter.ALL -> filtered
                ChargeTypeFilter.AC_ONLY -> filtered.filter { !it.isDcCharge }
                ChargeTypeFilter.DC_ONLY -> filtered.filter { it.isDcCharge }
            }

            return filtered
        }

    /** Drives filtered by year for map display */
    val filteredDriveLocations: List<DriveLocation>
        get() {
            if (selectedMapYear == null) return driveLocations
            return driveLocations.filter { drive ->
                drive.date.take(4).toIntOrNull() == selectedMapYear
            }
        }
}

@HiltViewModel
class RegionsVisitedViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val statsRepository: StatsRepository,
    private val teslamateRepository: TeslamateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegionsVisitedUiState())
    val uiState: StateFlow<RegionsVisitedUiState> = _uiState.asStateFlow()

    private var originalRegions: List<RegionRecord> = emptyList()

    fun loadRegions(carId: Int, countryCode: String, yearFilter: YearFilter) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Fetch units alongside regions
            launch {
                when (val result = teslamateRepository.getCarStatus(carId)) {
                    is ApiResult.Success -> _uiState.update { it.copy(units = result.data.units) }
                    is ApiResult.Error -> { /* default to metric */ }
                }
            }


            try {
                // Load country record for header card
                val countries = statsRepository.getCountriesVisited(carId, yearFilter)
                val countryRecord = countries.find { it.countryCode == countryCode }

                // Load regions within the country
                originalRegions = statsRepository.getRegionsVisited(carId, countryCode, yearFilter)
                val sorted = sortRegions(originalRegions, _uiState.value.sortOrder)

                // Load charge and drive locations for the map
                val chargeLocations = statsRepository.getChargeLocationsForCountry(carId, countryCode, yearFilter)
                val driveLocations = statsRepository.getDriveLocationsForCountry(carId, countryCode, yearFilter)

                // Extract available years from the data
                val chargeYears = chargeLocations.mapNotNull { it.date.take(4).toIntOrNull() }
                val driveYears = driveLocations.mapNotNull { it.date.take(4).toIntOrNull() }
                val availableYears = (chargeYears + driveYears).distinct().sortedDescending()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        countryRecord = countryRecord,
                        regions = sorted,
                        chargeLocations = chargeLocations,
                        driveLocations = driveLocations,
                        availableYears = availableYears,
                        error = null
                    )
                }

                // Fetch country boundary asynchronously (non-blocking)
                // This will update the UI when ready, dimming other countries on the map
                launch {
                    val boundary = statsRepository.getCountryBoundary(countryCode)
                    _uiState.update { it.copy(countryBoundary = boundary) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: context.getString(R.string.regions_error_load_failed)
                    )
                }
            }
        }
    }

    fun setSortOrder(order: GeoSortOrder) {
        val sorted = sortRegions(originalRegions, order)
        _uiState.update {
            it.copy(
                sortOrder = order,
                regions = sorted
            )
        }
    }

    fun setMapViewMode(mode: MapViewMode) {
        _uiState.update { it.copy(mapViewMode = mode) }
    }

    fun toggleChargeTypeFilter(filter: ChargeTypeFilter) {
        _uiState.update { current ->
            // If already selected, reset to ALL; otherwise set the filter
            val newFilter = if (current.chargeTypeFilter == filter) {
                ChargeTypeFilter.ALL
            } else {
                filter
            }
            current.copy(chargeTypeFilter = newFilter)
        }
    }

    fun setMapYearFilter(year: Int?) {
        _uiState.update { it.copy(selectedMapYear = year) }
    }

    private fun sortRegions(
        regions: List<RegionRecord>,
        order: GeoSortOrder
    ): List<RegionRecord> {
        return when (order) {
            GeoSortOrder.FIRST_VISIT -> regions.sortedBy { it.firstVisitDate }
            GeoSortOrder.ALPHABETICAL -> regions.sortedBy { it.regionName }
            GeoSortOrder.DRIVE_COUNT -> regions.sortedByDescending { it.driveCount }
            GeoSortOrder.DISTANCE -> regions.sortedByDescending { it.totalDistanceKm }
            GeoSortOrder.ENERGY -> regions.sortedByDescending { it.totalChargeEnergyKwh }
            GeoSortOrder.CHARGES -> regions.sortedByDescending { it.chargeCount }
        }
    }
}
