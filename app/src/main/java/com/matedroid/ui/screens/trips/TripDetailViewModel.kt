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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripRoutePoint(
    val latitude: Double,
    val longitude: Double
)

data class TripMapMarker(
    val latitude: Double,
    val longitude: Double,
    val type: TripMapPointType,
    val label: String
)

enum class TripMapPointType { START, CHARGE, END }

/** One drive leg's GPS points, kept separate for alternating colors on the map. */
data class TripRouteSegment(
    val points: List<TripRoutePoint>
)

data class TripDetailUiState(
    val isLoading: Boolean = true,
    val trip: Trip? = null,
    val routeSegments: List<TripRouteSegment> = emptyList(),
    val markers: List<TripMapMarker> = emptyList(),
    val isMapLoading: Boolean = true,
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
            launch {
                when (val result = repository.getCarStatus(carId)) {
                    is ApiResult.Success -> _uiState.update { it.copy(units = result.data.units) }
                    is ApiResult.Error -> {}
                }
            }

            val drives = driveSummaryDao.getAllChronological(carId)
            val dcCharges = aggregateDao.getDcChargeSummaries(carId)
            val trips = tripDetector.detectTrips(drives, dcCharges).reversed()

            val trip = trips.getOrNull(tripIndex)
            if (trip == null) {
                _uiState.update { it.copy(isLoading = false, isMapLoading = false) }
                return@launch
            }

            // Build markers from known coordinates
            val markers = mutableListOf<TripMapMarker>()
            trip.charges.forEach { charge ->
                markers.add(
                    TripMapMarker(
                        charge.latitude, charge.longitude,
                        TripMapPointType.CHARGE, charge.address
                    )
                )
            }

            // Show trip info immediately, map loads in background
            _uiState.update {
                it.copy(isLoading = false, trip = trip, markers = markers)
            }

            // Fetch GPS positions for all drives in parallel
            loadRoutePositions(carId, trip)
        }
    }

    private fun loadRoutePositions(carId: Int, trip: Trip) {
        viewModelScope.launch {
            val deferreds = trip.drives.map { drive ->
                async {
                    when (val result = repository.getDriveDetail(carId, drive.driveId)) {
                        is ApiResult.Success -> {
                            result.data.positions
                                ?.filter { it.latitude != null && it.longitude != null }
                                ?.map { TripRoutePoint(it.latitude!!, it.longitude!!) }
                                ?: emptyList()
                        }
                        is ApiResult.Error -> emptyList()
                    }
                }
            }

            val segments = deferreds.awaitAll()
                .map { TripRouteSegment(it) }
                .filter { it.points.isNotEmpty() }

            val allPoints = segments.flatMap { it.points }

            // Add start/end markers from actual GPS data
            val markers = _uiState.value.markers.toMutableList()
            if (allPoints.isNotEmpty()) {
                val first = allPoints.first()
                val last = allPoints.last()
                markers.add(
                    0,
                    TripMapMarker(
                        first.latitude, first.longitude,
                        TripMapPointType.START, trip.startAddress
                    )
                )
                markers.add(
                    TripMapMarker(
                        last.latitude, last.longitude,
                        TripMapPointType.END, trip.endAddress
                    )
                )
            }

            _uiState.update {
                it.copy(
                    routeSegments = segments,
                    markers = markers,
                    isMapLoading = false
                )
            }
        }
    }
}
