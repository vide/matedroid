package com.matedroid.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.repository.CountryBoundary
import com.matedroid.data.repository.StatsRepository
import com.matedroid.domain.model.ChargeLocation
import com.matedroid.domain.model.CountryRecord
import com.matedroid.domain.model.RegionRecord
import com.matedroid.domain.model.YearFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sorting options for the regions list.
 */
enum class RegionSortOrder {
    FIRST_VISIT,    // Chronological by first visit date (default)
    ALPHABETICAL,   // A-Z by region name
    DRIVE_COUNT,    // Most drives first
    DISTANCE,       // Most distance first
    ENERGY,         // Most energy charged first
    CHARGES         // Most charges first
}

data class RegionsVisitedUiState(
    val isLoading: Boolean = true,
    val countryRecord: CountryRecord? = null,
    val regions: List<RegionRecord> = emptyList(),
    val chargeLocations: List<ChargeLocation> = emptyList(),
    val countryBoundary: CountryBoundary? = null,
    val sortOrder: RegionSortOrder = RegionSortOrder.FIRST_VISIT,
    val error: String? = null
)

@HiltViewModel
class RegionsVisitedViewModel @Inject constructor(
    private val statsRepository: StatsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegionsVisitedUiState())
    val uiState: StateFlow<RegionsVisitedUiState> = _uiState.asStateFlow()

    private var originalRegions: List<RegionRecord> = emptyList()

    fun loadRegions(carId: Int, countryCode: String, yearFilter: YearFilter) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // Load country record for header card
                val countries = statsRepository.getCountriesVisited(carId, yearFilter)
                val countryRecord = countries.find { it.countryCode == countryCode }

                // Load regions within the country
                originalRegions = statsRepository.getRegionsVisited(carId, countryCode, yearFilter)
                val sorted = sortRegions(originalRegions, _uiState.value.sortOrder)

                // Load charge locations for the map
                val chargeLocations = statsRepository.getChargeLocationsForCountry(carId, countryCode, yearFilter)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        countryRecord = countryRecord,
                        regions = sorted,
                        chargeLocations = chargeLocations,
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
                        error = e.message ?: "Failed to load regions"
                    )
                }
            }
        }
    }

    fun setSortOrder(order: RegionSortOrder) {
        val sorted = sortRegions(originalRegions, order)
        _uiState.update {
            it.copy(
                sortOrder = order,
                regions = sorted
            )
        }
    }

    private fun sortRegions(
        regions: List<RegionRecord>,
        order: RegionSortOrder
    ): List<RegionRecord> {
        return when (order) {
            RegionSortOrder.FIRST_VISIT -> regions.sortedBy { it.firstVisitDate }
            RegionSortOrder.ALPHABETICAL -> regions.sortedBy { it.regionName }
            RegionSortOrder.DRIVE_COUNT -> regions.sortedByDescending { it.driveCount }
            RegionSortOrder.DISTANCE -> regions.sortedByDescending { it.totalDistanceKm }
            RegionSortOrder.ENERGY -> regions.sortedByDescending { it.totalChargeEnergyKwh }
            RegionSortOrder.CHARGES -> regions.sortedByDescending { it.chargeCount }
        }
    }
}
