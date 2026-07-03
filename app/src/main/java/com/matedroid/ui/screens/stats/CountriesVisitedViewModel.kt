package com.matedroid.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.Units
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.StatsRepository
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.domain.model.CountryRecord
import com.matedroid.domain.model.YearFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sorting options for the countries list.
 */

data class CountriesVisitedUiState(
    val isLoading: Boolean = true,
    val countries: List<CountryRecord> = emptyList(),
    val sortOrder: GeoSortOrder = GeoSortOrder.FIRST_VISIT,
    val units: Units? = null,
    val error: String? = null
)

@HiltViewModel
class CountriesVisitedViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
    private val teslamateRepository: TeslamateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CountriesVisitedUiState())
    val uiState: StateFlow<CountriesVisitedUiState> = _uiState.asStateFlow()

    private var originalCountries: List<CountryRecord> = emptyList()

    fun loadCountries(carId: Int, yearFilter: YearFilter) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Fetch units alongside countries
            launch {
                when (val result = teslamateRepository.getCarStatus(carId)) {
                    is ApiResult.Success -> _uiState.update { it.copy(units = result.data.units) }
                    is ApiResult.Error -> { /* default to metric */ }
                }
            }

            try {
                originalCountries = statsRepository.getCountriesVisited(carId, yearFilter)
                val sorted = sortCountries(originalCountries, _uiState.value.sortOrder)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        countries = sorted,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load countries"
                    )
                }
            }
        }
    }

    fun setSortOrder(order: GeoSortOrder) {
        val sorted = sortCountries(originalCountries, order)
        _uiState.update {
            it.copy(
                sortOrder = order,
                countries = sorted
            )
        }
    }

    private fun sortCountries(
        countries: List<CountryRecord>,
        order: GeoSortOrder
    ): List<CountryRecord> {
        return when (order) {
            GeoSortOrder.FIRST_VISIT -> countries.sortedBy { it.firstVisitDate }
            GeoSortOrder.ALPHABETICAL -> countries.sortedBy { it.countryName }
            GeoSortOrder.DRIVE_COUNT -> countries.sortedByDescending { it.driveCount }
            GeoSortOrder.DISTANCE -> countries.sortedByDescending { it.totalDistanceKm }
            GeoSortOrder.ENERGY -> countries.sortedByDescending { it.totalChargeEnergyKwh }
            GeoSortOrder.CHARGES -> countries.sortedByDescending { it.chargeCount }
        }
    }
}
