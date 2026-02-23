package com.matedroid.ui.screens.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.model.Currency
import com.matedroid.data.repository.GeocodeProgressInfo
import com.matedroid.data.repository.StatsRepository
import com.matedroid.data.sync.DataSyncWorker
import com.matedroid.data.sync.SyncLogCollector
import com.matedroid.data.sync.SyncManager
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.domain.model.CarStats
import com.matedroid.domain.model.SyncPhase
import com.matedroid.domain.model.SyncProgress
import com.matedroid.domain.model.YearFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSyncing: Boolean = false,
    val carStats: CarStats? = null,
    val availableYears: List<Int> = emptyList(),
    val selectedYearFilter: YearFilter = YearFilter.AllTime,
    val deepSyncProgress: Float = 0f,
    val syncProgress: SyncProgress? = null,
    val geocodeProgress: GeocodeProgressInfo? = null,
    val isGeocoding: Boolean = false,
    val currencySymbol: String = "€",
    val error: String? = null,
    val isUpdating: Boolean = false
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val statsRepository: StatsRepository,
    private val syncManager: SyncManager,
    private val syncLogCollector: SyncLogCollector,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    /** Sync logs for debug viewing */
    val syncLogs: StateFlow<List<String>> = syncLogCollector.logs

    /** Expose sync status for UI to observe */
    val syncStatus = syncManager.syncStatus

    private var carId: Int? = null
    private var syncObserverJob: Job? = null
    private var progressObserverJob: Job? = null
    private var geocodeProgressJob: Job? = null

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val settings = settingsDataStore.settings.first()
            val currency = Currency.findByCode(settings.currencyCode)
            _uiState.update { it.copy(currencySymbol = currency.symbol) }
        }
    }

    fun setCarId(id: Int) {
        carId = id
        loadStats()
        startObservingSyncStatus()
        startObservingProgress(id)
        startObservingGeocodeProgress(id)
    }

    /**
     * Observe sync progress directly from Room's reactive queries.
     * This provides real-time updates as aggregates are written to the database.
     */
    private fun startObservingProgress(id: Int) {
        progressObserverJob?.cancel()
        progressObserverJob = viewModelScope.launch {
            statsRepository.observeDeepSyncProgress(id).collect { progress ->
                _uiState.update { it.copy(deepSyncProgress = progress) }
            }
        }
    }

    /**
     * Observe geocoding progress for location identification.
     * Shows progress bar while locations are being identified in background.
     */
    private fun startObservingGeocodeProgress(id: Int) {
        geocodeProgressJob?.cancel()
        geocodeProgressJob = viewModelScope.launch {
            statsRepository.observeGeocodeProgress(id).collect { progress ->
                _uiState.update {
                    it.copy(
                        geocodeProgress = progress,
                        isGeocoding = progress != null && progress.processed < progress.total
                    )
                }
            }
        }
    }

    /**
     * Observe sync status changes to update isSyncing state.
     * Progress updates are handled separately by observeDeepSyncProgress Flow.
     */
    private fun startObservingSyncStatus() {
        syncObserverJob?.cancel()
        syncObserverJob = viewModelScope.launch {
            syncManager.syncStatus.collect { status ->
                val id = carId ?: return@collect
                val carProgress = status.carProgresses[id]

                // Update syncing state
                val isSyncing = carProgress != null &&
                    carProgress.phase != SyncPhase.COMPLETE &&
                    carProgress.phase != SyncPhase.ERROR &&
                    carProgress.phase != SyncPhase.IDLE

                _uiState.update { it.copy(isSyncing = isSyncing, syncProgress = carProgress) }

                // Reload full stats when sync completes (not during sync - too expensive)
                if (carProgress?.phase == SyncPhase.COMPLETE) {
                    loadStatsInternal()
                }
            }
        }
    }

    fun setYearFilter(yearFilter: YearFilter) {
        _uiState.update { it.copy(selectedYearFilter = yearFilter, isUpdating = true) }
        viewModelScope.launch {
            loadStatsInternal()
            _uiState.update { it.copy(isUpdating = false) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            // Trigger sync to fetch new data from server
            triggerSync()
            loadStatsInternal()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * Trigger a background sync to fetch new data from the server.
     * Uses KEEP policy to avoid interrupting a running sync.
     */
    fun triggerSync() {
        // Skip if sync is already running
        if (syncManager.syncStatus.value.isAnySyncing) {
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<DataSyncWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(DataSyncWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            DataSyncWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP, // Don't interrupt running sync
            syncRequest
        )
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun loadStats() {
        viewModelScope.launch {
            // Only show initial loading state if the screen is empty
            if (_uiState.value.carStats == null) {
                _uiState.update { it.copy(isLoading = true) }
            }
            loadStatsInternal()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Fetch drives between two dates for displaying range record details.
     */
    suspend fun getDrivesForRangeRecord(fromDate: String, toDate: String): List<DriveSummary> {
        val id = carId ?: return emptyList()
        return statsRepository.getDrivesBetweenDates(id, fromDate, toDate)
    }

    private suspend fun loadStatsInternal() {
        val id = carId ?: return

        try {
            // Check if we have any data
            val hasData = statsRepository.hasData(id)

            // Get deep sync progress (even if no data yet)
            val deepProgress = statsRepository.getDeepSyncProgress(id)

            if (!hasData) {
                // No data yet - show sync progress if available
                _uiState.update {
                    it.copy(
                        carStats = null,
                        deepSyncProgress = deepProgress,
                        error = null // Don't show error, show empty state with progress
                    )
                }
                return
            }

            // Load available years for filter
            val years = statsRepository.getAvailableYears(id)

            // Load stats with current filter
            val yearFilter = _uiState.value.selectedYearFilter
            val stats = statsRepository.getStats(id, yearFilter)

            _uiState.update {
                it.copy(
                    carStats = stats,
                    availableYears = years,
                    deepSyncProgress = deepProgress,
                    syncProgress = stats.syncProgress,
                    error = null
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    error = e.message ?: "Failed to load stats"
                )
            }
        }
    }
}
