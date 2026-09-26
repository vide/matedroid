package com.matedroid.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Finds the day holding an entry TeslamateAPI can't read.
 *
 * TeslamateAPI fails a whole list query when one row has a NULL in a column it scans into a
 * non-nullable type (seen with hand-entered charges that have no range or temperature), and it
 * says only "Unable to load charges." — no ID, no date. Halving the period and asking again
 * names the day in ~log2(days) requests, so the user knows which entry to fix in TeslaMate.
 *
 * The API filters on `start >= from AND end <= to`, so an entry running across the split point
 * belongs to neither half. When both halves load, that is the entry: it starts on the last day
 * of the first half.
 */
object UnreadableDaySearch {

    /** Lower bound for "All time": nothing TeslaMate logs can predate the first Model S. */
    val EARLIEST: LocalDate = LocalDate.of(2012, 1, 1)

    /**
     * @param fails asks whether [from]..[to] (whole days) is rejected: true = rejected,
     *   false = loads, null = the request failed for another reason, which aborts the search.
     * @return the day, or null when the search was aborted or [from]..[to] doesn't fail.
     */
    suspend fun find(
        from: LocalDate,
        to: LocalDate,
        fails: suspend (from: LocalDate, to: LocalDate) -> Boolean?
    ): LocalDate? {
        if (fails(from, to) != true) return null
        var lo = from
        var hi = to
        while (lo < hi) {
            val mid = lo.plusDays(ChronoUnit.DAYS.between(lo, hi) / 2)
            when (fails(lo, mid)) {
                true -> hi = mid
                false -> when (fails(mid.plusDays(1), hi)) {
                    true -> lo = mid.plusDays(1)
                    false -> return mid
                    null -> return null
                }
                null -> return null
            }
        }
        return lo
    }
}
