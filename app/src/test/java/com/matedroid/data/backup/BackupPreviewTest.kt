package com.matedroid.data.backup

import com.matedroid.data.local.StatsDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the import dialog puts in front of the user before it changes anything: which cars
 * the file holds, and how much of it each one accounts for.
 */
class BackupPreviewTest {

    private val preview = BackupPreview(
        header = BackupHeader(databaseVersion = StatsDatabase.SCHEMA_VERSION),
        cars = listOf(
            BackupCar(carId = 1, vin = "5YJ3E1EA7KF000001", name = "Kitt"),
            BackupCar(carId = 2, vin = "7SAYGDEF9NF000002", name = "Bandit")
        ),
        tripsByCar = mapOf(1 to 12, 2 to 3),
        sentryByCar = mapOf(1 to 340),
        statsByCar = mapOf(1 to 9000, 2 to 1200),
        places = 1204,
        tripMaps = 56,
        hasSettings = true
    )

    @Test
    fun `counts follow the cars that are ticked`() {
        assertEquals(15, preview.countOf(BackupSection.TRIPS, setOf(1, 2)))
        assertEquals(12, preview.countOf(BackupSection.TRIPS, setOf(1)))
        assertEquals(0, preview.countOf(BackupSection.SENTRY, setOf(2)))
        assertEquals(10200, preview.countOf(BackupSection.STATS, setOf(1, 2)))
    }

    @Test
    fun `caches shared by every car ignore the car selection`() {
        assertEquals(1204, preview.countOf(BackupSection.PLACES, emptySet()))
        assertEquals(56, preview.countOf(BackupSection.TRIP_MAPS, emptySet()))
    }

    @Test
    fun `a car's own row counts everything the file holds for it`() {
        assertEquals(12 + 340 + 9000, preview.countOfCar(1))
        assertEquals(3 + 1200, preview.countOfCar(2))
        assertEquals(0, preview.countOfCar(99))
    }

    @Test
    fun `which sections are on offer does not shift as cars are ticked off`() {
        assertEquals(
            setOf(
                BackupSection.TRIPS,
                BackupSection.SETTINGS,
                BackupSection.SENTRY,
                BackupSection.PLACES,
                BackupSection.TRIP_MAPS,
                BackupSection.STATS
            ),
            preview.availableSections
        )
    }

    @Test
    fun `an empty section is not offered`() {
        val nothingButSettings = BackupPreview(
            header = BackupHeader(databaseVersion = StatsDatabase.SCHEMA_VERSION),
            hasSettings = true
        )

        assertEquals(setOf(BackupSection.SETTINGS), nothingButSettings.availableSections)
    }

    @Test
    fun `drives written against another database version are flagged and withheld`() {
        val fromAnotherBuild = preview.copy(statsReadable = false)

        assertTrue(fromAnotherBuild.hasUnreadableStats)
        assertFalse(BackupSection.STATS in fromAnotherBuild.availableSections)
        assertTrue(BackupSection.TRIPS in fromAnotherBuild.availableSections)
    }
}
