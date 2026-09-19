package com.matedroid.data.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.matedroid.data.local.CarImageOverride
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.local.StatsDatabase
import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.ChargeSummaryDao
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.local.dao.GeocodeCacheDao
import com.matedroid.data.local.dao.SavedTripDao
import com.matedroid.data.local.dao.SentryAlertLogDao
import com.matedroid.data.local.dao.SyncStateDao
import com.matedroid.data.local.dao.TripCountryCacheDao
import com.matedroid.data.local.dao.TripRouteCacheDao
import com.matedroid.data.local.entity.ChargeDetailAggregate
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveDetailAggregate
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.data.local.entity.GeocodeCache
import com.matedroid.data.local.entity.SavedTrip
import com.matedroid.data.local.entity.SavedTripLeg
import com.matedroid.data.local.entity.SentryAlertLog
import com.matedroid.data.local.entity.SyncState
import com.matedroid.data.local.entity.TripCountryCache
import com.matedroid.data.local.entity.TripRouteCache
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** What a backup file turned out to contain, shown before anything is restored. */
data class BackupPreview(
    val header: BackupHeader,
    val trips: Int = 0,
    val sentryEvents: Int = 0,
    val places: Int = 0,
    val tripMaps: Int = 0,
    val drivesAndCharges: Int = 0,
    val hasSettings: Boolean = false,
    /**
     * False when the file's drives and charges were written against a different version of
     * the local database. They are re-downloadable, so the section is simply left out rather
     * than poured into tables that have moved on.
     */
    val statsReadable: Boolean = true
) {
    /** Sections worth offering as checkboxes: the ones this file actually carries. */
    val availableSections: Set<BackupSection> = buildSet {
        if (trips > 0) add(BackupSection.TRIPS)
        if (hasSettings) add(BackupSection.SETTINGS)
        if (sentryEvents > 0) add(BackupSection.SENTRY)
        if (places > 0) add(BackupSection.PLACES)
        if (tripMaps > 0) add(BackupSection.TRIP_MAPS)
        if (drivesAndCharges > 0 && statsReadable) add(BackupSection.STATS)
    }

    fun countOf(section: BackupSection): Int = when (section) {
        BackupSection.TRIPS -> trips
        BackupSection.SETTINGS -> 0
        BackupSection.SENTRY -> sentryEvents
        BackupSection.PLACES -> places
        BackupSection.TRIP_MAPS -> tripMaps
        BackupSection.STATS -> drivesAndCharges
    }
}

/** What a restore actually did, for the summary shown when it finishes. */
data class ImportReport(
    val tripsRestored: Int = 0,
    val tripsAlreadyHere: Int = 0,
    val tripsIncomplete: Int = 0,
    val tripsSkipped: Int = 0,
    val sentryRestored: Int = 0,
    val sentryAlreadyHere: Int = 0,
    val placesRestored: Int = 0,
    val tripMapsRestored: Int = 0,
    val drivesAndChargesRestored: Int = 0,
    val settingsRestored: Boolean = false
)

/** The file was not a MateDroid backup, or was too damaged to read. */
class NotABackupException(cause: Throwable? = null) : IOException("Not a MateDroid backup", cause)

/** The file comes from a newer MateDroid than this one. */
class NewerBackupException(val format: Int) : IOException("Backup format $format is too new")

/**
 * Reads a backup back in: first to describe it, then — once the user has said which parts
 * to take and whether to add or replace — to apply it.
 *
 * The picked file is copied into the cache before either pass, so neither depends on a
 * content:// grant outliving the screen, and both can stream the same bytes twice.
 */
@Singleton
class BackupImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
    private val database: StatsDatabase,
    private val settingsDataStore: SettingsDataStore,
    private val repository: TeslamateRepository,
    private val savedTripDao: SavedTripDao,
    private val sentryAlertLogDao: SentryAlertLogDao,
    private val geocodeCacheDao: GeocodeCacheDao,
    private val tripRouteCacheDao: TripRouteCacheDao,
    private val tripCountryCacheDao: TripCountryCacheDao,
    private val syncStateDao: SyncStateDao,
    private val driveSummaryDao: DriveSummaryDao,
    private val chargeSummaryDao: ChargeSummaryDao,
    private val aggregateDao: AggregateDao
) {

    /** Copy the picked document into the cache, where both passes can read it. */
    suspend fun stage(uri: Uri): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, STAGING_DIR)
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "import.json")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: throw NotABackupException()
        file
    }

    suspend fun preview(file: File): BackupPreview = withContext(Dispatchers.IO) {
        val counter = CountingVisitor()
        try {
            read(file, counter)
        } catch (e: NewerBackupException) {
            throw e
        } catch (e: Exception) {
            throw NotABackupException(e)
        }
        val header = counter.header ?: throw NotABackupException()
        BackupPreview(
            header = header,
            trips = counter.trips,
            sentryEvents = counter.sentryEvents,
            places = counter.places,
            tripMaps = counter.tripKeys.size,
            drivesAndCharges = counter.drives + counter.charges,
            hasSettings = counter.hasSettings,
            statsReadable = header.databaseVersion == StatsDatabase.SCHEMA_VERSION
        )
    }

    /** Restore [selection] out of [file]. Everything else in the file is left alone. */
    suspend fun restore(
        file: File,
        selection: Set<BackupSection>,
        mode: ImportMode
    ): ImportReport = withContext(Dispatchers.IO) {
        val header = readHeader(file)
        val carMap = BackupCarMap.of(header.cars, localCars())
        val visitor = RestoringVisitor(selection, carMap)
        // Replacing empties tables before the file has been read. One transaction around
        // both means a file that turns out to be truncated half-way leaves the phone with
        // what it had, rather than with the hole the clear just made.
        database.withTransaction {
            if (mode == ImportMode.REPLACE) clear(selection)
            read(file, visitor)
        }
        visitor.report
    }

    private suspend fun read(file: File, visitor: BackupVisitor) {
        file.source().buffer().use { source -> BackupFile.read(source, moshi, visitor) }
    }

    /**
     * Pull just the header out.
     *
     * The car mapping has to be settled before the first trip is read, and the header is the
     * first thing in the file, so this pass throws itself out of the parser as soon as it has
     * what it needs rather than walking megabytes of rows it will read again in a moment.
     */
    private suspend fun readHeader(file: File): BackupHeader {
        var found: BackupHeader? = null
        try {
            read(file, object : BackupVisitor {
                override suspend fun onHeader(header: BackupHeader) {
                    found = header
                    throw StopReading
                }
            })
        } catch (_: StopReading) {
            // Expected: the header was found and the rest of the file is of no interest here.
        }
        val header = found ?: throw NotABackupException()
        if (header.format > BACKUP_FORMAT_VERSION) throw NewerBackupException(header.format)
        return header
    }

    /** Best-effort, for matching cars by VIN. An unreachable server just means id-for-id. */
    private suspend fun localCars(): List<BackupCar> = runCatching {
        withTimeoutOrNull(CAR_LOOKUP_TIMEOUT_MS) {
            when (val result = repository.getCars()) {
                is ApiResult.Success -> result.data.map {
                    BackupCar(carId = it.carId, vin = it.carDetails?.vin, name = it.name)
                }

                is ApiResult.Error -> emptyList()
            }
        }
    }.getOrNull().orEmpty()

    private suspend fun clear(selection: Set<BackupSection>) {
        if (BackupSection.TRIPS in selection) savedTripDao.deleteAllTrips()
        if (BackupSection.SENTRY in selection) sentryAlertLogDao.deleteAll()
        if (BackupSection.TRIP_MAPS in selection) {
            tripRouteCacheDao.deleteAll()
            tripCountryCacheDao.deleteAll()
        }
        if (BackupSection.STATS in selection) {
            val carIds = (driveSummaryDao.getAllCarIds() + chargeSummaryDao.getAllCarIds())
                .distinct()
            carIds.forEach { carId ->
                driveSummaryDao.deleteAllForCar(carId)
                chargeSummaryDao.deleteAllForCar(carId)
                aggregateDao.deleteDriveAggregatesForCar(carId)
                aggregateDao.deleteChargeAggregatesForCar(carId)
            }
        }
        // Place names are never cleared: they are facts about the world, identical in every
        // copy, and dropping them would only mean paying for the lookups again.
    }

    private class CountingVisitor : BackupVisitor {
        var header: BackupHeader? = null
        var hasSettings = false
        var trips = 0
        var sentryEvents = 0
        var places = 0
        var drives = 0
        var charges = 0
        val tripKeys = mutableSetOf<String>()

        override suspend fun onHeader(header: BackupHeader) {
            this.header = header
            if (header.format > BACKUP_FORMAT_VERSION) throw NewerBackupException(header.format)
        }

        override suspend fun onSettings(settings: BackupSettings) {
            hasSettings = true
        }

        override suspend fun onTrips(batch: List<BackupTrip>) {
            trips += batch.size
        }

        override suspend fun onSentryEvents(batch: List<BackupSentryEvent>) {
            sentryEvents += batch.size
        }

        override suspend fun onPlaces(batch: List<BackupPlace>) {
            places += batch.size
        }

        override suspend fun onTripRoutes(batch: List<BackupTripRoute>) {
            batch.forEach { tripKeys.add(it.tripKey) }
        }

        override suspend fun onDrives(batch: List<DriveSummary>) {
            drives += batch.size
        }

        override suspend fun onCharges(batch: List<ChargeSummary>) {
            charges += batch.size
        }
    }

    /** Writes each batch into the database as it arrives. */
    private inner class RestoringVisitor(
        private val selection: Set<BackupSection>,
        private val carMap: BackupCarMap
    ) : BackupVisitor {

        var report = ImportReport()
            private set

        private val legResolver = BackupLegResolver(DaoLegIndex())
        private var existingTripSignatures: MutableSet<String>? = null
        private val restoredTripMapKeys = mutableSetOf<String>()
        private var existingSentryKeys: MutableSet<String>? = null
        private var statsReadable = true

        override suspend fun onHeader(header: BackupHeader) {
            statsReadable = header.databaseVersion == StatsDatabase.SCHEMA_VERSION
        }

        override suspend fun onSettings(settings: BackupSettings) {
            if (BackupSection.SETTINGS !in selection) return
            settingsDataStore.restoreFromBackup(
                SettingsDataStore.RestorableSettings(
                    serverUrl = settings.serverUrl,
                    secondaryServerUrl = settings.secondaryServerUrl,
                    teslamateBaseUrl = settings.teslamateBaseUrl,
                    acceptInvalidCerts = settings.acceptInvalidCerts,
                    connectTimeoutSeconds = settings.connectTimeoutSeconds,
                    currencyCode = settings.currencyCode,
                    costPerKwhBasisId = settings.costPerKwhBasis,
                    isImperial = settings.isImperial,
                    showShortDrivesCharges = settings.showShortDrivesCharges,
                    shortDriveMinDurationMin = settings.shortDriveMinDurationMin,
                    shortDriveMinDistance = settings.shortDriveMinDistance,
                    shortChargeMinEnergyKwh = settings.shortChargeMinEnergyKwh,
                    highSocWarningThreshold = settings.highSocWarningThreshold,
                    lowSocWarningThreshold = settings.lowSocWarningThreshold,
                    lastSelectedCarId = settings.lastSelectedCarId?.let { carMap[it] },
                    carImageOverrides = settings.carImageOverrides
                        .mapNotNull { (carId, image) ->
                            carId.toIntOrNull()?.let {
                                carMap[it] to CarImageOverride(image.variant, image.wheelCode)
                            }
                        }
                        .toMap()
                        .takeIf { it.isNotEmpty() }
                )
            )
            report = report.copy(settingsRestored = true)
        }

        override suspend fun onTrips(batch: List<BackupTrip>) {
            if (BackupSection.TRIPS !in selection) return
            val signatures = existingTripSignatures ?: loadTripSignatures()
            batch.forEach { backupTrip ->
                val carId = carMap[backupTrip.carId]
                val resolved = resolveLegs(carId, backupTrip.legs)
                if (resolved.legs.isEmpty()) {
                    report = report.copy(tripsSkipped = report.tripsSkipped + 1)
                    return@forEach
                }
                val signature = tripSignature(carId, resolved.legs)
                if (!signatures.add(signature)) {
                    report = report.copy(tripsAlreadyHere = report.tripsAlreadyHere + 1)
                    return@forEach
                }
                savedTripDao.insertRestoredTrip(
                    trip = SavedTrip(
                        carId = carId,
                        name = backupTrip.name,
                        source = backupTrip.source,
                        createdAt = backupTrip.createdAt,
                        updatedAt = backupTrip.updatedAt
                    ),
                    legs = resolved.legs,
                    // Kept as they are even when ids moved: a fingerprint covers the
                    // auto-detected trips this one replaced, and those cannot be recomputed
                    // from here. At worst an auto-detected trip shows up alongside this one.
                    consumedFingerprints = backupTrip.consumedFingerprints
                )
                report = report.copy(
                    tripsRestored = report.tripsRestored + 1,
                    tripsIncomplete = report.tripsIncomplete + if (resolved.dropped > 0) 1 else 0
                )
            }
        }

        private suspend fun resolveLegs(carId: Int, legs: List<BackupTripLeg>): ResolvedLegs {
            var dropped = 0
            val resolved = mutableListOf<SavedTripLeg>()
            legs.sortedBy { it.position }.forEach { leg ->
                when (val resolution = legResolver.resolve(carId, leg)) {
                    is LegResolution.Kept -> resolved += leg.toEntity(resolution.legId)
                    is LegResolution.Remapped -> resolved += leg.toEntity(resolution.legId)
                    is LegResolution.Pending -> resolved += leg.toEntity(resolution.legId)
                    LegResolution.Dropped -> dropped++
                }
            }
            return ResolvedLegs(
                legs = resolved.mapIndexed { index, leg -> leg.copy(position = index) },
                dropped = dropped
            )
        }

        private fun BackupTripLeg.toEntity(legId: Int) = SavedTripLeg(
            tripId = 0L,
            position = position,
            legType = type,
            legId = legId
        )

        private suspend fun loadTripSignatures(): MutableSet<String> {
            val signatures = savedTripDao.getAllTripsWithLegs()
                .map { tripSignature(it.trip.carId, it.legs) }
                .toMutableSet()
            existingTripSignatures = signatures
            return signatures
        }

        override suspend fun onSentryEvents(batch: List<BackupSentryEvent>) {
            if (BackupSection.SENTRY !in selection) return
            val known = existingSentryKeys ?: sentryAlertLogDao.getAllKeys().toMutableSet()
                .also { existingSentryKeys = it }
            val fresh = batch.mapNotNull { event ->
                val carId = carMap[event.carId]
                if (!known.add(sentryKey(carId, event.detectedAt))) return@mapNotNull null
                SentryAlertLog(
                    carId = carId,
                    detectedAt = event.detectedAt,
                    sessionStartedAt = event.sessionStartedAt,
                    latitude = event.latitude,
                    longitude = event.longitude,
                    address = event.address
                )
            }
            if (fresh.isNotEmpty()) sentryAlertLogDao.insertAll(fresh)
            report = report.copy(
                sentryRestored = report.sentryRestored + fresh.size,
                sentryAlreadyHere = report.sentryAlreadyHere + (batch.size - fresh.size)
            )
        }

        override suspend fun onPlaces(batch: List<BackupPlace>) {
            if (BackupSection.PLACES !in selection) return
            geocodeCacheDao.upsertAll(
                batch.map {
                    GeocodeCache(
                        gridLat = it.gridLat,
                        gridLon = it.gridLon,
                        countryCode = it.countryCode,
                        countryName = it.countryName,
                        regionName = it.regionName,
                        city = it.city,
                        cachedAt = it.cachedAt
                    )
                }
            )
            report = report.copy(placesRestored = report.placesRestored + batch.size)
        }

        override suspend fun onTripRoutes(batch: List<BackupTripRoute>) {
            if (BackupSection.TRIP_MAPS !in selection) return
            val segments = batch.mapNotNull { route ->
                val points = runCatching { Base64.decode(route.points, Base64.NO_WRAP) }.getOrNull()
                    ?: return@mapNotNull null
                TripRouteCache(
                    tripKey = route.tripKey,
                    segmentIndex = route.segmentIndex,
                    segmentData = points,
                    createdAt = route.createdAt
                )
            }
            if (segments.isNotEmpty()) tripRouteCacheDao.insertAll(segments)
            segments.forEach { restoredTripMapKeys.add(it.tripKey) }
            report = report.copy(tripMapsRestored = restoredTripMapKeys.size)
        }

        override suspend fun onTripCountries(batch: List<BackupTripCountries>) {
            if (BackupSection.TRIP_MAPS !in selection) return
            tripCountryCacheDao.insertAll(
                batch.map { TripCountryCache(it.tripKey, it.countries, it.createdAt) }
            )
        }

        override suspend fun onSyncStates(batch: List<SyncState>) {
            if (!statsSelected()) return
            batch.forEach { syncStateDao.upsert(it.copy(carId = carMap[it.carId])) }
        }

        override suspend fun onDrives(batch: List<DriveSummary>) {
            if (!statsSelected()) return
            driveSummaryDao.upsertAll(batch.map { it.copy(carId = carMap[it.carId]) })
            report = report.copy(
                drivesAndChargesRestored = report.drivesAndChargesRestored + batch.size
            )
        }

        override suspend fun onCharges(batch: List<ChargeSummary>) {
            if (!statsSelected()) return
            chargeSummaryDao.upsertAll(batch.map { it.copy(carId = carMap[it.carId]) })
            report = report.copy(
                drivesAndChargesRestored = report.drivesAndChargesRestored + batch.size
            )
        }

        override suspend fun onDriveAggregates(batch: List<DriveDetailAggregate>) {
            if (!statsSelected()) return
            aggregateDao.upsertDriveAggregates(batch.map { it.copy(carId = carMap[it.carId]) })
        }

        override suspend fun onChargeAggregates(batch: List<ChargeDetailAggregate>) {
            if (!statsSelected()) return
            aggregateDao.upsertChargeAggregates(batch.map { it.copy(carId = carMap[it.carId]) })
        }

        private fun statsSelected() = BackupSection.STATS in selection && statsReadable
    }

    private class ResolvedLegs(val legs: List<SavedTripLeg>, val dropped: Int)

    /** [LegIndex] over the local drives and charges tables, with the counts memoised. */
    private inner class DaoLegIndex : LegIndex {
        private val counts = mutableMapOf<String, Int>()

        override suspend fun startDateOf(carId: Int, legType: String, legId: Int): String? =
            if (legType == SavedTripLeg.TYPE_DRIVE) {
                driveSummaryDao.getStartDate(carId, legId)
            } else {
                chargeSummaryDao.getStartDate(carId, legId)
            }

        override suspend fun idAtStartDate(carId: Int, legType: String, startDate: String): Int? =
            if (legType == SavedTripLeg.TYPE_DRIVE) {
                driveSummaryDao.findIdByStartDate(carId, startDate)
            } else {
                chargeSummaryDao.findIdByStartDate(carId, startDate)
            }

        override suspend fun count(carId: Int, legType: String): Int {
            val key = "$carId|$legType"
            counts[key]?.let { return it }
            val value = if (legType == SavedTripLeg.TYPE_DRIVE) {
                driveSummaryDao.countForCar(carId)
            } else {
                chargeSummaryDao.countForCar(carId)
            }
            counts[key] = value
            return value
        }
    }

    /** Thrown to leave the parser early once the header has been read. */
    private object StopReading : Exception() {
        override fun fillInStackTrace(): Throwable = this
    }

    companion object {
        const val STAGING_DIR = "imports"
        private const val CAR_LOOKUP_TIMEOUT_MS = 5_000L
    }
}
