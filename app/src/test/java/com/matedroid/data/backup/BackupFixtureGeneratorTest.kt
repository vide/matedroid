package com.matedroid.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.local.StatsDatabase
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Writes the current-schema fixture by running the real exporter over a seeded database.
 *
 * It runs on every test pass, so the generator cannot quietly rot between schema bumps, and
 * so the export half gets exercised end to end rather than only through its unit tests.
 * The output lands in `build/outputs` — the one directory the build pod syncs back — and is
 * copied into the test resources by hand: a test has no business editing the source tree
 * behind anyone's back, and on the cluster it would be writing to a copy that never returns.
 *
 * See [CurrentBackupFixture] for why this fixture exists alongside the frozen one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupFixtureGeneratorTest {

    private lateinit var context: Context
    private lateinit var database: StatsDatabase
    private lateinit var settingsDataStore: SettingsDataStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, StatsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsDataStore = SettingsDataStore(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `the exporter writes a fixture this build can read back`() = runTest {
        CurrentBackupFixture.seed(database, settingsDataStore)

        val exported = exporter().export(
            sections = BackupSection.entries.toSet(),
            carIds = setOf(CurrentBackupFixture.CAR_A, CurrentBackupFixture.CAR_B)
        )
        val json = exported.readText()

        // The guarantee the whole feature rests on, checked on the writing side too.
        assertFalse(
            "The exporter put a credential in a backup file",
            json.contains(CurrentBackupFixture.SECRET_TOKEN) ||
                json.contains(CurrentBackupFixture.SECRET_PASSWORD) ||
                json.contains("fixture-user")
        )
        assertTrue(json.contains(CurrentBackupFixture.SERVER_URL))

        val generated = File(CurrentBackupFixture.GENERATED_PATH)
        generated.parentFile?.mkdirs()
        exported.copyTo(generated, overwrite = true)
        println("Wrote ${generated.absolutePath}")
    }

    private fun exporter() = BackupExporter(
        context = context,
        moshi = Moshi.Builder().build(),
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
}
