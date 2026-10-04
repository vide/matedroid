package com.matedroid.data.backup

import com.matedroid.data.local.entity.ChargeDetailAggregate
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveDetailAggregate
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.data.local.entity.SyncState
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The file format is the contract between two installations of the app, possibly months
 * and several releases apart, so these tests pin down what survives a round trip.
 */
class BackupFileTest {

    private val moshi = Moshi.Builder().build()

    private val header = BackupHeader(
        exportedAt = 1_758_240_000_000,
        appVersionName = "1.11.3",
        appVersionCode = 178982387,
        databaseVersion = 13,
        sections = listOf("trips", "settings", "sentry", "places"),
        cars = listOf(BackupCar(carId = 1, vin = "5YJ3E1EA7KF000001", name = "Kitt"))
    )

    private val trip = BackupTrip(
        carId = 1,
        name = "Barcelona to Pals",
        source = "USER_MERGED",
        createdAt = 1_700_000_000_000,
        updatedAt = 1_700_000_100_000,
        legs = listOf(
            BackupTripLeg("DRIVE", id = 12, position = 0, startDate = "2026-08-01 09:00:00"),
            BackupTripLeg("CHARGE", id = 34, position = 1, startDate = "2026-08-01 11:00:00"),
            BackupTripLeg("DRIVE", id = 13, position = 2, startDate = null)
        ),
        consumedFingerprints = listOf("abc123", "def456")
    )

    private val drive = DriveSummary(
        driveId = 12,
        carId = 1,
        startDate = "2026-08-01 09:00:00",
        endDate = "2026-08-01 10:30:00",
        durationMin = 90,
        startAddress = "Barcelona",
        endAddress = "Girona",
        distance = 103.4,
        speedMax = 120,
        speedAvg = 84,
        powerMax = 180,
        powerMin = -55,
        startBatteryLevel = 82,
        endBatteryLevel = 47,
        outsideTempAvg = 24.5,
        insideTempAvg = null,
        energyConsumed = 18.2,
        efficiency = 176.0
    )

    @Test
    fun `every section survives a round trip`() = runTest {
        val source = FakeSource(
            header = header,
            settings = BackupSettings(
                serverUrl = "https://apitm.example.com",
                currencyCode = "EUR",
                isImperial = false,
                highSocWarningThreshold = 90,
                carImageOverrides = mapOf("1" to BackupCarImage("myj", "WY19P"))
            ),
            trips = listOf(trip),
            sentryEvents = listOf(
                BackupSentryEvent(
                    carId = 1,
                    detectedAt = 1_757_000_000_000,
                    sessionStartedAt = 1_756_999_000_000,
                    latitude = 41.9,
                    longitude = 3.1,
                    address = "Pals"
                )
            ),
            places = listOf(BackupPlace(4190, 310, "ES", "Spain", "Catalonia", "Pals", 1L)),
            tripRoutes = listOf(BackupTripRoute("key1", 0, "AAECAw==", 2L)),
            tripCountries = listOf(BackupTripCountries("key1", "ES,FR", 3L)),
            syncStates = listOf(SyncState(carId = 1, lastDriveSyncAt = 42L)),
            drives = listOf(drive)
        )

        val collected = writeThenRead(source)

        assertEquals(header, collected.header)
        assertEquals(source.settings, collected.settings)
        assertEquals(listOf(trip), collected.trips)
        assertEquals(source.sentryEvents, collected.sentryEvents)
        assertEquals(source.places, collected.places)
        assertEquals(source.tripRoutes, collected.tripRoutes)
        assertEquals(source.tripCountries, collected.tripCountries)
        assertEquals(source.syncStates, collected.syncStates)
        assertEquals(listOf(drive), collected.drives)
    }

    @Test
    fun `sections left out come back empty rather than missing`() = runTest {
        val collected = writeThenRead(FakeSource(header = header))

        assertEquals(header, collected.header)
        assertNull(collected.settings)
        assertTrue(collected.trips.isEmpty())
        assertTrue(collected.sentryEvents.isEmpty())
        assertTrue(collected.drives.isEmpty())
    }

    @Test
    fun `batches are handed over in file order`() = runTest {
        val places = (1..BackupFile.BATCH_SIZE + 7).map { BackupPlace(it, it) }
        val collected = writeThenRead(FakeSource(header = header, places = places))

        assertEquals(places, collected.places)
        assertEquals(2, collected.placeBatches)
    }

    @Test
    fun `a file from a newer release still gives up what this one understands`() = runTest {
        val json = """
            {
              "header": { "format": 1, "exportedAt": 7, "somethingNew": true },
              "trips": [
                {
                  "carId": 2,
                  "source": "USER_EDITED",
                  "legs": [{ "type": "DRIVE", "id": 9, "position": 0, "mood": "sunny" }],
                  "unknownSection": [1, 2, 3]
                }
              ],
              "sectionThisReleaseNeverHeardOf": { "a": [1, 2] }
            }
        """.trimIndent()

        val collected = Collector()
        BackupFile.read(Buffer().writeUtf8(json), moshi, collected)

        assertEquals(7L, collected.header?.exportedAt)
        assertEquals(1, collected.trips.size)
        assertEquals(2, collected.trips.single().carId)
        assertEquals(9, collected.trips.single().legs.single().id)
        // Absent fields fall back to the declared defaults rather than failing the read.
        assertNull(collected.trips.single().name)
        assertTrue(collected.trips.single().consumedFingerprints.isEmpty())
    }

    private suspend fun writeThenRead(source: FakeSource): Collector {
        val buffer = Buffer()
        BackupFile.write(buffer, moshi, source)
        return Collector().also { BackupFile.read(buffer, moshi, it) }
    }

    private class FakeSource(
        private val header: BackupHeader,
        val settings: BackupSettings? = null,
        val trips: List<BackupTrip> = emptyList(),
        val sentryEvents: List<BackupSentryEvent> = emptyList(),
        val places: List<BackupPlace> = emptyList(),
        val tripRoutes: List<BackupTripRoute> = emptyList(),
        val tripCountries: List<BackupTripCountries> = emptyList(),
        val syncStates: List<SyncState> = emptyList(),
        val drives: List<DriveSummary> = emptyList(),
        val charges: List<ChargeSummary> = emptyList()
    ) : BackupSource {
        override suspend fun header() = header
        override suspend fun settings() = settings
        override suspend fun trips() = trips
        override suspend fun sentryEvents() = sentryEvents
        override suspend fun places() = places
        override suspend fun tripRoutes() = tripRoutes
        override suspend fun tripCountries() = tripCountries
        override suspend fun syncStates() = syncStates
        override suspend fun drives() = drives
        override suspend fun charges() = charges
        override suspend fun driveAggregates(): List<DriveDetailAggregate> = emptyList()
        override suspend fun chargeAggregates(): List<ChargeDetailAggregate> = emptyList()
    }

    private class Collector : BackupVisitor {
        var header: BackupHeader? = null
        var settings: BackupSettings? = null
        val trips = mutableListOf<BackupTrip>()
        val sentryEvents = mutableListOf<BackupSentryEvent>()
        val places = mutableListOf<BackupPlace>()
        var placeBatches = 0
        val tripRoutes = mutableListOf<BackupTripRoute>()
        val tripCountries = mutableListOf<BackupTripCountries>()
        val syncStates = mutableListOf<SyncState>()
        val drives = mutableListOf<DriveSummary>()

        override suspend fun onHeader(header: BackupHeader) {
            this.header = header
        }

        override suspend fun onSettings(settings: BackupSettings) {
            this.settings = settings
        }

        override suspend fun onTrips(batch: List<BackupTrip>) {
            trips += batch
        }

        override suspend fun onSentryEvents(batch: List<BackupSentryEvent>) {
            sentryEvents += batch
        }

        override suspend fun onPlaces(batch: List<BackupPlace>) {
            places += batch
            placeBatches++
        }

        override suspend fun onTripRoutes(batch: List<BackupTripRoute>) {
            tripRoutes += batch
        }

        override suspend fun onTripCountries(batch: List<BackupTripCountries>) {
            tripCountries += batch
        }

        override suspend fun onSyncStates(batch: List<SyncState>) {
            syncStates += batch
        }

        override suspend fun onDrives(batch: List<DriveSummary>) {
            drives += batch
        }
    }
}
