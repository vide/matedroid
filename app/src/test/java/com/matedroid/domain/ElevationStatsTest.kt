package com.matedroid.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ElevationStatsTest {

    @Test
    fun emptyOrSingleSample_hasNoChange() {
        assertEquals(ElevationStats.Change(0, 0), ElevationStats.of(emptyList()))
        assertEquals(ElevationStats.Change(0, 0), ElevationStats.of(listOf(100)))
    }

    @Test
    fun steadyClimb_countsTheWholeAscent() {
        // 100 -> 200 in 10 m steps, each step above the threshold.
        val elevations = (100..200 step 10).toList()
        assertEquals(ElevationStats.Change(100, 0), ElevationStats.of(elevations))
    }

    @Test
    fun steadyDescent_countsTheWholeDescent() {
        val elevations = (200 downTo 100 step 10).toList()
        assertEquals(ElevationStats.Change(0, 100), ElevationStats.of(elevations))
    }

    @Test
    fun jitterBelowThreshold_isNotCounted() {
        // GPS noise around a flat road: nothing here is a real climb or descent.
        val elevations = listOf(100, 101, 99, 102, 98, 101, 100, 102, 99, 100)
        assertEquals(ElevationStats.Change(0, 0), ElevationStats.of(elevations))
    }

    @Test
    fun jitterOnTopOfARealClimb_doesNotInflateIt() {
        // Climb from 100 to 150 in 1 m steps with +/-1 m of noise on each sample. Summing every
        // sample-to-sample delta reports 75 m for this 50 m climb; the anchor counts 48, the
        // real climb minus the sub-threshold remainder still pending at the last sample.
        val elevations = mutableListOf<Int>()
        for (i in 0..50) {
            elevations += 100 + i + (if (i % 2 == 0) 1 else -1)
        }
        val change = ElevationStats.of(elevations)
        assertEquals(48, change.climb)
        assertEquals(0, change.descent)
    }

    @Test
    fun netDownhillDrive_stillReportsTheClimbAlongTheWay() {
        // Down 100, up 60, down 40: ends 80 m below the start but climbs 60 m in the middle.
        val elevations = listOf(300, 250, 200, 230, 260, 240, 220)
        val change = ElevationStats.of(elevations)
        assertEquals(60, change.climb)
        assertEquals(140, change.descent)
        assertEquals(-80, elevations.last() - elevations.first())
    }

    @Test
    fun reversalsSmallerThanTheThreshold_doNotBreakARun() {
        // A 2 m dip mid-climb is noise, so the climb is counted end to end.
        val elevations = listOf(100, 110, 108, 120, 130)
        assertEquals(ElevationStats.Change(30, 0), ElevationStats.of(elevations))
    }

    @Test
    fun accumulator_matchesTheListForm() {
        val elevations = listOf(100, 103, 99, 115, 112, 140, 135, 90, 95, 130)
        val accumulator = ElevationStats.Accumulator()
        elevations.forEach(accumulator::add)
        assertEquals(ElevationStats.of(elevations), accumulator.change)
    }
}
