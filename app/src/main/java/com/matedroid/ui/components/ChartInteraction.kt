package com.matedroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Shared tap/scrub/tooltip interaction plumbing for the native canvas line charts
 * ([OptimizedLineChart], [DualAxisLineChart], [PowerSocOverlayChart]).
 *
 * Each chart keeps its own drawing and data mapping; the gesture handling, crosshair
 * fraction<->index math, cross-chart selection sync and tooltip positioning live here
 * so the three charts cannot drift apart.
 */

// ---------------------------------------------------------------------------
// Crosshair fraction <-> display-point index math
// ---------------------------------------------------------------------------

/** Snaps a 0..1 crosshair fraction to the nearest display-point index. */
internal fun fractionToIndex(fraction: Float, pointCount: Int): Int =
    (fraction * (pointCount - 1)).roundToInt().coerceIn(0, (pointCount - 1).coerceAtLeast(0))

/** The normalized 0..1 X fraction of a display-point index (index-snapped crosshair position). */
internal fun indexToFraction(index: Int, pointCount: Int): Float =
    if (pointCount > 1) index.toFloat() / (pointCount - 1) else 0f

/** X pixel position of a display-point index across [plotWidth]. */
internal fun indexToX(index: Int, pointCount: Int, plotWidth: Float): Float =
    index * (plotWidth / (pointCount - 1).coerceAtLeast(1))

// ---------------------------------------------------------------------------
// Selection state with cross-chart sync
// ---------------------------------------------------------------------------

/**
 * Holds a chart's own tap/scrub selection plus the "is the user actively scrubbing"
 * flag used to arbitrate between the local selection and an external (sibling-chart)
 * crosshair position.
 */
@Stable
internal class ChartSelectionState<T> {
    /** Point selected by tapping/scrubbing this chart, or null when dismissed. */
    var selected: T? by mutableStateOf(null)

    /** True while a horizontal scrub drag is in progress on this chart. */
    var isUserInteracting: Boolean by mutableStateOf(false)

    /**
     * The point to display: while the user is not interacting with this chart and a
     * sibling chart reports a crosshair fraction, the externally derived point wins;
     * otherwise this chart's own selection shows.
     */
    fun displayed(externalSelectedFraction: Float?, externalPoint: T?): T? =
        if (!isUserInteracting && externalSelectedFraction != null) externalPoint else selected
}

/**
 * Remembers a [ChartSelectionState] and clears the local selection when the sibling
 * chart dismisses its crosshair ([externalSelectedFraction] goes null) — but only when
 * cross-chart sync is active ([clearOnExternalDismiss]) and the user is not currently
 * scrubbing this chart.
 */
@Composable
internal fun <T> rememberChartSelectionState(
    externalSelectedFraction: Float?,
    clearOnExternalDismiss: Boolean,
): ChartSelectionState<T> {
    val state = remember { ChartSelectionState<T>() }
    LaunchedEffect(externalSelectedFraction) {
        if (externalSelectedFraction == null && clearOnExternalDismiss && !state.isUserInteracting) {
            state.selected = null
        }
    }
    return state
}

// ---------------------------------------------------------------------------
// Gesture handling
// ---------------------------------------------------------------------------

/**
 * Tap + horizontal-scrub gesture handling for a chart canvas.
 *
 * A tap reports its position via [onTap]; a horizontal drag scrubs via [onScrub].
 * Both report the normalized 0..1 fraction across the plot width (canvas width minus
 * [endInsetPx]) plus the raw Y pixel position. Only HORIZONTAL drags are claimed, so
 * vertical swipes fall through to the enclosing scroll container and the page still
 * scrolls.
 *
 * [keys] restart the pointer handlers exactly like [pointerInput] keys; [enabled] and
 * the callbacks are captured when the handlers (re)start, so derive them from the same
 * data as [keys].
 *
 * @param enabled When false no gestures are handled (e.g. not enough points).
 * @param endInsetPx Width reserved at the end of the canvas that is not part of the
 *   plot (e.g. a right-axis label strip).
 * @param activeHeightPx Taps and drag starts below this Y are ignored (e.g. the
 *   time-label strip under the plot). Unbounded by default.
 * @param scrubOnDragStart Whether the drag-start position itself already scrubs; when
 *   false only subsequent drag events do.
 * @param onScrubbingChange Reports true while a scrub drag is in progress.
 */
internal fun Modifier.chartScrubber(
    vararg keys: Any?,
    enabled: Boolean = true,
    endInsetPx: Float = 0f,
    activeHeightPx: Float = Float.POSITIVE_INFINITY,
    scrubOnDragStart: Boolean = true,
    onScrubbingChange: ((Boolean) -> Unit)? = null,
    onTap: (fraction: Float, y: Float) -> Unit,
    onScrub: (fraction: Float, y: Float) -> Unit,
): Modifier = this
    // Tap toggles/moves the crosshair at the nearest point.
    .pointerInput(keys = keys) {
        if (!enabled) return@pointerInput
        detectTapGestures { offset ->
            if (offset.y > activeHeightPx) return@detectTapGestures
            onTap(plotFraction(offset.x, endInsetPx), offset.y)
        }
    }
    // Only claim HORIZONTAL drags (scrubbing the crosshair); vertical swipes fall
    // through to the enclosing scroll container so the page still scrolls.
    .pointerInput(keys = keys) {
        if (!enabled) return@pointerInput
        detectHorizontalDragGestures(
            onDragStart = { offset ->
                if (offset.y <= activeHeightPx) {
                    onScrubbingChange?.invoke(true)
                    if (scrubOnDragStart) onScrub(plotFraction(offset.x, endInsetPx), offset.y)
                }
            },
            onDragEnd = { onScrubbingChange?.invoke(false) },
            onDragCancel = { onScrubbingChange?.invoke(false) },
            onHorizontalDrag = { change, _ ->
                onScrub(plotFraction(change.position.x, endInsetPx), change.position.y)
            }
        )
    }

private fun PointerInputScope.plotFraction(x: Float, endInsetPx: Float): Float {
    val plotWidth = (size.width - endInsetPx).coerceAtLeast(1f)
    return (x / plotWidth).coerceIn(0f, 1f)
}

// ---------------------------------------------------------------------------
// Tooltip
// ---------------------------------------------------------------------------

private val TooltipShape = RoundedCornerShape(8.dp)

/**
 * Theme-aware chart tooltip chip on the shared inverseSurface style, horizontally
 * centered on the crosshair and clamped so it stays fully on the canvas.
 *
 * Anchor and width are lambdas read inside the deferred offset block, so scrubbing
 * moves the tooltip without recomposing it.
 *
 * @param anchorX Crosshair X in container pixels; the tooltip centers on it.
 * @param containerWidthPx Container width used to clamp the tooltip on screen.
 * @param anchorY Selected point Y; the tooltip floats 24px above it (clamped to the
 *   top). Null pins the tooltip 4.dp from the top instead.
 * @param elevated True draws a 4.dp shadow; false just clips (no shadow).
 */
@Composable
internal fun ChartTooltip(
    anchorX: () -> Float,
    containerWidthPx: () -> Float,
    modifier: Modifier = Modifier,
    anchorY: (() -> Float)? = null,
    elevated: Boolean = true,
    verticalPadding: Dp = 6.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit
) {
    var tooltipSize by remember { mutableStateOf(IntSize.Zero) }
    val tooltipBg = MaterialTheme.colorScheme.inverseSurface
    Column(
        modifier = modifier
            .offset {
                val maxX = (containerWidthPx() - tooltipSize.width).coerceAtLeast(0f)
                val x = (anchorX() - tooltipSize.width / 2f).coerceIn(0f, maxX)
                val y = if (anchorY != null) {
                    (anchorY() - tooltipSize.height - 24f).coerceAtLeast(0f)
                } else {
                    4.dp.toPx()
                }
                IntOffset(x.roundToInt(), y.roundToInt())
            }
            .onSizeChanged { tooltipSize = it }
            .then(
                if (elevated) {
                    Modifier
                        .shadow(4.dp, TooltipShape)
                        .background(tooltipBg, TooltipShape)
                } else {
                    Modifier
                        .clip(TooltipShape)
                        .background(tooltipBg)
                }
            )
            .padding(horizontal = 10.dp, vertical = verticalPadding),
        verticalArrangement = verticalArrangement,
        content = content
    )
}
