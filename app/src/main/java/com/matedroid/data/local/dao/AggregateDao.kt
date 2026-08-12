package com.matedroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.matedroid.data.local.entity.ChargeDetailAggregate
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveDetailAggregate

/**
 * Range-parameterized queries: callers wanting all-time results pass
 * [DateBounds.MIN] / [DateBounds.MAX] as the date bounds.
 */
@Dao
interface AggregateDao {

    // === Drive Detail Aggregates ===

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDriveAggregates(aggregates: List<DriveDetailAggregate>)

    @Query("SELECT * FROM drive_detail_aggregates WHERE carId = :carId")
    suspend fun getDriveAggregatesForCar(carId: Int): List<DriveDetailAggregate>

    @Query("DELETE FROM drive_detail_aggregates WHERE carId = :carId")
    suspend fun deleteDriveAggregatesForCar(carId: Int)

    // === Charge Detail Aggregates ===

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChargeAggregates(aggregates: List<ChargeDetailAggregate>)

    @Query("SELECT * FROM charge_detail_aggregates WHERE chargeId = :chargeId")
    suspend fun getChargeAggregate(chargeId: Int): ChargeDetailAggregate?

    @Query("SELECT * FROM charge_detail_aggregates WHERE carId = :carId")
    suspend fun getChargeAggregatesForCar(carId: Int): List<ChargeDetailAggregate>

    @Query("DELETE FROM charge_detail_aggregates WHERE carId = :carId")
    suspend fun deleteChargeAggregatesForCar(carId: Int)

    // === Deep Stats: Elevation ===

    // Highest altitude reached
    @Query("""
        SELECT MAX(maxElevation) FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId AND a.hasElevationData = 1
        AND d.startDate >= :startDate AND d.startDate < :endDate
    """)
    suspend fun maxElevation(carId: Int, startDate: String, endDate: String): Int?

    // Drive with highest altitude
    @Query("""
        SELECT a.driveId, a.maxElevation AS elevation, a.elevationGain, d.startDate
        FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId AND a.hasElevationData = 1
        AND d.startDate >= :startDate AND d.startDate < :endDate
        ORDER BY a.maxElevation DESC LIMIT 1
    """)
    suspend fun driveWithMaxElevation(carId: Int, startDate: String, endDate: String): DriveElevationResult?

    // Lowest altitude ever reached
    @Query("""
        SELECT MIN(minElevation) FROM drive_detail_aggregates
        WHERE carId = :carId AND hasElevationData = 1
    """)
    suspend fun minElevation(carId: Int): Int?

    // Drive with lowest altitude (all-time only).
    // LEFT JOIN: the record must survive even without a matching summary row
    // (date is simply null then), matching the old follow-up-lookup behavior.
    @Query("""
        SELECT a.driveId, a.minElevation AS elevation, a.elevationGain, d.startDate
        FROM drive_detail_aggregates a
        LEFT JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId AND a.hasElevationData = 1
        ORDER BY a.minElevation ASC LIMIT 1
    """)
    suspend fun driveWithMinElevation(carId: Int): DriveElevationResult?

    // Drive with most accumulated elevation gain (total meters climbed)
    @Query("""
        SELECT a.driveId, a.maxElevation AS elevation, a.elevationGain, d.startDate
        FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId
        AND a.elevationGain IS NOT NULL
        AND d.startDate >= :startDate AND d.startDate < :endDate
        ORDER BY a.elevationGain DESC LIMIT 1
    """)
    suspend fun driveWithMostElevationGain(carId: Int, startDate: String, endDate: String): DriveElevationResult?

    // === Deep Stats: Temperature (Driving) ===

    // Hottest outside temperature while driving
    @Query("""
        SELECT MAX(maxOutsideTemp) FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId
        AND d.startDate >= :startDate AND d.startDate < :endDate
    """)
    suspend fun maxOutsideTempDriving(carId: Int, startDate: String, endDate: String): Double?

    // Drive with hottest temperature
    @Query("""
        SELECT a.driveId, a.maxOutsideTemp AS outsideTemp, d.startDate
        FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId AND a.maxOutsideTemp IS NOT NULL
        AND d.startDate >= :startDate AND d.startDate < :endDate
        ORDER BY a.maxOutsideTemp DESC LIMIT 1
    """)
    suspend fun hottestDrive(carId: Int, startDate: String, endDate: String): DriveTempResult?

    // Coldest outside temperature while driving
    @Query("""
        SELECT MIN(minOutsideTemp) FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId
        AND d.startDate >= :startDate AND d.startDate < :endDate
    """)
    suspend fun minOutsideTempDriving(carId: Int, startDate: String, endDate: String): Double?

    // Drive with coldest temperature
    @Query("""
        SELECT a.driveId, a.minOutsideTemp AS outsideTemp, d.startDate
        FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId AND a.minOutsideTemp IS NOT NULL
        AND d.startDate >= :startDate AND d.startDate < :endDate
        ORDER BY a.minOutsideTemp ASC LIMIT 1
    """)
    suspend fun coldestDrive(carId: Int, startDate: String, endDate: String): DriveTempResult?

    // Hottest cabin temperature
    @Query("""
        SELECT MAX(maxInsideTemp) FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId
        AND d.startDate >= :startDate AND d.startDate < :endDate
    """)
    suspend fun maxInsideTemp(carId: Int, startDate: String, endDate: String): Double?

    // Coldest cabin temperature
    @Query("""
        SELECT MIN(minInsideTemp) FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId
        AND d.startDate >= :startDate AND d.startDate < :endDate
    """)
    suspend fun minInsideTemp(carId: Int, startDate: String, endDate: String): Double?

    // === Deep Stats: Temperature (Charging) ===

    // Hottest outside temperature while charging
    @Query("""
        SELECT MAX(maxOutsideTemp) FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId
        AND c.startDate >= :startDate AND c.startDate < :endDate
    """)
    suspend fun maxOutsideTempCharging(carId: Int, startDate: String, endDate: String): Double?

    // Coldest outside temperature while charging
    @Query("""
        SELECT MIN(minOutsideTemp) FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId
        AND c.startDate >= :startDate AND c.startDate < :endDate
    """)
    suspend fun minOutsideTempCharging(carId: Int, startDate: String, endDate: String): Double?

    // Charge with hottest temperature
    @Query("""
        SELECT a.chargeId, a.maxOutsideTemp AS outsideTemp, c.startDate
        FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId AND a.maxOutsideTemp IS NOT NULL
        AND c.startDate >= :startDate AND c.startDate < :endDate
        ORDER BY a.maxOutsideTemp DESC LIMIT 1
    """)
    suspend fun hottestCharge(carId: Int, startDate: String, endDate: String): ChargeTempResult?

    // Charge with coldest temperature
    @Query("""
        SELECT a.chargeId, a.minOutsideTemp AS outsideTemp, c.startDate
        FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId AND a.minOutsideTemp IS NOT NULL
        AND c.startDate >= :startDate AND c.startDate < :endDate
        ORDER BY a.minOutsideTemp ASC LIMIT 1
    """)
    suspend fun coldestCharge(carId: Int, startDate: String, endDate: String): ChargeTempResult?

    // === Deep Stats: Charging Power ===

    // Max charge power achieved
    @Query("""
        SELECT MAX(maxChargerPower) FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId
        AND c.startDate >= :startDate AND c.startDate < :endDate
    """)
    suspend fun maxChargerPower(carId: Int, startDate: String, endDate: String): Int?

    // Charge with max power
    @Query("""
        SELECT a.chargeId, a.maxChargerPower, c.startDate
        FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId AND a.maxChargerPower IS NOT NULL
        AND c.startDate >= :startDate AND c.startDate < :endDate
        ORDER BY a.maxChargerPower DESC LIMIT 1
    """)
    suspend fun chargeWithMaxPower(carId: Int, startDate: String, endDate: String): ChargePowerResult?

    // === Deep Stats: AC/DC Ratio ===

    // Count of AC charges
    @Query("""
        SELECT COUNT(*) FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId AND a.isFastCharger = 0
        AND c.startDate >= :startDate AND c.startDate < :endDate
    """)
    suspend fun countAcCharges(carId: Int, startDate: String, endDate: String): Int

    // Count of DC charges
    @Query("""
        SELECT COUNT(*) FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId AND a.isFastCharger = 1
        AND c.startDate >= :startDate AND c.startDate < :endDate
    """)
    suspend fun countDcCharges(carId: Int, startDate: String, endDate: String): Int

    // Get set of DC charge IDs (for UI badges)
    @Query("SELECT chargeId FROM charge_detail_aggregates WHERE carId = :carId AND isFastCharger = 1")
    suspend fun getDcChargeIds(carId: Int): List<Int>

    // Sum of energy added for AC charges (join with summary to get energyAdded)
    @Query("""
        SELECT COALESCE(SUM(c.energyAdded), 0.0) FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId AND a.isFastCharger = 0
        AND c.startDate >= :startDate AND c.startDate < :endDate
    """)
    suspend fun sumAcChargeEnergy(carId: Int, startDate: String, endDate: String): Double

    // Sum of energy added for DC charges
    @Query("""
        SELECT COALESCE(SUM(c.energyAdded), 0.0) FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId AND a.isFastCharger = 1
        AND c.startDate >= :startDate AND c.startDate < :endDate
    """)
    suspend fun sumDcChargeEnergy(carId: Int, startDate: String, endDate: String): Double

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

    // Schema-version-aware Flow counts (for accurate progress during schema migrations)
    @Query("SELECT COUNT(*) FROM drive_detail_aggregates WHERE carId = :carId AND schemaVersion >= :minVersion")
    fun observeDriveAggregateCountWithSchema(carId: Int, minVersion: Int): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT COUNT(*) FROM charge_detail_aggregates WHERE carId = :carId AND schemaVersion >= :minVersion")
    fun observeChargeAggregateCountWithSchema(carId: Int, minVersion: Int): kotlinx.coroutines.flow.Flow<Int>

    // === Deep Stats: Countries Visited ===

    // Count unique countries visited
    @Query("""
        SELECT COUNT(DISTINCT a.startCountryCode) FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId AND a.startCountryCode IS NOT NULL
        AND d.startDate >= :startDate AND d.startDate < :endDate
    """)
    suspend fun countUniqueCountries(carId: Int, startDate: String, endDate: String): Int

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
    suspend fun getCountriesVisited(carId: Int, startDate: String, endDate: String): List<CountryVisitResult>

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
    suspend fun getRegionsVisited(carId: Int, countryCode: String, startDate: String, endDate: String): List<RegionVisitResult>

    // === Deep Stats: Cities Visited (Drives) ===

    @Query("""
        SELECT COUNT(DISTINCT a.startCity) FROM drive_detail_aggregates a
        JOIN drives_summary d ON a.driveId = d.driveId
        WHERE a.carId = :carId AND a.startCity IS NOT NULL
        AND d.startDate >= :startDate AND d.startDate < :endDate
    """)
    suspend fun countUniqueDriveCities(carId: Int, startDate: String, endDate: String): Int

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

    // Get ids + coordinates of drives that need geocoding (have coordinates but no country).
    // Carrying the id lets callers apply cached geocode data with indexed per-id updates
    // instead of CAST(lat*100)-matching UPDATEs, which SQLite can never index (full scan
    // per grid cell).
    @Query("""
        SELECT driveId AS id, startLatitude AS latitude, startLongitude AS longitude
        FROM drive_detail_aggregates
        WHERE carId = :carId
        AND startLatitude IS NOT NULL
        AND startLongitude IS NOT NULL
        AND startCountryCode IS NULL
    """)
    suspend fun getDriveLocationsNeedingGeocode(carId: Int): List<IdLatLonResult>

    // (0.0, 0.0) is the sentinel for "API returned no coordinates" — exclude it or those
    // charges get geocoded to Null Island.
    @Query("""
        SELECT c.chargeId AS id, c.latitude, c.longitude FROM charge_detail_aggregates a
        JOIN charges_summary c ON a.chargeId = c.chargeId
        WHERE a.carId = :carId
        AND NOT (c.latitude = 0.0 AND c.longitude = 0.0)
        AND a.countryCode IS NULL
    """)
    suspend fun getChargeLocationsNeedingGeocode(carId: Int): List<IdLatLonResult>

    @Query("""
        UPDATE drive_detail_aggregates
        SET startCountryCode = :countryCode,
            startCountryName = :countryName,
            startRegionName = :regionName,
            startCity = :city
        WHERE driveId IN (:driveIds)
    """)
    suspend fun updateDriveLocationsByIds(
        driveIds: List<Int>,
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
        WHERE chargeId IN (:chargeIds)
    """)
    suspend fun updateChargeLocationsByIds(
        chargeIds: List<Int>,
        countryCode: String?,
        countryName: String?,
        regionName: String?,
        city: String?
    )

    // === Charge Locations for Country Map ===

    // Get all charge locations for a specific country within a date range (for map display)
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
    suspend fun getChargeLocationsForCountry(
        carId: Int,
        countryCode: String,
        startDate: String,
        endDate: String
    ): List<ChargeLocationResult>

    // === Drive Locations for Country Map ===

    // Get drive start locations for a country within a date range (for map display)
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
    suspend fun getDriveLocationsForCountry(
        carId: Int,
        countryCode: String,
        startDate: String,
        endDate: String
    ): List<DriveLocationResult>

    // === Trip Detection Queries ===

    /** All DC charge summaries for a car (for trip detection). */
    @Query("""
        SELECT c.* FROM charges_summary c
        INNER JOIN charge_detail_aggregates a ON c.chargeId = a.chargeId
        WHERE c.carId = :carId AND a.isFastCharger = 1
        ORDER BY c.startDate ASC
    """)
    suspend fun getDcChargeSummaries(carId: Int): List<ChargeSummary>

    /** Drive start coordinates for trip map markers. */
    /** Drive start + end coordinates for trip country resolution. */
    @Query("""
        SELECT driveId, startLatitude, startLongitude, endLatitude, endLongitude
        FROM drive_detail_aggregates
        WHERE driveId IN (:driveIds)
        AND startLatitude IS NOT NULL AND startLongitude IS NOT NULL
    """)
    suspend fun getDriveEdgeCoordinates(driveIds: List<Int>): List<DriveEdgeCoordinateResult>
}

/**
 * Elevation record row: aggregate extremes plus the drive date fetched in the
 * same query (no follow-up drives_summary lookup needed).
 * [elevation] is maxElevation or minElevation depending on the query.
 */
data class DriveElevationResult(
    val driveId: Int,
    val elevation: Int?,
    val elevationGain: Int?,
    val startDate: String?
)

/**
 * Temperature record row for a drive; [temp] is the max or min outside
 * temperature depending on the query.
 */
data class DriveTempResult(
    val driveId: Int,
    val outsideTemp: Double?,
    val startDate: String?
)

/**
 * Temperature record row for a charge; [temp] is the max or min outside
 * temperature depending on the query.
 */
data class ChargeTempResult(
    val chargeId: Int,
    val outsideTemp: Double?,
    val startDate: String?
)

/**
 * Peak-power record row for a charge.
 */
data class ChargePowerResult(
    val chargeId: Int,
    val maxChargerPower: Int?,
    val startDate: String?
)

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
 * Row id + coordinates for geocoding queries.
 */
data class IdLatLonResult(
    val id: Int,
    val latitude: Double,
    val longitude: Double
)

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

// === Trip Detection Queries ===

data class DriveEdgeCoordinateResult(
    val driveId: Int,
    val startLatitude: Double,
    val startLongitude: Double,
    val endLatitude: Double?,
    val endLongitude: Double?
)
