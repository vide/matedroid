package com.matedroid.data.backup

import com.matedroid.data.local.KnownCar
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.local.StatsDatabase
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

/**
 * The second backup fixture: one written by the current exporter, against the current
 * database schema.
 *
 * It exists for the single section `v1-frozen.json` cannot cover. That file is frozen at an
 * older schema on purpose, so the drives and charges in it are always refused — which pins
 * the refusal but never the acceptance. This one is regenerated whenever the schema moves,
 * so the stats path itself stays tested.
 *
 * The trade is worth naming: a fixture written by this build's exporter and read by this
 * build's importer only proves the two agree with each other. That is why the frozen file
 * stays too, and why it is the one that must never be regenerated.
 */
internal object CurrentBackupFixture {

    const val RESOURCE = "backup/current.json"

    /**
     * Written by [BackupFixtureGeneratorTest] on every test run, not just when
     * regenerating: the generator stays exercised, and `build/outputs` is the one place the
     * build pod syncs back, so the same two commands work locally and on the cluster.
     */
    const val GENERATED_PATH = "build/outputs/backup-fixture/current.json"

    /** Where the checked-in copy lives, relative to the app module. */
    const val SOURCE_PATH = "src/test/resources/$RESOURCE"

    /** Printed by the staleness guard, so the fix never has to be looked up. */
    val regenerateInstructions: String = """
        Regenerate it with:
          ./scripts/remote-gradle.sh testDebugUnitTest --tests '*BackupFixtureGeneratorTest*'
          cp app/$GENERATED_PATH app/$SOURCE_PATH
        Then commit the result. Never edit backup/v1-frozen.json to make a test pass.
    """.trimIndent()

    const val CAR_A = 1
    const val CAR_B = 2
    const val VIN_A = "VIN-CURRENT-A"
    const val VIN_B = "VIN-CURRENT-B"
    const val SERVER_URL = "https://teslamate.current.example"

    /** Seeded into the settings before exporting, and never expected to reach the file. */
    const val SECRET_TOKEN = "token-that-must-never-be-exported"
    const val SECRET_PASSWORD = "password-that-must-never-be-exported"

    val driveIds = listOf(10, 11, 20)
    val chargeIds = listOf(30, 31)
    const val TRIP_LEG_COUNT = 3
    const val SENTRY_COUNT = 3
    const val PLACE_COUNT = 2

    /** A small but complete phone: two cars, synced stats, a saved trip and some caches. */
    suspend fun seed(database: StatsDatabase, settingsDataStore: SettingsDataStore) {
        settingsDataStore.saveSettings(
            serverUrl = SERVER_URL,
            secondaryServerUrl = "",
            apiToken = SECRET_TOKEN,
            httpBasicAuthUsername = "fixture-user",
            httpBasicAuthPassword = SECRET_PASSWORD,
            acceptInvalidCerts = false,
            currencyCode = "GBP"
        )
        settingsDataStore.saveKnownCars(
            listOf(KnownCar(CAR_A, VIN_A, "Current Alpha"), KnownCar(CAR_B, VIN_B, "Current Beta"))
        )

        database.syncStateDao().upsert(SyncState(carId = CAR_A, summariesSynced = true))
        database.syncStateDao().upsert(SyncState(carId = CAR_B, summariesSynced = true))

        database.driveSummaryDao().upsertAll(
            listOf(
                drive(10, CAR_A, "2026-04-01T08:00:00Z"),
                drive(11, CAR_A, "2026-04-02T08:00:00Z"),
                drive(20, CAR_B, "2026-04-03T08:00:00Z")
            )
        )
        database.chargeSummaryDao().upsertAll(
            listOf(charge(30, CAR_A, "2026-04-01T12:00:00Z"), charge(31, CAR_B, "2026-04-03T12:00:00Z"))
        )
        database.aggregateDao().upsertDriveAggregates(
            listOf(driveAggregate(10, CAR_A), driveAggregate(11, CAR_A), driveAggregate(20, CAR_B))
        )
        database.aggregateDao().upsertChargeAggregates(listOf(chargeAggregate(30, CAR_A)))

        database.savedTripDao().insertRestoredTrip(
            trip = SavedTrip(
                carId = CAR_A,
                name = "Current fixture trip",
                source = SavedTrip.SOURCE_USER_MERGED,
                createdAt = 1_700_000_000_000,
                updatedAt = 1_700_000_100_000
            ),
            legs = listOf(
                SavedTripLeg(tripId = 0, position = 0, legType = SavedTripLeg.TYPE_DRIVE, legId = 10),
                SavedTripLeg(tripId = 0, position = 1, legType = SavedTripLeg.TYPE_CHARGE, legId = 30),
                SavedTripLeg(tripId = 0, position = 2, legType = SavedTripLeg.TYPE_DRIVE, legId = 11)
            ),
            consumedFingerprints = listOf("current-fingerprint")
        )

        database.sentryAlertLogDao().insertAll(
            listOf(
                SentryAlertLog(carId = CAR_A, detectedAt = 1_700_100_000_000, sessionStartedAt = 1),
                SentryAlertLog(carId = CAR_A, detectedAt = 1_700_100_600_000, sessionStartedAt = 1),
                SentryAlertLog(carId = CAR_B, detectedAt = 1_700_101_200_000, sessionStartedAt = 1)
            )
        )
        database.geocodeCacheDao().upsertAll(
            listOf(
                GeocodeCache(4190, 310, "ES", "Spain", "Catalonia", "Pals", 1),
                GeocodeCache(4885, 235, "FR", "France", "Ile-de-France", "Paris", 1)
            )
        )
        database.tripRouteCacheDao().insertAll(
            listOf(TripRouteCache("current-trip-key", 0, ByteArray(16), 1))
        )
        database.tripCountryCacheDao().insert(TripCountryCache("current-trip-key", "ES,FR", 1))
    }

    private fun drive(driveId: Int, carId: Int, startDate: String) = DriveSummary(
        driveId = driveId,
        carId = carId,
        startDate = startDate,
        endDate = startDate,
        durationMin = 45,
        startAddress = "Start $driveId",
        endAddress = "End $driveId",
        distance = 60.0,
        speedMax = 110,
        speedAvg = 70,
        powerMax = 120,
        powerMin = -30,
        startBatteryLevel = 85,
        endBatteryLevel = 65,
        outsideTempAvg = 17.0,
        insideTempAvg = 21.0,
        energyConsumed = 11.0,
        efficiency = 183.0
    )

    private fun charge(chargeId: Int, carId: Int, startDate: String) = ChargeSummary(
        chargeId = chargeId,
        carId = carId,
        startDate = startDate,
        endDate = startDate,
        durationMin = 40,
        address = "Charger $chargeId",
        latitude = 41.5,
        longitude = 2.5,
        energyAdded = 30.0,
        energyUsed = 32.0,
        cost = 9.5,
        startBatteryLevel = 25,
        endBatteryLevel = 80,
        outsideTempAvg = 16.0,
        odometer = 54321.0
    )

    private fun driveAggregate(driveId: Int, carId: Int) = DriveDetailAggregate(
        driveId = driveId,
        carId = carId,
        schemaVersion = 1,
        computedAt = 1_700_000_000_000,
        maxElevation = 400,
        minElevation = 10,
        startElevation = 20,
        endElevation = 390,
        elevationGain = 380,
        elevationLoss = 10,
        hasElevationData = true,
        maxInsideTemp = 22.0,
        minInsideTemp = 19.0,
        maxOutsideTemp = 18.0,
        minOutsideTemp = 12.0,
        maxPower = 120,
        minPower = -30,
        climateOnPositions = 100,
        positionCount = 250
    )

    private fun chargeAggregate(chargeId: Int, carId: Int) = ChargeDetailAggregate(
        chargeId = chargeId,
        carId = carId,
        schemaVersion = 1,
        computedAt = 1_700_000_000_000,
        isFastCharger = true,
        fastChargerBrand = "Tesla",
        connectorType = "CCS",
        maxChargerPower = 150,
        maxChargerVoltage = 400,
        maxChargerCurrent = 375,
        chargerPhases = null,
        maxOutsideTemp = 18.0,
        minOutsideTemp = 14.0,
        chargePointCount = 60
    )
}
