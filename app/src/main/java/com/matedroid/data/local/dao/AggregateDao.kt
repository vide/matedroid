package com.matedroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.matedroid.data.local.entity.ChargeDetailAggregate
import com.matedroid.data.local.entity.DriveDetailAggregate

@Dao
interface AggregateDao {

    // === Drive Detail Aggregates ===

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDriveAggregate(aggregate: DriveDetailAggregate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDriveAggregates(aggregates: List<DriveDetailAggregate>)

    @Query("SELECT * FROM drive_detail_aggregates WHERE driveId = :driveId")
    suspend fun getDriveAggregate(driveId: Int): DriveDetailAggregate?

    @Query("DELETE FROM drive_detail_aggregates WHERE carId = :carId")
    suspend fun deleteDriveAggregatesForCar(carId: Int)

    // === Charge Detail Aggregates ===

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChargeAggregate(aggregate: ChargeDetailAggregate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChargeAggregates(aggregates: List<ChargeDetailAggregate>)

    @Query("SELECT * FROM charge_detail_aggregates WHERE chargeId = :chargeId")
    suspend fun getChargeAggregate(chargeId: Int): ChargeDetailAggregate?

    @Query("DELETE FROM charge_detail_aggregates WHERE carId = :carId")
    suspend fun deleteChargeAggregatesForCar(carId: Int)

    // === Deep Stats: Elevation ===

    // Highest altitude ever reached
    @Query("""
        SELECT MAX(maxElevation) FROM drive_detail_aggregates
        WHERE carId = :carId AND hasElevationData = 1
    """)
    suspend fun maxElevation(carId: Int): Int?

    @Query("""
        SELECT MAX(maxElevation) FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId AND a.hasElevationData = 1
        AND d.startDate >= :startDate AND d.startDate < :endDate
    """)
    suspend fun maxElevationInRange(carId: Int, startDate: String, endDate: String): Int?

    // Drive with highest altitude
    @Query("""
        SELECT a.* FROM drive_detail_aggregates a
        WHERE a.carId = :carId AND a.hasElevationData = 1
        ORDER BY a.maxElevation DESC LIMIT 1
    """)
    suspend fun driveWithMaxElevation(carId: Int): DriveDetailAggregate?

    @Query("""
        SELECT a.* FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId AND a.hasElevationData = 1
        AND d.startDate >= :startDate AND d.startDate < :endDate
        ORDER BY a.maxElevation DESC LIMIT 1
    """)
    suspend fun driveWithMaxElevationInRange(carId: Int, startDate: String, endDate: String): DriveDetailAggregate?

    // Lowest altitude ever reached
    @Query("""
        SELECT MIN(minElevation) FROM drive_detail_aggregates
        WHERE carId = :carId AND hasElevationData = 1
    """)
    suspend fun minElevation(carId: Int): Int?

    // Drive with lowest altitude
    @Query("""
        SELECT a.* FROM drive_detail_aggregates a
        WHERE a.carId = :carId AND a.hasElevationData = 1
        ORDER BY a.minElevation ASC LIMIT 1
    """)
    suspend fun driveWithMinElevation(carId: Int): DriveDetailAggregate?

    // Drive with most accumulated elevation gain (total meters climbed)
    @Query("""
        SELECT a.* FROM drive_detail_aggregates a
        WHERE a.carId = :carId
        AND a.elevationGain IS NOT NULL
        ORDER BY a.elevationGain DESC LIMIT 1
    """)
    suspend fun driveWithMostElevationGain(carId: Int): DriveDetailAggregate?

    @Query("""
        SELECT a.* FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId
        AND a.elevationGain IS NOT NULL
        AND d.startDate >= :startDate AND d.startDate < :endDate
        ORDER BY a.elevationGain DESC LIMIT 1
    """)
    suspend fun driveWithMostElevationGainInRange(carId: Int, startDate: String, endDate: String): DriveDetailAggregate?

    // === Deep Stats: Temperature (Driving) ===

    // Hottest outside temperature while driving
    @Query("""
        SELECT MAX(maxOutsideTemp) FROM drive_detail_aggregates
        WHERE carId = :carId
    """)
    suspend fun maxOutsideTempDriving(carId: Int): Double?

    @Query("""
        SELECT MAX(maxOutsideTemp) FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId
        AND d.startDate >= :startDate AND d.startDate < :endDate
    """)
    suspend fun maxOutsideTempDrivingInRange(carId: Int, startDate: String, endDate: String): Double?

    // Drive with hottest temperature
    @Query("""
        SELECT a.* FROM drive_detail_aggregates a
        WHERE a.carId = :carId AND a.maxOutsideTemp IS NOT NULL
        ORDER BY a.maxOutsideTemp DESC LIMIT 1
    """)
    suspend fun hottestDrive(carId: Int): DriveDetailAggregate?

    @Query("""
        SELECT a.* FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId AND a.maxOutsideTemp IS NOT NULL
        AND d.startDate >= :startDate AND d.startDate < :endDate
        ORDER BY a.maxOutsideTemp DESC LIMIT 1
    """)
    suspend fun hottestDriveInRange(carId: Int, startDate: String, endDate: String): DriveDetailAggregate?

    // Coldest outside temperature while driving
    @Query("""
        SELECT MIN(minOutsideTemp) FROM drive_detail_aggregates
        WHERE carId = :carId
    """)
    suspend fun minOutsideTempDriving(carId: Int): Double?

    // Drive with coldest temperature
    @Query("""
        SELECT a.* FROM drive_detail_aggregates a
        WHERE a.carId = :carId AND a.minOutsideTemp IS NOT NULL
        ORDER BY a.minOutsideTemp ASC LIMIT 1
    """)
    suspend fun coldestDrive(carId: Int): DriveDetailAggregate?

    @Query("""
        SELECT a.* FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId AND a.minOutsideTemp IS NOT NULL
        AND d.startDate >= :startDate AND d.startDate < :endDate
        ORDER BY a.minOutsideTemp ASC LIMIT 1
    """)
    suspend fun coldestDriveInRange(carId: Int, startDate: String, endDate: String): DriveDetailAggregate?

    // Hottest cabin temperature
    @Query("SELECT MAX(maxInsideTemp) FROM drive_detail_aggregates WHERE carId = :carId")
    suspend fun maxInsideTemp(carId: Int): Double?

    // Coldest cabin temperature
    @Query("SELECT MIN(minInsideTemp) FROM drive_detail_aggregates WHERE carId = :carId")
    suspend fun minInsideTemp(carId: Int): Double?

    // === Deep Stats: Temperature (Charging) ===

    // Hottest outside temperature while charging
    @Query("SELECT MAX(maxOutsideTemp) FROM charge_detail_aggregates WHERE carId = :carId")
    suspend fun maxOutsideTempCharging(carId: Int): Double?

    @Query("""
        SELECT MAX(maxOutsideTemp) FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId
        AND c.startDate >= :startDate AND c.startDate < :endDate
    """)
    suspend fun maxOutsideTempChargingInRange(carId: Int, startDate: String, endDate: String): Double?

    // Charge with hottest temperature
    @Query("""
        SELECT a.* FROM charge_detail_aggregates a
        WHERE a.carId = :carId AND a.maxOutsideTemp IS NOT NULL
        ORDER BY a.maxOutsideTemp DESC LIMIT 1
    """)
    suspend fun hottestCharge(carId: Int): ChargeDetailAggregate?

    @Query("""
        SELECT a.* FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId AND a.maxOutsideTemp IS NOT NULL
        AND c.startDate >= :startDate AND c.startDate < :endDate
        ORDER BY a.maxOutsideTemp DESC LIMIT 1
    """)
    suspend fun hottestChargeInRange(carId: Int, startDate: String, endDate: String): ChargeDetailAggregate?

    // Coldest outside temperature while charging
    @Query("SELECT MIN(minOutsideTemp) FROM charge_detail_aggregates WHERE carId = :carId")
    suspend fun minOutsideTempCharging(carId: Int): Double?

    // Charge with coldest temperature
    @Query("""
        SELECT a.* FROM charge_detail_aggregates a
        WHERE a.carId = :carId AND a.minOutsideTemp IS NOT NULL
        ORDER BY a.minOutsideTemp ASC LIMIT 1
    """)
    suspend fun coldestCharge(carId: Int): ChargeDetailAggregate?

    @Query("""
        SELECT a.* FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId AND a.minOutsideTemp IS NOT NULL
        AND c.startDate >= :startDate AND c.startDate < :endDate
        ORDER BY a.minOutsideTemp ASC LIMIT 1
    """)
    suspend fun coldestChargeInRange(carId: Int, startDate: String, endDate: String): ChargeDetailAggregate?

    // === Deep Stats: Charging Power ===

    // Max charge power ever achieved
    @Query("SELECT MAX(maxChargerPower) FROM charge_detail_aggregates WHERE carId = :carId")
    suspend fun maxChargerPower(carId: Int): Int?

    @Query("""
        SELECT MAX(maxChargerPower) FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId
        AND c.startDate >= :startDate AND c.startDate < :endDate
    """)
    suspend fun maxChargerPowerInRange(carId: Int, startDate: String, endDate: String): Int?

    // Charge with max power
    @Query("""
        SELECT a.* FROM charge_detail_aggregates a
        WHERE a.carId = :carId AND a.maxChargerPower IS NOT NULL
        ORDER BY a.maxChargerPower DESC LIMIT 1
    """)
    suspend fun chargeWithMaxPower(carId: Int): ChargeDetailAggregate?

    @Query("""
        SELECT a.* FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId AND a.maxChargerPower IS NOT NULL
        AND c.startDate >= :startDate AND c.startDate < :endDate
        ORDER BY a.maxChargerPower DESC LIMIT 1
    """)
    suspend fun chargeWithMaxPowerInRange(carId: Int, startDate: String, endDate: String): ChargeDetailAggregate?

    // === Deep Stats: AC/DC Ratio ===

    // Count of AC charges
    @Query("SELECT COUNT(*) FROM charge_detail_aggregates WHERE carId = :carId AND isFastCharger = 0")
    suspend fun countAcCharges(carId: Int): Int

    @Query("""
        SELECT COUNT(*) FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId AND a.isFastCharger = 0
        AND c.startDate >= :startDate AND c.startDate < :endDate
    """)
    suspend fun countAcChargesInRange(carId: Int, startDate: String, endDate: String): Int

    // Count of DC charges
    @Query("SELECT COUNT(*) FROM charge_detail_aggregates WHERE carId = :carId AND isFastCharger = 1")
    suspend fun countDcCharges(carId: Int): Int

    @Query("""
        SELECT COUNT(*) FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId AND a.isFastCharger = 1
        AND c.startDate >= :startDate AND c.startDate < :endDate
    """)
    suspend fun countDcChargesInRange(carId: Int, startDate: String, endDate: String): Int

    // Get set of DC charge IDs (for UI badges)
    @Query("SELECT chargeId FROM charge_detail_aggregates WHERE carId = :carId AND isFastCharger = 1")
    suspend fun getDcChargeIds(carId: Int): List<Int>

    // Sum of energy added for AC charges (join with summary to get energyAdded)
    @Query("""
        SELECT COALESCE(SUM(c.energyAdded), 0.0) FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId AND a.isFastCharger = 0
    """)
    suspend fun sumAcChargeEnergy(carId: Int): Double

    @Query("""
        SELECT COALESCE(SUM(c.energyAdded), 0.0) FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId AND a.isFastCharger = 0
        AND c.startDate >= :startDate AND c.startDate < :endDate
    """)
    suspend fun sumAcChargeEnergyInRange(carId: Int, startDate: String, endDate: String): Double

    // Sum of energy added for DC charges
    @Query("""
        SELECT COALESCE(SUM(c.energyAdded), 0.0) FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId AND a.isFastCharger = 1
    """)
    suspend fun sumDcChargeEnergy(carId: Int): Double

    @Query("""
        SELECT COALESCE(SUM(c.energyAdded), 0.0) FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId AND a.isFastCharger = 1
        AND c.startDate >= :startDate AND c.startDate < :endDate
    """)
    suspend fun sumDcChargeEnergyInRange(carId: Int, startDate: String, endDate: String): Double

    // Get all processed charge IDs (for checking if we have aggregate data)
    @Query("SELECT chargeId FROM charge_detail_aggregates WHERE carId = :carId")
    suspend fun getAllProcessedChargeIds(carId: Int): List<Int>

    // Total processed aggregates count (for progress)
    @Query("SELECT COUNT(*) FROM drive_detail_aggregates WHERE carId = :carId")
    suspend fun countDriveAggregates(carId: Int): Int

    @Query("SELECT COUNT(*) FROM charge_detail_aggregates WHERE carId = :carId")
    suspend fun countChargeAggregates(carId: Int): Int

    // Schema-version-aware counts (only count aggregates with current schema)
    @Query("SELECT COUNT(*) FROM drive_detail_aggregates WHERE carId = :carId AND schemaVersion >= :minVersion")
    suspend fun countDriveAggregatesWithSchema(carId: Int, minVersion: Int): Int

    @Query("SELECT COUNT(*) FROM charge_detail_aggregates WHERE carId = :carId AND schemaVersion >= :minVersion")
    suspend fun countChargeAggregatesWithSchema(carId: Int, minVersion: Int): Int

    // Flow-based counts for real-time progress updates (Room emits on table changes)
    @Query("SELECT COUNT(*) FROM drive_detail_aggregates WHERE carId = :carId")
    fun observeDriveAggregateCount(carId: Int): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT COUNT(*) FROM charge_detail_aggregates WHERE carId = :carId")
    fun observeChargeAggregateCount(carId: Int): kotlinx.coroutines.flow.Flow<Int>

    // Schema-version-aware Flow counts (for accurate progress during schema migrations)
    @Query("SELECT COUNT(*) FROM drive_detail_aggregates WHERE carId = :carId AND schemaVersion >= :minVersion")
    fun observeDriveAggregateCountWithSchema(carId: Int, minVersion: Int): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT COUNT(*) FROM charge_detail_aggregates WHERE carId = :carId AND schemaVersion >= :minVersion")
    fun observeChargeAggregateCountWithSchema(carId: Int, minVersion: Int): kotlinx.coroutines.flow.Flow<Int>

    // === Deep Stats: Countries Visited ===

    // Count unique countries visited
    @Query("""
        SELECT COUNT(DISTINCT startCountryCode) FROM drive_detail_aggregates
        WHERE carId = :carId AND startCountryCode IS NOT NULL
    """)
    suspend fun countUniqueCountries(carId: Int): Int

    @Query("""
        SELECT COUNT(DISTINCT a.startCountryCode) FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId AND a.startCountryCode IS NOT NULL
        AND d.startDate >= :startDate AND d.startDate < :endDate
    """)
    suspend fun countUniqueCountriesInRange(carId: Int, startDate: String, endDate: String): Int

    // Get countries visited with aggregated data (drives + charges)
    @Query("""
        SELECT
            drive_stats.countryCode,
            drive_stats.countryName,
            drive_stats.firstVisitDate,
            drive_stats.lastVisitDate,
            drive_stats.driveCount,
            drive_stats.totalDistanceKm,
            COALESCE(charge_stats.totalChargeEnergyKwh, 0.0) as totalChargeEnergyKwh,
            COALESCE(charge_stats.chargeCount, 0) as chargeCount
        FROM (
            SELECT a.startCountryCode as countryCode, a.startCountryName as countryName,
                   MIN(d.startDate) as firstVisitDate, MAX(d.startDate) as lastVisitDate,
                   COUNT(*) as driveCount, SUM(d.distance) as totalDistanceKm
            FROM drive_detail_aggregates a
            JOIN drives_summary d ON a.driveId = d.driveId
            WHERE a.carId = :carId AND a.startCountryCode IS NOT NULL
            GROUP BY a.startCountryCode
        ) drive_stats
        LEFT JOIN (
            SELECT ca.countryCode, SUM(cs.energyAdded) as totalChargeEnergyKwh, COUNT(*) as chargeCount
            FROM charge_detail_aggregates ca
            JOIN charges_summary cs ON ca.chargeId = cs.chargeId
            WHERE ca.carId = :carId AND ca.countryCode IS NOT NULL
            GROUP BY ca.countryCode
        ) charge_stats ON drive_stats.countryCode = charge_stats.countryCode
        ORDER BY drive_stats.firstVisitDate ASC
    """)
    suspend fun getCountriesVisited(carId: Int): List<CountryVisitResult>

    @Query("""
        SELECT
            drive_stats.countryCode,
            drive_stats.countryName,
            drive_stats.firstVisitDate,
            drive_stats.lastVisitDate,
            drive_stats.driveCount,
            drive_stats.totalDistanceKm,
            COALESCE(charge_stats.totalChargeEnergyKwh, 0.0) as totalChargeEnergyKwh,
            COALESCE(charge_stats.chargeCount, 0) as chargeCount
        FROM (
            SELECT a.startCountryCode as countryCode, a.startCountryName as countryName,
                   MIN(d.startDate) as firstVisitDate, MAX(d.startDate) as lastVisitDate,
                   COUNT(*) as driveCount, SUM(d.distance) as totalDistanceKm
            FROM drive_detail_aggregates a
            JOIN drives_summary d ON a.driveId = d.driveId
            WHERE a.carId = :carId AND a.startCountryCode IS NOT NULL
            AND d.startDate >= :startDate AND d.startDate < :endDate
            GROUP BY a.startCountryCode
        ) drive_stats
        LEFT JOIN (
            SELECT ca.countryCode, SUM(cs.energyAdded) as totalChargeEnergyKwh, COUNT(*) as chargeCount
            FROM charge_detail_aggregates ca
            JOIN charges_summary cs ON ca.chargeId = cs.chargeId
            WHERE ca.carId = :carId AND ca.countryCode IS NOT NULL
            AND cs.startDate >= :startDate AND cs.startDate < :endDate
            GROUP BY ca.countryCode
        ) charge_stats ON drive_stats.countryCode = charge_stats.countryCode
        ORDER BY drive_stats.firstVisitDate ASC
    """)
    suspend fun getCountriesVisitedInRange(carId: Int, startDate: String, endDate: String): List<CountryVisitResult>

    // === Deep Stats: Regions Visited (within a country) ===

    // Get regions visited within a specific country with aggregated data
    @Query("""
        SELECT
            drive_stats.regionName,
            drive_stats.countryCode,
            drive_stats.firstVisitDate,
            drive_stats.lastVisitDate,
            drive_stats.driveCount,
            drive_stats.totalDistanceKm,
            COALESCE(charge_stats.totalChargeEnergyKwh, 0.0) as totalChargeEnergyKwh,
            COALESCE(charge_stats.chargeCount, 0) as chargeCount
        FROM (
            SELECT a.startRegionName as regionName, a.startCountryCode as countryCode,
                   MIN(d.startDate) as firstVisitDate, MAX(d.startDate) as lastVisitDate,
                   COUNT(*) as driveCount, SUM(d.distance) as totalDistanceKm
            FROM drive_detail_aggregates a
            JOIN drives_summary d ON a.driveId = d.driveId
            WHERE a.carId = :carId AND a.startCountryCode = :countryCode AND a.startRegionName IS NOT NULL
            GROUP BY a.startRegionName
        ) drive_stats
        LEFT JOIN (
            SELECT ca.regionName, SUM(cs.energyAdded) as totalChargeEnergyKwh, COUNT(*) as chargeCount
            FROM charge_detail_aggregates ca
            JOIN charges_summary cs ON ca.chargeId = cs.chargeId
            WHERE ca.carId = :carId AND ca.countryCode = :countryCode AND ca.regionName IS NOT NULL
            GROUP BY ca.regionName
        ) charge_stats ON drive_stats.regionName = charge_stats.regionName
        ORDER BY drive_stats.firstVisitDate ASC
    """)
    suspend fun getRegionsVisited(carId: Int, countryCode: String): List<RegionVisitResult>

    @Query("""
        SELECT
            drive_stats.regionName,
            drive_stats.countryCode,
            drive_stats.firstVisitDate,
            drive_stats.lastVisitDate,
            drive_stats.driveCount,
            drive_stats.totalDistanceKm,
            COALESCE(charge_stats.totalChargeEnergyKwh, 0.0) as totalChargeEnergyKwh,
            COALESCE(charge_stats.chargeCount, 0) as chargeCount
        FROM (
            SELECT a.startRegionName as regionName, a.startCountryCode as countryCode,
                   MIN(d.startDate) as firstVisitDate, MAX(d.startDate) as lastVisitDate,
                   COUNT(*) as driveCount, SUM(d.distance) as totalDistanceKm
            FROM drive_detail_aggregates a
            JOIN drives_summary d ON a.driveId = d.driveId
            WHERE a.carId = :carId AND a.startCountryCode = :countryCode AND a.startRegionName IS NOT NULL
            AND d.startDate >= :startDate AND d.startDate < :endDate
            GROUP BY a.startRegionName
        ) drive_stats
        LEFT JOIN (
            SELECT ca.regionName, SUM(cs.energyAdded) as totalChargeEnergyKwh, COUNT(*) as chargeCount
            FROM charge_detail_aggregates ca
            JOIN charges_summary cs ON ca.chargeId = cs.chargeId
            WHERE ca.carId = :carId AND ca.countryCode = :countryCode AND ca.regionName IS NOT NULL
            AND cs.startDate >= :startDate AND cs.startDate < :endDate
            GROUP BY ca.regionName
        ) charge_stats ON drive_stats.regionName = charge_stats.regionName
        ORDER BY drive_stats.firstVisitDate ASC
    """)
    suspend fun getRegionsVisitedInRange(carId: Int, countryCode: String, startDate: String, endDate: String): List<RegionVisitResult>

    // Count unique regions in a country
    @Query("""
        SELECT COUNT(DISTINCT startRegionName) FROM drive_detail_aggregates
        WHERE carId = :carId AND startCountryCode = :countryCode AND startRegionName IS NOT NULL
    """)
    suspend fun countUniqueRegions(carId: Int, countryCode: String): Int

    // === Deep Stats: Cities Visited (Drives) ===

    @Query("""
        SELECT COUNT(DISTINCT startCity) FROM drive_detail_aggregates
        WHERE carId = :carId AND startCity IS NOT NULL
    """)
    suspend fun countUniqueDriveCities(carId: Int): Int

    @Query("""
        SELECT COUNT(DISTINCT a.startCity) FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId AND a.startCity IS NOT NULL
        AND d.startDate >= :startDate AND d.startDate < :endDate
    """)
    suspend fun countUniqueDriveCitiesInRange(carId: Int, startDate: String, endDate: String): Int

    @Query("""
        SELECT a.startCity as city, a.startCountryCode as countryCode, COUNT(*) as driveCount
        FROM drive_detail_aggregates a
        WHERE a.carId = :carId AND a.startCity IS NOT NULL
        GROUP BY a.startCity, a.startCountryCode
        ORDER BY driveCount DESC
        LIMIT :limit
    """)
    suspend fun getTopDriveCities(carId: Int, limit: Int = 5): List<CityDriveCount>

    @Query("""
        SELECT a.startCity as city, a.startCountryCode as countryCode, COUNT(*) as driveCount
        FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId AND a.startCity IS NOT NULL
        AND d.startDate >= :startDate AND d.startDate < :endDate
        GROUP BY a.startCity, a.startCountryCode
        ORDER BY driveCount DESC
        LIMIT :limit
    """)
    suspend fun getTopDriveCitiesInRange(carId: Int, startDate: String, endDate: String, limit: Int = 5): List<CityDriveCount>

    @Query("""
        SELECT a.startRegionName as region, a.startCountryCode as countryCode, COUNT(*) as driveCount
        FROM drive_detail_aggregates a
        WHERE a.carId = :carId AND a.startRegionName IS NOT NULL
        GROUP BY a.startRegionName, a.startCountryCode
        ORDER BY driveCount DESC
    """)
    suspend fun getDrivesByRegion(carId: Int): List<RegionDriveCount>

    // === Deep Stats: Cities/Countries for Charges ===

    @Query("""
        SELECT COUNT(DISTINCT countryCode) FROM charge_detail_aggregates
        WHERE carId = :carId AND countryCode IS NOT NULL
    """)
    suspend fun countUniqueChargeCountries(carId: Int): Int

    @Query("""
        SELECT COUNT(DISTINCT city) FROM charge_detail_aggregates
        WHERE carId = :carId AND city IS NOT NULL
    """)
    suspend fun countUniqueChargeCities(carId: Int): Int

    @Query("""
        SELECT a.city, a.countryCode, COUNT(*) as chargeCount, SUM(c.energyAdded) as totalEnergy
        FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId AND a.city IS NOT NULL
        GROUP BY a.city, a.countryCode
        ORDER BY chargeCount DESC
        LIMIT :limit
    """)
    suspend fun getTopChargeCities(carId: Int, limit: Int = 5): List<CityChargeStats>

    @Query("""
        SELECT a.countryCode, a.countryName,
               COUNT(*) as chargeCount,
               SUM(CASE WHEN a.isFastCharger = 1 THEN 1 ELSE 0 END) as dcCount,
               SUM(CASE WHEN a.isFastCharger = 0 THEN 1 ELSE 0 END) as acCount
        FROM charge_detail_aggregates a
        WHERE a.carId = :carId AND a.countryCode IS NOT NULL
        GROUP BY a.countryCode
        ORDER BY chargeCount DESC
    """)
    suspend fun getChargeStatsByCountry(carId: Int): List<CountryChargeStats>

    // === Location Update Methods (called by GeocodeWorker) ===

    @Query("""
        UPDATE drive_detail_aggregates
        SET startCountryCode = :countryCode,
            startCountryName = :countryName,
            startRegionName = :regionName,
            startCity = :city
        WHERE carId = :carId
        AND startLatitude IS NOT NULL
        AND startLongitude IS NOT NULL
        AND CAST(startLatitude * 100 AS INT) = :gridLat
        AND CAST(startLongitude * 100 AS INT) = :gridLon
        AND startCountryCode IS NULL
    """)
    suspend fun updateDriveLocationsInGrid(
        carId: Int,
        gridLat: Int,
        gridLon: Int,
        countryCode: String?,
        countryName: String?,
        regionName: String?,
        city: String?
    )

    @Query("""
        UPDATE charge_detail_aggregates
        SET countryCode = :countryCode,
            countryName = :countryName,
            regionName = :regionName,
            city = :city
        WHERE chargeId IN (
            SELECT c.chargeId FROM charges_summary c
            JOIN charge_detail_aggregates a ON c.chargeId = a.chargeId
            WHERE a.carId = :carId
            AND CAST(c.latitude * 100 AS INT) = :gridLat
            AND CAST(c.longitude * 100 AS INT) = :gridLon
            AND a.countryCode IS NULL
        )
    """)
    suspend fun updateChargeLocationsInGrid(
        carId: Int,
        gridLat: Int,
        gridLon: Int,
        countryCode: String?,
        countryName: String?,
        regionName: String?,
        city: String?
    )

    // Count drives/charges that need geocoding (have coordinates but no country)
    @Query("""
        SELECT COUNT(*) FROM drive_detail_aggregates
        WHERE carId = :carId
        AND startLatitude IS NOT NULL
        AND startLongitude IS NOT NULL
        AND startCountryCode IS NULL
    """)
    suspend fun countDrivesNeedingGeocode(carId: Int): Int

    @Query("""
        SELECT COUNT(*) FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId
        AND c.latitude IS NOT NULL
        AND c.longitude IS NOT NULL
        AND a.countryCode IS NULL
    """)
    suspend fun countChargesNeedingGeocode(carId: Int): Int

    // Get coordinates of drives that need geocoding (have coordinates but no country)
    @Query("""
        SELECT startLatitude AS latitude, startLongitude AS longitude FROM drive_detail_aggregates
        WHERE carId = :carId
        AND startLatitude IS NOT NULL
        AND startLongitude IS NOT NULL
        AND startCountryCode IS NULL
    """)
    suspend fun getDriveLocationsNeedingGeocode(carId: Int): List<LatLonResult>

    // Get coordinates of charges that need geocoding
    @Query("""
        SELECT c.latitude, c.longitude FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId
        AND c.latitude IS NOT NULL
        AND c.longitude IS NOT NULL
        AND a.countryCode IS NULL
    """)
    suspend fun getChargeLocationsNeedingGeocode(carId: Int): List<LatLonResult>

    // === Charge Locations for Country Map ===

    // Get all charge locations for a specific country (for map display)
    @Query("""
        SELECT c.chargeId, c.latitude, c.longitude, c.energyAdded, c.startDate,
               a.isFastCharger, c.address
        FROM charges_summary c
        JOIN charge_detail_aggregates a ON c.chargeId = a.chargeId
        WHERE a.carId = :carId
        AND a.countryCode = :countryCode
        AND c.latitude IS NOT NULL
        AND c.longitude IS NOT NULL
    """)
    suspend fun getChargeLocationsForCountry(carId: Int, countryCode: String): List<ChargeLocationResult>

    // Get all charge locations for a specific country within a date range
    @Query("""
        SELECT c.chargeId, c.latitude, c.longitude, c.energyAdded, c.startDate,
               a.isFastCharger, c.address
        FROM charges_summary c
        JOIN charge_detail_aggregates a ON c.chargeId = a.chargeId
        WHERE a.carId = :carId
        AND a.countryCode = :countryCode
        AND c.latitude IS NOT NULL
        AND c.longitude IS NOT NULL
        AND c.startDate >= :startDate AND c.startDate < :endDate
    """)
    suspend fun getChargeLocationsForCountryInRange(
        carId: Int,
        countryCode: String,
        startDate: String,
        endDate: String
    ): List<ChargeLocationResult>

    // === Drive Locations for Country Map ===

    // Get all drive start locations for a specific country (for map display)
    @Query("""
        SELECT a.driveId, a.startLatitude as latitude, a.startLongitude as longitude,
               d.distance, d.startDate, d.startAddress as address
        FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId
        AND a.startCountryCode = :countryCode
        AND a.startLatitude IS NOT NULL
        AND a.startLongitude IS NOT NULL
        ORDER BY d.startDate DESC
    """)
    suspend fun getDriveLocationsForCountry(carId: Int, countryCode: String): List<DriveLocationResult>

    // Get drive start locations for a country within a date range
    @Query("""
        SELECT a.driveId, a.startLatitude as latitude, a.startLongitude as longitude,
               d.distance, d.startDate, d.startAddress as address
        FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId
        AND a.startCountryCode = :countryCode
        AND a.startLatitude IS NOT NULL
        AND a.startLongitude IS NOT NULL
        AND d.startDate >= :startDate AND d.startDate < :endDate
        ORDER BY d.startDate DESC
    """)
    suspend fun getDriveLocationsForCountryInRange(
        carId: Int,
        countryCode: String,
        startDate: String,
        endDate: String
    ): List<DriveLocationResult>
}

/**
 * Result of drive locations query for map display.
 */
data class DriveLocationResult(
    val driveId: Int,
    val latitude: Double,
    val longitude: Double,
    val distance: Double,
    val startDate: String,
    val address: String
)

/**
 * Simple lat/lon result for geocoding queries.
 */
data class LatLonResult(
    val latitude: Double,
    val longitude: Double
) {
    fun toLatLon(): Pair<Double, Double> = latitude to longitude
}

/**
 * Result of a country visit aggregation query.
 */
data class CountryVisitResult(
    val countryCode: String,
    val countryName: String,
    val firstVisitDate: String,
    val lastVisitDate: String,
    val driveCount: Int,
    val totalDistanceKm: Double,
    val totalChargeEnergyKwh: Double,
    val chargeCount: Int
)

/**
 * Result of top drive cities query.
 */
data class CityDriveCount(
    val city: String,
    val countryCode: String?,
    val driveCount: Int
)

/**
 * Result of drives by region query.
 */
data class RegionDriveCount(
    val region: String,
    val countryCode: String?,
    val driveCount: Int
)

/**
 * Result of top charge cities query.
 */
data class CityChargeStats(
    val city: String,
    val countryCode: String?,
    val chargeCount: Int,
    val totalEnergy: Double
)

/**
 * Result of charge stats by country query.
 */
data class CountryChargeStats(
    val countryCode: String,
    val countryName: String?,
    val chargeCount: Int,
    val dcCount: Int,
    val acCount: Int
)

/**
 * Result of a region visit aggregation query.
 */
data class RegionVisitResult(
    val regionName: String,
    val countryCode: String,
    val firstVisitDate: String,
    val lastVisitDate: String,
    val driveCount: Int,
    val totalDistanceKm: Double,
    val totalChargeEnergyKwh: Double,
    val chargeCount: Int
)

/**
 * Result of charge locations query for map display.
 */
data class ChargeLocationResult(
    val chargeId: Int,
    val latitude: Double,
    val longitude: Double,
    val energyAdded: Double,
    val startDate: String,
    val isFastCharger: Boolean,
    val address: String
)
