package com.matedroid.domain

/**
 * Single source of truth for the cumulative climb / descent of a drive.
 *
 * TeslaMate logs positions at roughly 3 Hz and stores elevation as whole metres, so summing
 * every sample-to-sample delta also sums the GPS jitter between them. On real drives that
 * overstates the climb by 20% or more, and the error grows with the duration of the drive
 * rather than with the terrain. Deltas are therefore measured against a moving anchor and only
 * counted once they exceed [THRESHOLD_M] in one direction, which is the usual hysteresis filter
 * for altitude series; anything smaller is treated as noise and left out of both totals.
 *
 * Climb and descent are cumulative, NOT a net figure: a drive that ends lower than it started
 * still reports the metres it climbed along the way, so both totals can be non-zero at once.
 * The net change is simply end elevation minus start elevation and is computed by the caller.
 *
 * Elevation is handled in whatever unit the samples arrive in (metres for TeslamateAPI) and is
 * never converted here — display conversion belongs to
 * [com.matedroid.domain.model.UnitFormatter.formatElevation].
 */
object ElevationStats {

    /** Minimum sustained change before it counts as real climb or descent, in metres. */
    const val THRESHOLD_M = 5

    /**
     * Share of a drive that must carry elevation samples before its elevation figures can be
     * read as describing the whole drive. Only polled positions carry an elevation, streaming
     * ones don't, so a drive whose polling dropped out can end up with elevation for a small
     * slice of its route and nothing for the rest. Every figure derived from that slice (climb,
     * descent, net, min, max, the profile chart) then describes the slice while looking like it
     * describes the drive: one real 92 min descent of ~910 m carried elevation for its first
     * 11 min only and reported a 110 m descent. Below this share the figures are still shown,
     * but every surface showing them has to say which share of the drive they cover.
     */
    const val MIN_COVERAGE = 0.8

    /**
     * Share of [driveStartMs]..[driveEndMs] covered by elevation samples, 0..1.
     *
     * Gaps at the head, in the middle and at the tail all count against coverage, so a drive
     * that loses elevation halfway is caught as surely as one that never had any. Gaps shorter
     * than [toleranceMs] are the normal spacing between polled positions, not a dropout.
     *
     * This is deliberately measured in time rather than in "share of positions that have an
     * elevation": position density varies a lot within a drive (city crawling logs far more
     * points per minute than a motorway), so a count would call the example above 61% covered
     * when it actually describes 12% of the drive.
     *
     * Only the drive detail screen applies this. The sync path deliberately does not: it would
     * have to parse a timestamp for every position of every drive in the history, and the
     * records it feeds ("Most Climbing", "Highest Point") can only ever be *under*-reported by
     * a partially covered drive, never inflated, so such a drive loses those records anyway.
     */
    fun coverage(
        sampleTimesMs: List<Long>,
        driveStartMs: Long,
        driveEndMs: Long,
        toleranceMs: Long = COVERAGE_TOLERANCE_MS
    ): Double {
        val span = driveEndMs - driveStartMs
        if (span <= 0L) return if (sampleTimesMs.isEmpty()) 0.0 else 1.0
        if (sampleTimesMs.isEmpty()) return 0.0

        var uncovered = 0L
        val head = sampleTimesMs.first() - driveStartMs
        if (head > toleranceMs) uncovered += head
        val tail = driveEndMs - sampleTimesMs.last()
        if (tail > toleranceMs) uncovered += tail
        for (i in 1 until sampleTimesMs.size) {
            val gap = sampleTimesMs[i] - sampleTimesMs[i - 1]
            if (gap > toleranceMs) uncovered += gap
        }

        return ((span - uncovered).toDouble() / span).coerceIn(0.0, 1.0)
    }

    /** A gap between elevation samples longer than this is a dropout, not normal spacing. */
    private const val COVERAGE_TOLERANCE_MS = 60_000L

    /** Cumulative metres climbed and metres descended over a series of samples. */
    data class Change(val climb: Int, val descent: Int)

    /** Climb / descent over an already-collected list of elevation samples, in order. */
    fun of(elevations: List<Int>): Change {
        val accumulator = Accumulator()
        elevations.forEach(accumulator::add)
        return accumulator.change
    }

    /**
     * Incremental form of [of], for callers that already walk their positions once to compute
     * other stats and should not walk them a second time.
     */
    class Accumulator(private val thresholdM: Int = THRESHOLD_M) {
        private var anchor: Int? = null
        private var climb = 0
        private var descent = 0

        fun add(elevation: Int) {
            val previous = anchor
            if (previous == null) {
                anchor = elevation
                return
            }
            val diff = elevation - previous
            when {
                diff >= thresholdM -> {
                    climb += diff
                    anchor = elevation
                }
                diff <= -thresholdM -> {
                    descent += -diff
                    anchor = elevation
                }
            }
        }

        val change: Change get() = Change(climb, descent)
    }
}
