package com.matedroid.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    val density = LocalDensity.current
    // Text sizes in sp so labels respect density and the user's font scale.
    val labelTextSizePx = with(density) { 10.sp.toPx() }
    val chipTextSizePx = with(density) { 11.sp.toPx() }

    val surfaceColor = MaterialTheme.colorScheme.onSurface
    // Built once and reused across draws (the 800ms entrance redraws every frame).
    val labelPaint = remember(surfaceColor, labelTextSizePx) {
        android.graphics.Paint().apply {
            this.color = surfaceColor.copy(alpha = 0.7f).toArgb()
            textSize = labelTextSizePx
            isAntiAlias = true
        }
    }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val tooltipFg = MaterialTheme.colorScheme.inverseOnSurface

    val chartData = remember(data, fixedMinMax, convertValue) {
        prepareChartData(data, fixedMinMax, convertValue)
    }

    // Pre-compute the smooth path and fill path
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

    val selection = rememberChartSelectionState<SelectedPoint>(
        externalSelectedFraction = externalSelectedFraction,
        clearOnExternalDismiss = onXSelected != null
    )

    val timeLabelHeightDp = if (timeLabels.isNotEmpty()) 20.dp else 0.dp
    val timeLabelHeightPx = with(density) { timeLabelHeightDp.toPx() }
    val totalHeightDp = chartHeight + timeLabelHeightDp

    // Builds the selected point at a display-point index (position in canvas pixels).
    fun pointAt(index: Int): SelectedPoint {
        val points = chartData.displayPoints
        val pointX = indexToX(index, points.size, canvasWidthPx)
        val pointY = chartHeightPx * (1 - (points[index] - chartData.minValue) / chartData.range)
        return SelectedPoint(index, points[index], Offset(pointX, pointY))
    }

    val externalPoint: SelectedPoint? = remember(externalSelectedFraction, chartData, canvasWidthPx) {
        if (externalSelectedFraction == null || canvasWidthPx == 0f) return@remember null
        if (chartData.displayPoints.isEmpty()) return@remember null
        pointAt(fractionToIndex(externalSelectedFraction, chartData.displayPoints.size))
    }

    val displayedPoint = selection.displayed(externalSelectedFraction, externalPoint)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeightDp)
                .onSizeChanged { canvasWidthPx = it.width.toFloat() }
                .chartScrubber(
                    chartData,
                    enabled = chartData.displayPoints.isNotEmpty(),
                    onScrubbingChange = { selection.isUserInteracting = it },
                    onTap = { fraction, _ ->
                        val index = fractionToIndex(fraction, chartData.displayPoints.size)
                        if (selection.selected?.index == index) {
                            selection.selected = null
                            onXSelected?.invoke(null)
                        } else {
                            selection.selected = pointAt(index)
                            onXSelected?.invoke(indexToFraction(index, chartData.displayPoints.size))
                        }
                    },
                    onScrub = { fraction, _ ->
                        val index = fractionToIndex(fraction, chartData.displayPoints.size)
                        selection.selected = pointAt(index)
                        onXSelected?.invoke(indexToFraction(index, chartData.displayPoints.size))
                    }
                )
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
                    val timeStr = fractionToTimeLabel(indexToFraction(point.index, chartData.displayPoints.size))
                    drawFloatingTimeChip(timeStr, point.position.x, color, chartHeightPx, timeLabelHeightPx, width, chipTextSizePx)
                }
            }
        }

        // Theme-aware tooltip
        displayedPoint?.let { point ->
            val tooltipText = if (fractionToTimeLabel != null) {
                val timeStr = fractionToTimeLabel(indexToFraction(point.index, chartData.displayPoints.size))
                "$timeStr  \u2022  ${"%.1f".format(point.value)} $unit"
            } else {
                "${"%.1f".format(point.value)} $unit"
            }

            ChartTooltip(
                anchorX = { point.position.x },
                anchorY = { point.position.y },
                containerWidthPx = { canvasWidthPx },
                verticalPadding = 4.dp
            ) {
                Text(
                    text = tooltipText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = tooltipFg
                )
            }
        }
    }
}
