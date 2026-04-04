package com.matedroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.matedroid.data.local.entity.SentryAlertLog
import kotlinx.coroutines.flow.Flow

@Dao
interface SentryAlertLogDao {

    @Insert
    suspend fun insert(log: SentryAlertLog)

    /** All alerts for a car, newest first. */
    @Query("SELECT * FROM sentry_alert_log WHERE carId = :carId ORDER BY detectedAt DESC")
    fun observeAll(carId: Int): Flow<List<SentryAlertLog>>

    /** Delete all alerts for a car. */
    @Query("DELETE FROM sentry_alert_log WHERE carId = :carId")
    suspend fun deleteAllForCar(carId: Int)

    /** Update the address for a specific alert. */
    @Query("UPDATE sentry_alert_log SET address = :address WHERE id = :id")
    suspend fun updateAddress(id: Long, address: String)

    /** Count alerts per hour bucket within a time range. Returns pairs of (hourBucket, count). */
    @Query("""
        SELECT (detectedAt / 3600000) AS hourBucket, COUNT(*) AS cnt
        FROM sentry_alert_log
        WHERE carId = :carId AND detectedAt >= :sinceMillis
        GROUP BY hourBucket
    """)
    suspend fun countByHour(carId: Int, sinceMillis: Long): List<HourlyCount>
}

data class HourlyCount(
    val hourBucket: Long,
    val cnt: Int
)
