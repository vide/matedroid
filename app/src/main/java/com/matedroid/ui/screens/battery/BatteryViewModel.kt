package com.matedroid.ui.screens.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.BatteryHealth
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.api.models.Units
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BatteryUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val batteryHealth: BatteryHealth? = null,
    val carStatus: CarStatus? = null,
    val units: Units? = null,
    val originalCapacity: Double = 82.0, // Default for Model 3 LR, could be fetched from car details
    // Raw Teslamate cars.efficiency from the car details (kWh per km, e.g. 0.137); 0 when unknown
    val ratedEfficiency: Double = 0.0,
    // Derived from batteryHealth/carStatus when they land, so composition never recomputes it
    val stats: BatteryStats? = null
)

// Computed battery statistics
data class BatteryStats(
    val currentCapacity: Double,
    val originalCapacity: Double,
    val healthPercent: Double,
    val lossKwh: Double,
    val maxRangeNew: Double,
    val maxRangeNow: Double,
    val rangeLoss: Double,
    // Rated consumption in Wh per distance unit (Wh/km or Wh/mi), e.g. 137.0
    val ratedEfficiencyWhPerUnit: Double,
    // Current status
    val batteryLevel: Int,
    val usableBatteryLevel: Int,
    val estimatedRange: Double,
    val ratedRange: Double,
    val idealRange: Double,
    val chargeLimitSoc: Int?,
    val isPluggedIn: Boolean,
    val isCharging: Boolean
)

/** Rated consumption in Wh per distance unit used when neither source provides one. */
private const val DEFAULT_RATED_EFFICIENCY_WH_PER_UNIT = 150.0

/**
 * Derives the figures shown on the Battery Health screen. Pure, so it can be unit-tested.
 *
 * @param fallbackEfficiency Teslamate's raw `cars.efficiency` from the car details (kWh per km,
 *   e.g. 0.137), or 0 when unknown. Only used when `/battery-health` has no `rated_efficiency`.
 * @param defaultCapacityKwh capacity when new, used only when the API does not report one.
 */
internal fun computeBatteryStats(
    health: BatteryHealth?,
    status: CarStatus?,
    fallbackEfficiency: Double,
    defaultCapacityKwh: Double
): BatteryStats? {
    if (health == null) return null

    val healthPercent = health.batteryHealthPercentage ?: 100.0
    val originalCapacity = health.maxCapacity ?: defaultCapacityKwh
    val currentCapacity = health.currentCapacity ?: (originalCapacity * healthPercent / 100)
    val lossKwh = originalCapacity - currentCapacity

    // Range from API (already in the user's distance unit)
    val maxRangeNew = health.maxRange ?: 0.0
    val maxRangeNow = health.currentRange ?: 0.0
    val rangeLoss = maxRangeNew - maxRangeNow

    // Scale fix within one unit system, NOT a km<->mi conversion: TeslamateAPI's
    // rated_efficiency is kWh per 100 distance units. The live TeslamateAPI returns 13.7 for a
    // car whose rated consumption is 137 Wh/km (verified 2026-10-04), so x10 gives Wh per unit.
    // The car-details fallback is Teslamate's raw cars.efficiency in kWh per km (0.137), so x1000.
    val ratedEfficiencyWhPerUnit = health.ratedEfficiency?.takeIf { it > 0 }?.let { it * 10 }
        ?: fallbackEfficiency.takeIf { it > 0 }?.let { it * 1000 }
        ?: DEFAULT_RATED_EFFICIENCY_WH_PER_UNIT

    // Current status from CarStatus
    val batteryLevel = status?.batteryLevel ?: 0
    val usableBatteryLevel = status?.usableBatteryLevel ?: batteryLevel

    return BatteryStats(
        currentCapacity = currentCapacity,
        originalCapacity = originalCapacity,
        healthPercent = healthPercent,
        lossKwh = lossKwh,
        maxRangeNew = maxRangeNew,
        maxRangeNow = maxRangeNow,
        rangeLoss = rangeLoss,
        ratedEfficiencyWhPerUnit = ratedEfficiencyWhPerUnit,
        batteryLevel = batteryLevel,
        usableBatteryLevel = usableBatteryLevel,
        estimatedRange = status?.estBatteryRangeKm ?: 0.0,
        ratedRange = status?.ratedBatteryRangeKm ?: 0.0,
        idealRange = status?.idealBatteryRangeKm ?: 0.0,
        chargeLimitSoc = status?.chargeLimitSoc,
        isPluggedIn = status?.pluggedIn == true,
        isCharging = status?.isCharging == true
    )
}

@HiltViewModel
class BatteryViewModel @Inject constructor(
    private val repository: TeslamateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatteryUiState())
    val uiState: StateFlow<BatteryUiState> = _uiState.asStateFlow()

    private var carId: Int? = null

    fun setCarId(id: Int, efficiency: Double? = null) {
        if (carId != id) {
            carId = id
            efficiency?.let { eff ->
                _uiState.update { it.copy(ratedEfficiency = eff) }
            }
            loadBatteryData()
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
                        val updated = it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            batteryHealth = healthResult.data,
                            carStatus = statusResult.data.status,
                            units = statusResult.data.units,
                            error = null
                        )
                        updated.copy(
                            stats = computeBatteryStats(
                                health = updated.batteryHealth,
                                status = updated.carStatus,
                                fallbackEfficiency = updated.ratedEfficiency,
                                defaultCapacityKwh = updated.originalCapacity
                            )
                        )
                    }
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
}
