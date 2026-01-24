package com.matedroid.data.repository

import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.ChargeSummaryDao
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.local.entity.SchemaVersion
import com.matedroid.data.sync.SyncManager
import com.matedroid.domain.model.CarStats
import com.matedroid.domain.model.ChargePowerRecord
import com.matedroid.domain.model.ChargeTempRecord
import com.matedroid.domain.model.DeepStats
import com.matedroid.domain.model.DriveElevationRecord
import com.matedroid.domain.model.DriveTempRecord
import com.matedroid.domain.model.QuickStats
import com.matedroid.domain.model.BatteryChangeRecord
import com.matedroid.domain.model.ChargeLocation
import com.matedroid.domain.model.CountryRecord
import com.matedroid.domain.model.DriveLocation
import com.matedroid.domain.model.RegionRecord
import com.matedroid.domain.model.GapRecord
import com.matedroid.domain.model.MaxDistanceBetweenChargesRecord
import com.matedroid.domain.model.StreakRecord
import com.matedroid.domain.model.YearFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for computing and retrieving stats for a car.
 */
@Singleton
class StatsRepository @Inject constructor(
    private val driveSummaryDao: DriveSummaryDao,
    private val chargeSummaryDao: ChargeSummaryDao,
    private val aggregateDao: AggregateDao,
    private val syncManager: SyncManager,
    private val geocodingRepository: GeocodingRepository
) {

    /**
     * Get complete stats for a car with the given year filter.
     */
    suspend fun getStats(carId: Int, yearFilter: YearFilter): CarStats {
        val quickStats = getQuickStats(carId, yearFilter)
        val deepStats = getDeepStats(carId, yearFilter)
        val syncProgress = syncManager.getProgressForCar(carId)

        return CarStats(
            carId = carId,
            yearFilter = yearFilter,
            quickStats = quickStats,
            deepStats = deepStats,
            syncProgress = syncProgress
        )
    }

    /**
     * Get quick stats (from summary tables, instant).
     */
    suspend fun getQuickStats(carId: Int, yearFilter: YearFilter): QuickStats {
        return when (yearFilter) {
            is YearFilter.AllTime -> getQuickStatsAllTime(carId)
            is YearFilter.Year -> getQuickStatsForYear(carId, yearFilter.year)
        }
    }

    private suspend fun getQuickStatsAllTime(carId: Int): QuickStats {
        return QuickStats(
            totalDrives = driveSummaryDao.count(carId),
            totalDistanceKm = driveSummaryDao.sumDistance(carId),
            totalEnergyConsumedKwh = driveSummaryDao.sumEnergyConsumed(carId),
            avgEfficiencyWhKm = driveSummaryDao.avgEfficiency(carId),
            maxSpeedKmh = driveSummaryDao.maxSpeed(carId),
            avgDriveMinutes = driveSummaryDao.avgDuration(carId),
            totalDrivingDays = driveSummaryDao.countDrivingDays(carId),

            totalCharges = chargeSummaryDao.count(carId),
            totalEnergyAddedKwh = chargeSummaryDao.sumEnergyAdded(carId),
            totalCost = chargeSummaryDao.sumCost(carId).takeIf { it > 0 },
            avgCostPerKwh = chargeSummaryDao.avgCostPerKwh(carId).takeIf { it > 0 },
            avgChargeMinutes = chargeSummaryDao.avgDuration(carId),

            longestDrive = driveSummaryDao.longestDrive(carId),
            fastestDrive = driveSummaryDao.fastestDrive(carId),
            mostEfficientDrive = driveSummaryDao.mostEfficientDrive(carId),
            leastEfficientDrive = driveSummaryDao.leastEfficientDrive(carId),
            biggestCharge = chargeSummaryDao.biggestCharge(carId),
            mostExpensiveCharge = chargeSummaryDao.mostExpensiveCharge(carId),
            mostExpensivePerKwhCharge = chargeSummaryDao.mostExpensivePerKwhCharge(carId),

            firstDriveDate = driveSummaryDao.firstDriveDate(carId),
            firstChargeDate = chargeSummaryDao.firstChargeDate(carId),
            busiestDay = driveSummaryDao.busiestDay(carId),
            mostDistanceDay = driveSummaryDao.mostDistanceDay(carId),

            maxDistanceBetweenCharges = chargeSummaryDao.maxDistanceBetweenCharges(carId)?.let {
                MaxDistanceBetweenChargesRecord(
                    distance = it.distance,
                    fromChargeId = it.fromChargeId,
                    toChargeId = it.toChargeId,
                    fromDate = it.fromDate,
                    toDate = it.toDate
                )
            },

            longestGapWithoutCharging = chargeSummaryDao.longestGapBetweenCharges(carId)?.let {
                GapRecord(gapDays = it.gapDays, fromDate = it.fromDate, toDate = it.toDate)
            },
            longestGapWithoutDriving = driveSummaryDao.longestGapBetweenDrives(carId)?.let {
                GapRecord(gapDays = it.gapDays, fromDate = it.fromDate, toDate = it.toDate)
            },

            longestDrivingStreak = computeLongestStreak(
                driveSummaryDao.getDistinctDrivingDays(carId)
            ),

            biggestBatteryGainCharge = chargeSummaryDao.biggestBatteryGainCharge(carId)?.let {
                BatteryChangeRecord(
                    percentChange = it.endBatteryLevel - it.startBatteryLevel,
                    startLevel = it.startBatteryLevel,
                    endLevel = it.endBatteryLevel,
                    recordId = it.chargeId,
                    date = it.startDate,
                    isCharge = true
                )
            },
            biggestBatteryDrainDrive = driveSummaryDao.biggestBatteryDrainDrive(carId)?.let {
                BatteryChangeRecord(
                    percentChange = it.startBatteryLevel - it.endBatteryLevel,
                    startLevel = it.startBatteryLevel,
                    endLevel = it.endBatteryLevel,
                    recordId = it.driveId,
                    date = it.startDate,
                    isCharge = false
                )
            }
        )
    }

    private suspend fun getQuickStatsForYear(carId: Int, year: Int): QuickStats {
        val startDate = "$year-01-01T00:00:00"
        val endDate = "${year + 1}-01-01T00:00:00"

        return QuickStats(
            totalDrives = driveSummaryDao.countInRange(carId, startDate, endDate),
            totalDistanceKm = driveSummaryDao.sumDistanceInRange(carId, startDate, endDate),
            totalEnergyConsumedKwh = driveSummaryDao.sumEnergyConsumedInRange(carId, startDate, endDate),
            avgEfficiencyWhKm = driveSummaryDao.avgEfficiencyInRange(carId, startDate, endDate),
            maxSpeedKmh = driveSummaryDao.maxSpeedInRange(carId, startDate, endDate),
            avgDriveMinutes = null, // Not critical for year view
            totalDrivingDays = driveSummaryDao.countDrivingDaysInRange(carId, startDate, endDate),

            totalCharges = chargeSummaryDao.countInRange(carId, startDate, endDate),
            totalEnergyAddedKwh = chargeSummaryDao.sumEnergyAddedInRange(carId, startDate, endDate),
            totalCost = chargeSummaryDao.sumCostInRange(carId, startDate, endDate).takeIf { it > 0 },
            avgCostPerKwh = null, // Not critical for year view
            avgChargeMinutes = null, // Not critical for year view

            longestDrive = driveSummaryDao.longestDriveInRange(carId, startDate, endDate),
            fastestDrive = driveSummaryDao.fastestDriveInRange(carId, startDate, endDate),
            mostEfficientDrive = driveSummaryDao.mostEfficientDriveInRange(carId, startDate, endDate),
            leastEfficientDrive = driveSummaryDao.leastEfficientDriveInRange(carId, startDate, endDate),
            biggestCharge = chargeSummaryDao.biggestChargeInRange(carId, startDate, endDate),
            mostExpensiveCharge = chargeSummaryDao.mostExpensiveChargeInRange(carId, startDate, endDate),
            mostExpensivePerKwhCharge = chargeSummaryDao.mostExpensivePerKwhChargeInRange(carId, startDate, endDate),

            firstDriveDate = driveSummaryDao.firstDriveDate(carId), // Always show first ever
            firstChargeDate = chargeSummaryDao.firstChargeDate(carId), // Always show first ever
            busiestDay = driveSummaryDao.busiestDayInRange(carId, startDate, endDate),
            mostDistanceDay = driveSummaryDao.mostDistanceDayInRange(carId, startDate, endDate),

            maxDistanceBetweenCharges = chargeSummaryDao.maxDistanceBetweenChargesInRange(carId, startDate, endDate)?.let {
                MaxDistanceBetweenChargesRecord(
                    distance = it.distance,
                    fromChargeId = it.fromChargeId,
                    toChargeId = it.toChargeId,
                    fromDate = it.fromDate,
                    toDate = it.toDate
                )
            },

            longestGapWithoutCharging = chargeSummaryDao.longestGapBetweenChargesInRange(carId, startDate, endDate)?.let {
                GapRecord(gapDays = it.gapDays, fromDate = it.fromDate, toDate = it.toDate)
            },
            longestGapWithoutDriving = driveSummaryDao.longestGapBetweenDrivesInRange(carId, startDate, endDate)?.let {
                GapRecord(gapDays = it.gapDays, fromDate = it.fromDate, toDate = it.toDate)
            },

            longestDrivingStreak = computeLongestStreak(
                driveSummaryDao.getDistinctDrivingDaysInRange(carId, startDate, endDate)
            ),

            biggestBatteryGainCharge = chargeSummaryDao.biggestBatteryGainChargeInRange(carId, startDate, endDate)?.let {
                BatteryChangeRecord(
                    percentChange = it.endBatteryLevel - it.startBatteryLevel,
                    startLevel = it.startBatteryLevel,
                    endLevel = it.endBatteryLevel,
                    recordId = it.chargeId,
                    date = it.startDate,
                    isCharge = true
                )
            },
            biggestBatteryDrainDrive = driveSummaryDao.biggestBatteryDrainDriveInRange(carId, startDate, endDate)?.let {
                BatteryChangeRecord(
                    percentChange = it.startBatteryLevel - it.endBatteryLevel,
                    startLevel = it.startBatteryLevel,
                    endLevel = it.endBatteryLevel,
                    recordId = it.driveId,
                    date = it.startDate,
                    isCharge = false
                )
            }
        )
    }

    /**
     * Get deep stats (from aggregate tables, requires sync).
     * Returns null if no aggregates exist yet.
     */
    suspend fun getDeepStats(carId: Int, yearFilter: YearFilter): DeepStats? {
        val driveAggregates = aggregateDao.countDriveAggregates(carId)
        val chargeAggregates = aggregateDao.countChargeAggregates(carId)

        // Return null if no aggregates exist at all
        if (driveAggregates == 0 && chargeAggregates == 0) {
            return null
        }

        return when (yearFilter) {
            is YearFilter.AllTime -> getDeepStatsAllTime(carId, driveAggregates, chargeAggregates)
            is YearFilter.Year -> getDeepStatsForYear(carId, yearFilter.year, driveAggregates, chargeAggregates)
        }
    }

    private suspend fun getDeepStatsAllTime(carId: Int, driveCount: Int, chargeCount: Int): DeepStats {
        // Elevation records
        val driveWithMaxElev = aggregateDao.driveWithMaxElevation(carId)
        val driveWithMinElev = aggregateDao.driveWithMinElevation(carId)
        val driveWithMostGain = aggregateDao.driveWithMostElevationGain(carId)

        // Temperature records (driving)
        val hottestDriveAgg = aggregateDao.hottestDrive(carId)
        val coldestDriveAgg = aggregateDao.coldestDrive(carId)

        // Temperature records (charging)
        val hottestChargeAgg = aggregateDao.hottestCharge(carId)
        val coldestChargeAgg = aggregateDao.coldestCharge(carId)

        // Power record
        val chargeWithMaxPowerAgg = aggregateDao.chargeWithMaxPower(carId)

        return DeepStats(
            maxElevationM = aggregateDao.maxElevation(carId),
            minElevationM = aggregateDao.minElevation(carId),
            driveWithMaxElevation = driveWithMaxElev?.let { agg ->
                val drive = driveSummaryDao.get(agg.driveId)
                DriveElevationRecord(
                    driveId = agg.driveId,
                    elevationM = agg.maxElevation ?: 0,
                    elevationGainM = agg.elevationGain,
                    date = drive?.startDate
                )
            },
            driveWithMinElevation = driveWithMinElev?.let { agg ->
                val drive = driveSummaryDao.get(agg.driveId)
                DriveElevationRecord(
                    driveId = agg.driveId,
                    elevationM = agg.minElevation ?: 0,
                    elevationGainM = agg.elevationGain,
                    date = drive?.startDate
                )
            },
            driveWithMostClimbing = driveWithMostGain?.let { agg ->
                val drive = driveSummaryDao.get(agg.driveId)
                DriveElevationRecord(
                    driveId = agg.driveId,
                    elevationM = agg.maxElevation ?: 0,
                    elevationGainM = agg.elevationGain,
                    date = drive?.startDate
                )
            },

            maxOutsideTempDrivingC = aggregateDao.maxOutsideTempDriving(carId),
            minOutsideTempDrivingC = aggregateDao.minOutsideTempDriving(carId),
            maxCabinTempC = aggregateDao.maxInsideTemp(carId),
            minCabinTempC = aggregateDao.minInsideTemp(carId),
            hottestDrive = hottestDriveAgg?.let { agg ->
                val drive = driveSummaryDao.get(agg.driveId)
                DriveTempRecord(
                    driveId = agg.driveId,
                    tempC = agg.maxOutsideTemp ?: 0.0,
                    date = drive?.startDate
                )
            },
            coldestDrive = coldestDriveAgg?.let { agg ->
                val drive = driveSummaryDao.get(agg.driveId)
                DriveTempRecord(
                    driveId = agg.driveId,
                    tempC = agg.minOutsideTemp ?: 0.0,
                    date = drive?.startDate
                )
            },

            maxOutsideTempChargingC = aggregateDao.maxOutsideTempCharging(carId),
            minOutsideTempChargingC = aggregateDao.minOutsideTempCharging(carId),
            hottestCharge = hottestChargeAgg?.let { agg ->
                val charge = chargeSummaryDao.get(agg.chargeId)
                ChargeTempRecord(
                    chargeId = agg.chargeId,
                    tempC = agg.maxOutsideTemp ?: 0.0,
                    date = charge?.startDate
                )
            },
            coldestCharge = coldestChargeAgg?.let { agg ->
                val charge = chargeSummaryDao.get(agg.chargeId)
                ChargeTempRecord(
                    chargeId = agg.chargeId,
                    tempC = agg.minOutsideTemp ?: 0.0,
                    date = charge?.startDate
                )
            },

            maxChargerPowerKw = aggregateDao.maxChargerPower(carId),
            chargeWithMaxPower = chargeWithMaxPowerAgg?.let { agg ->
                val charge = chargeSummaryDao.get(agg.chargeId)
                ChargePowerRecord(
                    chargeId = agg.chargeId,
                    powerKw = agg.maxChargerPower ?: 0,
                    date = charge?.startDate
                )
            },

            acChargeCount = aggregateDao.countAcCharges(carId),
            dcChargeCount = aggregateDao.countDcCharges(carId),
            acChargeEnergyKwh = aggregateDao.sumAcChargeEnergy(carId),
            dcChargeEnergyKwh = aggregateDao.sumDcChargeEnergy(carId),

            countriesVisitedCount = aggregateDao.countUniqueCountries(carId).takeIf { it > 0 },

            driveDetailsProcessed = driveCount,
            chargeDetailsProcessed = chargeCount
        )
    }

    private suspend fun getDeepStatsForYear(
        carId: Int,
        year: Int,
        driveCount: Int,
        chargeCount: Int
    ): DeepStats {
        val startDate = "$year-01-01T00:00:00"
        val endDate = "${year + 1}-01-01T00:00:00"

        // Elevation records for year
        val driveWithMaxElev = aggregateDao.driveWithMaxElevationInRange(carId, startDate, endDate)
        val driveWithMostGain = aggregateDao.driveWithMostElevationGainInRange(carId, startDate, endDate)

        // Temperature records for year
        val hottestDriveAgg = aggregateDao.hottestDriveInRange(carId, startDate, endDate)
        val coldestDriveAgg = aggregateDao.coldestDriveInRange(carId, startDate, endDate)
        val hottestChargeAgg = aggregateDao.hottestChargeInRange(carId, startDate, endDate)
        val coldestChargeAgg = aggregateDao.coldestChargeInRange(carId, startDate, endDate)

        // Power record for year
        val chargeWithMaxPowerAgg = aggregateDao.chargeWithMaxPowerInRange(carId, startDate, endDate)

        return DeepStats(
            maxElevationM = aggregateDao.maxElevationInRange(carId, startDate, endDate),
            minElevationM = null, // Not shown in UI
            driveWithMaxElevation = driveWithMaxElev?.let { agg ->
                val drive = driveSummaryDao.get(agg.driveId)
                DriveElevationRecord(
                    driveId = agg.driveId,
                    elevationM = agg.maxElevation ?: 0,
                    elevationGainM = agg.elevationGain,
                    date = drive?.startDate
                )
            },
            driveWithMinElevation = null, // Not shown in UI
            driveWithMostClimbing = driveWithMostGain?.let { agg ->
                val drive = driveSummaryDao.get(agg.driveId)
                DriveElevationRecord(
                    driveId = agg.driveId,
                    elevationM = agg.maxElevation ?: 0,
                    elevationGainM = agg.elevationGain,
                    date = drive?.startDate
                )
            },

            maxOutsideTempDrivingC = aggregateDao.maxOutsideTempDrivingInRange(carId, startDate, endDate),
            minOutsideTempDrivingC = null, // Not needed for records
            maxCabinTempC = null, // Not needed for records
            minCabinTempC = null, // Not needed for records
            hottestDrive = hottestDriveAgg?.let { agg ->
                val drive = driveSummaryDao.get(agg.driveId)
                DriveTempRecord(
                    driveId = agg.driveId,
                    tempC = agg.maxOutsideTemp ?: 0.0,
                    date = drive?.startDate
                )
            },
            coldestDrive = coldestDriveAgg?.let { agg ->
                val drive = driveSummaryDao.get(agg.driveId)
                DriveTempRecord(
                    driveId = agg.driveId,
                    tempC = agg.minOutsideTemp ?: 0.0,
                    date = drive?.startDate
                )
            },

            maxOutsideTempChargingC = aggregateDao.maxOutsideTempChargingInRange(carId, startDate, endDate),
            minOutsideTempChargingC = null, // Not needed for records
            hottestCharge = hottestChargeAgg?.let { agg ->
                val charge = chargeSummaryDao.get(agg.chargeId)
                ChargeTempRecord(
                    chargeId = agg.chargeId,
                    tempC = agg.maxOutsideTemp ?: 0.0,
                    date = charge?.startDate
                )
            },
            coldestCharge = coldestChargeAgg?.let { agg ->
                val charge = chargeSummaryDao.get(agg.chargeId)
                ChargeTempRecord(
                    chargeId = agg.chargeId,
                    tempC = agg.minOutsideTemp ?: 0.0,
                    date = charge?.startDate
                )
            },

            maxChargerPowerKw = aggregateDao.maxChargerPowerInRange(carId, startDate, endDate),
            chargeWithMaxPower = chargeWithMaxPowerAgg?.let { agg ->
                val charge = chargeSummaryDao.get(agg.chargeId)
                ChargePowerRecord(
                    chargeId = agg.chargeId,
                    powerKw = agg.maxChargerPower ?: 0,
                    date = charge?.startDate
                )
            },

            acChargeCount = aggregateDao.countAcChargesInRange(carId, startDate, endDate),
            dcChargeCount = aggregateDao.countDcChargesInRange(carId, startDate, endDate),
            acChargeEnergyKwh = aggregateDao.sumAcChargeEnergyInRange(carId, startDate, endDate),
            dcChargeEnergyKwh = aggregateDao.sumDcChargeEnergyInRange(carId, startDate, endDate),

            countriesVisitedCount = aggregateDao.countUniqueCountriesInRange(carId, startDate, endDate).takeIf { it > 0 },

            driveDetailsProcessed = driveCount,
            chargeDetailsProcessed = chargeCount
        )
    }

    /**
     * Get available years for the year filter dropdown.
     */
    suspend fun getAvailableYears(carId: Int): List<Int> {
        val driveYears = driveSummaryDao.getYears(carId)
        val chargeYears = chargeSummaryDao.getYears(carId)
        return (driveYears + chargeYears).distinct().sortedDescending()
    }

    /**
     * Check if any data is available for stats.
     */
    suspend fun hasData(carId: Int): Boolean {
        return driveSummaryDao.count(carId) > 0 || chargeSummaryDao.count(carId) > 0
    }

    /**
     * Check if deep stats are being processed.
     */
    suspend fun isDeepSyncInProgress(carId: Int): Boolean {
        val progress = syncManager.getProgressForCar(carId)
        return progress != null && progress.phase.isProcessing()
    }

    /**
     * Get drives between two dates (for range record details).
     */
    suspend fun getDrivesBetweenDates(carId: Int, afterDate: String, beforeDate: String) =
        driveSummaryDao.getDrivesBetweenDates(carId, afterDate, beforeDate)

    /**
     * Get the sync completion percentage for deep stats.
     * Returns 1.0 if sync is marked complete, regardless of actual count
     * (some items may have failed but sync is done).
     * Only counts aggregates with current schema version to accurately reflect
     * progress when schema changes require reprocessing.
     */
    suspend fun getDeepSyncProgress(carId: Int): Float {
        // If sync is marked complete, return 1.0
        val progress = syncManager.getProgressForCar(carId)
        if (progress?.phase == com.matedroid.domain.model.SyncPhase.COMPLETE) {
            return 1f
        }

        val totalDrives = driveSummaryDao.count(carId)
        val totalCharges = chargeSummaryDao.count(carId)
        // Only count aggregates with current schema version
        val processedDrives = aggregateDao.countDriveAggregatesWithSchema(carId, SchemaVersion.CURRENT)
        val processedCharges = aggregateDao.countChargeAggregatesWithSchema(carId, SchemaVersion.CURRENT)

        val total = totalDrives + totalCharges
        val processed = processedDrives + processedCharges

        return if (total > 0) processed.toFloat() / total else 0f
    }

    /**
     * Observe sync progress as a Flow. Room automatically emits when tables change.
     * This provides real-time progress updates without relying on StateFlow propagation.
     * Only counts aggregates with current schema version to accurately reflect
     * progress when schema changes require reprocessing.
     */
    fun observeDeepSyncProgress(carId: Int): kotlinx.coroutines.flow.Flow<Float> {
        return kotlinx.coroutines.flow.combine(
            driveSummaryDao.observeCount(carId),
            chargeSummaryDao.observeCount(carId),
            aggregateDao.observeDriveAggregateCountWithSchema(carId, SchemaVersion.CURRENT),
            aggregateDao.observeChargeAggregateCountWithSchema(carId, SchemaVersion.CURRENT)
        ) { totalDrives, totalCharges, processedDrives, processedCharges ->
            val total = totalDrives + totalCharges
            val processed = processedDrives + processedCharges
            if (total > 0) processed.toFloat() / total else 0f
        }
    }

    /**
     * Get countries visited with aggregated data.
     */
    suspend fun getCountriesVisited(carId: Int, yearFilter: YearFilter): List<CountryRecord> {
        val results = when (yearFilter) {
            is YearFilter.AllTime -> aggregateDao.getCountriesVisited(carId)
            is YearFilter.Year -> {
                val startDate = "${yearFilter.year}-01-01T00:00:00"
                val endDate = "${yearFilter.year + 1}-01-01T00:00:00"
                aggregateDao.getCountriesVisitedInRange(carId, startDate, endDate)
            }
        }
        return results.map { it.toCountryRecord() }
    }

    /**
     * Get regions visited within a specific country with aggregated data.
     */
    suspend fun getRegionsVisited(carId: Int, countryCode: String, yearFilter: YearFilter): List<RegionRecord> {
        val results = when (yearFilter) {
            is YearFilter.AllTime -> aggregateDao.getRegionsVisited(carId, countryCode)
            is YearFilter.Year -> {
                val startDate = "${yearFilter.year}-01-01T00:00:00"
                val endDate = "${yearFilter.year + 1}-01-01T00:00:00"
                aggregateDao.getRegionsVisitedInRange(carId, countryCode, startDate, endDate)
            }
        }
        return results.map { it.toRegionRecord() }
    }

    /**
     * Observe geocoding progress for a car.
     * Returns null when geocoding is complete or hasn't started.
     */
    fun observeGeocodeProgress(carId: Int): Flow<GeocodeProgressInfo?> {
        return geocodingRepository.observeGeocodeProgress(carId)
    }

    /**
     * Get all charge locations for a specific country (for map display).
     */
    suspend fun getChargeLocationsForCountry(
        carId: Int,
        countryCode: String,
        yearFilter: YearFilter
    ): List<ChargeLocation> {
        val results = when (yearFilter) {
            is YearFilter.AllTime -> aggregateDao.getChargeLocationsForCountry(carId, countryCode)
            is YearFilter.Year -> {
                val startDate = "${yearFilter.year}-01-01T00:00:00"
                val endDate = "${yearFilter.year + 1}-01-01T00:00:00"
                aggregateDao.getChargeLocationsForCountryInRange(carId, countryCode, startDate, endDate)
            }
        }
        return results.map { it.toChargeLocation() }
    }

    /**
     * Get country boundary polygon for map overlay.
     */
    suspend fun getCountryBoundary(countryCode: String): CountryBoundary? {
        return geocodingRepository.getCountryBoundary(countryCode)
    }

    /**
     * Get all drive start locations for a specific country (for map display).
     */
    suspend fun getDriveLocationsForCountry(
        carId: Int,
        countryCode: String,
        yearFilter: YearFilter
    ): List<DriveLocation> {
        val results = when (yearFilter) {
            is YearFilter.AllTime -> aggregateDao.getDriveLocationsForCountry(carId, countryCode)
            is YearFilter.Year -> {
                val startDate = "${yearFilter.year}-01-01T00:00:00"
                val endDate = "${yearFilter.year + 1}-01-01T00:00:00"
                aggregateDao.getDriveLocationsForCountryInRange(carId, countryCode, startDate, endDate)
            }
        }
        return results.map { it.toDriveLocation() }
    }
}

/**
 * Convert ISO 3166-1 alpha-2 country code to flag emoji.
 * Uses Regional Indicator Symbols to create flag emojis.
 * Example: "IT" -> "🇮🇹", "US" -> "🇺🇸"
 */
fun countryCodeToFlag(countryCode: String): String {
    if (countryCode.length != 2) return ""
    val firstChar = countryCode[0].uppercaseChar()
    val secondChar = countryCode[1].uppercaseChar()
    // Regional Indicator Symbol Letter A starts at U+1F1E6
    val first = 0x1F1E6 - 'A'.code + firstChar.code
    val second = 0x1F1E6 - 'A'.code + secondChar.code
    return String(intArrayOf(first, second), 0, 2)
}

private fun com.matedroid.data.local.dao.CountryVisitResult.toCountryRecord() = CountryRecord(
    countryCode = countryCode,
    countryName = countryName,
    flagEmoji = countryCodeToFlag(countryCode),
    firstVisitDate = firstVisitDate,
    lastVisitDate = lastVisitDate,
    driveCount = driveCount,
    totalDistanceKm = totalDistanceKm,
    totalChargeEnergyKwh = totalChargeEnergyKwh,
    chargeCount = chargeCount
)

private fun com.matedroid.data.local.dao.RegionVisitResult.toRegionRecord() = RegionRecord(
    regionName = regionName,
    countryCode = countryCode,
    firstVisitDate = firstVisitDate,
    lastVisitDate = lastVisitDate,
    driveCount = driveCount,
    totalDistanceKm = totalDistanceKm,
    totalChargeEnergyKwh = totalChargeEnergyKwh,
    chargeCount = chargeCount
)

private fun com.matedroid.data.local.dao.ChargeLocationResult.toChargeLocation() = ChargeLocation(
    chargeId = chargeId,
    latitude = latitude,
    longitude = longitude,
    energyAddedKwh = energyAdded,
    date = startDate,
    isDcCharge = isFastCharger,
    address = address
)

private fun com.matedroid.data.local.dao.DriveLocationResult.toDriveLocation() = DriveLocation(
    driveId = driveId,
    latitude = latitude,
    longitude = longitude,
    distanceKm = distance,
    date = startDate,
    address = address
)

private fun com.matedroid.domain.model.SyncPhase.isProcessing(): Boolean {
    return this == com.matedroid.domain.model.SyncPhase.SYNCING_SUMMARIES ||
            this == com.matedroid.domain.model.SyncPhase.SYNCING_DRIVE_DETAILS ||
            this == com.matedroid.domain.model.SyncPhase.SYNCING_CHARGE_DETAILS
}

/**
 * Compute the longest consecutive driving streak from a sorted list of date strings.
 * Each date string should be in "YYYY-MM-DD" format.
 */
private fun computeLongestStreak(sortedDays: List<String>): StreakRecord? {
    if (sortedDays.isEmpty()) return null
    if (sortedDays.size == 1) {
        return StreakRecord(
            streakDays = 1,
            startDate = sortedDays.first(),
            endDate = sortedDays.first()
        )
    }

    var maxStreak = 1
    var maxStreakStart = sortedDays.first()
    var maxStreakEnd = sortedDays.first()

    var currentStreak = 1
    var currentStreakStart = sortedDays.first()

    for (i in 1 until sortedDays.size) {
        val prevDate = java.time.LocalDate.parse(sortedDays[i - 1])
        val currDate = java.time.LocalDate.parse(sortedDays[i])

        if (currDate == prevDate.plusDays(1)) {
            // Consecutive day
            currentStreak++
        } else {
            // Gap found - check if previous streak was longest
            if (currentStreak > maxStreak) {
                maxStreak = currentStreak
                maxStreakStart = currentStreakStart
                maxStreakEnd = sortedDays[i - 1]
            }
            // Start new streak
            currentStreak = 1
            currentStreakStart = sortedDays[i]
        }
    }

    // Check final streak
    if (currentStreak > maxStreak) {
        maxStreak = currentStreak
        maxStreakStart = currentStreakStart
        maxStreakEnd = sortedDays.last()
    }

    return StreakRecord(
        streakDays = maxStreak,
        startDate = maxStreakStart,
        endDate = maxStreakEnd
    )
}
