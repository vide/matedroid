package com.matedroid.ui.screens.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.data.local.entity.SavedTripLeg
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.domain.EligibleLegs
import com.matedroid.domain.LegRef
import com.matedroid.domain.TripAggregator
import com.matedroid.domain.TripRepository
import com.matedroid.domain.model.Trip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class CreateTripUiState(
    val showDatePicker: Boolean = true,
    val anchorStart: String? = null,
    val anchorEnd: String? = null,
    val draftLegs: List<LegRef> = emptyList(),
    val drives: List<DriveSummary> = emptyList(),
    val charges: List<ChargeSummary> = emptyList(),
    val preview: Trip? = null,
    val name: String = "",
    val units: Units? = null,
    val dcChargeIds: Set<Int> = emptySet(),
    val showAddLegSheet: Boolean = false,
    val eligibleLegs: EligibleLegs? = null,
    val isSaving: Boolean = false,
    val createdStartDate: String? = null
)

@HiltViewModel
class CreateTripViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val aggregateDao: AggregateDao,
    private val repository: TeslamateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateTripUiState())
    val uiState: StateFlow<CreateTripUiState> = _uiState.asStateFlow()

    private var carId: Int = -1
    private var started = false

    fun start(carId: Int) {
        if (started) return
        started = true
        this.carId = carId
        viewModelScope.launch {
            val dcIds = try { aggregateDao.getDcChargeIds(carId).toSet() } catch (_: Exception) { emptySet() }
            _uiState.update { it.copy(dcChargeIds = dcIds) }
        }
        viewModelScope.launch {
            when (val result = repository.getCarStatus(carId)) {
                is ApiResult.Success -> _uiState.update { it.copy(units = result.data.units) }
                is ApiResult.Error -> {}
            }
        }
    }

    /** The user picked the trip's day → anchor the candidate window and open the leg picker. */
    fun onDatePicked(date: LocalDate) {
        _uiState.update {
            it.copy(
                showDatePicker = false,
                anchorStart = date.atStartOfDay().toString(),
                anchorEnd = date.atTime(23, 59, 59).toString()
            )
        }
        openAddLegSheet()
    }

    fun openAddLegSheet() {
        viewModelScope.launch {
            val st = _uiState.value
            val (anchorStart, anchorEnd) = if (st.draftLegs.isEmpty()) {
                val s = st.anchorStart ?: return@launch
                val e = st.anchorEnd ?: return@launch
                s to e
            } else {
                val dates = st.drives.flatMap { listOf(it.startDate, it.endDate) } +
                    st.charges.flatMap { listOf(it.startDate, it.endDate) }
                (dates.minOrNull() ?: return@launch) to (dates.maxOrNull() ?: return@launch)
            }
            val eligible = tripRepository.getUnusedLegsAround(carId, anchorStart, anchorEnd)
            val draftSet = st.draftLegs.toSet()
            val filtered = EligibleLegs(
                drives = eligible.drives.filter { LegRef(SavedTripLeg.TYPE_DRIVE, it.driveId) !in draftSet },
                charges = eligible.charges.filter { LegRef(SavedTripLeg.TYPE_CHARGE, it.chargeId) !in draftSet }
            )
            _uiState.update { it.copy(showAddLegSheet = true, eligibleLegs = filtered) }
        }
    }

    fun closeAddLegSheet() {
        _uiState.update { it.copy(showAddLegSheet = false, eligibleLegs = null) }
    }

    fun pickLegs(refs: List<LegRef>) {
        if (refs.isEmpty()) {
            closeAddLegSheet()
            return
        }
        viewModelScope.launch {
            rebuild((_uiState.value.draftLegs + refs).distinct())
            _uiState.update { it.copy(showAddLegSheet = false, eligibleLegs = null) }
        }
    }

    fun removeLeg(ref: LegRef) {
        viewModelScope.launch {
            rebuild(_uiState.value.draftLegs.filterNot { it == ref })
        }
    }

    fun setName(name: String) {
        _uiState.update {
            it.copy(
                name = name,
                preview = TripAggregator.buildTrip(it.drives, it.charges, name.trim().ifEmpty { null })
            )
        }
    }

    fun save() {
        val st = _uiState.value
        val startDate = st.preview?.startDate ?: return
        if (st.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            tripRepository.createTrip(carId, st.draftLegs, st.name.trim().ifEmpty { null })
            _uiState.update { it.copy(isSaving = false, createdStartDate = startDate) }
        }
    }

    private suspend fun rebuild(draft: List<LegRef>) {
        val resolved = tripRepository.resolveLegs(carId, draft)
        val drives = resolved.drives.sortedBy { it.startDate }
        val charges = resolved.charges.sortedBy { it.startDate }
        _uiState.update {
            it.copy(
                draftLegs = draft,
                drives = drives,
                charges = charges,
                preview = TripAggregator.buildTrip(drives, charges, it.name.trim().ifEmpty { null })
            )
        }
    }
}
