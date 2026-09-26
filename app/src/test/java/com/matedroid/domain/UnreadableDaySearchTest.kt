package com.matedroid.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UnreadableDaySearchTest {

    private fun day(s: String) = LocalDate.parse(s)

    /** Mimics TeslamateAPI: a period fails when it holds a whole bad entry (start >= from, end <= to). */
    private class FakeServer(private val badEntries: List<Pair<LocalDate, LocalDate>>) {
        var requests = 0
        suspend fun fails(from: LocalDate, to: LocalDate): Boolean {
            requests++
            return badEntries.any { (start, end) -> start >= from && end <= to }
        }
    }

    @Test
    fun `finds the day of a bad entry inside one day`() = runTest {
        val server = FakeServer(listOf(day("2026-06-19") to day("2026-06-19")))

        val found = UnreadableDaySearch.find(UnreadableDaySearch.EARLIEST, day("2026-09-26"), server::fails)

        assertEquals(day("2026-06-19"), found)
        // ~log2(5400 days) halvings, each at most two requests, plus the initial check.
        assertTrue("took ${server.requests} requests", server.requests <= 30)
    }

    @Test
    fun `finds an entry that runs past midnight across a split point`() = runTest {
        // 2026-01-01..2026-01-04: mid is 01-02, so an entry from 01-02 to 01-03 is in neither half.
        val server = FakeServer(listOf(day("2026-01-02") to day("2026-01-03")))

        val found = UnreadableDaySearch.find(day("2026-01-01"), day("2026-01-04"), server::fails)

        assertEquals(day("2026-01-02"), found)
    }

    @Test
    fun `finds an overnight entry wherever it falls in a long period`() = runTest {
        val server = FakeServer(listOf(day("2026-06-19") to day("2026-06-20")))

        val found = UnreadableDaySearch.find(day("2025-09-27"), day("2026-09-26"), server::fails)

        assertEquals(day("2026-06-19"), found)
    }

    @Test
    fun `a period that loads has nothing to find`() = runTest {
        val server = FakeServer(listOf(day("2026-06-19") to day("2026-06-19")))

        assertNull(UnreadableDaySearch.find(day("2025-01-01"), day("2025-12-31"), server::fails))
        assertEquals(1, server.requests)
    }

    @Test
    fun `any other failure aborts the search`() = runTest {
        var calls = 0
        val found = UnreadableDaySearch.find(day("2026-01-01"), day("2026-12-31")) { _, _ ->
            if (calls++ == 0) true else null
        }

        assertNull(found)
        assertEquals(2, calls)
    }
}
