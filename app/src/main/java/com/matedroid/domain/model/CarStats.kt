package com.matedroid.domain.model

import com.matedroid.data.local.dao.BusiestDayResult
import com.matedroid.data.local.dao.MostDistanceDayResult
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary

/**
 * Complete stats for a car, containing both Quick Stats and Deep Stats.
 */
data class CarStats(
    val carId: Int,
    val yearFilter: YearFilter,
    val quickStats: QuickStats,
    val deepStats: DeepStats?,
    val syncProgress: SyncProgress?
)

/**
 * Year filter options for stats.
 */
sealed class YearFilter {
    data object AllTime : YearFilter()
    data class Year(val year: Int) : YearFilter()

    fun toDisplayString(): String = when (this) {
        is AllTime -> "All Time"
        is Year -> year.toString()
    }
}

/**
 * Quick stats from summary data (instant, no detail sync needed).
 */
data class QuickStats(
    // === Drives Overview ===
    val totalDrives: Int,
    val totalDistanceKm: Double,
    val totalEnergyConsumedKwh: Double,
    val avgEfficiencyWhKm: Double,
    val maxSpeedKmh: Int?,
    val avgDriveMinutes: Double?,
    val totalDrivingDays: Int?,

    // === Charges Overview ===
    val totalCharges: Int,
    val totalEnergyAddedKwh: Double,
    val totalCost: Double?,
    val avgCostPerKwh: Double?,
    val avgChargeMinutes: Double?,

    // === Records (with full drive/charge info) ===
    val longestDrive: DriveSummary?,
    val fastestDrive: DriveSummary?,
    val mostEfficientDrive: DriveSummary?,
    val leastEfficientDrive: DriveSummary?,
    val biggestCharge: ChargeSummary?,
    val mostExpensiveCharge: ChargeSummary?,
    val mostExpensivePerKwhCharge: ChargeSummary?,

    // === Time Stats ===
    val firstDriveDate: String?,
    val firstChargeDate: String?,
    val busiestDay: BusiestDayResult?,
    val mostDistanceDay: MostDistanceDayResult?,

    // === Range Records ===
    val maxDistanceBetweenCharges: MaxDistanceBetweenChargesRecord?,

    // === Gap Records ===
    val longestGapWithoutCharging: GapRecord?,
    val longestGapWithoutDriving: GapRecord?,

    // === Streak Records ===
    val longestDrivingStreak: StreakRecord?,

    // === Battery Records ===
    val biggestBatteryGainCharge: BatteryChangeRecord?,
    val biggestBatteryDrainDrive: BatteryChangeRecord?
)

/**
 * Deep stats from detail aggregates (requires detail sync).
 * Null fields mean data not yet available.
 */
data class DeepStats(
    // === Elevation ===
    val maxElevationM: Int?,
    val minElevationM: Int?,
    val driveWithMaxElevation: DriveElevationRecord?,
    val driveWithMinElevation: DriveElevationRecord?,
    val driveWithMostClimbing: DriveElevationRecord?,

    // === Temperature (Driving) ===
    val maxOutsideTempDrivingC: Double?,
    val minOutsideTempDrivingC: Double?,
    val maxCabinTempC: Double?,
    val minCabinTempC: Double?,
    val hottestDrive: DriveTempRecord?,
    val coldestDrive: DriveTempRecord?,

    // === Temperature (Charging) ===
    val maxOutsideTempChargingC: Double?,
    val minOutsideTempChargingC: Double?,
    val hottestCharge: ChargeTempRecord?,
    val coldestCharge: ChargeTempRecord?,

    // === Charging Power ===
    val maxChargerPowerKw: Int?,
    val chargeWithMaxPower: ChargePowerRecord?,

    // === AC/DC Ratio ===
    val acChargeCount: Int,
    val dcChargeCount: Int,
    val acChargeEnergyKwh: Double,
    val dcChargeEnergyKwh: Double,

    // === Sync Progress ===
    val driveDetailsProcessed: Int,
    val chargeDetailsProcessed: Int
) {
    val acDcRatio: String
        get() {
            val total = acChargeCount + dcChargeCount
            return if (total > 0) {
                val acPercent = (acChargeCount * 100) / total
                val dcPercent = (dcChargeCount * 100) / total
                "$acPercent% AC / $dcPercent% DC"
            } else {
                "N/A"
            }
        }
}

/**
 * Record for elevation-related drives.
 */
data class DriveElevationRecord(
    val driveId: Int,
    val elevationM: Int,
    val elevationGainM: Int?,
    val date: String?
)

/**
 * Record for temperature-related drives.
 */
data class DriveTempRecord(
    val driveId: Int,
    val tempC: Double,
    val date: String?
)

/**
 * Record for temperature-related charges.
 */
data class ChargeTempRecord(
    val chargeId: Int,
    val tempC: Double,
    val date: String?
)

/**
 * Record for power-related charges.
 */
data class ChargePowerRecord(
    val chargeId: Int,
    val powerKw: Int,
    val date: String?
)

/**
 * Record for maximum distance traveled between two consecutive charges.
 */
data class MaxDistanceBetweenChargesRecord(
    val distance: Double,           // km
    val fromChargeId: Int,
    val toChargeId: Int,
    val fromDate: String,
    val toDate: String
)

/**
 * Record for a gap (time without driving or charging).
 */
data class GapRecord(
    val gapDays: Double,
    val fromDate: String,
    val toDate: String
)

/**
 * Record for a driving streak (consecutive days with driving).
 */
data class StreakRecord(
    val streakDays: Int,
    val startDate: String,
    val endDate: String
)

/**
 * Record for battery change (gain from charging or drain from driving).
 */
data class BatteryChangeRecord(
    val percentChange: Int,         // % gained or drained
    val startLevel: Int,            // starting battery %
    val endLevel: Int,              // ending battery %
    val recordId: Int,              // chargeId or driveId
    val date: String,
    val isCharge: Boolean           // true for charge, false for drive
)
