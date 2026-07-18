package com.matedroid.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CostSummaryWidgetDateRangeTest {

    @Test
    fun `last 7 days covers today through six days back`() {
        val today = LocalDate.of(2026, 7, 18)
        val range = CostSummaryWidgetDateRange.lastDays(today = today, days = 7)

        assertTrue(range.startDate.startsWith("2026-07-12T00:00:00"))
        assertTrue(range.endDate.startsWith("2026-07-19T00:00:00"))
    }

    @Test
    fun `last 30 days matches default range`() {
        val today = LocalDate.of(2026, 7, 18)
        val range = CostSummaryWidgetDateRange.lastDays(today = today, days = 30)

        assertEquals("2026-06-19T00:00:00", range.startDate)
        assertEquals("2026-07-19T00:00:00", range.endDate)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero days is rejected`() {
        CostSummaryWidgetDateRange.lastDays(days = 0)
    }
}
