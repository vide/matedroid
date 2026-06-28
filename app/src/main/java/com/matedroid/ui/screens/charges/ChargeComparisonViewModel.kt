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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How the comparison leaderboard is ranked. */
enum class CompareSort { PEAK, DURATION, COST }

data class ChargeComparisonUiState(
    val isLoading: Boolean = true,
    val comparison: ChargeComparison? = null,
    val sort: CompareSort = CompareSort.PEAK,
    val currencySymbol: String = "€",
    val units: Units? = null
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

    fun load(carId: Int, baseChargeId: Int) {
        if (loaded) return
        loaded = true

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
        }
    }

    fun setSort(sort: CompareSort) {
        _uiState.update { it.copy(sort = sort) }
    }
}
