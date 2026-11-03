package com.matedroid.data.repository

import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.ChargeSummaryDao
import com.matedroid.data.local.dao.DateBounds
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.local.entity.SchemaVersion
import com.matedroid.data.sync.SyncManager
import com.matedroid.domain.CostPerKwhBasis
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
    suspend fun getStats(carId: Int, yearFilter: YearFilter): CarStats = withContext(Dispatchers.IO) {
        // Off the main thread, and the three groups are independent — run them in parallel.
        val quickStats = async { getQuickStats(carId, yearFilter) }
        val deepStats = async { getDeepStats(carId, yearFilter) }
        val syncProgress = async { syncManager.getProgressForCar(carId) }

        CarStats(
            carId = carId,
            yearFilter = yearFilter,
            quickStats = quickStats.await(),
            deepStats = deepStats.await(),
            syncProgress = syncProgress.await()
        )
    }

    /**
     * Get quick stats (from summary tables, instant).
     */
    suspend fun getQuickStats(carId: Int, yearFilter: YearFilter): QuickStats = coroutineScope {
        val (startDate, endDate) = yearFilter.toDateBounds()
        val allTime = yearFilter is YearFilter.AllTime
        val useEnergyUsedBasis = CostPerKwhBasis.current == CostPerKwhBasis.ENERGY_USED

        // Independent queries: fan them out like getDeepStats instead of ~27 sequential round trips.
        val totalDrives = async { driveSummaryDao.count(carId, startDate, endDate) }
        val totalDistance = async { driveSummaryDao.sumDistance(carId, startDate, endDate) }
        val totalEnergyConsumed = async { driveSummaryDao.sumEnergyConsumed(carId, startDate, endDate) }
        val avgEfficiency = async { driveSummaryDao.avgEfficiency(carId, startDate, endDate) }
        val maxSpeed = async { driveSummaryDao.maxSpeed(carId, startDate, endDate) }
        val avgDriveMinutes = async { if (allTime) driveSummaryDao.avgDuration(carId) else null } // Not critical for year view
        val totalDrivingDays = async { driveSummaryDao.countDrivingDays(carId, startDate, endDate) }

        val totalCharges = async { chargeSummaryDao.count(carId, startDate, endDate) }
        val totalEnergyAdded = async { chargeSummaryDao.sumEnergyAdded(carId, startDate, endDate) }
        val totalCost = async { chargeSummaryDao.sumCost(carId, startDate, endDate) }
        val avgCostPerKwh = async { chargeSummaryDao.avgCostPerKwh(carId, startDate, endDate, useEnergyUsedBasis) }
        val avgChargeMinutes = async { if (allTime) chargeSummaryDao.avgDuration(carId) else null } // Not critical for year view

        val longestDrive = async { driveSummaryDao.longestDrive(carId, startDate, endDate) }
        val fastestDrive = async { driveSummaryDao.fastestDrive(carId, startDate, endDate) }
        val mostEfficientDrive = async { driveSummaryDao.mostEfficientDrive(carId, startDate, endDate) }
        val leastEfficientDrive = async { driveSummaryDao.leastEfficientDrive(carId, startDate, endDate) }
        val biggestCharge = async { chargeSummaryDao.biggestCharge(carId, startDate, endDate) }
        val mostExpensiveCharge = async { chargeSummaryDao.mostExpensiveCharge(carId, startDate, endDate) }
        val mostExpensivePerKwhCharge = async {
            chargeSummaryDao.mostExpensivePerKwhCharge(carId, startDate, endDate, useEnergyUsedBasis)
        }

        val firstDriveDate = async { driveSummaryDao.firstDriveDate(carId) } // Always show first ever
        val firstChargeDate = async { chargeSummaryDao.firstChargeDate(carId) } // Always show first ever
        val busiestDay = async { driveSummaryDao.busiestDay(carId, startDate, endDate) }
        val mostDistanceDay = async { driveSummaryDao.mostDistanceDay(carId, startDate, endDate) }

        val maxDistanceBetweenCharges = async { chargeSummaryDao.maxDistanceBetweenCharges(carId, startDate, endDate) }
        val longestGapWithoutCharging = async { chargeSummaryDao.longestGapBetweenCharges(carId, startDate, endDate) }
        val longestGapWithoutDriving = async { driveSummaryDao.longestGapBetweenDrives(carId, startDate, endDate) }
        val drivingDays = async { driveSummaryDao.getDistinctDrivingDays(carId, startDate, endDate) }

        val biggestBatteryGainCharge = async { chargeSummaryDao.biggestBatteryGainCharge(carId, startDate, endDate) }
        val biggestBatteryDrainDrive = async { driveSummaryDao.biggestBatteryDrainDrive(carId, startDate, endDate) }

        QuickStats(
            totalDrives = totalDrives.await(),
            totalDistanceKm = totalDistance.await(),
            totalEnergyConsumedKwh = totalEnergyConsumed.await(),
            avgEfficiencyWhKm = avgEfficiency.await(),
            maxSpeedKmh = maxSpeed.await(),
            avgDriveMinutes = avgDriveMinutes.await(),
            totalDrivingDays = totalDrivingDays.await(),

            totalCharges = totalCharges.await(),
            totalEnergyAddedKwh = totalEnergyAdded.await(),
            totalCost = totalCost.await().takeIf { it > 0 },
            avgCostPerKwh = avgCostPerKwh.await().takeIf { it > 0 },
            avgChargeMinutes = avgChargeMinutes.await(),

            longestDrive = longestDrive.await(),
            fastestDrive = fastestDrive.await(),
            mostEfficientDrive = mostEfficientDrive.await(),
            leastEfficientDrive = leastEfficientDrive.await(),
            biggestCharge = biggestCharge.await(),
            mostExpensiveCharge = mostExpensiveCharge.await(),
            mostExpensivePerKwhCharge = mostExpensivePerKwhCharge.await(),

            firstDriveDate = firstDriveDate.await(),
            firstChargeDate = firstChargeDate.await(),
            busiestDay = busiestDay.await(),
            mostDistanceDay = mostDistanceDay.await(),

            maxDistanceBetweenCharges = maxDistanceBetweenCharges.await()?.let {
                MaxDistanceBetweenChargesRecord(
                    distance = it.distance,
                    fromChargeId = it.fromChargeId,
                    toChargeId = it.toChargeId,
                    fromDate = it.fromDate,
                    toDate = it.toDate
                )
            },

            longestGapWithoutCharging = longestGapWithoutCharging.await()?.let {
                GapRecord(gapDays = it.gapDays, fromDate = it.fromDate, toDate = it.toDate)
            },
            longestGapWithoutDriving = longestGapWithoutDriving.await()?.let {
                GapRecord(gapDays = it.gapDays, fromDate = it.fromDate, toDate = it.toDate)
            },

            longestDrivingStreak = computeLongestStreak(drivingDays.await()),

            biggestBatteryGainCharge = biggestBatteryGainCharge.await()?.let {
                BatteryChangeRecord(
                    percentChange = it.endBatteryLevel - it.startBatteryLevel,
                    startLevel = it.startBatteryLevel,
                    endLevel = it.endBatteryLevel,
                    recordId = it.chargeId,
                    date = it.startDate,
                    isCharge = true
                )
            },
            biggestBatteryDrainDrive = biggestBatteryDrainDrive.await()?.let {
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
    suspend fun getDeepStats(carId: Int, yearFilter: YearFilter): DeepStats? = coroutineScope {
        // Existence gate: everything below is pointless without aggregates.
        val driveAggregatesDeferred = async { aggregateDao.countDriveAggregates(carId) }
        val chargeAggregatesDeferred = async { aggregateDao.countChargeAggregates(carId) }
        val driveAggregates = driveAggregatesDeferred.await()
        val chargeAggregates = chargeAggregatesDeferred.await()

        // Return null if no aggregates exist at all
        if (driveAggregates == 0 && chargeAggregates == 0) {
            return@coroutineScope null
        }

        val (startDate, endDate) = yearFilter.toDateBounds()
        val allTime = yearFilter is YearFilter.AllTime

        // All queries below are independent; Room suspend queries run on Room's
        // query executor, so launching them under async fans them out in parallel
        // instead of paying ~23 sequential round trips.

        // Elevation
        val maxElevation = async { aggregateDao.maxElevation(carId, startDate, endDate) }
        val minElevation = async { if (allTime) aggregateDao.minElevation(carId) else null } // Not shown in year view
        val driveWithMaxElev = async { aggregateDao.driveWithMaxElevation(carId, startDate, endDate) }
        val driveWithMinElev = async { if (allTime) aggregateDao.driveWithMinElevation(carId) else null } // Not shown in year view
        val driveWithMostGain = async { aggregateDao.driveWithMostElevationGain(carId, startDate, endDate) }

        // Temperature (driving)
        val maxOutsideTempDriving = async { aggregateDao.maxOutsideTempDriving(carId, startDate, endDate) }
        val minOutsideTempDriving = async { aggregateDao.minOutsideTempDriving(carId, startDate, endDate) }
        val maxInsideTemp = async { aggregateDao.maxInsideTemp(carId, startDate, endDate) }
        val minInsideTemp = async { aggregateDao.minInsideTemp(carId, startDate, endDate) }
        val hottestDrive = async { aggregateDao.hottestDrive(carId, startDate, endDate) }
        val coldestDrive = async { aggregateDao.coldestDrive(carId, startDate, endDate) }

        // Temperature (charging)
        val maxOutsideTempCharging = async { aggregateDao.maxOutsideTempCharging(carId, startDate, endDate) }
        val minOutsideTempCharging = async { aggregateDao.minOutsideTempCharging(carId, startDate, endDate) }
        val hottestCharge = async { aggregateDao.hottestCharge(carId, startDate, endDate) }
        val coldestCharge = async { aggregateDao.coldestCharge(carId, startDate, endDate) }

        // Charging power
        val maxChargerPower = async { aggregateDao.maxChargerPower(carId, startDate, endDate) }
        val chargeWithMaxPower = async { aggregateDao.chargeWithMaxPower(carId, startDate, endDate) }

        // AC/DC split
        val acChargeCount = async { aggregateDao.countAcCharges(carId, startDate, endDate) }
        val dcChargeCount = async { aggregateDao.countDcCharges(carId, startDate, endDate) }
        val acChargeEnergy = async { aggregateDao.sumAcChargeEnergy(carId, startDate, endDate) }
        val dcChargeEnergy = async { aggregateDao.sumDcChargeEnergy(carId, startDate, endDate) }

        // Countries
        val uniqueCountries = async { aggregateDao.countUniqueCountries(carId, startDate, endDate) }

        DeepStats(
            maxElevationM = maxElevation.await(),
            minElevationM = minElevation.await(),
            driveWithMaxElevation = driveWithMaxElev.await()?.let {
                DriveElevationRecord(
                    driveId = it.driveId,
                    elevationM = it.elevation ?: 0,
                    elevationGainM = it.elevationGain,
                    date = it.startDate
                )
            },
            driveWithMinElevation = driveWithMinElev.await()?.let {
                DriveElevationRecord(
                    driveId = it.driveId,
                    elevationM = it.elevation ?: 0,
                    elevationGainM = it.elevationGain,
                    date = it.startDate
                )
            },
            driveWithMostClimbing = driveWithMostGain.await()?.let {
                DriveElevationRecord(
                    driveId = it.driveId,
                    elevationM = it.elevation ?: 0,
                    elevationGainM = it.elevationGain,
                    date = it.startDate
                )
            },

            maxOutsideTempDrivingC = maxOutsideTempDriving.await(),
            minOutsideTempDrivingC = minOutsideTempDriving.await(),
            maxCabinTempC = maxInsideTemp.await(),
            minCabinTempC = minInsideTemp.await(),
            hottestDrive = hottestDrive.await()?.let {
                DriveTempRecord(driveId = it.driveId, tempC = it.outsideTemp ?: 0.0, date = it.startDate)
            },
            coldestDrive = coldestDrive.await()?.let {
                DriveTempRecord(driveId = it.driveId, tempC = it.outsideTemp ?: 0.0, date = it.startDate)
            },

            maxOutsideTempChargingC = maxOutsideTempCharging.await(),
            minOutsideTempChargingC = minOutsideTempCharging.await(),
            hottestCharge = hottestCharge.await()?.let {
                ChargeTempRecord(chargeId = it.chargeId, tempC = it.outsideTemp ?: 0.0, date = it.startDate)
            },
            coldestCharge = coldestCharge.await()?.let {
                ChargeTempRecord(chargeId = it.chargeId, tempC = it.outsideTemp ?: 0.0, date = it.startDate)
            },

            maxChargerPowerKw = maxChargerPower.await(),
            chargeWithMaxPower = chargeWithMaxPower.await()?.let {
                ChargePowerRecord(chargeId = it.chargeId, powerKw = it.maxChargerPower ?: 0, date = it.startDate)
            },

            acChargeCount = acChargeCount.await(),
            dcChargeCount = dcChargeCount.await(),
            acChargeEnergyKwh = acChargeEnergy.await(),
            dcChargeEnergyKwh = dcChargeEnergy.await(),

            countriesVisitedCount = uniqueCountries.await().takeIf { it > 0 },

            driveDetailsProcessed = driveAggregates,
            chargeDetailsProcessed = chargeAggregates
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
        return driveSummaryDao.count(carId, DateBounds.MIN, DateBounds.MAX) > 0 ||
                chargeSummaryDao.count(carId, DateBounds.MIN, DateBounds.MAX) > 0
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

        val totalDrives = driveSummaryDao.count(carId, DateBounds.MIN, DateBounds.MAX)
        val totalCharges = chargeSummaryDao.count(carId, DateBounds.MIN, DateBounds.MAX)
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
        val (startDate, endDate) = yearFilter.toDateBounds()
        return aggregateDao.getCountriesVisited(carId, startDate, endDate)
            .map { it.toCountryRecord() }
    }

    /**
     * Get regions visited within a specific country with aggregated data.
     */
    suspend fun getRegionsVisited(carId: Int, countryCode: String, yearFilter: YearFilter): List<RegionRecord> {
        val (startDate, endDate) = yearFilter.toDateBounds()
        return aggregateDao.getRegionsVisited(carId, countryCode, startDate, endDate)
            .map { it.toRegionRecord() }
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
        val (startDate, endDate) = yearFilter.toDateBounds()
        return aggregateDao.getChargeLocationsForCountry(carId, countryCode, startDate, endDate)
            .map { it.toChargeLocation() }
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
        val (startDate, endDate) = yearFilter.toDateBounds()
        return aggregateDao.getDriveLocationsForCountry(carId, countryCode, startDate, endDate)
            .map { it.toDriveLocation() }
    }
}

/**
 * Map a [YearFilter] to (startDate, endDate) query bounds.
 * All-time uses the [DateBounds] sentinels, which match every row.
 */
private fun YearFilter.toDateBounds(): Pair<String, String> = when (this) {
    is YearFilter.AllTime -> DateBounds.MIN to DateBounds.MAX
    is YearFilter.Year -> "$year-01-01T00:00:00" to "${year + 1}-01-01T00:00:00"
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
