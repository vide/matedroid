package com.matedroid.ui.screens.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.domain.TripDetector
import com.matedroid.domain.model.Trip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.inject.Inject

data class TripsUiState(
    val isLoading: Boolean = true,
    val trips: List<Trip> = emptyList(),
    val totalDistance: Double = 0.0,
    val totalDrivingMin: Int = 0,
    val totalEnergyCharged: Double = 0.0,
    val availableYears: List<Int> = emptyList(),
    val selectedYear: Int? = null,
    val units: Units? = null
)

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val driveSummaryDao: DriveSummaryDao,
    private val aggregateDao: AggregateDao,
    private val tripDetector: TripDetector,
    private val repository: TeslamateRepository,
    private val tripCache: TripCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripsUiState())
    val uiState: StateFlow<TripsUiState> = _uiState.asStateFlow()

    private var carId: Int? = null
    private var allTrips: List<Trip> = emptyList()

    fun setCarId(id: Int) {
        if (carId == id) return
        carId = id
        loadTrips(id)
        loadUnits(id)
    }

    /** Cache the trip before navigating to detail, avoiding re-detection. */
    fun cacheTrip(trip: Trip) {
        tripCache.put(trip)
    }

    fun setYear(year: Int?) {
        _uiState.update { it.copy(selectedYear = year) }
        applyFilter()
    }

    private fun loadUnits(carId: Int) {
        viewModelScope.launch {
            when (val result = repository.getCarStatus(carId)) {
                is ApiResult.Success -> _uiState.update { it.copy(units = result.data.units) }
                is ApiResult.Error -> {}
            }
        }
    }

    private fun loadTrips(carId: Int) {
        viewModelScope.launch {
            val drives = driveSummaryDao.getAllChronological(carId)
            val dcCharges = aggregateDao.getDcChargeSummaries(carId)
            allTrips = tripDetector.detectTrips(drives, dcCharges).reversed()

            val years = allTrips.mapNotNull { parseYear(it.startDate) }
                .distinct()
                .sortedDescending()

            _uiState.update { it.copy(isLoading = false, availableYears = years) }
            applyFilter()
        }
    }

    private fun applyFilter() {
        val year = _uiState.value.selectedYear
        val filtered = if (year == null) {
            allTrips
        } else {
            allTrips.filter { parseYear(it.startDate) == year }
        }

        _uiState.update {
            it.copy(
                trips = filtered,
                totalDistance = filtered.sumOf { t -> t.totalDistance },
                totalDrivingMin = filtered.sumOf { t -> t.totalDrivingDurationMin },
                totalEnergyCharged = filtered.sumOf { t -> t.totalEnergyCharged }
            )
        }
    }

    private fun parseYear(dateStr: String): Int? {
        return try {
            OffsetDateTime.parse(dateStr).year
        } catch (e: DateTimeParseException) {
            try {
                LocalDateTime.parse(dateStr.replace("Z", "")).year
            } catch (e2: Exception) {
                null
            }
        }
    }
}
