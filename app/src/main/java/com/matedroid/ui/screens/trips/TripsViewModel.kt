package com.matedroid.ui.screens.trips

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.R
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.inject.Inject

enum class TripDateFilter(@get:StringRes val labelRes: Int, val days: Long?) {
    LAST_90_DAYS(R.string.filter_last_90_days, 90),
    LAST_YEAR(R.string.filter_last_year, 365),
    ALL_TIME(R.string.filter_all_time, null)
}

data class TripsUiState(
    val isLoading: Boolean = true,
    val trips: List<Trip> = emptyList(),
    val totalDistance: Double = 0.0,
    val totalDrivingMin: Int = 0,
    val totalEnergyCharged: Double = 0.0,
    val dateFilter: TripDateFilter = TripDateFilter.ALL_TIME,
    val units: Units? = null
)

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val driveSummaryDao: DriveSummaryDao,
    private val aggregateDao: AggregateDao,
    private val tripDetector: TripDetector,
    private val repository: TeslamateRepository
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

    fun setDateFilter(filter: TripDateFilter) {
        _uiState.update { it.copy(dateFilter = filter) }
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

            _uiState.update { it.copy(isLoading = false) }
            applyFilter()
        }
    }

    private fun applyFilter() {
        val filter = _uiState.value.dateFilter
        val filtered = if (filter.days == null) {
            allTrips
        } else {
            val cutoff = LocalDate.now().minusDays(filter.days)
            allTrips.filter { trip ->
                val tripDate = parseDate(trip.startDate)
                tripDate != null && !tripDate.isBefore(cutoff)
            }
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

    private fun parseDate(dateStr: String): LocalDate? {
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
