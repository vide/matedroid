package com.matedroid.widget

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TripSummaryWidgetDateRangeTest {

    @Test
    fun lastDaysIncludesTodayAndPreviousDays() {
        val (startDate, endDate) = TripSummaryWidgetDateRange.lastDays(
            today = LocalDate.of(2026, 7, 8),
            days = 30
        )

        assertEquals("2026-06-09T00:00:00", startDate)
        assertEquals("2026-07-09T00:00:00", endDate)
    }

    @Test
    fun lastDaysSupportsShortAndLongWidgetRanges() {
        val today = LocalDate.of(2026, 7, 8)

        val (sevenDayStart, sevenDayEnd) = TripSummaryWidgetDateRange.lastDays(
            today = today,
            days = 7
        )
        val (ninetyDayStart, ninetyDayEnd) = TripSummaryWidgetDateRange.lastDays(
            today = today,
            days = 90
        )

        assertEquals("2026-07-02T00:00:00", sevenDayStart)
        assertEquals("2026-07-09T00:00:00", sevenDayEnd)
        assertEquals("2026-04-10T00:00:00", ninetyDayStart)
        assertEquals("2026-07-09T00:00:00", ninetyDayEnd)
    }

    @Test
    fun rangeFallsBackToDefaultWhenStoredPreferenceIsUnsupported() {
        assertEquals(TripSummaryWidgetRange.SevenDays, TripSummaryWidgetRange.fromDays(7))
        assertEquals(TripSummaryWidgetRange.ThirtyDays, TripSummaryWidgetRange.fromDays(30))
        assertEquals(TripSummaryWidgetRange.NinetyDays, TripSummaryWidgetRange.fromDays(90))
        assertEquals(TripSummaryWidgetRange.DEFAULT, TripSummaryWidgetRange.fromDays(42))
    }

    @Test
    fun lastDaysRejectsNonPositiveRanges() {
        assertThrows(IllegalArgumentException::class.java) {
            TripSummaryWidgetDateRange.lastDays(days = 0)
        }
    }
}
