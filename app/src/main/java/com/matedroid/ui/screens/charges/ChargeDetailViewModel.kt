package com.matedroid.ui.screens.charges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.ChargeDetail
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.local.entity.SavedTripLeg
import com.matedroid.data.model.Currency
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.domain.LegRef
import com.matedroid.domain.TripRepository
import com.matedroid.domain.model.Trip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChargeDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val chargeDetail: ChargeDetail? = null,
    val units: Units? = null,
    val stats: ChargeDetailStats? = null,
    val currencySymbol: String = "€",
    val isDcCharge: Boolean = false,
    val containingTrip: Pair<Long, Trip>? = null
)

data class ChargeDetailStats(
    val powerMax: Int,
    val powerMin: Int,
    val powerAvg: Double,
    val voltageMax: Int,
    val voltageMin: Int,
    val voltageAvg: Double,
    val currentMax: Int,
    val currentMin: Int,
    val currentAvg: Double,
    val tempMax: Double,
    val tempMin: Double,
    val tempAvg: Double,
    val batteryStart: Int,
    val batteryEnd: Int,
    val batteryAdded: Int,
    val energyAdded: Double,
    val energyUsed: Double,
    val efficiency: Double,
    val durationMin: Int,
    val cost: Double?
)

@HiltViewModel
class ChargeDetailViewModel @Inject constructor(
    private val repository: TeslamateRepository,
    private val settingsDataStore: SettingsDataStore,
    private val tripRepository: TripRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChargeDetailUiState())
    val uiState: StateFlow<ChargeDetailUiState> = _uiState.asStateFlow()

    private var carId: Int? = null
    private var chargeId: Int? = null

    init {
        loadCurrency()
    }

    private fun loadCurrency() {
        viewModelScope.launch {
            val settings = settingsDataStore.settings.first()
            val currency = Currency.findByCode(settings.currencyCode)
            _uiState.update { it.copy(currencySymbol = currency.symbol) }
        }
    }

    fun loadChargeDetail(carId: Int, chargeId: Int) {
        if (this.carId == carId && this.chargeId == chargeId && _uiState.value.chargeDetail != null) {
            return // Already loaded
        }

        this.carId = carId
        this.chargeId = chargeId

        viewModelScope.launch {
            val containing = tripRepository.findTripContaining(carId, SavedTripLeg.TYPE_CHARGE, chargeId)
            _uiState.update { it.copy(containingTrip = containing) }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Fetch charge detail and units in parallel
            val detailResult = repository.getChargeDetail(carId, chargeId)
            val statusResult = repository.getCarStatus(carId)

            val units = when (statusResult) {
                is ApiResult.Success -> statusResult.data.units
                is ApiResult.Error -> null
            }

            when (detailResult) {
                is ApiResult.Success -> {
                    val detail = detailResult.data
                    val stats = ChargeStatsCalculator.calculateStats(detail)
                    val isDcCharge = ChargeStatsCalculator.detectDcCharge(detail)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            chargeDetail = detail,
                            units = units,
                            stats = stats,
                            isDcCharge = isDcCharge,
                            error = null
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = detailResult.message
                        )
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Detach this charge from its containing saved trip (auto-transitions the trip to USER_EDITED). */
    fun removeFromTrip() {
        val tripId = _uiState.value.containingTrip?.first ?: return
        val charge = chargeId ?: return
        viewModelScope.launch {
            tripRepository.removeLegFromTrip(tripId, LegRef(SavedTripLeg.TYPE_CHARGE, charge))
            _uiState.update { it.copy(containingTrip = null) }
        }
    }

}
