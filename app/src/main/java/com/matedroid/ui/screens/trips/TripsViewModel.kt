package com.matedroid.ui.screens.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.domain.TripRepository
import com.matedroid.domain.model.Trip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    val isCustomDateFilter: Boolean = false,
    val customStartDate: LocalDate? = null,
    val customEndDate: LocalDate? = null,
    val units: Units? = null,
    val dcChargeIds: Set<Int> = emptySet()
)

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val repository: TeslamateRepository,
    private val tripCache: TripCache,
    private val aggregateDao: AggregateDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripsUiState())
    val uiState: StateFlow<TripsUiState> = _uiState.asStateFlow()

    private var carId: Int? = null
    private var allTrips: List<Trip> = emptyList()
    private var hasBeenPaused = false

    fun setCarId(id: Int) {
        if (carId == id) return
        carId = id
        loadTrips(id)
        loadUnits(id)
        loadDcChargeIds(id)
    }

    private fun loadDcChargeIds(id: Int) {
        viewModelScope.launch {
            val ids = try {
                aggregateDao.getDcChargeIds(id).toSet()
            } catch (e: Exception) { emptySet() }
            _uiState.update { it.copy(dcChargeIds = ids) }
        }
    }

    /** Screen went to background (user navigated to a child). */
    fun onScreenPaused() {
        hasBeenPaused = true
    }

    /** Screen is resuming — reload trips if the user is returning from a child screen. */
    fun onScreenResumed() {
        val id = carId ?: return
        if (!hasBeenPaused) return
        hasBeenPaused = false
        loadTrips(id)
    }

    /** Cache the trip before navigating to detail, avoiding re-detection. */
    fun cacheTrip(trip: Trip) {
        tripCache.put(trip)
    }

    fun setYear(year: Int?) {
        _uiState.update { it.copy(
            selectedYear = year,
            isCustomDateFilter = false,
            customStartDate = null,
            customEndDate = null
        )}
        applyFilter()
    }

    fun setCustomDateRange(start: LocalDate, end: LocalDate) {
        _uiState.update { it.copy(
            selectedYear = null,
            isCustomDateFilter = true,
            customStartDate = start,
            customEndDate = end
        )}
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
            allTrips = tripRepository.getTrips(carId)

            val years = allTrips.mapNotNull { parseYear(it.startDate) }
                .distinct()
                .sortedDescending()

            _uiState.update { it.copy(isLoading = false, availableYears = years) }
            applyFilter()
        }
    }

    private fun applyFilter() {
        val state = _uiState.value
        val filtered = if (state.isCustomDateFilter && state.customStartDate != null && state.customEndDate != null) {
            allTrips.filter { trip ->
                val tripDate = parseLocalDate(trip.startDate)
                tripDate != null && !tripDate.isBefore(state.customStartDate) && !tripDate.isAfter(state.customEndDate)
            }
        } else if (state.selectedYear != null) {
            allTrips.filter { parseYear(it.startDate) == state.selectedYear }
        } else {
            allTrips
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
        return parseLocalDate(dateStr)?.year
    }

    private fun parseLocalDate(dateStr: String): LocalDate? {
        return try {
            OffsetDateTime.parse(dateStr).toLocalDate()
        } catch (e: DateTimeParseException) {
            try {
                LocalDateTime.parse(dateStr.replace("Z", "")).toLocalDate()
            } catch (e2: Exception) {
                null
            }
        }
    }
}
