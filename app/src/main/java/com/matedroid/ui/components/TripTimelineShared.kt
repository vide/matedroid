package com.matedroid.ui.components

import androidx.compose.ui.graphics.Color
import com.matedroid.ui.theme.CarColorPalette
import kotlin.math.sqrt

/**
 * Duration compression and color mapping shared by [TripTimeline] (detail screen) and
 * [TripFingerprintStrip] (trips list), so both render the same silhouette for a trip.
 *
 * "Idle" segments (parking gaps and AC charges) share the same compression curve:
 * linear up to 30 min, sqrt-compressed beyond, scaled 0.5× so multi-hour/day stretches
 * stay visible without crushing the drives. DC sessions are left linear since they're
 * naturally bounded (~1h) and their duration is useful signal.
 */
private const val IDLE_LINEAR_THRESHOLD_MIN = 30f
private const val IDLE_COMPRESSION_SCALE = 0.5f

/** Linear up to 30min, then sqrt-compressed and scaled down. Used for both parking gaps and AC charges. */
internal fun compressIdle(durationMin: Int): Float {
    val d = durationMin.toFloat().coerceAtLeast(1f)
    return if (d <= IDLE_LINEAR_THRESHOLD_MIN) d
    else IDLE_LINEAR_THRESHOLD_MIN +
            sqrt((d - IDLE_LINEAR_THRESHOLD_MIN) * IDLE_LINEAR_THRESHOLD_MIN) * IDLE_COMPRESSION_SCALE
}

/** Visual weight for one segment — drives and DC charges are honest, AC and parking are sqrt-compressed. */
internal fun segmentWeight(seg: TripTimelineSegment): Float = when (seg) {
    is TripTimelineSegment.Parking -> compressIdle(seg.durationMin)
    is TripTimelineSegment.Charge ->
        if (seg.isDc) seg.durationMin.toFloat().coerceAtLeast(1f)
        else compressIdle(seg.durationMin)
    is TripTimelineSegment.Drive -> seg.durationMin.toFloat().coerceAtLeast(1f)
}

internal fun colorForSegment(
    seg: TripTimelineSegment,
    palette: CarColorPalette,
    parkingColor: Color
): Color = when (seg) {
    is TripTimelineSegment.Drive -> palette.accent
    is TripTimelineSegment.Charge -> if (seg.isDc) palette.dcColor else palette.acColor
    is TripTimelineSegment.Parking -> parkingColor
}
