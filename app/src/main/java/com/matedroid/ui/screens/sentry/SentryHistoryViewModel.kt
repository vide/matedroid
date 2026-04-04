package com.matedroid.ui.screens.sentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.local.SentryStateDataStore
import com.matedroid.data.local.dao.SentryAlertLogDao
import com.matedroid.data.local.entity.SentryAlertLog
import com.matedroid.data.repository.GeocodingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class DayGroup(
    val dateMillis: Long,
    val alerts: List<SentryAlertLog>
)

data class SentryHistoryUiState(
    val isLoading: Boolean = true,
    val isSessionActive: Boolean = false,
    val sessionStartedAt: Long = 0L,
    val currentSessionAlerts: List<SentryAlertLog> = emptyList(),
    val pastAlertsByDay: List<DayGroup> = emptyList(),
    /** 72-element array: index 0 = oldest hour (72h ago), index 71 = current hour */
    val heatmapCounts: IntArray = IntArray(HEATMAP_BLOCKS),
    /** Epoch millis of the start of the heatmap window (floored to hour boundary) */
    val heatmapStartMillis: Long = 0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SentryHistoryUiState) return false
        return isLoading == other.isLoading &&
            isSessionActive == other.isSessionActive &&
            sessionStartedAt == other.sessionStartedAt &&
            currentSessionAlerts == other.currentSessionAlerts &&
            pastAlertsByDay == other.pastAlertsByDay &&
            heatmapCounts.contentEquals(other.heatmapCounts) &&
            heatmapStartMillis == other.heatmapStartMillis
    }

    override fun hashCode(): Int {
        var result = isLoading.hashCode()
        result = 31 * result + isSessionActive.hashCode()
        result = 31 * result + sessionStartedAt.hashCode()
        result = 31 * result + currentSessionAlerts.hashCode()
        result = 31 * result + pastAlertsByDay.hashCode()
        result = 31 * result + heatmapCounts.contentHashCode()
        result = 31 * result + heatmapStartMillis.hashCode()
        return result
    }
}

const val HEATMAP_BLOCKS = 72
const val HEATMAP_COLS = 12
const val HEATMAP_ROWS = 6
/** Each block covers 2 hours → 72 blocks = 144 hours = 6 days */
const val HEATMAP_BUCKET_HOURS = 2
const val HEATMAP_BUCKET_MS = HEATMAP_BUCKET_HOURS * 3_600_000L

@HiltViewModel
class SentryHistoryViewModel @Inject constructor(
    private val sentryAlertLogDao: SentryAlertLogDao,
    private val sentryStateDataStore: SentryStateDataStore,
    private val geocodingRepository: GeocodingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SentryHistoryUiState())
    val uiState: StateFlow<SentryHistoryUiState> = _uiState.asStateFlow()

    private var carId: Int? = null
    private val geocodedIds = mutableSetOf<Long>()

    fun setCarId(id: Int) {
        if (carId != id) {
            carId = id
            loadHistory(id)
            loadHeatmap(id)
        }
    }

    private fun loadHeatmap(carId: Int) {
        viewModelScope.launch {
            // Align to midnight: each row = one calendar day, 6 rows = 6 days
            val todayMidnight = java.time.LocalDate.now()
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val heatmapStart = todayMidnight - (HEATMAP_ROWS - 1).toLong() * 24 * 3_600_000L

            val hourlyCounts = sentryAlertLogDao.countByHour(carId, heatmapStart)
            // Map 1-hour buckets from the DAO into our 2-hour blocks
            val counts = IntArray(HEATMAP_BLOCKS)
            for (hc in hourlyCounts) {
                val alertMillis = hc.hourBucket * 3_600_000L
                val index = ((alertMillis - heatmapStart) / HEATMAP_BUCKET_MS).toInt()
                if (index in counts.indices) {
                    counts[index] += hc.cnt
                }
            }

            _uiState.update { it.copy(heatmapCounts = counts, heatmapStartMillis = heatmapStart) }
        }
    }

    private fun loadHistory(carId: Int) {
        viewModelScope.launch {
            val state = sentryStateDataStore.getState(carId)
            _uiState.update {
                it.copy(
                    isSessionActive = state.sentryActive,
                    sessionStartedAt = state.sessionStartedAt
                )
            }

            sentryAlertLogDao.observeAll(carId).collect { allAlerts ->
                val sessionStartedAt = _uiState.value.sessionStartedAt
                val currentSession = if (sessionStartedAt > 0L) {
                    allAlerts.filter { it.sessionStartedAt == sessionStartedAt }
                } else {
                    emptyList()
                }
                val pastAlerts = allAlerts.filter {
                    sessionStartedAt == 0L || it.sessionStartedAt != sessionStartedAt
                }

                val grouped = pastAlerts
                    .groupBy { alert ->
                        Instant.ofEpochMilli(alert.detectedAt)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    }
                    .map { (dayMillis, alerts) -> DayGroup(dayMillis, alerts) }
                    .sortedByDescending { it.dateMillis }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentSessionAlerts = currentSession,
                        pastAlertsByDay = grouped
                    )
                }

                // Lazily reverse geocode alerts that have coordinates but no address
                resolveAddresses(allAlerts)

                // Refresh heatmap when alerts change
                loadHeatmap(carId)
            }
        }
    }

    private fun resolveAddresses(alerts: List<SentryAlertLog>) {
        for (alert in alerts) {
            if (alert.address == null && alert.latitude != null && alert.longitude != null && alert.id !in geocodedIds) {
                geocodedIds.add(alert.id)
                viewModelScope.launch {
                    // Try the geocode cache first -- sentry position is the end of the
                    // last drive, so it should already be cached from drive geocoding.
                    val cached = geocodingRepository.getFromCache(alert.latitude, alert.longitude)
                    val address = if (cached != null) {
                        listOfNotNull(cached.city, cached.regionName)
                            .joinToString(", ")
                            .ifBlank { null }
                    } else {
                        // Fall back to Nominatim API if not in cache
                        geocodingRepository.reverseGeocode(alert.latitude, alert.longitude)
                    }
                    if (address != null) {
                        sentryAlertLogDao.updateAddress(alert.id, address)
                    }
                }
            }
        }
    }
}
