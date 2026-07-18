package com.matedroid.ui.screens.costs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.Units
import com.matedroid.data.model.Currency
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.domain.CostAnalyticsMetrics
import com.matedroid.domain.CostAnalyticsRepository
import com.matedroid.domain.IceAssumptions
import com.matedroid.domain.IceComparisonResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Selectable ranges for the Cost Analytics screen. Values mirror the widget's
 * options so users see consistent numbers across both surfaces.
 */
enum class CostAnalyticsRange(val days: Int?) {
    SevenDays(7),
    ThirtyDays(30),
    NinetyDays(90),
    AllTime(null);

    companion object {
        val DEFAULT = ThirtyDays
    }
}

data class CostAnalyticsUiState(
    val isLoading: Boolean = true,
    val range: CostAnalyticsRange = CostAnalyticsRange.DEFAULT,
    val metrics: CostAnalyticsMetrics = CostAnalyticsMetrics(),
    val currencySymbol: String = Currency.DEFAULT.symbol,
    val units: Units? = null,
    val iceAssumptions: IceAssumptions = IceAssumptions(),
    val iceComparison: IceComparisonResult? = null,
    val error: String? = null,
)

@HiltViewModel
class CostAnalyticsViewModel @Inject constructor(
    private val costRepository: CostAnalyticsRepository,
    private val teslamateRepository: TeslamateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CostAnalyticsUiState())
    val uiState: StateFlow<CostAnalyticsUiState> = _uiState.asStateFlow()

    private var carId: Int? = null

    fun setCarId(id: Int) {
        if (carId == id) return
        carId = id
        loadUnits(id)
        reload()
    }

    fun setRange(range: CostAnalyticsRange) {
        if (_uiState.value.range == range) return
        _uiState.update { it.copy(range = range) }
        reload()
    }

    private fun loadUnits(carId: Int) {
        viewModelScope.launch {
            when (val result = teslamateRepository.getCarStatus(carId)) {
                is ApiResult.Success -> {
                    // Cache only — Cost Analytics labels Room data with the synced unit.
                    result.data.units.unitOfLength
                        ?.takeIf { it == "km" || it == "mi" }
                        ?.let { costRepository.cacheUnitOfLength(it) }
                }
                is ApiResult.Error -> Unit
            }
        }
    }

    private fun reload() {
        val id = carId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val days = _uiState.value.range.days
                val range = days?.let { CostAnalyticsRepository.DateRange.lastDays(it.toLong()) }
                val snapshot = costRepository.loadMetrics(id, range)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        metrics = snapshot.metrics,
                        currencySymbol = snapshot.currencySymbol,
                        // Always label with the unit cached alongside Room summaries.
                        units = Units(unitOfLength = snapshot.unitOfLength),
                        iceAssumptions = snapshot.iceAssumptions,
                        iceComparison = snapshot.iceComparison,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load cost analytics",
                    )
                }
            }
        }
    }
}
