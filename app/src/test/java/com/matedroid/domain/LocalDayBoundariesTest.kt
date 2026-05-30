package com.matedroid.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LocalDayBoundariesTest {

    private val date = LocalDate.of(2026, 5, 30)

    @Test
    fun `start and end of day carry a positive offset ahead of UTC`() {
        val madrid = ZoneId.of("Europe/Madrid") // CEST = +02:00 in summer
        assertEquals("2026-05-30T00:00:00+02:00", LocalDayBoundaries.startOfDay(date, madrid))
        assertEquals("2026-05-30T23:59:59+02:00", LocalDayBoundaries.endOfDay(date, madrid))
    }

    @Test
    fun `start and end of day carry a negative offset behind UTC`() {
        val toronto = ZoneId.of("America/Toronto") // EDT = -04:00 in summer
        assertEquals("2026-05-30T00:00:00-04:00", LocalDayBoundaries.startOfDay(date, toronto))
        assertEquals("2026-05-30T23:59:59-04:00", LocalDayBoundaries.endOfDay(date, toronto))
    }

    @Test
    fun `UTC renders as Z`() {
        val utc = ZoneId.of("UTC")
        assertEquals("2026-05-30T00:00:00Z", LocalDayBoundaries.startOfDay(date, utc))
        assertEquals("2026-05-30T23:59:59Z", LocalDayBoundaries.endOfDay(date, utc))
    }

    @Test
    fun `spring-forward DST day uses the pre-transition offset at start and post at end`() {
        // Europe/Madrid sprang forward on 2026-03-29 (02:00 -> 03:00): +01:00 before, +02:00 after.
        val madrid = ZoneId.of("Europe/Madrid")
        val dstDay = LocalDate.of(2026, 3, 29)
        assertEquals("2026-03-29T00:00:00+01:00", LocalDayBoundaries.startOfDay(dstDay, madrid))
        assertEquals("2026-03-29T23:59:59+02:00", LocalDayBoundaries.endOfDay(dstDay, madrid))
    }
}
