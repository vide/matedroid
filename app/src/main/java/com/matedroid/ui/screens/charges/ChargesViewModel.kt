package com.matedroid.ui.screens.charges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.ChargeData
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.model.Currency
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

enum class ChartGranularity {
    DAILY, WEEKLY, MONTHLY
}

enum class DateFilter(val label: String, val days: Long?) {
    LAST_7_DAYS("Last 7 days", 7),
    LAST_30_DAYS("Last 30 days", 30),
    LAST_90_DAYS("Last 90 days", 90),
    LAST_YEAR("Last year", 365),
    ALL_TIME("All time", null)
}

enum class ChargeTypeFilter(val label: String) {
    ALL("All"),
    AC("AC"),
    DC("DC")
}

data class ChargeChartData(
    val label: String,
    val count: Int,
    val totalEnergy: Double,
    val sortKey: Long // For sorting (epoch day, week number, or year-month)
)

data class ChargesUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val charges: List<ChargeData> = emptyList(),
    val dcChargeIds: Set<Int> = emptySet(),
    val processedChargeIds: Set<Int> = emptySet(),  // Charges that have aggregate data
    val chartData: List<ChargeChartData> = emptyList(),
    val chartGranularity: ChartGranularity = ChartGranularity.MONTHLY,
    val error: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val selectedFilter: DateFilter = DateFilter.LAST_7_DAYS,  // Preserve filter in ViewModel
    val chargeTypeFilter: ChargeTypeFilter = ChargeTypeFilter.ALL,
    val scrollPosition: Int = 0,  // First visible item index
    val scrollOffset: Int = 0,    // Scroll offset within first item
    val summary: ChargesSummary = ChargesSummary(),
    val currencySymbol: String = "€",
    val teslamateBaseUrl: String = ""
)

data class ChargesSummary(
    val totalCharges: Int = 0,
    val totalEnergyAdded: Double = 0.0,
    val totalCost: Double = 0.0,
    val avgEnergyPerCharge: Double = 0.0,
    val avgCostPerCharge: Double = 0.0
)

@HiltViewModel
class ChargesViewModel @Inject constructor(
    private val repository: TeslamateRepository,
    private val settingsDataStore: SettingsDataStore,
    private val aggregateDao: AggregateDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChargesUiState())
    val uiState: StateFlow<ChargesUiState> = _uiState.asStateFlow()

    private var carId: Int? = null
    private var showShortDrivesCharges: Boolean = false
    private var allCharges: List<ChargeData> = emptyList()

    companion object {
        private const val MIN_ENERGY_KWH = 0.1
    }

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val settings = settingsDataStore.settings.first()
            val currency = Currency.findByCode(settings.currencyCode)
            _uiState.update {
                it.copy(
                    currencySymbol = currency.symbol,
                    teslamateBaseUrl = settings.teslamateBaseUrl
                )
            }
        }
    }

    fun setCarId(id: Int) {
        if (carId != id) {
            carId = id
            // Apply default filter on first load
            setDateFilter(_uiState.value.selectedFilter)
        }
    }

    fun setDateFilter(filter: DateFilter) {
        val endDate = LocalDate.now()
        val startDate = filter.days?.let { endDate.minusDays(it) }
        _uiState.update { it.copy(startDate = startDate, endDate = if (filter.days != null) endDate else null, selectedFilter = filter) }
        loadCharges(startDate, if (filter.days != null) endDate else null)
    }

    fun clearDateFilter() {
        _uiState.update { it.copy(startDate = null, endDate = null, selectedFilter = DateFilter.ALL_TIME) }
        loadCharges(null, null)
    }

    fun refresh() {
        carId?.let {
            _uiState.update { it.copy(isRefreshing = true) }
            val state = _uiState.value
            loadCharges(state.startDate, state.endDate)
        }
    }

    fun setChargeTypeFilter(filter: ChargeTypeFilter) {
        val currentFilter = _uiState.value.chargeTypeFilter
        // Toggle: if same filter is selected, reset to ALL
        val newFilter = if (filter == currentFilter && filter != ChargeTypeFilter.ALL) {
            ChargeTypeFilter.ALL
        } else {
            filter
        }
        _uiState.update { it.copy(chargeTypeFilter = newFilter) }
        applyFiltersAndUpdateState()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun saveScrollPosition(firstVisibleIndex: Int, offset: Int) {
        _uiState.update { it.copy(scrollPosition = firstVisibleIndex, scrollOffset = offset) }
    }

    private fun loadCharges(startDate: LocalDate? = null, endDate: LocalDate? = null) {
        val id = carId ?: return

        viewModelScope.launch {
            val state = _uiState.value
            if (!state.isRefreshing) {
                _uiState.update { it.copy(isLoading = true) }
            }

            // Load the display setting
            showShortDrivesCharges = settingsDataStore.showShortDrivesCharges.first()

            // API expects RFC3339 format: 2006-01-02T15:04:05Z
            val startDateStr = startDate?.let { "${it}T00:00:00Z" }
            val endDateStr = endDate?.let { "${it}T23:59:59Z" }

            // Fetch charge IDs from local database aggregates
            val dcChargeIds = try {
                aggregateDao.getDcChargeIds(id).toSet()
            } catch (e: Exception) {
                emptySet()
            }
            val processedChargeIds = try {
                aggregateDao.getAllProcessedChargeIds(id).toSet()
            } catch (e: Exception) {
                emptySet()
            }

            when (val result = repository.getCharges(id, startDateStr, endDateStr)) {
                is ApiResult.Success -> {
                    allCharges = result.data
                    // Calculate summary and chart from ALL charges (including minimal ones)
                    val summary = calculateSummary(allCharges)
                    val granularity = determineGranularity(startDate, endDate)
                    val chartData = calculateChartData(allCharges, granularity)

                    _uiState.update {
                        it.copy(
                            dcChargeIds = dcChargeIds,
                            processedChargeIds = processedChargeIds,
                            chartData = chartData,
                            chartGranularity = granularity,
                            summary = summary,
                            error = null
                        )
                    }

                    applyFiltersAndUpdateState()
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = result.message
                        )
                    }
                }
            }
        }
    }

    private fun applyFiltersAndUpdateState() {
        val state = _uiState.value
        val chargeTypeFilter = state.chargeTypeFilter
        val dcChargeIds = state.dcChargeIds

        // First apply short charges filter
        var filteredCharges = if (showShortDrivesCharges) {
            allCharges
        } else {
            allCharges.filter { charge ->
                (charge.chargeEnergyAdded ?: 0.0) > MIN_ENERGY_KWH
            }
        }

        // Then apply charge type filter (AC/DC)
        filteredCharges = when (chargeTypeFilter) {
            ChargeTypeFilter.ALL -> filteredCharges
            ChargeTypeFilter.DC -> filteredCharges.filter { it.chargeId in dcChargeIds }
            ChargeTypeFilter.AC -> filteredCharges.filter { it.chargeId !in dcChargeIds }
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                charges = filteredCharges
            )
        }
    }

    private fun determineGranularity(startDate: LocalDate?, endDate: LocalDate?): ChartGranularity {
        if (startDate == null || endDate == null) return ChartGranularity.MONTHLY
        val days = ChronoUnit.DAYS.between(startDate, endDate)
        return when {
            days <= 30 -> ChartGranularity.DAILY
            days <= 90 -> ChartGranularity.WEEKLY
            else -> ChartGranularity.MONTHLY
        }
    }

    private fun calculateChartData(charges: List<ChargeData>, granularity: ChartGranularity): List<ChargeChartData> {
        if (charges.isEmpty()) return emptyList()

        val formatter = DateTimeFormatter.ISO_DATE_TIME
        val weekFields = WeekFields.of(Locale.getDefault())

        return charges
            .mapNotNull { charge ->
                charge.startDate?.let { dateStr ->
                    try {
                        val date = LocalDate.parse(dateStr, formatter)
                        val (label, sortKey) = when (granularity) {
                            ChartGranularity.DAILY -> {
                                val dayLabel = date.format(DateTimeFormatter.ofPattern("d/M"))
                                dayLabel to date.toEpochDay()
                            }
                            ChartGranularity.WEEKLY -> {
                                val weekOfYear = date.get(weekFields.weekOfWeekBasedYear())
                                val year = date.get(weekFields.weekBasedYear())
                                "W$weekOfYear" to (year * 100L + weekOfYear)
                            }
                            ChartGranularity.MONTHLY -> {
                                val yearMonth = YearMonth.of(date.year, date.month)
                                yearMonth.format(DateTimeFormatter.ofPattern("MMM yy")) to (date.year * 12L + date.monthValue)
                            }
                        }
                        Triple(label, sortKey, charge)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            .groupBy { Pair(it.first, it.second) }
            .map { (key, chargesInPeriod) ->
                ChargeChartData(
                    label = key.first,
                    count = chargesInPeriod.size,
                    totalEnergy = chargesInPeriod.sumOf { it.third.chargeEnergyAdded ?: 0.0 },
                    sortKey = key.second
                )
            }
            .sortedBy { it.sortKey }
    }

    private fun calculateSummary(charges: List<ChargeData>): ChargesSummary {
        if (charges.isEmpty()) return ChargesSummary()

        val totalEnergy = charges.sumOf { it.chargeEnergyAdded ?: 0.0 }
        val totalCost = charges.sumOf { it.cost ?: 0.0 }
        val count = charges.size

        return ChargesSummary(
            totalCharges = count,
            totalEnergyAdded = totalEnergy,
            totalCost = totalCost,
            avgEnergyPerCharge = if (count > 0) totalEnergy / count else 0.0,
            avgCostPerCharge = if (count > 0) totalCost / count else 0.0
        )
    }
}
