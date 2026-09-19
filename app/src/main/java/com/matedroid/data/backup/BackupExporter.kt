package com.matedroid.data.backup

import android.content.Context
import android.util.Base64
import com.matedroid.BuildConfig
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
import com.matedroid.data.local.entity.SavedTripLeg
import com.matedroid.data.local.entity.SyncState
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How much of each section there is to export, for the checkboxes to show.
 *
 * The car-scoped numbers follow whichever cars are ticked; [places] and [tripMaps] do not,
 * because those caches are keyed by map grid and by trip, not by car.
 */
data class BackupCounts(
    val trips: Int = 0,
    val sentryEvents: Int = 0,
    val places: Int = 0,
    val tripMaps: Int = 0,
    val drivesAndCharges: Int = 0
)

/**
 * Builds a backup file in the cache directory, ready to be handed to the share sheet.
 *
 * The file is staged in the cache on purpose: MateDroid never picks where a backup lives.
 * It writes one, offers it to whatever app the user chooses, and forgets about it — the
 * next export clears the staging directory out.
 */
@Singleton
class BackupExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
    private val settingsDataStore: SettingsDataStore,
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

    /**
     * The cars this backup could cover, named where the app knows who they are.
     *
     * Identities come from the store the repository fills on every successful car fetch, so
     * they survive an export made away from a Teslamate that only answers on the home LAN.
     * Ids found in the local tables are included too: a car the server has stopped listing
     * still has drives and trips here, and leaving it out would quietly drop them.
     */
    suspend fun cars(): List<BackupCar> = withContext(Dispatchers.IO) {
        val known = settingsDataStore.knownCars.first().associateBy { it.carId }
        (known.keys + localCarIds()).distinct().sorted().map { carId ->
            BackupCar(carId = carId, vin = known[carId]?.vin, name = known[carId]?.name)
        }
    }

    suspend fun counts(carIds: Set<Int>): BackupCounts = withContext(Dispatchers.IO) {
        val ids = carIds.sorted()
        BackupCounts(
            trips = savedTripDao.countForCars(ids),
            sentryEvents = sentryAlertLogDao.countForCars(ids),
            places = geocodeCacheDao.count(),
            tripMaps = tripRouteCacheDao.countTrips(),
            drivesAndCharges = driveSummaryDao.countForCars(ids) + chargeSummaryDao.countForCars(ids)
        )
    }

    /** Write a backup holding [sections] for [carIds], returning the staged file. */
    suspend fun export(sections: Set<BackupSection>, carIds: Set<Int>): File =
        withContext(Dispatchers.IO) {
            val ids = carIds.sorted()
            val selectedCars = cars().filter { it.carId in carIds }
            val file = stagingFile()
            file.sink().buffer().use { sink ->
                BackupFile.write(sink, moshi, DatabaseSource(sections, ids, selectedCars))
            }
            file
        }

    private suspend fun localCarIds(): List<Int> = (
        syncStateDao.getAll().map { it.carId } +
            driveSummaryDao.getAllCarIds() +
            chargeSummaryDao.getAllCarIds() +
            savedTripDao.getAllCarIds() +
            sentryAlertLogDao.getAllCarIds()
        ).distinct()

    private fun stagingFile(): File {
        val dir = File(context.cacheDir, STAGING_DIR)
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, "matedroid-backup-${LocalDate.now()}.json")
    }

    /** Reads each section straight out of the database, one at a time. */
    private inner class DatabaseSource(
        private val sections: Set<BackupSection>,
        private val carIds: List<Int>,
        private val cars: List<BackupCar>
    ) : BackupSource {

        override suspend fun header() = BackupHeader(
            format = BACKUP_FORMAT_VERSION,
            exportedAt = System.currentTimeMillis(),
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE.toLong(),
            databaseVersion = StatsDatabase.SCHEMA_VERSION,
            sections = sections.sortedBy { it.ordinal }.map { it.key },
            cars = cars
        )

        override suspend fun settings(): BackupSettings? {
            if (BackupSection.SETTINGS !in sections) return null
            val settings = settingsDataStore.settings.first()
            val overrides = settingsDataStore.carImageOverrides.first()
            // Demo mode parks the sample-data URL in the same field a real server would use.
            // Carrying it into a backup would quietly disconnect whoever restores the file.
            val isDemo = settings.isDemoMode
            return BackupSettings(
                serverUrl = settings.serverUrl.takeUnless { isDemo },
                secondaryServerUrl = settings.secondaryServerUrl.takeUnless { isDemo },
                teslamateBaseUrl = settings.teslamateBaseUrl.takeUnless { isDemo },
                acceptInvalidCerts = settings.acceptInvalidCerts,
                connectTimeoutSeconds = settings.connectTimeoutSeconds,
                currencyCode = settings.currencyCode,
                costPerKwhBasis = settings.costPerKwhBasis.id,
                isImperial = settingsDataStore.isImperial.first(),
                showShortDrivesCharges = settings.showShortDrivesCharges,
                shortDriveMinDurationMin = settings.shortDriveMinDurationMin,
                shortDriveMinDistance = settings.shortDriveMinDistance,
                shortChargeMinEnergyKwh = settings.shortChargeMinEnergyKwh,
                highSocWarningThreshold = settings.highSocWarningThreshold,
                lowSocWarningThreshold = settings.lowSocWarningThreshold,
                lastSelectedCarId = settings.lastSelectedCarId?.takeIf { it in carIds },
                // Only for the cars going into this file: an override names a picture for a
                // car id, and an id nobody exported is an id nobody can make sense of.
                carImageOverrides = overrides
                    .filterKeys { it in carIds }
                    .entries
                    .associate { (carId, override) ->
                        carId.toString() to BackupCarImage(override.variant, override.wheelCode)
                    }
            )
        }

        override suspend fun trips(): List<BackupTrip> {
            if (BackupSection.TRIPS !in sections) return emptyList()
            val fingerprintsByTrip = savedTripDao.getAllConsumedFingerprints()
                .groupBy({ it.savedTripId }, { it.fingerprint })
            return savedTripDao.getTripsWithLegsForCars(carIds).map { saved ->
                BackupTrip(
                    carId = saved.trip.carId,
                    name = saved.trip.name,
                    source = saved.trip.source,
                    createdAt = saved.trip.createdAt,
                    updatedAt = saved.trip.updatedAt,
                    legs = saved.legs.sortedBy { it.position }.map { leg ->
                        BackupTripLeg(
                            type = leg.legType,
                            id = leg.legId,
                            position = leg.position,
                            startDate = startDateOf(saved.trip.carId, leg.legType, leg.legId)
                        )
                    },
                    consumedFingerprints = fingerprintsByTrip[saved.trip.id].orEmpty()
                )
            }
        }

        /** The natural key that lets a restore re-find this drive if the ids have moved. */
        private suspend fun startDateOf(carId: Int, legType: String, legId: Int): String? =
            if (legType == SavedTripLeg.TYPE_DRIVE) {
                driveSummaryDao.getStartDate(carId, legId)
            } else {
                chargeSummaryDao.getStartDate(carId, legId)
            }

        override suspend fun sentryEvents(): List<BackupSentryEvent> {
            if (BackupSection.SENTRY !in sections) return emptyList()
            return sentryAlertLogDao.getAllForCars(carIds).map {
                BackupSentryEvent(
                    carId = it.carId,
                    detectedAt = it.detectedAt,
                    sessionStartedAt = it.sessionStartedAt,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    address = it.address
                )
            }
        }

        // Place names and trip maps are keyed by map grid and by trip, not by car, so they
        // go in whole whenever their section is ticked.
        override suspend fun places(): List<BackupPlace> {
            if (BackupSection.PLACES !in sections) return emptyList()
            return geocodeCacheDao.getAll().map {
                BackupPlace(
                    gridLat = it.gridLat,
                    gridLon = it.gridLon,
                    countryCode = it.countryCode,
                    countryName = it.countryName,
                    regionName = it.regionName,
                    city = it.city,
                    cachedAt = it.cachedAt
                )
            }
        }

        override suspend fun tripRoutes(): List<BackupTripRoute> {
            if (BackupSection.TRIP_MAPS !in sections) return emptyList()
            return tripRouteCacheDao.getAll().map {
                BackupTripRoute(
                    tripKey = it.tripKey,
                    segmentIndex = it.segmentIndex,
                    points = Base64.encodeToString(it.segmentData, Base64.NO_WRAP),
                    createdAt = it.createdAt
                )
            }
        }

        override suspend fun tripCountries(): List<BackupTripCountries> {
            if (BackupSection.TRIP_MAPS !in sections) return emptyList()
            return tripCountryCacheDao.getAll().map {
                BackupTripCountries(it.tripKey, it.countries, it.createdAt)
            }
        }

        override suspend fun syncStates(): List<SyncState> =
            if (BackupSection.STATS in sections) {
                syncStateDao.getAll().filter { it.carId in carIds }
            } else {
                emptyList()
            }

        override suspend fun drives(): List<DriveSummary> =
            perCar { driveSummaryDao.getAllForCar(it) }

        override suspend fun charges(): List<ChargeSummary> =
            perCar { chargeSummaryDao.getAllForCar(it) }

        override suspend fun driveAggregates(): List<DriveDetailAggregate> =
            perCar { aggregateDao.getDriveAggregatesForCar(it) }

        override suspend fun chargeAggregates(): List<ChargeDetailAggregate> =
            perCar { aggregateDao.getChargeAggregatesForCar(it) }

        private suspend fun <T> perCar(load: suspend (Int) -> List<T>): List<T> {
            if (BackupSection.STATS !in sections) return emptyList()
            return carIds.flatMap { load(it) }
        }
    }

    companion object {
        const val STAGING_DIR = "backups"
        const val MIME_TYPE = "application/json"
    }
}
