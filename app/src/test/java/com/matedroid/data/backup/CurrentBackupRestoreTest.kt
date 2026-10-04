package com.matedroid.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.local.StatsDatabase
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The half of the restore path the frozen fixture cannot reach.
 *
 * `v1-frozen.json` declares an older database version on purpose, so its drives and charges
 * are always refused — which pins the refusal and never the acceptance. This one is written
 * against the current schema, so the stats section actually lands, and the guard below
 * fails the build the moment a migration leaves it behind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CurrentBackupRestoreTest {

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
        fixture = File(context.cacheDir, "current.json").apply {
            val stream = checkNotNull(
                CurrentBackupRestoreTest::class.java.classLoader
                    ?.getResourceAsStream(CurrentBackupFixture.RESOURCE)
            ) { "Missing test fixture ${CurrentBackupFixture.RESOURCE}" }
            writeBytes(stream.use { it.readBytes() })
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * The staleness guard. A migration bumps [StatsDatabase.SCHEMA_VERSION], which leaves
     * the drives and charges in this fixture unreadable and the stats path untested — so the
     * build stops here until the fixture is regenerated.
     */
    @Test
    fun `the fixture still matches this build's schema`() = runTest {
        val header = importer.preview(fixture).header

        assertEquals(
            "backup/current.json is stale: it was written for format " +
                "${header.format}, this build writes $BACKUP_FORMAT_VERSION.\n" +
                CurrentBackupFixture.regenerateInstructions,
            BACKUP_FORMAT_VERSION,
            header.format
        )
        assertEquals(
            "backup/current.json is stale: it was written against database version " +
                "${header.databaseVersion}, this build is on ${StatsDatabase.SCHEMA_VERSION}. " +
                "Its drives and charges would be refused, leaving the stats restore untested.\n" +
                CurrentBackupFixture.regenerateInstructions,
            StatsDatabase.SCHEMA_VERSION,
            header.databaseVersion
        )
    }

    @Test
    fun `drives and charges are offered when the schema matches`() = runTest {
        val preview = importer.preview(fixture)

        assertTrue(preview.statsReadable)
        assertFalse(preview.hasUnreadableStats)
        assertTrue(BackupSection.STATS in preview.availableSections)
        assertEquals(
            CurrentBackupFixture.driveIds.size + CurrentBackupFixture.chargeIds.size,
            preview.countOf(BackupSection.STATS, setOf(CurrentBackupFixture.CAR_A, CurrentBackupFixture.CAR_B))
        )
        assertEquals(
            listOf(CurrentBackupFixture.CAR_A, CurrentBackupFixture.CAR_B),
            preview.cars.map { it.carId }
        )
        assertEquals(CurrentBackupFixture.VIN_A, preview.cars.first().vin)
    }

    @Test
    fun `a fresh phone gets the whole file, stats included`() = runTest {
        val preview = importer.preview(fixture)

        val report = importer.restore(
            file = fixture,
            selection = preview.availableSections,
            carIds = preview.cars.map { it.carId }.toSet(),
            mode = ImportMode.MERGE
        )

        assertEquals(
            CurrentBackupFixture.driveIds.size + CurrentBackupFixture.chargeIds.size,
            report.drivesAndChargesRestored
        )
        assertEquals(CurrentBackupFixture.driveIds.size, database.driveSummaryDao().countAll())
        assertEquals(CurrentBackupFixture.chargeIds.size, database.chargeSummaryDao().countAll())
        assertEquals(2, database.syncStateDao().getAll().size)

        // Aggregates are the expensive half of a sync; losing them means recomputing every
        // drive one API call at a time, so a backup carrying them has to put them back.
        assertEquals(
            2,
            database.aggregateDao().getDriveAggregatesForCar(CurrentBackupFixture.CAR_A).size
        )
        assertEquals(
            1,
            database.aggregateDao().getChargeAggregatesForCar(CurrentBackupFixture.CAR_A).size
        )

        // Nothing to match against on a fresh phone, so every leg is taken at face value.
        val trip = database.savedTripDao().getAllTripsWithLegs().single()
        assertEquals(CurrentBackupFixture.CAR_A, trip.trip.carId)
        assertEquals(CurrentBackupFixture.TRIP_LEG_COUNT, trip.legs.size)
        assertEquals(1, report.tripsRestored)
        assertEquals(0, report.tripsIncomplete)
        assertEquals(0, report.tripsSkipped)

        assertEquals(CurrentBackupFixture.SENTRY_COUNT, database.sentryAlertLogDao().count())
        assertEquals(CurrentBackupFixture.PLACE_COUNT, database.geocodeCacheDao().count())
        assertTrue(report.settingsRestored)
        assertEquals(CurrentBackupFixture.SERVER_URL, settingsDataStore.settings.first().serverUrl)
    }

    @Test
    fun `the checked-in fixture carries no credentials`() {
        val json = fixture.readText()

        assertFalse(json.contains(CurrentBackupFixture.SECRET_TOKEN))
        assertFalse(json.contains(CurrentBackupFixture.SECRET_PASSWORD))
        assertTrue(json.contains(CurrentBackupFixture.SERVER_URL))
    }
}
