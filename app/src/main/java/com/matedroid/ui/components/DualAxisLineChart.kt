package com.matedroid.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * A dual-axis line chart showing two data series with independent Y axes.
 * Left Y axis shows the first series (e.g., Voltage), right Y axis shows the second (e.g., Current).
 *
 * Visual enhancements: smooth cubic curves, dual gradient fills, dashed grid, crosshair,
 * glowing indicators, entrance animation, theme-aware tooltip with colored dot prefixes.
 */
@Composable
fun DualAxisLineChart(
    dataLeft: List<Float>,
    dataRight: List<Float>,
    modifier: Modifier = Modifier,
    colorLeft: Color = MaterialTheme.colorScheme.tertiary,
    colorRight: Color = MaterialTheme.colorScheme.secondary,
    unitLeft: String = "V",
    unitRight: String = "A",
    timeLabels: List<String> = emptyList(),
    chartHeight: Dp = 120.dp,
    externalSelectedFraction: Float? = null,
    onXSelected: ((Float?) -> Unit)? = null,
    fractionToTimeLabel: ((Float) -> String)? = null
) {
    if (dataLeft.size < 2 && dataRight.size < 2) return

    val density = LocalDensity.current
    // Text sizes in sp so labels respect density and the user's font scale.
    val axisLabelTextSizePx = with(density) { 9.sp.toPx() }
    val timeLabelTextSizePx = with(density) { 10.sp.toPx() }
    val chipTextSizePx = with(density) { 11.sp.toPx() }

    val surfaceColor = MaterialTheme.colorScheme.onSurface
    // Built once and reused across draws (the 800ms entrance redraws every frame).
    val leftLabelPaint = remember(colorLeft, axisLabelTextSizePx) {
        android.graphics.Paint().apply {
            color = colorLeft.copy(alpha = 0.8f).toArgb()
            textSize = axisLabelTextSizePx
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.LEFT
        }
    }
    val rightLabelPaint = remember(colorRight, axisLabelTextSizePx) {
        android.graphics.Paint().apply {
            color = colorRight.copy(alpha = 0.8f).toArgb()
            textSize = axisLabelTextSizePx
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
    }
    val timeLabelPaint = remember(surfaceColor, timeLabelTextSizePx) {
        android.graphics.Paint().apply {
            color = surfaceColor.copy(alpha = 0.7f).toArgb()
            textSize = timeLabelTextSizePx
            isAntiAlias = true
        }
    }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val tooltipBg = MaterialTheme.colorScheme.inverseSurface
    val tooltipFg = MaterialTheme.colorScheme.inverseOnSurface

    val chartDataLeft = remember(dataLeft) { prepareChartData(dataLeft, fixedMinMax = null) { it } }
    val chartDataRight = remember(dataRight) { prepareChartData(dataRight, fixedMinMax = null) { it } }

    val chartHeightPx = with(density) { chartHeight.toPx() }
    var canvasWidthPx by remember { mutableStateOf(0f) }

    // Selection works in display-space: the rendered curves use the downsampled
    // points, so tap indices must index those too. Raw indices past
    // MAX_DISPLAY_POINTS would look up nothing and leave the tooltip valueless.
    val dataSize = maxOf(chartDataLeft.displayPoints.size, chartDataRight.displayPoints.size)
    val rightLabelWidth = with(density) { 27.dp.toPx() }

    // Pre-compute smooth paths
    val chartWidth = (canvasWidthPx - rightLabelWidth).coerceAtLeast(0f)
    val smoothPathLeft = remember(chartDataLeft, chartWidth) {
        if (chartWidth <= 0f || chartDataLeft.displayPoints.size < 2) null
        else createSmoothPath(chartDataLeft.displayPoints, chartWidth, chartHeightPx, chartDataLeft.minValue, chartDataLeft.range)
    }
    val fillPathLeft = remember(smoothPathLeft, chartWidth) {
        smoothPathLeft?.let { createFillPath(it, chartWidth, chartHeightPx) }
    }
    val smoothPathRight = remember(chartDataRight, chartWidth) {
        if (chartWidth <= 0f || chartDataRight.displayPoints.size < 2) null
        else createSmoothPath(chartDataRight.displayPoints, chartWidth, chartHeightPx, chartDataRight.minValue, chartDataRight.range)
    }
    val fillPathRight = remember(smoothPathRight, chartWidth) {
        smoothPathRight?.let { createFillPath(it, chartWidth, chartHeightPx) }
    }

    // Entrance animation
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(chartDataLeft, chartDataRight) {
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }

    var selectedPoint by remember { mutableStateOf<DualSelectedPoint?>(null) }
    var isUserInteracting by remember { mutableStateOf(false) }

    val timeLabelHeightDp = if (timeLabels.isNotEmpty()) 20.dp else 0.dp
    val totalHeightDp = chartHeight + timeLabelHeightDp

    val externalPoint: DualSelectedPoint? = remember(externalSelectedFraction, chartDataLeft, chartDataRight, canvasWidthPx) {
        if (externalSelectedFraction == null || canvasWidthPx == 0f) return@remember null
        val index = (externalSelectedFraction * (dataSize - 1)).roundToInt().coerceIn(0, dataSize - 1)
        val cWidth = canvasWidthPx - rightLabelWidth
        val stepX = cWidth / (dataSize - 1).coerceAtLeast(1)
        val pointX = index * stepX
        val fraction = if (dataSize > 1) index.toFloat() / (dataSize - 1) else 0f
        val leftVal = valueAtFraction(chartDataLeft.displayPoints, fraction)
        val leftY = if (leftVal != null) {
            chartHeightPx * (1 - (leftVal - chartDataLeft.minValue) / chartDataLeft.range)
        } else chartHeightPx / 2
        DualSelectedPoint(
            index = index,
            valueLeft = leftVal,
            valueRight = valueAtFraction(chartDataRight.displayPoints, fraction),
            position = Offset(pointX, leftY)
        )
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
                // Tap toggles the tooltip at the nearest point (ignoring the time-label strip below the chart).
                .pointerInput(chartDataLeft, chartDataRight) {
                    if (dataSize < 2) return@pointerInput
                    detectTapGestures { offset ->
                        if (offset.y > chartHeightPx) return@detectTapGestures
                        val cWidth = size.width.toFloat() - rightLabelWidth
                        val stepX = cWidth / (dataSize - 1).coerceAtLeast(1)
                        val index = (offset.x / stepX).roundToInt().coerceIn(0, dataSize - 1)
                        if (selectedPoint?.index == index) {
                            selectedPoint = null
                            onXSelected?.invoke(null)
                        } else {
                            val fraction = if (dataSize > 1) index.toFloat() / (dataSize - 1) else 0f
                            val pointX = index * stepX
                            val leftVal = valueAtFraction(chartDataLeft.displayPoints, fraction)
                            val rightVal = valueAtFraction(chartDataRight.displayPoints, fraction)
                            val leftY = if (leftVal != null) {
                                chartHeightPx * (1 - (leftVal - chartDataLeft.minValue) / chartDataLeft.range)
                            } else offset.y
                            selectedPoint = DualSelectedPoint(index, leftVal, rightVal, Offset(pointX, leftY))
                            onXSelected?.invoke(fraction)
                        }
                    }
                }
                // Only claim HORIZONTAL drags (scrubbing the crosshair); vertical swipes fall
                // through to the enclosing scroll container so the page still scrolls.
                .pointerInput(chartDataLeft, chartDataRight) {
                    if (dataSize < 2) return@pointerInput

                    fun updateSelection(xOffset: Float, yOffset: Float) {
                        val width = size.width.toFloat()
                        val cWidth = width - rightLabelWidth
                        val stepX = cWidth / (dataSize - 1).coerceAtLeast(1)
                        val index = ((xOffset / stepX).roundToInt()).coerceIn(0, dataSize - 1)
                        val fraction = if (dataSize > 1) index.toFloat() / (dataSize - 1) else 0f
                        val pointX = index * stepX
                        val leftVal = valueAtFraction(chartDataLeft.displayPoints, fraction)
                        val rightVal = valueAtFraction(chartDataRight.displayPoints, fraction)
                        val leftY = if (leftVal != null) {
                            chartHeightPx * (1 - (leftVal - chartDataLeft.minValue) / chartDataLeft.range)
                        } else yOffset
                        selectedPoint = DualSelectedPoint(
                            index = index,
                            valueLeft = leftVal,
                            valueRight = rightVal,
                            position = Offset(pointX, leftY)
                        )
                        onXSelected?.invoke(fraction)
                    }

                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (offset.y <= chartHeightPx) {
                                isUserInteracting = true
                                updateSelection(offset.x, offset.y)
                            }
                        },
                        onDragEnd = { isUserInteracting = false },
                        onDragCancel = { isUserInteracting = false },
                        onHorizontalDrag = { change, _ -> updateSelection(change.position.x, change.position.y) }
                    )
                }
        ) {
            val width = size.width
            val timeLabelHeightPx = timeLabelHeightDp.toPx()
            val rLabelWidth = rightLabelWidth
            val progress = animProgress.value

            // Dashed grid lines (3 interior lines)
            drawGridLines(gridColor, width, chartHeightPx)

            // Gradient fills (lower alpha for dual to avoid occlusion)
            fillPathLeft?.let {
                drawGradientFill(it, colorLeft, chartWidth, alpha = 0.15f, progress = progress)
            }
            fillPathRight?.let {
                drawGradientFill(it, colorRight, chartWidth, alpha = 0.15f, progress = progress)
            }

            // Smooth cubic lines
            smoothPathLeft?.let {
                drawAnimatedLine(it, colorLeft, chartWidth, progress)
            }
            smoothPathRight?.let {
                drawAnimatedLine(it, colorRight, chartWidth, progress)
            }

            // Y-axis labels
            drawDualYAxisLabels(leftLabelPaint, chartDataLeft, unitLeft, chartHeightPx, isLeft = true)
            drawDualYAxisLabels(rightLabelPaint, chartDataRight, unitRight, chartHeightPx, isLeft = false, width = width)

            // Time labels
            if (timeLabels.size == 5) {
                drawTimeLabels(timeLabelPaint, timeLabels, width - rLabelWidth, chartHeightPx, timeLabelHeightPx)
            }

            // Selection indicators
            displayedPoint?.let { point ->
                val stepX = (width - rLabelWidth) / (dataSize - 1).coerceAtLeast(1)
                val pointX = point.index * stepX

                // Vertical crosshair
                drawCrosshair(surfaceColor, pointX, chartHeightPx)

                // Glowing indicators for both series
                point.valueLeft?.let { v ->
                    val y = chartHeightPx * (1 - (v - chartDataLeft.minValue) / chartDataLeft.range)
                    drawGlowIndicator(Offset(pointX, y), colorLeft)
                }
                point.valueRight?.let { v ->
                    val y = chartHeightPx * (1 - (v - chartDataRight.minValue) / chartDataRight.range)
                    drawGlowIndicator(Offset(pointX, y), colorRight)
                }

                // Floating time chip
                if (fractionToTimeLabel != null && timeLabelHeightPx > 0) {
                    val fraction = if (dataSize > 1) point.index.toFloat() / (dataSize - 1) else 0f
                    val timeStr = fractionToTimeLabel(fraction)
                    drawFloatingTimeChip(timeStr, pointX, colorLeft, chartHeightPx, timeLabelHeightPx, width - rLabelWidth, chipTextSizePx)
                }
            }
        }

        // Theme-aware tooltip with colored dot prefixes
        displayedPoint?.let { point ->
            var tooltipWidth by remember { mutableStateOf(0) }
            var tooltipHeight by remember { mutableStateOf(0) }

            val xPx = (point.position.x - tooltipWidth / 2f)
                .coerceIn(0f, (canvasWidthPx - tooltipWidth).coerceAtLeast(0f))
            val yPx = (point.position.y - tooltipHeight - 24f).coerceAtLeast(0f)

            Column(
                modifier = Modifier
                    .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                    .onSizeChanged { tooltipWidth = it.width; tooltipHeight = it.height }
                    .shadow(4.dp, RoundedCornerShape(8.dp))
                    .background(tooltipBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                // Time label at top if available
                if (fractionToTimeLabel != null) {
                    val fraction = if (dataSize > 1) point.index.toFloat() / (dataSize - 1) else 0f
                    val timeStr = fractionToTimeLabel(fraction)
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = tooltipFg.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Left value with colored dot
                point.valueLeft?.let { v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(colorLeft, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${"%.1f".format(v)} $unitLeft",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = tooltipFg
                        )
                    }
                }

                // Right value with colored dot
                point.valueRight?.let { v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(colorRight, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${"%.1f".format(v)} $unitRight",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = tooltipFg
                        )
                    }
                }
            }
        }
    }
}

/**
 * Looks up the display point nearest to [fraction] (0..1 across the X axis).
 * The two series may downsample to different lengths, so each is sampled by
 * fraction rather than by a shared index.
 */
private fun valueAtFraction(points: List<Float>, fraction: Float): Float? {
    if (points.isEmpty()) return null
    return points.getOrNull((fraction * (points.size - 1)).roundToInt())
}
