package com.matedroid.data.backup

import com.matedroid.data.local.entity.SavedTripLeg
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide where a restored trip points, which is the one place a backup can
 * do real damage: a leg aimed at the wrong drive would quietly rewrite a trip's history.
 */
class BackupMatchingTest {

    private val drive = SavedTripLeg.TYPE_DRIVE

    private fun leg(id: Int, startDate: String? = "2026-08-01 09:00:00", position: Int = 0) =
        BackupTripLeg(type = drive, id = id, position = position, startDate = startDate)

    @Test
    fun `an id that still starts at the same moment is kept`() = runTest {
        val resolver = BackupLegResolver(
            FakeLegIndex(listOf(Row(1, drive, 12, "2026-08-01 09:00:00")))
        )

        assertEquals(LegResolution.Kept(12), resolver.resolve(1, leg(12)))
    }

    @Test
    fun `an unsynced phone takes the file at its word`() = runTest {
        val resolver = BackupLegResolver(FakeLegIndex(emptyList()))

        assertEquals(LegResolution.Kept(12), resolver.resolve(1, leg(12)))
    }

    @Test
    fun `a drive whose id moved is found again by its start time`() = runTest {
        val resolver = BackupLegResolver(
            FakeLegIndex(
                listOf(
                    Row(1, drive, 12, "2026-01-01 00:00:00"),
                    Row(1, drive, 908, "2026-08-01 09:00:00")
                )
            )
        )

        assertEquals(LegResolution.Remapped(908), resolver.resolve(1, leg(12)))
    }

    @Test
    fun `an id this phone has not synced yet is left pointing where it was`() = runTest {
        val resolver = BackupLegResolver(
            FakeLegIndex(listOf(Row(1, drive, 5, "2026-07-01 08:00:00")))
        )

        assertEquals(LegResolution.Pending(12), resolver.resolve(1, leg(12)))
    }

    @Test
    fun `an id belonging to a different drive is dropped rather than guessed at`() = runTest {
        val resolver = BackupLegResolver(
            FakeLegIndex(listOf(Row(1, drive, 12, "2019-05-05 05:00:00")))
        )

        assertEquals(LegResolution.Dropped, resolver.resolve(1, leg(12)))
    }

    @Test
    fun `a leg with no start time can only be taken on trust`() = runTest {
        val resolver = BackupLegResolver(
            FakeLegIndex(listOf(Row(1, drive, 12, "2019-05-05 05:00:00")))
        )

        assertEquals(LegResolution.Kept(12), resolver.resolve(1, leg(12, startDate = null)))
    }

    @Test
    fun `another car's drives are not consulted`() = runTest {
        val resolver = BackupLegResolver(
            FakeLegIndex(listOf(Row(2, drive, 12, "2026-08-01 09:00:00")))
        )

        assertEquals(LegResolution.Kept(12), resolver.resolve(1, leg(12)))
    }

    @Test
    fun `cars are matched by VIN, whatever their ids are`() {
        val map = BackupCarMap.of(
            backupCars = listOf(
                BackupCar(carId = 1, vin = "5yj3e1ea7kf000001"),
                BackupCar(carId = 2, vin = "5YJ3E1EA7KF000002")
            ),
            localCars = listOf(
                BackupCar(carId = 7, vin = "5YJ3E1EA7KF000001"),
                BackupCar(carId = 8, vin = "5YJ3E1EA7KF000002")
            )
        )

        assertTrue(map.isRemapping)
        assertEquals(7, map[1])
        assertEquals(8, map[2])
    }

    @Test
    fun `a car the server does not know keeps the id it arrived with`() {
        val map = BackupCarMap.of(
            backupCars = listOf(BackupCar(carId = 3, vin = "5YJ3E1EA7KF000003")),
            localCars = listOf(BackupCar(carId = 7, vin = "5YJ3E1EA7KF000001"))
        )

        assertEquals(3, map[3])
    }

    @Test
    fun `without VINs on both sides the ids are left alone`() {
        val noVins = BackupCarMap.of(
            backupCars = listOf(BackupCar(carId = 1)),
            localCars = listOf(BackupCar(carId = 9))
        )
        val noCars = BackupCarMap.of(emptyList(), emptyList())

        assertFalse(noVins.isRemapping)
        assertEquals(1, noVins[1])
        assertEquals(1, noCars[1])
    }

    @Test
    fun `the same drives in a different order are the same trip`() {
        val one = tripSignature(1, listOf(savedLeg(drive, 3, 0), savedLeg("CHARGE", 9, 1)))
        val other = tripSignature(1, listOf(savedLeg("CHARGE", 9, 0), savedLeg(drive, 3, 1)))

        assertEquals(one, other)
    }

    @Test
    fun `the same drives on another car are another trip`() {
        val first = tripSignature(1, listOf(savedLeg(drive, 3, 0)))
        val second = tripSignature(2, listOf(savedLeg(drive, 3, 0)))

        assertNotEquals(first, second)
    }

    private fun savedLeg(type: String, id: Int, position: Int) =
        SavedTripLeg(tripId = 0, position = position, legType = type, legId = id)

    private data class Row(
        val carId: Int,
        val type: String,
        val id: Int,
        val startDate: String
    )

    private class FakeLegIndex(private val rows: List<Row>) : LegIndex {
        override suspend fun startDateOf(carId: Int, legType: String, legId: Int): String? =
            rows.firstOrNull { it.carId == carId && it.type == legType && it.id == legId }
                ?.startDate

        override suspend fun idAtStartDate(
            carId: Int,
            legType: String,
            startDate: String
        ): Int? = rows
            .firstOrNull { it.carId == carId && it.type == legType && it.startDate == startDate }
            ?.id

        override suspend fun count(carId: Int, legType: String): Int =
            rows.count { it.carId == carId && it.type == legType }
    }
}
