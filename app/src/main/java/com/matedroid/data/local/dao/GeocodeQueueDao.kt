package com.matedroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.matedroid.data.local.entity.GeocodeQueueItem

@Dao
interface GeocodeQueueDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueAll(items: List<GeocodeQueueItem>)

    @Query("""
        SELECT * FROM geocode_queue
        WHERE attempts < 3
        ORDER BY addedAt ASC
        LIMIT :limit
    """)
    suspend fun getNextBatch(limit: Int = 10): List<GeocodeQueueItem>

    @Query("DELETE FROM geocode_queue WHERE gridLat = :gridLat AND gridLon = :gridLon")
    suspend fun remove(gridLat: Int, gridLon: Int)

    @Query("SELECT COUNT(*) FROM geocode_queue WHERE attempts < 3")
    suspend fun countPending(): Int

    // For batch dedup when enqueueing (progress totals must not re-count queued items)
    @Query("SELECT gridLat, gridLon FROM geocode_queue")
    suspend fun getAllGridKeys(): List<GridKey>

    @Query("""
        UPDATE geocode_queue
        SET attempts = attempts + 1, lastAttemptAt = :timestamp
        WHERE gridLat = :gridLat AND gridLon = :gridLon
    """)
    suspend fun markAttempt(gridLat: Int, gridLon: Int, timestamp: Long)

    // Clear queue for a specific car (for resync)
    @Query("DELETE FROM geocode_queue WHERE carId = :carId")
    suspend fun clearForCar(carId: Int)

    // Count total items in queue (including failed)
    @Query("SELECT COUNT(*) FROM geocode_queue")
    suspend fun countTotal(): Int

    // Count failed items (attempts >= 3)
    @Query("SELECT COUNT(*) FROM geocode_queue WHERE attempts >= 3")
    suspend fun countFailed(): Int

    // Reset all failed items to retry them
    @Query("UPDATE geocode_queue SET attempts = 0, lastAttemptAt = NULL WHERE attempts >= 3")
    suspend fun resetFailed()
}
