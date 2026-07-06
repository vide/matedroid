package com.matedroid.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * An optimized line chart component with premium visuals:
 * - Smooth cubic Bezier curves (monotone interpolation, no overshoot)
 * - Gradient fill under the line
 * - Dashed grid lines
 * - Vertical crosshair on interaction
 * - Glowing data point indicator
 * - Animated line drawing entrance
 * - Theme-aware tooltip
 *
 * @param externalSelectedFraction When provided (0.0-1.0), shows a tooltip at the corresponding
 *   X position. Used for cross-chart synchronization when user is interacting with a sibling chart.
 * @param onXSelected Called with the normalized X fraction (0.0-1.0) when the user interacts
 *   with this chart, or null when the tooltip is dismissed.
 */
@Composable
fun OptimizedLineChart(
    data: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    unit: String = "",
    showZeroLine: Boolean = false,
    fixedMinMax: Pair<Float, Float>? = null,
    timeLabels: List<String> = emptyList(),
    convertValue: (Float) -> Float = { it },
    chartHeight: Dp = 120.dp,
    externalSelectedFraction: Float? = null,
    onXSelected: ((Float?) -> Unit)? = null,
    fractionToTimeLabel: ((Float) -> String)? = null,
    annotationRanges: List<AnnotationRange> = emptyList()
) {
    if (data.size < 2) return

    val surfaceColor = MaterialTheme.colorScheme.onSurface
    // Built once and reused across draws (the 800ms entrance redraws every frame).
    val labelPaint = remember(surfaceColor) {
        android.graphics.Paint().apply {
            this.color = surfaceColor.copy(alpha = 0.7f).toArgb()
            textSize = 26f
            isAntiAlias = true
        }
    }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val tooltipBg = MaterialTheme.colorScheme.inverseSurface
    val tooltipFg = MaterialTheme.colorScheme.inverseOnSurface

    val chartData = remember(data, fixedMinMax, convertValue) {
        prepareChartData(data, fixedMinMax, convertValue)
    }

    // Pre-compute the smooth path and fill path
    val density = LocalDensity.current
    val chartHeightPx = with(density) { chartHeight.toPx() }
    var canvasWidthPx by remember { mutableStateOf(0f) }

    val smoothPath = remember(chartData, canvasWidthPx) {
        if (canvasWidthPx <= 0f) return@remember null
        createSmoothPath(chartData.displayPoints, canvasWidthPx, chartHeightPx, chartData.minValue, chartData.range)
    }
    val fillPath = remember(smoothPath, canvasWidthPx) {
        smoothPath?.let { createFillPath(it, canvasWidthPx, chartHeightPx) }
    }

    // Entrance animation
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(chartData) {
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }

    var selectedPoint by remember { mutableStateOf<SelectedPoint?>(null) }
    var isUserInteracting by remember { mutableStateOf(false) }

    val timeLabelHeightDp = if (timeLabels.isNotEmpty()) 20.dp else 0.dp
    val timeLabelHeightPx = with(density) { timeLabelHeightDp.toPx() }
    val totalHeightDp = chartHeight + timeLabelHeightDp

    val externalPoint: SelectedPoint? = remember(externalSelectedFraction, chartData, canvasWidthPx) {
        if (externalSelectedFraction == null || canvasWidthPx == 0f) return@remember null
        val points = chartData.displayPoints
        if (points.isEmpty()) return@remember null
        val index = (externalSelectedFraction * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)
        val stepX = canvasWidthPx / (points.size - 1).coerceAtLeast(1)
        val pointX = index * stepX
        val pointY = chartHeightPx * (1 - (points[index] - chartData.minValue) / chartData.range)
        SelectedPoint(index, points[index], Offset(pointX, pointY))
    }

    LaunchedEffect(externalSelectedFraction) {
        if (externalSelectedFraction == null && onXSelected != null && !isUserInteracting) {
            selectedPoint = null
        }
    }

    val displayedPoint = if (!isUserInteracting && externalSelectedFraction != null) {
        externalPoint
    } else {
        selectedPoint
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeightDp)
                .onSizeChanged { canvasWidthPx = it.width.toFloat() }
                // Tap toggles the tooltip at the nearest point.
                .pointerInput(chartData) {
                    val points = chartData.displayPoints
                    if (points.isEmpty()) return@pointerInput
                    detectTapGestures { offset ->
                        val width = size.width.toFloat()
                        val stepX = width / (points.size - 1).coerceAtLeast(1)
                        val index = (offset.x / stepX).roundToInt().coerceIn(0, points.lastIndex)
                        if (selectedPoint?.index == index) {
                            selectedPoint = null
                            onXSelected?.invoke(null)
                        } else {
                            val fraction = if (points.size > 1) index.toFloat() / (points.size - 1) else 0f
                            val pointX = index * stepX
                            val pointY = chartHeightPx * (1 - (points[index] - chartData.minValue) / chartData.range)
                            selectedPoint = SelectedPoint(index, points[index], Offset(pointX, pointY))
                            onXSelected?.invoke(fraction)
                        }
                    }
                }
                // Only claim HORIZONTAL drags (scrubbing the crosshair); vertical swipes fall
                // through to the enclosing scroll container so the page still scrolls.
                .pointerInput(chartData) {
                    val points = chartData.displayPoints
                    if (points.isEmpty()) return@pointerInput

                    fun updateSelection(xOffset: Float) {
                        val width = size.width.toFloat()
                        val stepX = width / (points.size - 1).coerceAtLeast(1)
                        val index = ((xOffset / stepX).roundToInt()).coerceIn(0, points.lastIndex)
                        val fraction = if (points.size > 1) index.toFloat() / (points.size - 1) else 0f
                        val pointX = index * stepX
                        val pointY = chartHeightPx * (1 - (points[index] - chartData.minValue) / chartData.range)
                        selectedPoint = SelectedPoint(index, points[index], Offset(pointX, pointY))
                        onXSelected?.invoke(fraction)
                    }

                    detectHorizontalDragGestures(
                        onDragStart = { offset -> isUserInteracting = true; updateSelection(offset.x) },
                        onDragEnd = { isUserInteracting = false },
                        onDragCancel = { isUserInteracting = false },
                        onHorizontalDrag = { change, _ -> updateSelection(change.position.x) }
                    )
                }
        ) {
            val width = size.width
            val progress = animProgress.value

            // Dashed grid lines (3 interior lines)
            drawGridLines(gridColor, width, chartHeightPx)

            // Annotation ranges (Grafana-style bands behind the data)
            if (annotationRanges.isNotEmpty()) {
                drawAnnotationRanges(annotationRanges, width, chartHeightPx)
            }

            // Zero line if needed (for power chart with negative values)
            if (showZeroLine && chartData.minValue < 0 && chartData.maxValue > 0) {
                drawZeroLine(surfaceColor, chartData.minValue, chartData.range, width, chartHeightPx)
            }

            // Gradient fill under the line
            fillPath?.let {
                drawGradientFill(it, color, width, progress = progress)
            }

            // Smooth cubic line
            smoothPath?.let {
                drawAnimatedLine(it, color, width, progress)
            }

            // Y-axis labels
            drawYAxisLabels(labelPaint, chartData, unit, chartHeightPx)

            // Time labels (5 positions)
            if (timeLabels.size == 5) {
                drawTimeLabels(labelPaint, timeLabels, width, chartHeightPx, timeLabelHeightPx)
            }

            // Selection indicators
            displayedPoint?.let { point ->
                // Vertical crosshair
                drawCrosshair(surfaceColor, point.position.x, chartHeightPx)

                // Glowing data point
                drawGlowIndicator(point.position, color)

                // Floating time chip
                if (fractionToTimeLabel != null && timeLabelHeightPx > 0) {
                    val pts = chartData.displayPoints
                    val fraction = if (pts.size > 1) point.index.toFloat() / (pts.size - 1) else 0f
                    val timeStr = fractionToTimeLabel(fraction)
                    drawFloatingTimeChip(timeStr, point.position.x, color, chartHeightPx, timeLabelHeightPx, width)
                }
            }
        }

        // Theme-aware tooltip
        displayedPoint?.let { point ->
            var tooltipWidth by remember { mutableStateOf(0) }
            var tooltipHeight by remember { mutableStateOf(0) }

            val tooltipText = if (fractionToTimeLabel != null) {
                val pts = chartData.displayPoints
                val fraction = if (pts.size > 1) point.index.toFloat() / (pts.size - 1) else 0f
                val timeStr = fractionToTimeLabel(fraction)
                "$timeStr  \u2022  ${"%.1f".format(point.value)} $unit"
            } else {
                "${"%.1f".format(point.value)} $unit"
            }

            val xPx = (point.position.x - tooltipWidth / 2f)
                .coerceIn(0f, (canvasWidthPx - tooltipWidth).coerceAtLeast(0f))
            val yPx = (point.position.y - tooltipHeight - 24f).coerceAtLeast(0f)

            Text(
                text = tooltipText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = tooltipFg,
                modifier = Modifier
                    .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                    .onSizeChanged { tooltipWidth = it.width; tooltipHeight = it.height }
                    .shadow(4.dp, RoundedCornerShape(8.dp))
                    .background(tooltipBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
