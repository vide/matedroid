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
import javax.inject.Inject

data class TripMapPoint(
    val latitude: Double,
    val longitude: Double,
    val type: TripMapPointType,
    val label: String
)

enum class TripMapPointType { START, CHARGE, END }

data class TripDetailUiState(
    val isLoading: Boolean = true,
    val trip: Trip? = null,
    val mapPoints: List<TripMapPoint> = emptyList(),
    val units: Units? = null
)

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    private val driveSummaryDao: DriveSummaryDao,
    private val aggregateDao: AggregateDao,
    private val tripDetector: TripDetector,
    private val repository: TeslamateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripDetailUiState())
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()

    private var loaded = false

    fun loadTrip(carId: Int, tripIndex: Int) {
        if (loaded) return
        loaded = true

        viewModelScope.launch {
            // Load units in parallel
            launch {
                when (val result = repository.getCarStatus(carId)) {
                    is ApiResult.Success -> _uiState.update { it.copy(units = result.data.units) }
                    is ApiResult.Error -> { /* default to metric */ }
                }
            }

            val drives = driveSummaryDao.getAllChronological(carId)
            val dcCharges = aggregateDao.getDcChargeSummaries(carId)
            val trips = tripDetector.detectTrips(drives, dcCharges).reversed()

            val trip = trips.getOrNull(tripIndex)
            if (trip == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            val mapPoints = mutableListOf<TripMapPoint>()

            val driveIds = trip.drives.map { it.driveId }
            val driveCoords = aggregateDao.getDriveCoordinates(driveIds)
                .associateBy { it.driveId }

            // Trip start
            driveCoords[trip.drives.first().driveId]?.let { coord ->
                mapPoints.add(
                    TripMapPoint(
                        coord.startLatitude, coord.startLongitude,
                        TripMapPointType.START, trip.startAddress
                    )
                )
            }

            // Charge stops
            trip.charges.forEach { charge ->
                mapPoints.add(
                    TripMapPoint(
                        charge.latitude, charge.longitude,
                        TripMapPointType.CHARGE, charge.address
                    )
                )
            }

            // Trip end — use last drive's start coordinate as approximation
            driveCoords[trip.drives.last().driveId]?.let { coord ->
                mapPoints.add(
                    TripMapPoint(
                        coord.startLatitude, coord.startLongitude,
                        TripMapPointType.END, trip.endAddress
                    )
                )
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    trip = trip,
                    mapPoints = mapPoints
                )
            }
        }
    }
}
