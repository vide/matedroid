package com.matedroid.data.sync

import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.ChargeSummaryDao
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.local.dao.SyncStateDao
import com.matedroid.data.local.entity.SchemaVersion
import com.matedroid.data.local.entity.SyncState
import com.matedroid.domain.model.OverallSyncStatus
import com.matedroid.domain.model.SyncPhase
import com.matedroid.domain.model.SyncProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages sync state and emits progress updates.
 * This is the single source of truth for sync status across the app.
 */
@Singleton
class SyncManager @Inject constructor(
    private val syncStateDao: SyncStateDao,
    private val driveSummaryDao: DriveSummaryDao,
    private val chargeSummaryDao: ChargeSummaryDao,
    private val aggregateDao: AggregateDao
) {
    private val _syncStatus = MutableStateFlow(OverallSyncStatus.IDLE)
    val syncStatus: StateFlow<OverallSyncStatus> = _syncStatus.asStateFlow()

    private val _carProgress = MutableStateFlow<Map<Int, SyncProgress>>(emptyMap())

    /**
     * Get sync progress for a specific car.
     */
    fun getProgressForCar(carId: Int): SyncProgress? = _carProgress.value[carId]

    /**
     * Check if summaries are synced for a car (Quick Stats available).
     */
    suspend fun areSummariesSynced(carId: Int): Boolean {
        return syncStateDao.get(carId)?.summariesSynced == true
    }

    /**
     * Check if details are synced for a car (Deep Stats available).
     */
    suspend fun areDetailsSynced(carId: Int): Boolean {
        return syncStateDao.get(carId)?.detailsSynced == true
    }

    /**
     * Get or create sync state for a car.
     */
    suspend fun getOrCreateSyncState(carId: Int): SyncState {
        return syncStateDao.get(carId) ?: SyncState(carId = carId).also {
            syncStateDao.upsert(it)
        }
    }

    /**
     * Update progress for summary sync phase.
     */
    fun updateSummaryProgress(carId: Int, message: String? = null) {
        updateProgress(carId, SyncPhase.SYNCING_SUMMARIES, 0, 1, message)
    }

    /**
     * Mark summaries as synced and calculate total items to process.
     */
    suspend fun markSummariesComplete(carId: Int) {
        syncStateDao.markSummariesSynced(carId, System.currentTimeMillis())

        // Calculate total items to process for detail sync
        val unprocessedDrives = driveSummaryDao.countUnprocessedDrives(carId, SchemaVersion.CURRENT)
        val unprocessedCharges = chargeSummaryDao.countUnprocessedCharges(carId, SchemaVersion.CURRENT)

        val state = syncStateDao.get(carId)
        if (state != null) {
            syncStateDao.upsert(
                state.copy(
                    totalDrivesToProcess = unprocessedDrives,
                    totalChargesToProcess = unprocessedCharges,
                    drivesProcessed = 0,
                    chargesProcessed = 0
                )
            )
        }

        // If nothing to process, mark as complete
        if (unprocessedDrives == 0 && unprocessedCharges == 0) {
            markSyncComplete(carId)
        } else {
            updateProgress(
                carId,
                SyncPhase.SYNCING_DRIVE_DETAILS,
                0,
                unprocessedDrives + unprocessedCharges
            )
        }
    }

    /**
     * Update progress for drive detail sync.
     */
    suspend fun updateDriveDetailProgress(carId: Int, driveId: Int) {
        syncStateDao.updateDriveDetailProgress(carId, driveId)

        val state = syncStateDao.get(carId) ?: return
        val total = state.totalDrivesToProcess + state.totalChargesToProcess
        val current = state.drivesProcessed + 1

        updateProgress(carId, SyncPhase.SYNCING_DRIVE_DETAILS, current, total)
    }

    /**
     * Mark drive details as complete and start charge details.
     */
    suspend fun markDriveDetailsComplete(carId: Int) {
        val state = syncStateDao.get(carId) ?: return
        val total = state.totalDrivesToProcess + state.totalChargesToProcess

        if (state.totalChargesToProcess > 0) {
            updateProgress(
                carId,
                SyncPhase.SYNCING_CHARGE_DETAILS,
                state.drivesProcessed,
                total
            )
        } else {
            markSyncComplete(carId)
        }
    }

    /**
     * Update progress for charge detail sync.
     */
    suspend fun updateChargeDetailProgress(carId: Int, chargeId: Int) {
        syncStateDao.updateChargeDetailProgress(carId, chargeId)

        val state = syncStateDao.get(carId) ?: return
        val total = state.totalDrivesToProcess + state.totalChargesToProcess
        val current = state.drivesProcessed + state.chargesProcessed + 1

        updateProgress(carId, SyncPhase.SYNCING_CHARGE_DETAILS, current, total)
    }

    /**
     * Mark sync as complete for a car.
     */
    suspend fun markSyncComplete(carId: Int) {
        syncStateDao.markDetailsSynced(carId)
        updateProgress(carId, SyncPhase.COMPLETE, 1, 1)
        updateOverallStatus()
    }

    /**
     * Mark sync as failed with error.
     */
    fun markSyncError(carId: Int, message: String) {
        updateProgress(carId, SyncPhase.ERROR, 0, 0, message)
        updateOverallStatus()
    }

    /**
     * Reset sync state for a car (keeps cached data, only retries unprocessed items).
     */
    suspend fun resetSync(carId: Int) {
        syncStateDao.resetForResync(carId)
        updateProgress(carId, SyncPhase.IDLE, 0, 0)
        updateOverallStatus()
    }

    /**
     * Full reset for a car: deletes ALL cached data (drives, charges, aggregates)
     * and resets sync state. This forces a complete resync from the API.
     */
    suspend fun fullResetSync(carId: Int) {
        // Delete all cached data
        driveSummaryDao.deleteAllForCar(carId)
        chargeSummaryDao.deleteAllForCar(carId)
        aggregateDao.deleteDriveAggregatesForCar(carId)
        aggregateDao.deleteChargeAggregatesForCar(carId)

        // Reset sync state
        syncStateDao.resetForResync(carId)

        updateProgress(carId, SyncPhase.IDLE, 0, 0)
        updateOverallStatus()
    }

    private fun updateProgress(
        carId: Int,
        phase: SyncPhase,
        current: Int,
        total: Int,
        message: String? = null
    ) {
        val progress = SyncProgress(
            carId = carId,
            phase = phase,
            currentItem = current,
            totalItems = total,
            message = message
        )

        _carProgress.update { map ->
            map + (carId to progress)
        }

        updateOverallStatus()
    }

    private fun updateOverallStatus() {
        val progresses = _carProgress.value
        val isAnySyncing = progresses.values.any {
            it.phase != SyncPhase.COMPLETE &&
            it.phase != SyncPhase.ERROR &&
            it.phase != SyncPhase.IDLE
        }
        val allComplete = progresses.isNotEmpty() && progresses.values.all {
            it.phase == SyncPhase.COMPLETE
        }

        _syncStatus.value = OverallSyncStatus(
            carProgresses = progresses,
            isAnySyncing = isAnySyncing,
            allComplete = allComplete
        )
    }
}
