package com.matedroid.ui.screens.charges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.model.Currency
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.domain.ChargeComparison
import com.matedroid.domain.ChargeComparisonRepository
import com.matedroid.domain.ComparableCharge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How the comparison leaderboard is ranked. */
enum class CompareSort { PEAK, DURATION, COST }

/** One point of a charging curve: power (kW) at a given state of charge (%). */
data class CurvePoint(val soc: Float, val power: Float)

/** A session's charging curve in power-vs-SoC space, for the overlay chart. */
data class SessionCurve(
    val chargeId: Int,
    val isBase: Boolean,
    val points: List<CurvePoint>
)

data class ChargeComparisonUiState(
    val isLoading: Boolean = true,
    val comparison: ChargeComparison? = null,
    val sort: CompareSort = CompareSort.PEAK,
    val currencySymbol: String = "€",
    val units: Units? = null,
    val curves: List<SessionCurve> = emptyList(),
    val curvesLoading: Boolean = false
)

@HiltViewModel
class ChargeComparisonViewModel @Inject constructor(
    private val comparisonRepository: ChargeComparisonRepository,
    private val repository: TeslamateRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChargeComparisonUiState())
    val uiState: StateFlow<ChargeComparisonUiState> = _uiState.asStateFlow()

    private var loaded = false
    private var carId: Int? = null

    /** Cache of fetched curves by charge id, so re-sorting the overlay doesn't refetch details. */
    private val curveCache = mutableMapOf<Int, SessionCurve>()
    private var curveFetchJob: Job? = null

    fun load(carId: Int, baseChargeId: Int) {
        if (loaded) return
        loaded = true
        this.carId = carId

        viewModelScope.launch {
            val settings = settingsDataStore.settings.first()
            val currency = Currency.findByCode(settings.currencyCode)
            val units = when (val status = repository.getCarStatus(carId)) {
                is ApiResult.Success -> status.data.units
                is ApiResult.Error -> null
            }
            val comparison = comparisonRepository.findComparable(carId, baseChargeId)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    comparison = comparison,
                    currencySymbol = currency.symbol,
                    units = units
                )
            }
            refreshCurves(carId)
        }
    }

    fun setSort(sort: CompareSort) {
        _uiState.update { it.copy(sort = sort) }
        carId?.let { refreshCurves(it) }
    }

    /** Pick the base + the top others by the current sort, and overlay at most [MAX_CURVES] curves. */
    private fun refreshCurves(carId: Int) {
        val comparison = _uiState.value.comparison ?: return
        val ordered = listOf(comparison.base) +
            sortedOthers(comparison.others, _uiState.value.sort).take(MAX_CURVES - 1)

        curveFetchJob?.cancel()
        curveFetchJob = viewModelScope.launch {
            _uiState.update { it.copy(curvesLoading = true) }
            ordered.forEach { charge ->
                if (!curveCache.containsKey(charge.chargeId)) {
                    buildCurve(carId, charge.chargeId, charge.isBase)?.let { curveCache[charge.chargeId] = it }
                }
            }
            val curves = ordered.mapNotNull { curveCache[it.chargeId] }
            _uiState.update { it.copy(curves = curves, curvesLoading = false) }
        }
    }

    private fun sortedOthers(others: List<ComparableCharge>, sort: CompareSort): List<ComparableCharge> =
        when (sort) {
            CompareSort.PEAK -> others.sortedByDescending { it.peakKw ?: -1 }
            CompareSort.DURATION -> others.sortedBy { it.durationMin }
            CompareSort.COST -> others.sortedBy { it.costPerKwh ?: Double.MAX_VALUE }
        }

    private suspend fun buildCurve(carId: Int, chargeId: Int, isBase: Boolean): SessionCurve? {
        val detail = when (val result = repository.getChargeDetail(carId, chargeId)) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> return null
        }
        val points = detail.chargePoints.orEmpty().mapNotNull { point ->
            val soc = point.batteryLevel?.toFloat()
            val power = point.chargerPower?.toFloat()
            if (soc != null && power != null && power >= 0f) CurvePoint(soc, power) else null
        }
        return if (points.size >= 2) SessionCurve(chargeId, isBase, points) else null
    }

    companion object {
        /** Max curves overlaid at once (base + up to 4 others) — keeps a busy SuC readable. */
        const val MAX_CURVES = 5
    }
}
