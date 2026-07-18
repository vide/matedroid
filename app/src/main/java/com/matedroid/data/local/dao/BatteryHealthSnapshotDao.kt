package com.matedroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.matedroid.data.local.entity.BatteryHealthSnapshot
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryHealthSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: BatteryHealthSnapshot): Long

    /** All snapshots for a car, oldest first — the chart wants chronological data. */
    @Query("""
        SELECT * FROM battery_health_snapshots
        WHERE carId = :carId
        ORDER BY recordedAt ASC
    """)
    fun observeAllForCar(carId: Int): Flow<List<BatteryHealthSnapshot>>

    /** Latest snapshot for a car, or null if none. Used by the write path to decide whether the new sample is a duplicate worth skipping. */
    @Query("""
        SELECT * FROM battery_health_snapshots
        WHERE carId = :carId
        ORDER BY recordedAt DESC
        LIMIT 1
    """)
    suspend fun getLatestForCar(carId: Int): BatteryHealthSnapshot?

    @Query("SELECT COUNT(*) FROM battery_health_snapshots WHERE carId = :carId")
    suspend fun countForCar(carId: Int): Int

    /**
     * Prune the car's history so at most [keep] most-recent rows remain.
     * Called after inserts to bound storage (~90 rows ~= 3 months of daily samples).
     */
    @Query("""
        DELETE FROM battery_health_snapshots
        WHERE carId = :carId AND id NOT IN (
            SELECT id FROM battery_health_snapshots
            WHERE carId = :carId
            ORDER BY recordedAt DESC
            LIMIT :keep
        )
    """)
    suspend fun pruneOldest(carId: Int, keep: Int)

    /** Convenience wrapper: insert + prune in a single transaction. */
    @Transaction
    suspend fun insertAndPrune(snapshot: BatteryHealthSnapshot, keep: Int = MAX_ROWS_PER_CAR) {
        insert(snapshot)
        pruneOldest(snapshot.carId, keep)
    }

    companion object {
        /** Retention cap per car — roughly 3 months of daily snapshots. */
        const val MAX_ROWS_PER_CAR = 90
    }
}
