package com.matedroid.ui.screens.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.BatteryHealth
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.dao.BatteryHealthSnapshotDao
import com.matedroid.data.local.entity.BatteryHealthSnapshot
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.abs

data class BatteryUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val batteryHealth: BatteryHealth? = null,
    val carStatus: CarStatus? = null,
    val units: Units? = null,
    val originalCapacity: Double = 82.0, // Default for Model 3 LR, could be fetched from car details
    val ratedEfficiency: Double = 0.0,
    val showDetail: Boolean = false,
    /**
     * Historical battery health snapshots for this car, oldest first, sourced from the
     * local DB. Populated once [setCarId] runs; unaffected by refreshes.
     */
    val snapshots: List<BatteryHealthSnapshot> = emptyList(),
)

// Computed battery statistics
data class BatteryStats(
    val currentCapacity: Double,
    val originalCapacity: Double,
    val healthPercent: Double,
    val lossKwh: Double,
    val lossPercent: Double,
    val maxRangeNew: Double,
    val maxRangeNow: Double,
    val rangeLoss: Double,
    val ratedEfficiency: Double,
    // Current status
    val batteryLevel: Int,
    val usableBatteryLevel: Int,
    val estimatedRange: Double,
    val ratedRange: Double,
    val idealRange: Double,
    val rangeAt100: Double
)

@HiltViewModel
class BatteryViewModel @Inject constructor(
    private val repository: TeslamateRepository,
    private val snapshotDao: BatteryHealthSnapshotDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatteryUiState())
    val uiState: StateFlow<BatteryUiState> = _uiState.asStateFlow()

    private var carId: Int? = null
    private var snapshotObserverJob: Job? = null

    fun setCarId(id: Int, efficiency: Double? = null) {
        if (carId != id) {
            carId = id
            efficiency?.let { eff ->
                _uiState.update { it.copy(ratedEfficiency = eff) }
            }
            observeSnapshots(id)
            loadBatteryData()
        }
    }

    private fun observeSnapshots(id: Int) {
        snapshotObserverJob?.cancel()
        snapshotObserverJob = viewModelScope.launch {
            snapshotDao.observeAllForCar(id).collectLatest { rows ->
                _uiState.update { it.copy(snapshots = rows) }
            }
        }
    }

    fun refresh() {
        carId?.let {
            _uiState.update { it.copy(isRefreshing = true) }
            loadBatteryData()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun showDetail() {
        _uiState.update { it.copy(showDetail = true) }
    }

    fun hideDetail() {
        _uiState.update { it.copy(showDetail = false) }
    }

    private fun loadBatteryData() {
        val id = carId ?: return

        viewModelScope.launch {
            val state = _uiState.value
            if (!state.isRefreshing) {
                _uiState.update { it.copy(isLoading = true) }
            }

            // Fetch both battery health and car status in parallel
            val healthResult = repository.getBatteryHealth(id)
            val statusResult = repository.getCarStatus(id)

            when {
                healthResult is ApiResult.Success && statusResult is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            batteryHealth = healthResult.data,
                            carStatus = statusResult.data.status,
                            units = statusResult.data.units,
                            error = null
                        )
                    }
                    persistSnapshotIfWorthKeeping(id, healthResult.data)
                }
                healthResult is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = healthResult.message
                        )
                    }
                }
                statusResult is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = statusResult.message
                        )
                    }
                }
            }
        }
    }

    fun computeStats(): BatteryStats? {
        val state = _uiState.value
        val health = state.batteryHealth ?: return null
        val status = state.carStatus

        // Use data from the battery health API
        val healthPercent = health.batteryHealthPercentage ?: 100.0
        val originalCapacity = health.maxCapacity ?: state.originalCapacity
        val currentCapacity = health.currentCapacity ?: (originalCapacity * healthPercent / 100)
        val lossKwh = originalCapacity - currentCapacity
        val lossPercent = 100 - healthPercent

        // Range from API
        val maxRangeNew = health.maxRange ?: 0.0
        val maxRangeNow = health.currentRange ?: 0.0
        val rangeLoss = maxRangeNew - maxRangeNow

        // Efficiency from API (Wh/km)
        val ratedEfficiency = health.ratedEfficiency ?: state.ratedEfficiency.takeIf { it > 0 } ?: 150.0

        // Current status from CarStatus
        val batteryLevel = status?.batteryLevel ?: 0
        val usableBatteryLevel = status?.usableBatteryLevel ?: batteryLevel
        val estimatedRange = status?.estBatteryRangeKm ?: 0.0
        val ratedRange = status?.ratedBatteryRangeKm ?: 0.0
        val idealRange = status?.idealBatteryRangeKm ?: 0.0

        // Estimate range at 100%
        val rangeAt100 = if (batteryLevel > 0 && ratedRange > 0) {
            (ratedRange / batteryLevel) * 100
        } else {
            maxRangeNow
        }

        return BatteryStats(
            currentCapacity = currentCapacity,
            originalCapacity = originalCapacity,
            healthPercent = healthPercent,
            lossKwh = lossKwh,
            lossPercent = lossPercent,
            maxRangeNew = maxRangeNew,
            maxRangeNow = maxRangeNow,
            rangeLoss = rangeLoss,
            ratedEfficiency = ratedEfficiency,
            batteryLevel = batteryLevel,
            usableBatteryLevel = usableBatteryLevel,
            estimatedRange = estimatedRange,
            ratedRange = ratedRange,
            idealRange = idealRange,
            rangeAt100 = rangeAt100
        )
    }

    /**
     * Append a snapshot of [health] for [carId] iff the last stored sample is either from a
     * previous local calendar day, or from today but with a noticeably different max range.
     * The debounce keeps refreshes / multiple screen opens on the same day from swelling the
     * table with near-duplicates.
     */
    private fun persistSnapshotIfWorthKeeping(carId: Int, health: BatteryHealth) {
        viewModelScope.launch {
            val last = snapshotDao.getLatestForCar(carId)
            val now = System.currentTimeMillis()
            val today = LocalDate.now(ZoneId.systemDefault())
            val lastDay = last?.let {
                Instant.ofEpochMilli(it.recordedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            }
            val sameDay = lastDay == today
            val nearlyIdenticalRange = last?.maxRange != null && health.maxRange != null &&
                abs((last.maxRange ?: 0.0) - (health.maxRange ?: 0.0)) < MAX_RANGE_EPSILON
            if (sameDay && nearlyIdenticalRange) return@launch

            snapshotDao.insertAndPrune(
                BatteryHealthSnapshot(
                    carId = carId,
                    recordedAt = now,
                    maxRange = health.maxRange,
                    currentRange = health.currentRange,
                    maxCapacity = health.maxCapacity,
                    currentCapacity = health.currentCapacity,
                    ratedEfficiency = health.ratedEfficiency,
                    batteryHealthPercentage = health.batteryHealthPercentage,
                )
            )
        }
    }

    private companion object {
        /** Two max-range readings closer than this (in km/mi — units are stored raw) count as the same value for debouncing. */
        const val MAX_RANGE_EPSILON = 0.5
    }
}
