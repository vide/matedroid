package com.matedroid.ui.screens.charges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.ChargeDetail
import com.matedroid.data.api.models.ChargePoint
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.ChargeSessionStateDataStore
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.inject.Inject

data class CurrentChargeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val chargeDetail: ChargeDetail? = null,
    val units: Units? = null,
    val stats: ChargeDetailStats? = null,
    val isDcCharge: Boolean = false,
    val isUnsupportedApi: Boolean = false,
    val isNotCharging: Boolean = false,
    val isDcFinishedPluggedIn: Boolean = false,
    val dcFinishedSince: String? = null,
    val timeToFullCharge: Double? = null,
    val chargeLimitSoc: Int? = null,
    /** Charge points in chronological order (reversed from API's newest-first) */
    val chronologicalPoints: List<ChargePoint> = emptyList()
)

@HiltViewModel
class CurrentChargeViewModel @Inject constructor(
    private val repository: TeslamateRepository,
    private val chargeSessionStateDataStore: ChargeSessionStateDataStore
) : ViewModel() {

    companion object {
        private const val REFRESH_INTERVAL_MS = 30_000L
    }

    private val _uiState = MutableStateFlow(CurrentChargeUiState())
    val uiState: StateFlow<CurrentChargeUiState> = _uiState.asStateFlow()

    private var carId: Int? = null
    private var refreshJob: Job? = null

    fun loadCurrentCharge(carId: Int) {
        this.carId = carId
        startRefreshLoop()
    }

    private fun startRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                fetchData()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private suspend fun fetchData() {
        val carId = this.carId ?: return

        if (_uiState.value.chargeDetail == null) {
            _uiState.update { it.copy(isLoading = true, error = null) }
        }

        // Fetch current charge and car status concurrently
        val chargeResult = repository.getCurrentCharge(carId)
        val statusResult = repository.getCarStatus(carId)

        val units = when (statusResult) {
            is ApiResult.Success -> statusResult.data.units
            is ApiResult.Error -> _uiState.value.units
        }
        val timeToFullCharge = when (statusResult) {
            is ApiResult.Success -> statusResult.data.status.timeToFullCharge
            is ApiResult.Error -> _uiState.value.timeToFullCharge
        }
        val chargeLimitSoc = when (statusResult) {
            is ApiResult.Success -> statusResult.data.status.chargeLimitSoc
            is ApiResult.Error -> _uiState.value.chargeLimitSoc
        }
        val isDcChargeFromStatus = when (statusResult) {
            is ApiResult.Success -> statusResult.data.status.isDcCharging
            is ApiResult.Error -> null  // No status data available -> assume AC
        }
        val status = (statusResult as? ApiResult.Success)?.data?.status

        // Persist the DC flag while the session is still live; post-completion we can
        // only tell whether a session was DC from this stored value.
        if (status?.isCharging == true && status.isDcCharging) {
            chargeSessionStateDataStore.setLastSessionDc(carId, true)
        } else if (status?.pluggedIn == false) {
            chargeSessionStateDataStore.clear(carId)
        }

        val wasDcSession = chargeSessionStateDataStore.wasLastSessionDc(carId)
        val isDcFinishedPluggedIn = status?.isChargeCompletePluggedIn == true && wasDcSession
        val stateSince = status?.stateSince

        when (chargeResult) {
            is ApiResult.Success -> {
                val detail = chargeResult.data

                // API returns charge_details sorted newest-first; reverse to chronological
                val chronoPoints = detail.chargePoints?.reversed() ?: emptyList()
                val detailWithChronoPoints = detail.copy(chargePoints = chronoPoints)

                val stats = ChargeStatsCalculator.calculateStats(detailWithChronoPoints)
                val isDcCharge = isDcChargeFromStatus ?: ChargeStatsCalculator.detectDcCharge(detailWithChronoPoints)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        chargeDetail = detailWithChronoPoints,
                        units = units,
                        stats = stats,
                        isDcCharge = isDcCharge,
                        isUnsupportedApi = false,
                        isNotCharging = detail.isCharging == false && !isDcFinishedPluggedIn,
                        isDcFinishedPluggedIn = isDcFinishedPluggedIn,
                        dcFinishedSince = if (isDcFinishedPluggedIn) stateSince else null,
                        timeToFullCharge = timeToFullCharge,
                        chargeLimitSoc = chargeLimitSoc,
                        chronologicalPoints = chronoPoints,
                        error = null
                    )
                }
            }
            is ApiResult.Error -> {
                when (chargeResult.code) {
                    404 -> {
                        _uiState.update {
                            it.copy(isLoading = false, isUnsupportedApi = true, error = null)
                        }
                        refreshJob?.cancel()
                    }
                    null -> {
                        if (isDcFinishedPluggedIn) {
                            // DC charge finished but still plugged — keep showing last data with warning
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    isNotCharging = false,
                                    isDcFinishedPluggedIn = true,
                                    dcFinishedSince = stateSince,
                                    error = null
                                )
                            }
                            // Keep refresh loop running to detect unplug
                        } else {
                            // Charging has stopped and cable unplugged
                            _uiState.update {
                                it.copy(isLoading = false, isNotCharging = true, isDcFinishedPluggedIn = false, error = null)
                            }
                            refreshJob?.cancel()
                        }
                    }
                    else -> {
                        _uiState.update {
                            it.copy(isLoading = false, error = chargeResult.message)
                        }
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
