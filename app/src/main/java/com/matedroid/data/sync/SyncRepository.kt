package com.matedroid.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.matedroid.data.api.models.ChargeData
import com.matedroid.data.api.models.ChargeDetail
import com.matedroid.data.api.models.DriveData
import com.matedroid.data.api.models.DriveDetail
import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.ChargeSummaryDao
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.local.entity.ChargeDetailAggregate
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveDetailAggregate
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.data.local.entity.SchemaVersion
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.GeocodingRepository
import com.matedroid.data.repository.TeslamateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for syncing data from TeslamateApi to local database.
 */
@Singleton
class SyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val teslamateRepository: TeslamateRepository,
    private val driveSummaryDao: DriveSummaryDao,
    private val chargeSummaryDao: ChargeSummaryDao,
    private val aggregateDao: AggregateDao,
    private val syncManager: SyncManager,
    private val logCollector: SyncLogCollector,
    private val geocodingRepository: GeocodingRepository
) {
    companion object {
        private const val TAG = "SyncRepository"
        private const val THROTTLE_DELAY_MS = 10L  // Reduced from 100ms
        private const val BATCH_SIZE = 10  // Number of concurrent API calls
    }

    private fun log(message: String) = logCollector.log(TAG, message)
    private fun logError(message: String, error: Throwable? = null) = logCollector.logError(TAG, message, error)

    /**
     * Ensure geocoding worker is running or scheduled.
     * Called whenever new locations are enqueued.
     * Uses KEEP policy: if worker is running, do nothing; if finished, start new one.
     */
    private fun ensureGeocodingRunning() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<GeocodeWorker>()
            .setConstraints(constraints)
            .addTag(GeocodeWorker.TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                GeocodeWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,  // Don't interrupt running worker; start new if finished
                request
            )

        log("Started geocoding worker (parallel with sync)")
    }

    /**
     * Sync all data for a car.
     * Phase 1: Sync summaries (fast, 2 API calls)
     * Phase 2: Sync details (slow, 1 API call per drive/charge)
     */
    suspend fun syncCar(carId: Int): Boolean {
        log("Starting sync for car $carId")

        // Phase 1: Sync summaries
        syncManager.updateSummaryProgress(carId, "Fetching drives and charges...")

        val summariesSuccess = syncSummaries(carId)
        if (!summariesSuccess) {
            syncManager.markSyncError(carId, "Failed to sync summaries")
            return false
        }

        syncManager.markSummariesComplete(carId)

        // Check if details need syncing
        if (syncManager.areDetailsSynced(carId)) {
            log("Details already synced for car $carId")
            return true
        }

        // Phase 2: Sync drive details
        val driveSuccess = syncDriveDetails(carId)
        if (!driveSuccess) {
            syncManager.markSyncError(carId, "Failed to sync drive details")
            return false
        }

        syncManager.markDriveDetailsComplete(carId)

        // Phase 3: Sync charge details
        val chargeSuccess = syncChargeDetails(carId)
        if (!chargeSuccess) {
            syncManager.markSyncError(carId, "Failed to sync charge details")
            return false
        }

        // Re-enqueue any locations that need geocoding (in case queue was cleared)
        reEnqueueLocationsForGeocoding(carId)

        syncManager.markSyncComplete(carId)
        log("Sync complete for car $carId")
        return true
    }

    /**
     * Sync only summaries (Quick Stats).
     * Fast operation - 2 API calls regardless of data size.
     */
    suspend fun syncSummaries(carId: Int): Boolean {
        log("Syncing summaries for car $carId")

        // Fetch and store drives
        when (val drivesResult = teslamateRepository.getDrives(carId)) {
            is ApiResult.Success -> {
                val summaries = drivesResult.data.map { it.toDriveSummary(carId) }
                driveSummaryDao.upsertAll(summaries)
                log("Synced ${summaries.size} drives for car $carId")
            }
            is ApiResult.Error -> {
                logError("Failed to fetch drives: ${drivesResult.message}")
                return false
            }
        }

        // Fetch and store charges
        when (val chargesResult = teslamateRepository.getCharges(carId)) {
            is ApiResult.Success -> {
                val summaries = chargesResult.data.map { it.toChargeSummary(carId) }
                chargeSummaryDao.upsertAll(summaries)
                log("Synced ${summaries.size} charges for car $carId")
            }
            is ApiResult.Error -> {
                logError("Failed to fetch charges: ${chargesResult.message}")
                return false
            }
        }

        return true
    }

    /**
     * Sync drive details (Deep Stats - elevation, temperature extremes, country).
     * Processes drives in parallel batches for improved performance.
     * Locations are enqueued for geocoding incrementally (per batch) and geocoding
     * starts in parallel without blocking drive retrieval.
     */
    suspend fun syncDriveDetails(carId: Int): Boolean {
        val unprocessedIds = driveSummaryDao.getUnprocessedDriveIds(carId, SchemaVersion.CURRENT)
        val total = unprocessedIds.size
        log("Processing $total drive details for car $carId (batch size: $BATCH_SIZE)")

        // Process in batches
        unprocessedIds.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
            val processed = batchIndex * BATCH_SIZE
            val remaining = total - processed - batch.size

            // Fetch all drives in this batch concurrently
            val apiResults = coroutineScope {
                batch.map { driveId ->
                    async {
                        driveId to teslamateRepository.getDriveDetail(carId, driveId)
                    }
                }.awaitAll()
            }

            // Collect locations from this batch for geocoding
            val batchLocations = mutableListOf<Pair<Double, Double>>()

            // Process results and compute aggregates
            val aggregates = apiResults.mapNotNull { (driveId, result) ->
                when (result) {
                    is ApiResult.Success -> {
                        val aggregate = computeDriveAggregate(carId, result.data)
                        // Collect location for geocoding
                        if (aggregate.startLatitude != null && aggregate.startLongitude != null) {
                            batchLocations.add(aggregate.startLatitude to aggregate.startLongitude)
                        }
                        aggregate
                    }
                    is ApiResult.Error -> {
                        logError("Drive $driveId failed: ${result.message}")
                        null
                    }
                }
            }

            // Batch write all successful aggregates
            if (aggregates.isNotEmpty()) {
                aggregateDao.upsertDriveAggregates(aggregates)
            }

            // Enqueue this batch's locations immediately (survives interruption)
            if (batchLocations.isNotEmpty()) {
                val enqueued = geocodingRepository.enqueueLocationsForCar(carId, batchLocations)
                if (enqueued > 0) {
                    log("Enqueued $enqueued drive locations from batch ${batchIndex + 1}")
                    // Start geocoding worker on first batch with locations (runs in parallel)
                    ensureGeocodingRunning()
                }
            }

            // Update progress once per batch (last drive ID in batch)
            val lastDriveId = batch.lastOrNull()
            if (lastDriveId != null) {
                syncManager.updateDriveDetailProgress(carId, lastDriveId)
            }

            log("Batch ${batchIndex + 1}: ${aggregates.size}/${batch.size} drives synced ($remaining remaining)")

            // Small delay between batches to avoid overwhelming the API
            if (remaining > 0) {
                delay(THROTTLE_DELAY_MS)
            }
        }

        return true
    }

    /**
     * Sync charge details (Deep Stats - AC/DC ratio, max power).
     * Processes charges in parallel batches for improved performance.
     * Locations are enqueued for geocoding incrementally (per batch) and geocoding
     * starts in parallel without blocking charge retrieval.
     */
    suspend fun syncChargeDetails(carId: Int): Boolean {
        val unprocessedIds = chargeSummaryDao.getUnprocessedChargeIds(carId, SchemaVersion.CURRENT)
        val total = unprocessedIds.size
        log("Processing $total charge details for car $carId (batch size: $BATCH_SIZE)")

        // Process in batches
        unprocessedIds.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
            val processed = batchIndex * BATCH_SIZE
            val remaining = total - processed - batch.size

            // Fetch all charges in this batch concurrently
            val results = coroutineScope {
                batch.map { chargeId ->
                    async {
                        chargeId to teslamateRepository.getChargeDetail(carId, chargeId)
                    }
                }.awaitAll()
            }

            // Collect locations from this batch for geocoding
            val batchLocations = mutableListOf<Pair<Double, Double>>()

            // Process results and collect successful aggregates
            val aggregates = mutableListOf<ChargeDetailAggregate>()
            for ((chargeId, result) in results) {
                when (result) {
                    is ApiResult.Success -> {
                        val aggregate = computeChargeAggregate(carId, result.data)
                        aggregates.add(aggregate)

                        // Get location from charge summary for geocoding
                        val summary = chargeSummaryDao.get(chargeId)
                        if (summary != null && summary.latitude != 0.0 && summary.longitude != 0.0) {
                            batchLocations.add(summary.latitude to summary.longitude)
                        }
                    }
                    is ApiResult.Error -> {
                        logError("Charge $chargeId failed: ${result.message}")
                        // Continue with other charges instead of failing entirely
                    }
                }
            }

            // Batch write all successful aggregates
            if (aggregates.isNotEmpty()) {
                aggregateDao.upsertChargeAggregates(aggregates)
            }

            // Enqueue this batch's locations immediately (survives interruption)
            if (batchLocations.isNotEmpty()) {
                val enqueued = geocodingRepository.enqueueLocationsForCar(carId, batchLocations)
                if (enqueued > 0) {
                    log("Enqueued $enqueued charge locations from batch ${batchIndex + 1}")
                    // Start geocoding worker if not already running
                    ensureGeocodingRunning()
                }
            }

            // Update progress once per batch (last charge ID in batch)
            val lastChargeId = batch.lastOrNull()
            if (lastChargeId != null) {
                syncManager.updateChargeDetailProgress(carId, lastChargeId)
            }

            log("Batch ${batchIndex + 1}: ${aggregates.size}/${batch.size} charges synced ($remaining remaining)")

            // Small delay between batches to avoid overwhelming the API
            if (remaining > 0) {
                delay(THROTTLE_DELAY_MS)
            }
        }

        return true
    }

    /**
     * Compute aggregates from drive detail positions.
     * Coordinates are stored for background geocoding.
     */
    private fun computeDriveAggregate(
        carId: Int,
        detail: DriveDetail
    ): DriveDetailAggregate {
        val positions = detail.positions ?: emptyList()

        // Single pass over positions for all stats
        var maxElevation: Int? = null
        var minElevation: Int? = null
        var maxInsideTemp: Double? = null
        var minInsideTemp: Double? = null
        var maxOutsideTemp: Double? = null
        var minOutsideTemp: Double? = null
        var maxPower: Int? = null
        var minPower: Int? = null
        var climateOnCount = 0
        var elevationGain = 0
        var elevationLoss = 0
        var prevElevation: Int? = null

        for (pos in positions) {
            // Elevation
            pos.elevation?.let { elev ->
                maxElevation = maxOf(maxElevation ?: elev, elev)
                minElevation = minOf(minElevation ?: elev, elev)
                prevElevation?.let { prev ->
                    val diff = elev - prev
                    if (diff > 0) elevationGain += diff
                    else elevationLoss += -diff
                }
                prevElevation = elev
            }

            // Temperature
            pos.insideTemp?.let { temp ->
                maxInsideTemp = maxOf(maxInsideTemp ?: temp, temp)
                minInsideTemp = minOf(minInsideTemp ?: temp, temp)
            }
            pos.outsideTemp?.let { temp ->
                maxOutsideTemp = maxOf(maxOutsideTemp ?: temp, temp)
                minOutsideTemp = minOf(minOutsideTemp ?: temp, temp)
            }

            // Power
            pos.power?.let { pwr ->
                maxPower = maxOf(maxPower ?: pwr, pwr)
                minPower = minOf(minPower ?: pwr, pwr)
            }

            // Climate
            if (pos.isClimateOn) climateOnCount++
        }

        val firstPosition = positions.firstOrNull()
        val lastPosition = positions.lastOrNull()
        val startElevation = firstPosition?.elevation
        val endElevation = lastPosition?.elevation
        val hasElevationData = maxElevation != null

        // Extract start/end coordinates for geocoding and trip country resolution
        val startLatitude = firstPosition?.latitude
        val startLongitude = firstPosition?.longitude
        val endLatitude = lastPosition?.latitude
        val endLongitude = lastPosition?.longitude

        return DriveDetailAggregate(
            driveId = detail.driveId,
            carId = carId,
            schemaVersion = SchemaVersion.CURRENT,
            computedAt = System.currentTimeMillis(),

            maxElevation = maxElevation,
            minElevation = minElevation,
            startElevation = startElevation,
            endElevation = endElevation,
            elevationGain = if (hasElevationData) elevationGain else null,
            elevationLoss = if (hasElevationData) elevationLoss else null,
            hasElevationData = hasElevationData,

            maxInsideTemp = maxInsideTemp,
            minInsideTemp = minInsideTemp,
            maxOutsideTemp = maxOutsideTemp,
            minOutsideTemp = minOutsideTemp,

            maxPower = maxPower,
            minPower = minPower,

            climateOnPositions = climateOnCount,
            positionCount = positions.size,

            // Store coordinates for background geocoding
            startLatitude = startLatitude,
            startLongitude = startLongitude,
            // Location data will be filled by GeocodeWorker
            startCountryCode = null,
            startCountryName = null,
            startRegionName = null,
            startCity = null,

            endLatitude = endLatitude,
            endLongitude = endLongitude
        )
    }

    /**
     * Compute aggregates from charge detail points.
     */
    private fun computeChargeAggregate(carId: Int, detail: ChargeDetail): ChargeDetailAggregate {
        val points = detail.chargePoints ?: emptyList()

        // Find first point with charger info for type detection
        val firstWithCharger = points.firstOrNull { it.chargerDetails != null }
        val chargerDetails = firstWithCharger?.chargerDetails

        // Single pass for all stats
        var maxPower: Int? = null
        var maxVoltage: Int? = null
        var maxCurrent: Int? = null
        var maxOutsideTemp: Double? = null
        var minOutsideTemp: Double? = null
        val phaseVotes = mutableMapOf<Int, Int>()

        for (point in points) {
            point.chargerPower?.let { pwr ->
                maxPower = maxOf(maxPower ?: pwr, pwr)
            }
            point.chargerVoltage?.let { volt ->
                maxVoltage = maxOf(maxVoltage ?: volt, volt)
            }
            point.chargerCurrent?.let { curr ->
                maxCurrent = maxOf(maxCurrent ?: curr, curr)
            }
            point.outsideTemp?.let { temp ->
                maxOutsideTemp = maxOf(maxOutsideTemp ?: temp, temp)
                minOutsideTemp = minOf(minOutsideTemp ?: temp, temp)
            }
            point.chargerDetails?.chargerPhases?.let { phases ->
                if (phases > 0) {
                    phaseVotes[phases] = (phaseVotes[phases] ?: 0) + 1
                }
            }
        }

        // Determine if DC charger using Teslamate's logic:
        // DC charging has charger_phases = 0 or null (bypasses onboard charger)
        // AC charging has charger_phases = 1, 2, or 3
        val modePhases = phaseVotes.maxByOrNull { it.value }?.key
        val isFastCharger = modePhases == null  // No non-zero phases means DC

        return ChargeDetailAggregate(
            chargeId = detail.chargeId,
            carId = carId,
            schemaVersion = SchemaVersion.CURRENT,
            computedAt = System.currentTimeMillis(),

            isFastCharger = isFastCharger,
            fastChargerBrand = chargerDetails?.fastChargerBrand?.takeIf { it != "<invalid>" },
            connectorType = chargerDetails?.fastChargerType,

            maxChargerPower = maxPower,
            maxChargerVoltage = maxVoltage,
            maxChargerCurrent = maxCurrent,
            chargerPhases = chargerDetails?.chargerPhases,

            maxOutsideTemp = maxOutsideTemp,
            minOutsideTemp = minOutsideTemp,

            chargePointCount = points.size
        )
    }

    /**
     * Re-enqueue locations from existing aggregates that still need geocoding.
     * Also applies any cached geocode data to aggregates that weren't updated.
     * Returns the number of unique locations enqueued.
     */
    suspend fun reEnqueueLocationsForGeocoding(carId: Int): Int {
        val driveLocations = aggregateDao.getDriveLocationsNeedingGeocode(carId)
            .mapNotNull { it.toLatLon() }
        val chargeLocations = aggregateDao.getChargeLocationsNeedingGeocode(carId)
            .mapNotNull { it.toLatLon() }

        val allLocations = driveLocations + chargeLocations
        log("Found ${driveLocations.size} drive + ${chargeLocations.size} charge locations needing geocode")

        if (allLocations.isEmpty()) {
            log("No locations need geocoding")
            return 0
        }

        // First, apply any cached geocode data to aggregates
        val applied = applyCachedGeocodeData(carId, allLocations)
        if (applied > 0) {
            log("Applied cached geocode data to $applied locations")
        }

        // Then enqueue any still-uncached locations
        val enqueued = geocodingRepository.enqueueLocationsForCar(carId, allLocations)
        log("Re-enqueued $enqueued unique locations for geocoding")
        return enqueued
    }

    /**
     * Apply cached geocode data to drive/charge aggregates.
     * This handles the case where geocoding completed but aggregates weren't updated.
     */
    private suspend fun applyCachedGeocodeData(carId: Int, locations: List<Pair<Double, Double>>): Int {
        var appliedCount = 0
        val uniqueGridCells = locations
            .map { (lat, lon) ->
                geocodingRepository.toGridCoord(lat) to geocodingRepository.toGridCoord(lon)
            }
            .distinct()

        for ((gridLat, gridLon) in uniqueGridCells) {
            val cached = geocodingRepository.getFromCacheByGrid(gridLat, gridLon)
            if (cached != null) {
                // Update drive aggregates in this grid cell
                aggregateDao.updateDriveLocationsInGrid(
                    carId = carId,
                    gridLat = gridLat,
                    gridLon = gridLon,
                    countryCode = cached.countryCode,
                    countryName = cached.countryName,
                    regionName = cached.regionName,
                    city = cached.city
                )
                // Update charge aggregates in this grid cell
                aggregateDao.updateChargeLocationsInGrid(
                    carId = carId,
                    gridLat = gridLat,
                    gridLon = gridLon,
                    countryCode = cached.countryCode,
                    countryName = cached.countryName,
                    regionName = cached.regionName,
                    city = cached.city
                )
                appliedCount++
            }
        }
        return appliedCount
    }
}

// Extension functions to convert API models to database entities

private fun DriveData.toDriveSummary(carId: Int): DriveSummary {
    return DriveSummary(
        driveId = driveId,
        carId = carId,
        startDate = startDate ?: "",
        endDate = endDate ?: "",
        startAddress = startAddress ?: "",
        endAddress = endAddress ?: "",
        distance = distance ?: 0.0,
        durationMin = durationMin ?: 0,
        speedMax = speedMax ?: 0,
        speedAvg = speedAvg?.toInt() ?: 0,
        powerMax = powerMax ?: 0,
        powerMin = powerMin ?: 0,
        startBatteryLevel = startBatteryLevel ?: 0,
        endBatteryLevel = endBatteryLevel ?: 0,
        outsideTempAvg = outsideTempAvg,
        insideTempAvg = insideTempAvg,
        energyConsumed = energyConsumedNet,
        efficiency = efficiencyWhKm
    )
}

private fun ChargeData.toChargeSummary(carId: Int): ChargeSummary {
    return ChargeSummary(
        chargeId = chargeId,
        carId = carId,
        startDate = startDate ?: "",
        endDate = endDate ?: "",
        address = address ?: "",
        latitude = latitude ?: 0.0,
        longitude = longitude ?: 0.0,
        energyAdded = chargeEnergyAdded ?: 0.0,
        energyUsed = chargeEnergyUsed,
        cost = cost,
        durationMin = durationMin ?: 0,
        startBatteryLevel = startBatteryLevel ?: 0,
        endBatteryLevel = endBatteryLevel ?: 0,
        outsideTempAvg = outsideTempAvg,
        odometer = odometer ?: 0.0
    )
}
