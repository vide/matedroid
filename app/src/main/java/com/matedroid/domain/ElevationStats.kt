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
