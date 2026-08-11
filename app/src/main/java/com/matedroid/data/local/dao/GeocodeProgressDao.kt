package com.matedroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.matedroid.data.local.entity.GeocodeProgress
import kotlinx.coroutines.flow.Flow

@Dao
interface GeocodeProgressDao {

    @Query("SELECT * FROM geocode_progress WHERE carId = :carId")
    suspend fun get(carId: Int): GeocodeProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: GeocodeProgress)

    // Increment total when new locations are enqueued
    @Query("""
        UPDATE geocode_progress
        SET totalLocations = totalLocations + :count,
            lastUpdatedAt = :timestamp
        WHERE carId = :carId
    """)
    suspend fun incrementTotal(carId: Int, count: Int, timestamp: Long)

    // Increment processed when geocoding succeeds
    @Query("""
        UPDATE geocode_progress
        SET processedLocations = processedLocations + 1,
            lastUpdatedAt = :timestamp
        WHERE carId = :carId
    """)
    suspend fun incrementProcessed(carId: Int, timestamp: Long)

    // Flow-based query for real-time progress bar
    @Query("SELECT * FROM geocode_progress WHERE carId = :carId")
    fun observe(carId: Int): Flow<GeocodeProgress?>

    // Reset progress (for full resync)
    @Query("UPDATE geocode_progress SET totalLocations = 0, processedLocations = 0 WHERE carId = :carId")
    suspend fun reset(carId: Int)

    // Delete progress record
    @Query("DELETE FROM geocode_progress WHERE carId = :carId")
    suspend fun delete(carId: Int)

    // Queue is empty → nothing is pending, so every car's progress is complete. (The old
    // variant wrote the GLOBAL cache count into every car's total — wrong for multi-car.)
    @Query("""
        UPDATE geocode_progress
        SET processedLocations = totalLocations,
            lastUpdatedAt = :timestamp
        WHERE processedLocations < totalLocations
    """)
    suspend fun markAllComplete(timestamp: Long = System.currentTimeMillis())
}
