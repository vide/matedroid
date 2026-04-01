package com.matedroid.ui.screens.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.ChargeSummaryDao
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
import javax.inject.Inject

data class TripsUiState(
    val isLoading: Boolean = true,
    val trips: List<Trip> = emptyList(),
    val totalDistance: Double = 0.0,
    val totalDrivingMin: Int = 0,
    val totalEnergyCharged: Double = 0.0,
    val syncWarning: Boolean = false,
    val units: Units? = null
)

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val driveSummaryDao: DriveSummaryDao,
    private val aggregateDao: AggregateDao,
    private val chargeSummaryDao: ChargeSummaryDao,
    private val tripDetector: TripDetector,
    private val repository: TeslamateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripsUiState())
    val uiState: StateFlow<TripsUiState> = _uiState.asStateFlow()

    private var carId: Int? = null

    fun setCarId(id: Int) {
        if (carId == id) return
        carId = id
        loadTrips(id)
        loadUnits(id)
    }

    private fun loadUnits(carId: Int) {
        viewModelScope.launch {
            when (val result = repository.getCarStatus(carId)) {
                is ApiResult.Success -> _uiState.update { it.copy(units = result.data.units) }
                is ApiResult.Error -> { /* default to metric */ }
            }
        }
    }

    private fun loadTrips(carId: Int) {
        viewModelScope.launch {
            val drives = driveSummaryDao.getAllChronological(carId)
            val dcCharges = aggregateDao.getDcChargeSummaries(carId)
            val trips = tripDetector.detectTrips(drives, dcCharges).reversed()

            val totalCharges = chargeSummaryDao.count(carId)
            val processedCharges = aggregateDao.countChargeAggregates(carId)
            val syncWarning = processedCharges < totalCharges

            _uiState.update {
                it.copy(
                    isLoading = false,
                    trips = trips,
                    totalDistance = trips.sumOf { t -> t.totalDistance },
                    totalDrivingMin = trips.sumOf { t -> t.totalDrivingDurationMin },
                    totalEnergyCharged = trips.sumOf { t -> t.totalEnergyCharged },
                    syncWarning = syncWarning
                )
            }
        }
    }
}
