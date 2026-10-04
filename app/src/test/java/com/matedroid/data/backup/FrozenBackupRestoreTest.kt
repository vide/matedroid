package com.matedroid.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.matedroid.data.local.KnownCar
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.local.StatsDatabase
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.data.local.entity.SavedTrip
import com.matedroid.data.local.entity.SavedTripLeg
import com.matedroid.data.local.entity.SentryAlertLog
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The whole restore path, run against a backup file that is never regenerated.
 *
 * `v1-frozen.json` is hand-written and frozen on purpose: it is what a release writing
 * format 1 produces, and this test is the promise that such a file still restores years
 * and several schema versions later. Nothing here may be "fixed" by editing the fixture —
 * if an assertion fails, either the reader broke its own format or the file is being
 * misread, and both are the bug.
 *
 * It is deliberately written against an older database version than this build, so it also
 * covers the one section that is allowed to go stale: drives and charges are refused, while
 * everything that cannot be downloaded again still lands.
 *
 * The local state it restores onto disagrees with the file on purpose, so every branch of
 * [BackupLegResolver] and both dedupe rules are exercised in one pass.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FrozenBackupRestoreTest {

    private lateinit var context: Context
    private lateinit var database: StatsDatabase
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var importer: BackupImporter
    private lateinit var fixture: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, StatsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsDataStore = SettingsDataStore(context)
        importer = BackupImporter(
            context = context,
            moshi = Moshi.Builder().build(),
            database = database,
            settingsDataStore = settingsDataStore,
            savedTripDao = database.savedTripDao(),
            sentryAlertLogDao = database.sentryAlertLogDao(),
            geocodeCacheDao = database.geocodeCacheDao(),
            tripRouteCacheDao = database.tripRouteCacheDao(),
            tripCountryCacheDao = database.tripCountryCacheDao(),
            syncStateDao = database.syncStateDao(),
            driveSummaryDao = database.driveSummaryDao(),
            chargeSummaryDao = database.chargeSummaryDao(),
            aggregateDao = database.aggregateDao()
        )
        fixture = File(context.cacheDir, "v1-frozen.json").apply {
            // Robolectric's sandbox classloader is the one holding the test resources, so
            // the class's own getResourceAsStream does not see them.
            val stream = checkNotNull(
                FrozenBackupRestoreTest::class.java.classLoader?.getResourceAsStream(FIXTURE)
            ) { "Missing test fixture $FIXTURE" }
            writeBytes(stream.use { it.readBytes() })
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `the preview describes the file before anything is touched`() = runTest {
        seedLocalState()

        val preview = importer.preview(fixture)

        assertEquals(1, preview.header.format)
        assertEquals("1.11.3", preview.header.appVersionName)
        assertEquals(3, preview.tripsByCar.values.sum())
        assertEquals(3, preview.sentryByCar.values.sum())
        assertEquals(2, preview.places)
        assertEquals(1, preview.tripMaps)
        assertTrue(preview.hasSettings)

        // Car 3 appears only in a sentry row: the header never knew it, the preview must.
        assertEquals(listOf(1, 2, 3), preview.cars.map { it.carId })
        assertEquals(listOf("Alpha", "Beta", null), preview.cars.map { it.name })
        assertEquals("VIN-ALPHA", preview.cars.first().vin)

        // Written against database version 11, which is not this build's.
        assertFalse(preview.statsReadable)
        assertTrue(preview.hasUnreadableStats)
        assertFalse(BackupSection.STATS in preview.availableSections)
        assertTrue(BackupSection.TRIPS in preview.availableSections)
        assertTrue(BackupSection.SENTRY in preview.availableSections)
    }

    @Test
    fun `counts on the preview follow the cars that are ticked`() = runTest {
        val preview = importer.preview(fixture)

        assertEquals(3, preview.countOf(BackupSection.SENTRY, setOf(1, 2, 3)))
        assertEquals(2, preview.countOf(BackupSection.SENTRY, setOf(1)))
        assertEquals(1, preview.countOf(BackupSection.SENTRY, setOf(3)))
        // Place names are keyed by map grid, not by car, so they ignore the selection.
        assertEquals(2, preview.countOf(BackupSection.PLACES, emptySet()))
    }

    @Test
    fun `a frozen backup restores whole onto a phone that disagrees with it`() = runTest {
        seedLocalState()
        val preview = importer.preview(fixture)

        val report = importer.restore(
            file = importer.stage(Uri.fromFile(fixture)),
            selection = preview.availableSections,
            carIds = preview.cars.map { it.carId }.toSet(),
            mode = ImportMode.MERGE
        )

        assertEquals(1, report.tripsRestored)
        assertEquals(1, report.tripsAlreadyHere)
        assertEquals(1, report.tripsIncomplete)
        assertEquals(1, report.tripsSkipped)
        assertEquals(2, report.sentryRestored)
        assertEquals(1, report.sentryAlreadyHere)
        assertEquals(2, report.placesRestored)
        assertEquals(1, report.tripMapsRestored)
        assertTrue(report.settingsRestored)
        assertEquals(0, report.drivesAndChargesRestored)
    }

    @Test
    fun `the restored trip lands on the right car with its legs repaired`() = runTest {
        seedLocalState()
        restoreEverything()

        // The file calls this car 1; this phone calls it 7, and only the VIN says so.
        val restored = database.savedTripDao().getAllTripsWithLegs()
            .single { it.trip.name == "Mixed outcomes" }
        assertEquals(LOCAL_CAR_ID, restored.trip.carId)
        assertEquals(SavedTrip.SOURCE_USER_MERGED, restored.trip.source)

        // Kept as it was, re-found under a new id, and kept pending a sync that has not
        // happened — while the leg pointing at somebody else's drive was dropped.
        assertEquals(
            listOf(100, 201, 999),
            restored.legs.sortedBy { it.position }.map { it.legId }
        )
        // Positions are closed up after the drop rather than left with a hole.
        assertEquals(listOf(0, 1, 2), restored.legs.map { it.position }.sorted())

        assertEquals(
            2,
            database.savedTripDao().getAllConsumedFingerprints()
                .count { it.savedTripId == restored.trip.id }
        )
    }

    @Test
    fun `settings come back but the local API token is left alone`() = runTest {
        seedLocalState()
        restoreEverything()

        val settings = settingsDataStore.settings.first()
        assertEquals("https://teslamate.frozen.example", settings.serverUrl)
        assertEquals("CHF", settings.currencyCode)
        assertEquals(85, settings.highSocWarningThreshold)
        assertEquals(25, settings.connectTimeoutSeconds)
        assertTrue(settingsDataStore.isImperial.first())

        // The one thing a backup never carries, and so must never overwrite.
        assertEquals(LOCAL_TOKEN, settings.apiToken)

        // The override was filed under the file's car id; it has to follow the car.
        val overrides = settingsDataStore.carImageOverrides.first()
        assertNotNull(overrides[LOCAL_CAR_ID])
        assertEquals("WY19P", overrides[LOCAL_CAR_ID]?.wheelCode)
        assertEquals(LOCAL_CAR_ID, settings.lastSelectedCarId)
    }

    @Test
    fun `drives and charges from another database version are left out`() = runTest {
        seedLocalState()
        restoreEverything()

        // Only the two seeded drives; the file's own drive row was refused.
        assertEquals(2, database.driveSummaryDao().countAll())
        assertEquals(0, database.chargeSummaryDao().countAll())
        assertEquals(0, database.syncStateDao().getAll().size)

        // Everything that cannot be downloaded again still came through.
        assertEquals(2, database.geocodeCacheDao().count())
        assertEquals(1, database.tripRouteCacheDao().countTrips())
        assertNotNull(database.tripCountryCacheDao().get("frozen-trip-key"))
    }

    @Test
    fun `sentry alerts are deduplicated by car and moment`() = runTest {
        seedLocalState()
        restoreEverything()

        val all = database.sentryAlertLogDao().getAll()
        assertEquals(3, all.size)
        assertEquals(2, all.count { it.carId == LOCAL_CAR_ID })
        // The car the header forgot keeps the id it arrived with.
        assertEquals(1, all.count { it.carId == 3 })
        assertEquals("Paris", all.single { it.carId == 3 }.address)
    }

    @Test
    fun `replacing clears the ticked car and leaves the others alone`() = runTest {
        seedLocalState()
        database.savedTripDao().insertRestoredTrip(
            trip = SavedTrip(carId = OTHER_CAR_ID, name = "Other car", source = "AUTO_DETECTED",
                createdAt = 1, updatedAt = 1),
            legs = listOf(SavedTripLeg(tripId = 0, position = 0, legType = "DRIVE", legId = 500)),
            consumedFingerprints = emptyList()
        )

        importer.restore(
            file = fixture,
            selection = setOf(BackupSection.TRIPS),
            carIds = setOf(1),
            mode = ImportMode.REPLACE
        )

        val trips = database.savedTripDao().getAllTripsWithLegs()
        // The seeded trip is gone and nothing was treated as a duplicate of it: replacing
        // means the car ends up holding what the file holds, not a merge of the two.
        assertTrue(trips.none { it.trip.name == "Existing" })
        assertEquals(
            listOf("Already here", "Mixed outcomes"),
            trips.filter { it.trip.carId == LOCAL_CAR_ID }.mapNotNull { it.trip.name }.sorted()
        )
        // The other car was never ticked, so its trip was never in reach.
        assertEquals(1, trips.count { it.trip.carId == OTHER_CAR_ID })
        assertEquals("Other car", trips.single { it.trip.carId == OTHER_CAR_ID }.trip.name)
    }

    private suspend fun restoreEverything() {
        val preview = importer.preview(fixture)
        importer.restore(
            file = fixture,
            selection = preview.availableSections,
            carIds = preview.cars.map { it.carId }.toSet(),
            mode = ImportMode.MERGE
        )
    }

    /**
     * A phone that has already been running: a different numbering for the same car, two
     * drives synced, one saved trip and one sentry alert the file also carries.
     */
    private suspend fun seedLocalState() {
        settingsDataStore.saveSettings(
            serverUrl = "https://local.example",
            secondaryServerUrl = "",
            apiToken = LOCAL_TOKEN,
            httpBasicAuthUsername = "",
            httpBasicAuthPassword = "",
            acceptInvalidCerts = false,
            currencyCode = "EUR"
        )
        settingsDataStore.saveKnownCars(
            listOf(
                KnownCar(LOCAL_CAR_ID, "VIN-ALPHA", "Alpha here"),
                KnownCar(OTHER_CAR_ID, "VIN-BETA", "Beta here")
            )
        )
        database.driveSummaryDao().upsertAll(
            listOf(
                drive(driveId = 100, startDate = "2026-01-01T10:00:00Z"),
                // The file calls this drive 200; here it ended up as 201.
                drive(driveId = 201, startDate = "2026-02-01T10:00:00Z")
            )
        )
        database.savedTripDao().insertRestoredTrip(
            trip = SavedTrip(
                carId = LOCAL_CAR_ID,
                name = "Existing",
                source = SavedTrip.SOURCE_AUTO_DETECTED,
                createdAt = 1,
                updatedAt = 1
            ),
            legs = listOf(SavedTripLeg(tripId = 0, position = 0, legType = "DRIVE", legId = 100)),
            consumedFingerprints = emptyList()
        )
        database.sentryAlertLogDao().insertAll(
            listOf(
                SentryAlertLog(
                    carId = LOCAL_CAR_ID,
                    detectedAt = 1_700_000_000_000,
                    sessionStartedAt = 1_699_999_000_000
                )
            )
        )
    }

    private fun drive(driveId: Int, startDate: String) = DriveSummary(
        driveId = driveId,
        carId = LOCAL_CAR_ID,
        startDate = startDate,
        endDate = startDate,
        durationMin = 30,
        startAddress = "A",
        endAddress = "B",
        distance = 20.0,
        speedMax = 100,
        speedAvg = 60,
        powerMax = 100,
        powerMin = -20,
        startBatteryLevel = 80,
        endBatteryLevel = 70,
        outsideTempAvg = null,
        insideTempAvg = null,
        energyConsumed = null,
        efficiency = null
    )

    private companion object {
        const val FIXTURE = "backup/v1-frozen.json"
        const val LOCAL_TOKEN = "local-secret-token"

        /** The file numbers this car 1; this phone numbers it 7. Only the VIN connects them. */
        const val LOCAL_CAR_ID = 7
        const val OTHER_CAR_ID = 2
    }
}
