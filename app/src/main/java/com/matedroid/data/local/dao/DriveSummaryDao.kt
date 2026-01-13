package com.matedroid.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.matedroid.data.local.entity.DriveSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface DriveSummaryDao {

    // === CRUD Operations ===

    @Upsert
    suspend fun upsertAll(drives: List<DriveSummary>)

    @Upsert
    suspend fun upsert(drive: DriveSummary)

    @Query("SELECT * FROM drives_summary WHERE driveId = :driveId")
    suspend fun get(driveId: Int): DriveSummary?

    @Query("SELECT * FROM drives_summary WHERE carId = :carId ORDER BY startDate DESC")
    fun observeAll(carId: Int): Flow<List<DriveSummary>>

    @Query("SELECT MAX(driveId) FROM drives_summary WHERE carId = :carId")
    suspend fun getMaxDriveId(carId: Int): Int?

    @Query("DELETE FROM drives_summary WHERE carId = :carId")
    suspend fun deleteAllForCar(carId: Int)

    // === Quick Stats Queries ===

    // Total count
    @Query("SELECT COUNT(*) FROM drives_summary WHERE carId = :carId")
    suspend fun count(carId: Int): Int

    @Query("""
        SELECT COUNT(*) FROM drives_summary
        WHERE carId = :carId
        AND startDate >= :startDate AND startDate < :endDate
    """)
    suspend fun countInRange(carId: Int, startDate: String, endDate: String): Int

    // Total distance
    @Query("SELECT COALESCE(SUM(distance), 0) FROM drives_summary WHERE carId = :carId")
    suspend fun sumDistance(carId: Int): Double

    @Query("""
        SELECT COALESCE(SUM(distance), 0) FROM drives_summary
        WHERE carId = :carId
        AND startDate >= :startDate AND startDate < :endDate
    """)
    suspend fun sumDistanceInRange(carId: Int, startDate: String, endDate: String): Double

    // Total energy consumed
    @Query("SELECT COALESCE(SUM(energyConsumed), 0) FROM drives_summary WHERE carId = :carId")
    suspend fun sumEnergyConsumed(carId: Int): Double

    @Query("""
        SELECT COALESCE(SUM(energyConsumed), 0) FROM drives_summary
        WHERE carId = :carId
        AND startDate >= :startDate AND startDate < :endDate
    """)
    suspend fun sumEnergyConsumedInRange(carId: Int, startDate: String, endDate: String): Double

    // Average efficiency
    @Query("""
        SELECT COALESCE(SUM(energyConsumed) * 1000 / NULLIF(SUM(distance), 0), 0)
        FROM drives_summary WHERE carId = :carId
    """)
    suspend fun avgEfficiency(carId: Int): Double

    @Query("""
        SELECT COALESCE(SUM(energyConsumed) * 1000 / NULLIF(SUM(distance), 0), 0)
        FROM drives_summary
        WHERE carId = :carId
        AND startDate >= :startDate AND startDate < :endDate
    """)
    suspend fun avgEfficiencyInRange(carId: Int, startDate: String, endDate: String): Double

    // Max speed ever
    @Query("SELECT MAX(speedMax) FROM drives_summary WHERE carId = :carId")
    suspend fun maxSpeed(carId: Int): Int?

    @Query("""
        SELECT MAX(speedMax) FROM drives_summary
        WHERE carId = :carId
        AND startDate >= :startDate AND startDate < :endDate
    """)
    suspend fun maxSpeedInRange(carId: Int, startDate: String, endDate: String): Int?

    // Longest drive (by distance)
    @Query("""
        SELECT * FROM drives_summary
        WHERE carId = :carId
        ORDER BY distance DESC LIMIT 1
    """)
    suspend fun longestDrive(carId: Int): DriveSummary?

    @Query("""
        SELECT * FROM drives_summary
        WHERE carId = :carId
        AND startDate >= :startDate AND startDate < :endDate
        ORDER BY distance DESC LIMIT 1
    """)
    suspend fun longestDriveInRange(carId: Int, startDate: String, endDate: String): DriveSummary?

    // Drive with max speed
    @Query("""
        SELECT * FROM drives_summary
        WHERE carId = :carId
        ORDER BY speedMax DESC LIMIT 1
    """)
    suspend fun fastestDrive(carId: Int): DriveSummary?

    @Query("""
        SELECT * FROM drives_summary
        WHERE carId = :carId
        AND startDate >= :startDate AND startDate < :endDate
        ORDER BY speedMax DESC LIMIT 1
    """)
    suspend fun fastestDriveInRange(carId: Int, startDate: String, endDate: String): DriveSummary?

    // Best efficiency (lowest Wh/km, excluding very short drives)
    @Query("""
        SELECT * FROM drives_summary
        WHERE carId = :carId AND efficiency > 0 AND distance > 5
        ORDER BY efficiency ASC LIMIT 1
    """)
    suspend fun mostEfficientDrive(carId: Int): DriveSummary?

    @Query("""
        SELECT * FROM drives_summary
        WHERE carId = :carId AND efficiency > 0 AND distance > 5
        AND startDate >= :startDate AND startDate < :endDate
        ORDER BY efficiency ASC LIMIT 1
    """)
    suspend fun mostEfficientDriveInRange(carId: Int, startDate: String, endDate: String): DriveSummary?

    // Worst efficiency (highest Wh/km)
    @Query("""
        SELECT * FROM drives_summary
        WHERE carId = :carId AND efficiency > 0 AND distance > 5
        ORDER BY efficiency DESC LIMIT 1
    """)
    suspend fun leastEfficientDrive(carId: Int): DriveSummary?

    @Query("""
        SELECT * FROM drives_summary
        WHERE carId = :carId AND efficiency > 0 AND distance > 5
        AND startDate >= :startDate AND startDate < :endDate
        ORDER BY efficiency DESC LIMIT 1
    """)
    suspend fun leastEfficientDriveInRange(carId: Int, startDate: String, endDate: String): DriveSummary?

    // Average drive duration
    @Query("SELECT AVG(durationMin) FROM drives_summary WHERE carId = :carId")
    suspend fun avgDuration(carId: Int): Double?

    // First drive date
    @Query("SELECT MIN(startDate) FROM drives_summary WHERE carId = :carId")
    suspend fun firstDriveDate(carId: Int): String?

    // Busiest day (most drives)
    @Query("""
        SELECT DATE(startDate) as day, COUNT(*) as count
        FROM drives_summary
        WHERE carId = :carId
        GROUP BY DATE(startDate)
        ORDER BY count DESC LIMIT 1
    """)
    suspend fun busiestDay(carId: Int): BusiestDayResult?

    @Query("""
        SELECT DATE(startDate) as day, COUNT(*) as count
        FROM drives_summary
        WHERE carId = :carId
        AND startDate >= :startDate AND startDate < :endDate
        GROUP BY DATE(startDate)
        ORDER BY count DESC LIMIT 1
    """)
    suspend fun busiestDayInRange(carId: Int, startDate: String, endDate: String): BusiestDayResult?

    // Count of unique driving days
    @Query("SELECT COUNT(DISTINCT DATE(startDate)) FROM drives_summary WHERE carId = :carId")
    suspend fun countDrivingDays(carId: Int): Int

    @Query("SELECT COUNT(DISTINCT DATE(startDate)) FROM drives_summary WHERE carId = :carId AND startDate >= :startDate AND startDate < :endDate")
    suspend fun countDrivingDaysInRange(carId: Int, startDate: String, endDate: String): Int

    // Most distance in a single day
    @Query("""
        SELECT DATE(startDate) as day, SUM(distance) as totalDistance
        FROM drives_summary
        WHERE carId = :carId
        GROUP BY DATE(startDate)
        ORDER BY totalDistance DESC LIMIT 1
    """)
    suspend fun mostDistanceDay(carId: Int): MostDistanceDayResult?

    @Query("""
        SELECT DATE(startDate) as day, SUM(distance) as totalDistance
        FROM drives_summary
        WHERE carId = :carId
        AND startDate >= :startDate AND startDate < :endDate
        GROUP BY DATE(startDate)
        ORDER BY totalDistance DESC LIMIT 1
    """)
    suspend fun mostDistanceDayInRange(carId: Int, startDate: String, endDate: String): MostDistanceDayResult?

    // === Queries for Detail Sync ===

    // Get drive IDs that need detail processing
    @Query("""
        SELECT d.driveId FROM drives_summary d
        LEFT JOIN drive_detail_aggregates a ON d.driveId = a.driveId
        WHERE d.carId = :carId
        AND (a.driveId IS NULL OR a.schemaVersion < :currentVersion)
        ORDER BY d.driveId
    """)
    suspend fun getUnprocessedDriveIds(carId: Int, currentVersion: Int): List<Int>

    // Count unprocessed drives
    @Query("""
        SELECT COUNT(*) FROM drives_summary d
        LEFT JOIN drive_detail_aggregates a ON d.driveId = a.driveId
        WHERE d.carId = :carId
        AND (a.driveId IS NULL OR a.schemaVersion < :currentVersion)
    """)
    suspend fun countUnprocessedDrives(carId: Int, currentVersion: Int): Int

    // === Year List for Filter ===

    @Query("""
        SELECT DISTINCT CAST(strftime('%Y', startDate) AS INTEGER) as year
        FROM drives_summary
        WHERE carId = :carId
        ORDER BY year DESC
    """)
    suspend fun getYears(carId: Int): List<Int>

    // === Range Record Queries ===

    /**
     * Get all drives between two dates (exclusive), ordered by start date.
     * Used for showing drives in a "longest range" record.
     */
    @Query("""
        SELECT * FROM drives_summary
        WHERE carId = :carId
          AND startDate > :afterDate
          AND startDate < :beforeDate
        ORDER BY startDate ASC
    """)
    suspend fun getDrivesBetweenDates(carId: Int, afterDate: String, beforeDate: String): List<DriveSummary>

    /**
     * Find the longest gap (in days) between two consecutive drives.
     */
    @Query("""
        SELECT
            prev.driveId as fromDriveId,
            curr.driveId as toDriveId,
            CAST(julianday(curr.startDate) - julianday(prev.startDate) AS REAL) as gapDays,
            prev.startDate as fromDate,
            curr.startDate as toDate
        FROM drives_summary curr
        INNER JOIN drives_summary prev ON prev.carId = curr.carId
            AND prev.startDate = (
                SELECT MAX(p.startDate)
                FROM drives_summary p
                WHERE p.carId = curr.carId AND p.startDate < curr.startDate
            )
        WHERE curr.carId = :carId
        ORDER BY gapDays DESC
        LIMIT 1
    """)
    suspend fun longestGapBetweenDrives(carId: Int): GapBetweenDrivesResult?

    @Query("""
        SELECT
            prev.driveId as fromDriveId,
            curr.driveId as toDriveId,
            CAST(julianday(curr.startDate) - julianday(prev.startDate) AS REAL) as gapDays,
            prev.startDate as fromDate,
            curr.startDate as toDate
        FROM drives_summary curr
        INNER JOIN drives_summary prev ON prev.carId = curr.carId
            AND prev.startDate = (
                SELECT MAX(p.startDate)
                FROM drives_summary p
                WHERE p.carId = curr.carId AND p.startDate < curr.startDate
            )
        WHERE curr.carId = :carId
            AND prev.startDate >= :startDate
            AND curr.startDate < :endDate
        ORDER BY gapDays DESC
        LIMIT 1
    """)
    suspend fun longestGapBetweenDrivesInRange(
        carId: Int,
        startDate: String,
        endDate: String
    ): GapBetweenDrivesResult?

    /**
     * Find the drive with the biggest battery drain (startBatteryLevel - endBatteryLevel).
     */
    @Query("""
        SELECT * FROM drives_summary
        WHERE carId = :carId
        ORDER BY (startBatteryLevel - endBatteryLevel) DESC
        LIMIT 1
    """)
    suspend fun biggestBatteryDrainDrive(carId: Int): DriveSummary?

    @Query("""
        SELECT * FROM drives_summary
        WHERE carId = :carId
        AND startDate >= :startDate AND startDate < :endDate
        ORDER BY (startBatteryLevel - endBatteryLevel) DESC
        LIMIT 1
    """)
    suspend fun biggestBatteryDrainDriveInRange(
        carId: Int,
        startDate: String,
        endDate: String
    ): DriveSummary?

    /**
     * Get all distinct driving days (for computing streak in Kotlin).
     */
    @Query("""
        SELECT DISTINCT DATE(startDate) as day
        FROM drives_summary
        WHERE carId = :carId
        ORDER BY day ASC
    """)
    suspend fun getDistinctDrivingDays(carId: Int): List<String>

    @Query("""
        SELECT DISTINCT DATE(startDate) as day
        FROM drives_summary
        WHERE carId = :carId
        AND startDate >= :startDate AND startDate < :endDate
        ORDER BY day ASC
    """)
    suspend fun getDistinctDrivingDaysInRange(carId: Int, startDate: String, endDate: String): List<String>
}

data class BusiestDayResult(
    val day: String,
    val count: Int
)

data class MostDistanceDayResult(
    val day: String,
    val totalDistance: Double
)

/**
 * Result of longest gap between drives query.
 */
data class GapBetweenDrivesResult(
    val fromDriveId: Int,
    val toDriveId: Int,
    val gapDays: Double,
    val fromDate: String,
    val toDate: String
)

/**
 * Result of longest driving streak query.
 */
data class DrivingStreakResult(
    val streakDays: Int,
    val startDate: String,
    val endDate: String
)
