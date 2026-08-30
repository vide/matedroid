package com.matedroid.data.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The demo dataset is a simulation, not a fixture, so what is worth testing is that it stays
 * *coherent*: the odometer only goes up, a drive picks up where the last one left off, and
 * nothing is dated in the future. Those are the properties every derived screen — mileage,
 * stats, battery — silently depends on.
 */
class DemoDataSetTest {

    private val zone: ZoneId = ZoneId.of("Europe/Madrid")
    private val anchor: Instant = OffsetDateTime.of(2026, 6, 15, 14, 30, 0, 0, java.time.ZoneOffset.ofHours(2))
        .toInstant()
    private val data = DemoDataSet(anchor, zone)

    // Oldest first, which is the order the simulation produced them in.
    private val chronological = data.drives.sortedBy { it.startDate }

    @Test
    fun `there is a year of history to look at`() {
        assertTrue("expected a few hundred drives, got ${data.drives.size}", data.drives.size > 150)
        assertTrue("expected a good number of charges, got ${data.charges(anchor).size}",
            data.charges(anchor).size > 40)
        assertTrue(data.updates.size >= 5)
    }

    @Test
    fun `nothing is dated in the future`() {
        data.drives.forEach { drive ->
            val end = OffsetDateTime.parse(drive.endDate).toInstant()
            assertTrue("drive ${drive.driveId} ends after now", end.isBefore(anchor))
        }
        data.charges(anchor).forEach { charge ->
            val end = OffsetDateTime.parse(charge.endDate).toInstant()
            assertTrue("charge ${charge.chargeId} ends after now", !end.isAfter(anchor))
        }
    }

    @Test
    fun `history spans two calendar years so the year filter has a choice`() {
        val years = data.drives.map { OffsetDateTime.parse(it.startDate).year }.toSet()
        assertEquals(setOf(2025, 2026), years)
    }

    @Test
    fun `the odometer only ever moves forward, and each drive resumes where the last stopped`() {
        chronological.zipWithNext { previous, next ->
            val previousEnd = previous.odometerDetails?.odometerEnd ?: 0.0
            val nextStart = next.odometerDetails?.odometerStart ?: 0.0
            assertEquals(
                "drive ${next.driveId} does not resume from drive ${previous.driveId}",
                previousEnd, nextStart, 0.05
            )
        }
    }

    @Test
    fun `every drive spends charge and stays inside a plausible battery range`() {
        data.drives.forEach { drive ->
            val start = drive.startBatteryLevel ?: 0
            val end = drive.endBatteryLevel ?: 0
            assertTrue("drive ${drive.driveId} gained charge", end < start)
            assertTrue("drive ${drive.driveId} battery out of range", end in 1..100 && start in 1..100)
            assertTrue("drive ${drive.driveId} has no distance", (drive.distance ?: 0.0) > 0.0)
            assertTrue("drive ${drive.driveId} consumption implausible",
                (drive.consumptionNet ?: 0.0) in 100.0..320.0)
        }
    }

    @Test
    fun `every charge adds energy and its cost follows from it`() {
        data.charges(anchor).forEach { charge ->
            val start = charge.startBatteryLevel ?: 0
            val end = charge.endBatteryLevel ?: 0
            assertTrue("charge ${charge.chargeId} did not add charge", end > start)
            val added = charge.chargeEnergyAdded ?: 0.0
            val used = charge.chargeEnergyUsed ?: 0.0
            assertTrue("charge ${charge.chargeId} added no energy", added > 0.0)
            assertTrue("charge ${charge.chargeId} used less than it added", used >= added)
            assertTrue("charge ${charge.chargeId} has a negative cost", (charge.cost ?: 0.0) >= 0.0)
            assertTrue("charge ${charge.chargeId} takes no time", (charge.durationMin ?: 0) > 0)
        }
    }

    @Test
    fun `the car leaves the country, so Visited Countries has something to show`() {
        val addresses = data.drives.mapNotNull { it.startAddress }.toSet() +
            data.drives.mapNotNull { it.endAddress }.toSet()
        assertTrue("no French leg", addresses.any { it.contains("Perpignan") })
        assertTrue("no Andorran leg", addresses.any { it.contains("Andorra") })
    }

    @Test
    fun `both AC and DC charging appear in the history`() {
        val addresses = data.charges(anchor).mapNotNull { it.address }.toSet()
        assertTrue("no home AC charging", addresses.any { it == "Home" })
        assertTrue("no Supercharger sessions", addresses.any { it.contains("Supercharger") })
    }

    @Test
    fun `a drive detail traces the route it claims to`() {
        val drive = data.drives.first { (it.distance ?: 0.0) > 50 }
        val detail = data.driveDetail(drive.driveId)
        assertNotNull(detail)
        val positions = detail!!.positions.orEmpty()
        assertTrue("too few positions to draw", positions.size > 20)

        // The trace must cover the whole distance the summary reports, or the map line
        // stops short of the destination.
        val traced = positions.zipWithNext { a, b ->
            haversineKm(
                DemoPoint(a.latitude!!, a.longitude!!, 0),
                DemoPoint(b.latitude!!, b.longitude!!, 0)
            )
        }.sum()
        assertEquals("traced route does not match reported distance", drive.distance!!, traced, 1.0)

        assertEquals(drive.startBatteryLevel, positions.first().batteryLevel)
        assertEquals(drive.endBatteryLevel, positions.last().batteryLevel)
        assertTrue("no regen anywhere in the trace", positions.any { (it.power ?: 0) < 0 })
    }

    @Test
    fun `a charge detail tapers and lands on the level the summary reports`() {
        val dc = data.charges(anchor).first { it.address.orEmpty().contains("Supercharger") }
        val detail = data.chargeDetail(dc.chargeId, anchor)
        assertNotNull(detail)
        val points = detail!!.chargePoints.orEmpty()
        assertTrue(points.size >= 12)
        assertEquals(dc.startBatteryLevel, points.first().batteryLevel)
        assertEquals(dc.endBatteryLevel, points.last().batteryLevel)
        assertTrue("DC power should taper as the battery fills",
            points.first().chargerPower!! > points.last().chargerPower!!)
        assertTrue("DC session should report itself as fast charging",
            points.all { it.chargerDetails?.chargerPhases == 0 })
    }

    @Test
    fun `the live session charges for part of the cycle and rests for the other`() {
        val charging = instantWithCyclePhase(1_000)
        val resting = instantWithCyclePhase(12_000)

        val active = DemoDataSet(anchor, zone).currentCharge(charging)
        assertNotNull("expected a charge in progress", active)
        assertTrue(active!!.isCharging == true)
        assertNotNull("a live charge reports its current level", active.currentBatteryLevel)

        assertNull("expected no charge in progress", DemoDataSet(anchor, zone).currentCharge(resting))
    }

    @Test
    fun `status agrees with the live session`() {
        val charging = instantWithCyclePhase(4_000)
        val chargingStatus = data.status(charging)
        assertEquals("charging", chargingStatus.state)
        assertTrue(chargingStatus.isCharging)
        assertEquals(true, chargingStatus.pluggedIn)
        assertTrue((chargingStatus.chargerPower ?: 0) > 0)
        assertEquals(3, chargingStatus.acPhases)

        val restingStatus = data.status(instantWithCyclePhase(13_000))
        assertEquals("asleep", restingStatus.state)
        assertEquals(false, restingStatus.pluggedIn)
        assertEquals(80, restingStatus.batteryLevel)
    }

    @Test
    fun `a finished live session joins the charge list exactly once`() {
        val resting = instantWithCyclePhase(13_000)
        val charges = data.charges(resting)
        assertEquals(charges.map { it.chargeId }.distinct().size, charges.size)

        val charging = instantWithCyclePhase(2_000)
        val liveId = data.currentCharge(charging)!!.chargeId
        assertTrue("an in-progress charge must not also be listed",
            data.charges(charging).none { it.chargeId == liveId })
    }

    @Test
    fun `software versions are named for the year they were installed`() {
        data.updates.forEach { update ->
            val year = OffsetDateTime.parse(update.startDate).year
            assertTrue(
                "version ${update.version} does not belong to $year",
                update.version!!.startsWith("$year.")
            )
        }
        val installs = data.updates.map { OffsetDateTime.parse(it.startDate).toInstant() }
        assertEquals("updates should be newest first", installs.sortedDescending(), installs)
    }

    @Test
    fun `the dataset is metric and says so`() {
        assertEquals("km", data.units.unitOfLength)
        assertEquals("bar", data.units.unitOfPressure)
        assertEquals("C", data.units.unitOfTemperature)
        assertTrue(data.units.isMetric)
    }

    /** An instant whose position in the live-charge cycle is exactly [phaseSeconds]. */
    private fun instantWithCyclePhase(phaseSeconds: Long): Instant {
        val cycle = 4 * 60 * 60L
        val base = anchor.truncatedTo(ChronoUnit.DAYS).epochSecond
        return Instant.ofEpochSecond(base - Math.floorMod(base, cycle) + phaseSeconds)
    }
}
