package com.matedroid.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.matedroid.ui.theme.CarColorPalette
import kotlin.math.max

/**
 * A thin horizontal "fingerprint" of a trip for the trips list.
 *
 * Non-interactive — renders each [TripTimelineSegment] as a colored rectangle with a tiny
 * 1 px gap between adjacent segments. Uses the same compression curve as the detail screen's
 * timeline bar so a 6 h AC overnight doesn't squash the DC spikes into slivers. Meant to sit
 * inline in a list row, not as a standalone card.
 */
@Composable
fun TripFingerprintStrip(
    segments: List<TripTimelineSegment>,
    palette: CarColorPalette,
    modifier: Modifier = Modifier
) {
    if (segments.isEmpty()) return

    val parkingColor = MaterialTheme.colorScheme.surfaceVariant
    val ratios = computeFingerprintRatios(segments)

    Canvas(
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .fillMaxSize()
    ) {
        val total = size.width
        val gapPx = 1.dp.toPx()
        var x = 0f
        ratios.forEachIndexed { idx, ratio ->
            val segWidth = ratio * total
            val isLast = idx == ratios.lastIndex
            val drawWidth = if (isLast) segWidth else (segWidth - gapPx).coerceAtLeast(0f)
            val color = fingerprintColorFor(segments[idx], palette, parkingColor)
            drawRect(
                color = color,
                topLeft = Offset(x, 0f),
                size = Size(drawWidth, size.height)
            )
            x += segWidth
        }
    }
}

private const val MIN_SEGMENT_RATIO = 0.02f

/** Uses the detail timeline's shared compression ([segmentWeight]) so silhouettes match between screens. */
private fun computeFingerprintRatios(segments: List<TripTimelineSegment>): List<Float> {
    if (segments.isEmpty()) return emptyList()
    val weights = segments.map { segmentWeight(it) }
    val total = weights.sum().coerceAtLeast(1f)
    val raw = weights.map { it / total }
    val effectiveMin = MIN_SEGMENT_RATIO.coerceAtMost(1f / segments.size)
    val lifted = raw.map { max(it, effectiveMin) }
    val liftedSum = lifted.sum()
    return lifted.map { it / liftedSum }
}

/** Alpha applied to every segment so the strip reads as a quiet accent, not a loud bar. */
private const val FINGERPRINT_ALPHA = 0.4f

private fun fingerprintColorFor(
    seg: TripTimelineSegment,
    palette: CarColorPalette,
    parkingColor: Color
): Color = colorForSegment(seg, palette, parkingColor).copy(alpha = FINGERPRINT_ALPHA)
