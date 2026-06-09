package com.matedroid.ui.screens.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.UpdateData
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class UpdatesStats(
    val totalUpdates: Int = 0,
    val meanDaysBetweenUpdates: Double = 0.0,
    val oldestVersion: String? = null,
    val newestVersion: String? = null
)

data class MonthlyUpdateCount(
    val yearMonth: YearMonth,
    val count: Int
)

data class SoftwareVersionsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val updates: List<SoftwareVersionItem> = emptyList(),
    val stats: UpdatesStats = UpdatesStats(),
    val monthlyData: List<MonthlyUpdateCount> = emptyList(),
    val longestInstalledId: Int? = null,
    val error: String? = null,
    val filterMonths: Int? = null  // null = All time, 6 = Last 6 months, 12 = Last year
)

data class SoftwareVersionItem(
    val id: Int,
    val version: String,
    val installDate: LocalDateTime?,
    val endDate: LocalDateTime?,
    val updateDurationMinutes: Long?,  // Duration of the update process (start to end)
    val daysInstalled: Long?,          // Days the version was installed
    val isCurrent: Boolean
)

@HiltViewModel
class SoftwareVersionsViewModel @Inject constructor(
    private val repository: TeslamateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SoftwareVersionsUiState())
    val uiState: StateFlow<SoftwareVersionsUiState> = _uiState.asStateFlow()

    private var carId: Int? = null
    private var allUpdates: List<UpdateData> = emptyList()

    fun setCarId(id: Int) {
        if (carId != id) {
            carId = id
            loadUpdates()
        }
    }

    fun setFilter(months: Int?) {
        _uiState.update { it.copy(filterMonths = months) }
        applyFilter(months)
    }

    fun refresh() {
        carId?.let {
            _uiState.update { it.copy(isRefreshing = true) }
            loadUpdates()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun loadUpdates() {
        val id = carId ?: return

        viewModelScope.launch {
            val state = _uiState.value
            if (!state.isRefreshing) {
                _uiState.update { it.copy(isLoading = true) }
            }

            when (val result = repository.getUpdates(id)) {
                is ApiResult.Success -> {
                    allUpdates = result.data
                    applyFilter(_uiState.value.filterMonths)
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

    private fun applyFilter(months: Int?) {
        val cutoffDate = months?.let {
            LocalDate.now().minusMonths(it.toLong())
        }

        val filteredUpdates = if (cutoffDate != null) {
            allUpdates.filter { update ->
                val startDate = parseDate(update.startDate)
                startDate == null || startDate.toLocalDate() >= cutoffDate
            }
        } else {
            allUpdates
        }

        // Calculate days installed for each version.
        // The list is newest-first, so item index 0 is the current/newest version and the item
        // at index-1 is the NEWER version that replaced the one at index.
        // Days installed = (replacing version's start date) - (this version's start date).
        // For the current version: now - start date.
        val items = filteredUpdates.mapIndexed { index, update ->
            val startDate = parseDate(update.startDate)
            val endDate = parseDate(update.endDate)
            val isCurrent = index == 0 && (endDate == null || startDate == endDate)

            // Duration of the update process itself (from start to end of update)
            val updateDurationMinutes = if (startDate != null && endDate != null && startDate != endDate) {
                Duration.between(startDate, endDate).toMinutes()
            } else {
                null
            }

            // Days installed = time until next version was installed
            // For current version, calculate from install date to now
            val daysInstalled: Long? = when {
                startDate == null -> null
                isCurrent -> Duration.between(startDate, LocalDateTime.now()).toDays()
                index - 1 >= 0 -> {
                    // The newer version that replaced this one is at index-1; this version was
                    // installed from its own start until that replacing version's start.
                    val replacedByStart = parseDate(filteredUpdates[index - 1].startDate)
                    if (replacedByStart != null) {
                        Duration.between(startDate, replacedByStart).toDays()
                    } else {
                        null
                    }
                }
                else -> null
            }

            SoftwareVersionItem(
                id = update.id ?: 0,
                version = cleanVersion(update.version),
                installDate = startDate,
                endDate = endDate,
                updateDurationMinutes = updateDurationMinutes,
                daysInstalled = daysInstalled,
                isCurrent = isCurrent
            )
        }

        // Find the version that stayed installed the longest (among displayed ones)
        val longestId = items
            .filter { it.daysInstalled != null && it.daysInstalled > 0 }
            .maxByOrNull { it.daysInstalled!! }
            ?.id

        // Calculate stats
        val stats = calculateStats(items)

        // Calculate monthly data for chart
        val monthlyData = calculateMonthlyData(items, months)

        _uiState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                updates = items,
                stats = stats,
                monthlyData = monthlyData,
                longestInstalledId = longestId,
                error = null
            )
        }
    }

    private fun calculateStats(items: List<SoftwareVersionItem>): UpdatesStats {
        if (items.isEmpty()) return UpdatesStats()

        val totalUpdates = items.size
        val newestVersion = items.firstOrNull()?.version
        val oldestVersion = items.lastOrNull()?.version

        // Calculate mean days between updates
        val installDates = items.mapNotNull { it.installDate }.sortedDescending()
        val meanDaysBetween = if (installDates.size >= 2) {
            val intervals = installDates.zipWithNext { newer, older ->
                Duration.between(older, newer).toDays().toDouble()
            }
            intervals.average()
        } else {
            0.0
        }

        return UpdatesStats(
            totalUpdates = totalUpdates,
            meanDaysBetweenUpdates = meanDaysBetween,
            oldestVersion = oldestVersion,
            newestVersion = newestVersion
        )
    }

    private fun calculateMonthlyData(items: List<SoftwareVersionItem>, filterMonths: Int?): List<MonthlyUpdateCount> {
        if (items.isEmpty()) return emptyList()

        // Group updates by month
        val monthCounts = items
            .mapNotNull { it.installDate }
            .groupBy { YearMonth.from(it) }
            .mapValues { it.value.size }

        // Determine the range of months to show
        val maxMonths = when {
            filterMonths != null -> filterMonths
            else -> 24  // For "all time", show max 24 months
        }

        val now = YearMonth.now()
        val startMonth = now.minusMonths(maxMonths.toLong() - 1)

        // Create a list of all months in range with counts (including zeros)
        return (0 until maxMonths).map { offset ->
            val month = startMonth.plusMonths(offset.toLong())
            MonthlyUpdateCount(
                yearMonth = month,
                count = monthCounts[month] ?: 0
            )
        }
    }

    /**
     * Remove the hash suffix from version strings.
     * "2025.44.25.1 abc123def456" -> "2025.44.25.1"
     */
    private fun cleanVersion(version: String?): String {
        if (version == null) return "Unknown"
        // Split by space and take only the first part (the version number)
        return version.split(" ").firstOrNull() ?: version
    }

    private fun parseDate(dateStr: String?): LocalDateTime? {
        if (dateStr == null) return null
        return try {
            LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)
        } catch (e: Exception) {
            try {
                LocalDateTime.parse(dateStr.replace(" ", "T"), DateTimeFormatter.ISO_DATE_TIME)
            } catch (e2: Exception) {
                null
            }
        }
    }
}
