package com.matedroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.matedroid.data.local.entity.SyncState

@Dao
interface SyncStateDao {

    @Query("SELECT * FROM sync_state WHERE carId = :carId")
    suspend fun get(carId: Int): SyncState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(syncState: SyncState)

    // Summary sync completion
    @Query("""
        UPDATE sync_state
        SET summariesSynced = 1,
            lastDriveSyncAt = :timestamp,
            lastChargeSyncAt = :timestamp
        WHERE carId = :carId
    """)
    suspend fun markSummariesSynced(carId: Int, timestamp: Long)

    // Recorded only when the summary fetch ran without a startDate filter
    @Query("UPDATE sync_state SET lastFullSummarySyncAt = :timestamp WHERE carId = :carId")
    suspend fun markFullSummarySync(carId: Int, timestamp: Long)

    // Detail sync progress updates
    @Query("""
        UPDATE sync_state
        SET lastDriveDetailId = :driveId,
            drivesProcessed = drivesProcessed + 1
        WHERE carId = :carId
    """)
    suspend fun updateDriveDetailProgress(carId: Int, driveId: Int)

    @Query("""
        UPDATE sync_state
        SET lastChargeDetailId = :chargeId,
            chargesProcessed = chargesProcessed + 1
        WHERE carId = :carId
    """)
    suspend fun updateChargeDetailProgress(carId: Int, chargeId: Int)

    // Detail sync completion
    @Query("UPDATE sync_state SET detailsSynced = 1 WHERE carId = :carId")
    suspend fun markDetailsSynced(carId: Int)

    // Reset for full resync
    @Query("""
        UPDATE sync_state
        SET summariesSynced = 0,
            detailsSynced = 0,
            lastDriveDetailId = 0,
            lastChargeDetailId = 0,
            drivesProcessed = 0,
            chargesProcessed = 0,
            totalDrivesToProcess = 0,
            totalChargesToProcess = 0
        WHERE carId = :carId
    """)
    suspend fun resetForResync(carId: Int)
}
