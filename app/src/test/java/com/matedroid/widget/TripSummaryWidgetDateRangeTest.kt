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
    fun lastDaysRejectsNonPositiveRanges() {
        assertThrows(IllegalArgumentException::class.java) {
            TripSummaryWidgetDateRange.lastDays(days = 0)
        }
    }
}
