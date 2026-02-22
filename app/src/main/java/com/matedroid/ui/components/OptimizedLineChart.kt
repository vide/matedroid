package com.matedroid.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Maximum number of data points to display on the chart.
 * Higher values mean more detail but slower rendering.
 * 150 points is a good balance between visual quality and performance.
 */
private const val MAX_DISPLAY_POINTS = 150

/**
 * An optimized line chart component designed for smooth scrolling performance.
 *
 * Performance optimizations:
 * 1. Data downsampling using LTTB algorithm - reduces points while preserving visual shape
 * 2. Cached computations using remember - prevents recalculation on every frame
 * 3. Path-based drawing - single draw call instead of many line segments
 * 4. Minimal text drawing - labels drawn efficiently
 *
 * @param externalSelectedFraction When provided (0.0–1.0), shows a tooltip at the corresponding
 *   X position. Used for cross-chart synchronization when user is interacting with a sibling chart.
 * @param onXSelected Called with the normalized X fraction (0.0–1.0) when the user interacts
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
    fractionToTimeLabel: ((Float) -> String)? = null
) {
    if (data.size < 2) return

    val surfaceColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    // Cache all computed values to avoid recalculation during scroll
    val chartData = remember(data, fixedMinMax, convertValue) {
        prepareChartData(data, fixedMinMax, convertValue)
    }

    var selectedPoint by remember { mutableStateOf<SelectedPoint?>(null) }
    var isUserInteracting by remember { mutableStateOf(false) }
    var canvasWidthPx by remember { mutableStateOf(0f) }

    val density = LocalDensity.current
    val chartHeightPx = with(density) { chartHeight.toPx() }
    val timeLabelHeightDp = if (timeLabels.isNotEmpty()) 20.dp else 0.dp
    val timeLabelHeightPx = with(density) { timeLabelHeightDp.toPx() }
    val totalHeightDp = chartHeight + timeLabelHeightDp

    // Compute tooltip position from external sync state (when another chart is being interacted with)
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

    // Show external state when user is not touching this chart and external sync is active
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

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isUserInteracting = true

                        // Record what was selected before this press for toggle detection
                        val width = size.width.toFloat()
                        val stepX = width / (points.size - 1).coerceAtLeast(1)
                        val initialIndex = ((down.position.x / stepX).roundToInt()).coerceIn(0, points.lastIndex)
                        val wasSelectedAtSameIndex = selectedPoint?.index == initialIndex

                        // Show tooltip immediately on press
                        updateSelection(down.position.x)

                        var hasDragged = false
                        drag(down.id) { change ->
                            change.consume()
                            hasDragged = true
                            updateSelection(change.position.x)
                        }

                        // Tap on already-selected point: toggle off (dismiss tooltip)
                        if (!hasDragged && wasSelectedAtSameIndex) {
                            selectedPoint = null
                            onXSelected?.invoke(null)
                        }

                        isUserInteracting = false
                    }
                }
        ) {
            val width = size.width

            // Draw grid lines
            drawGridLines(gridColor, width, chartHeightPx)

            // Draw zero line if needed (for power chart with negative values)
            if (showZeroLine && chartData.minValue < 0 && chartData.maxValue > 0) {
                drawZeroLine(surfaceColor, chartData, width, chartHeightPx)
            }

            // Draw the cached path
            drawPath(
                path = chartData.createPath(width, chartHeightPx),
                color = color,
                style = Stroke(width = 2.5f)
            )

            // Draw Y-axis labels
            drawYAxisLabels(surfaceColor, chartData, unit, chartHeightPx)

            // Draw time labels if provided (5 labels: start, 1st quarter, half, 3rd quarter, end)
            if (timeLabels.size == 5) {
                drawTimeLabels(surfaceColor, timeLabels, width, chartHeightPx, timeLabelHeightPx)
            }

            // Draw selected point indicator
            displayedPoint?.let { point ->
                drawCircle(
                    color = color,
                    radius = 6.dp.toPx(),
                    center = point.position
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = point.position
                )

                // Draw floating time chip in the X axis label zone
                if (fractionToTimeLabel != null && timeLabelHeightPx > 0) {
                    val pts = chartData.displayPoints
                    val fraction = if (pts.size > 1) point.index.toFloat() / (pts.size - 1) else 0f
                    val timeStr = fractionToTimeLabel(fraction)
                    drawFloatingTimeChip(timeStr, point.position.x, color, chartHeightPx, timeLabelHeightPx, width)
                }
            }
        }

        // Tooltip popup
        displayedPoint?.let { point ->
            val d = LocalDensity.current
            Popup(
                offset = with(d) {
                    IntOffset(
                        x = point.position.x.roundToInt(),
                        y = (point.position.y - 48.dp.toPx()).roundToInt()
                    )
                },
                onDismissRequest = { selectedPoint = null },
                properties = PopupProperties(focusable = false)
            ) {
                val text = "%.1f".format(point.value) + " $unit"
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .background(
                            Color(0xCC323232),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Holds pre-computed chart data for efficient rendering
 */
private data class ChartData(
    val displayPoints: List<Float>,
    val minValue: Float,
    val maxValue: Float,
    val range: Float
) {
    /**
     * Creates a Path for the line chart.
     * Using Path is more efficient than drawing individual line segments.
     */
    fun createPath(width: Float, height: Float): Path {
        val path = Path()
        if (displayPoints.size < 2) return path

        val stepX = width / (displayPoints.size - 1).coerceAtLeast(1)

        displayPoints.forEachIndexed { index, value ->
            val x = index * stepX
            val y = height * (1 - (value - minValue) / range)

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        return path
    }
}

private data class SelectedPoint(
    val index: Int,
    val value: Float,
    val position: Offset
)

/**
 * Prepares chart data with downsampling if needed
 */
private fun prepareChartData(
    data: List<Float>,
    fixedMinMax: Pair<Float, Float>?,
    convertValue: (Float) -> Float
): ChartData {
    // Convert values
    val convertedData = data.map { convertValue(it) }

    // Downsample if necessary
    val displayPoints = if (convertedData.size > MAX_DISPLAY_POINTS) {
        downsampleLTTB(convertedData, MAX_DISPLAY_POINTS)
    } else {
        convertedData
    }

    // Calculate min/max
    val minValue = fixedMinMax?.first ?: displayPoints.minOrNull() ?: 0f
    val maxValue = fixedMinMax?.second ?: displayPoints.maxOrNull() ?: 1f
    val range = (maxValue - minValue).coerceAtLeast(1f)

    return ChartData(displayPoints, minValue, maxValue, range)
}

/**
 * Largest Triangle Three Buckets (LTTB) downsampling algorithm.
 * This algorithm reduces the number of data points while preserving the visual
 * shape of the line chart. It's specifically designed for time series visualization.
 *
 * The algorithm works by:
 * 1. Always keeping the first and last points
 * 2. Dividing remaining points into buckets
 * 3. For each bucket, selecting the point that forms the largest triangle
 *    with the previous selected point and the average of the next bucket
 *
 * Reference: Sveinn Steinarsson, "Downsampling Time Series for Visual Representation"
 */
private fun downsampleLTTB(data: List<Float>, targetPoints: Int): List<Float> {
    if (data.size <= targetPoints) return data
    if (targetPoints < 3) return listOf(data.first(), data.last())

    val result = mutableListOf<Float>()

    // Always include the first point
    result.add(data.first())

    // Calculate bucket size
    val bucketSize = (data.size - 2).toFloat() / (targetPoints - 2)

    var prevSelectedIndex = 0

    for (i in 0 until targetPoints - 2) {
        // Calculate bucket boundaries
        val bucketStart = ((i * bucketSize) + 1).toInt()
        val bucketEnd = (((i + 1) * bucketSize) + 1).toInt().coerceAtMost(data.size - 1)

        // Calculate average point of the next bucket (for triangle calculation)
        val nextBucketStart = bucketEnd
        val nextBucketEnd = (((i + 2) * bucketSize) + 1).toInt().coerceAtMost(data.size)

        var avgX = 0f
        var avgY = 0f
        var count = 0
        for (j in nextBucketStart until nextBucketEnd) {
            avgX += j.toFloat()
            avgY += data[j]
            count++
        }
        if (count > 0) {
            avgX /= count
            avgY /= count
        } else {
            avgX = nextBucketStart.toFloat()
            avgY = data.getOrElse(nextBucketStart) { data.last() }
        }

        // Find the point in current bucket that creates the largest triangle
        val prevX = prevSelectedIndex.toFloat()
        val prevY = data[prevSelectedIndex]

        var maxArea = -1f
        var selectedIndex = bucketStart

        for (j in bucketStart until bucketEnd) {
            // Calculate triangle area using the cross product formula
            val area = abs(
                (prevX - avgX) * (data[j] - prevY) -
                (prevX - j.toFloat()) * (avgY - prevY)
            ) * 0.5f

            if (area > maxArea) {
                maxArea = area
                selectedIndex = j
            }
        }

        result.add(data[selectedIndex])
        prevSelectedIndex = selectedIndex
    }

    // Always include the last point
    result.add(data.last())

    return result
}

private fun DrawScope.drawGridLines(gridColor: Color, width: Float, height: Float) {
    val gridLineCount = 4
    for (i in 0..gridLineCount) {
        val y = height * i / gridLineCount
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 1f
        )
    }
}

private fun DrawScope.drawZeroLine(surfaceColor: Color, chartData: ChartData, width: Float, height: Float) {
    val zeroY = height * (1 - (0f - chartData.minValue) / chartData.range)
    drawLine(
        color = surfaceColor.copy(alpha = 0.5f),
        start = Offset(0f, zeroY),
        end = Offset(width, zeroY),
        strokeWidth = 2f
    )
}

/**
 * Draws Y-axis labels at 4 positions: 1st quarter (25%), half (50%), 3rd quarter (75%), end (100%)
 * Following the chart guidelines from CLAUDE.md
 */
private fun DrawScope.drawYAxisLabels(
    surfaceColor: Color,
    chartData: ChartData,
    unit: String,
    height: Float
) {
    drawContext.canvas.nativeCanvas.apply {
        val textPaint = Paint().apply {
            color = surfaceColor.copy(alpha = 0.7f).toArgb()
            textSize = 26f
            isAntiAlias = true
        }

        // 4 labels at: 1st quarter (25%), half (50%), 3rd quarter (75%), end (100%)
        // These correspond to grid lines 1, 2, 3, 4 (skipping 0 which is the top/max)
        val labelPositions = listOf(1, 2, 3, 4) // Skip position 0 (top)
        val gridLineCount = 4

        for (i in labelPositions) {
            val y = height * i / gridLineCount
            val value = chartData.maxValue - (chartData.range * i / gridLineCount)
            val label = "%.0f".format(value) + " $unit"

            // Position the label: bottom label above line, others centered on line
            val textY = when (i) {
                gridLineCount -> y - 4f  // Bottom label above line
                else -> y + textPaint.textSize / 3  // Others centered
            }

            drawText(label, 8f, textY, textPaint)
        }
    }
}

/**
 * Draws X-axis time labels at 5 positions: start (0%), 1st quarter (25%), half (50%), 3rd quarter (75%), end (100%)
 * Following the chart guidelines from CLAUDE.md
 */
private fun DrawScope.drawTimeLabels(
    surfaceColor: Color,
    timeLabels: List<String>,
    width: Float,
    chartHeight: Float,
    timeLabelHeight: Float
) {
    drawContext.canvas.nativeCanvas.apply {
        val textPaint = Paint().apply {
            color = surfaceColor.copy(alpha = 0.7f).toArgb()
            textSize = 26f
            isAntiAlias = true
        }

        val timeY = chartHeight + timeLabelHeight - 4f
        // 5 positions at 0%, 25%, 50%, 75%, 100%
        val positions = listOf(0f, width * 0.25f, width * 0.5f, width * 0.75f, width)

        timeLabels.forEachIndexed { index, label ->
            if (label.isNotEmpty()) {
                val textWidth = textPaint.measureText(label)
                val x = when (index) {
                    0 -> 0f  // Left aligned (start)
                    4 -> positions[index] - textWidth  // Right aligned (end)
                    else -> positions[index] - textWidth / 2  // Center aligned (quarters)
                }
                drawText(label, x.coerceAtLeast(0f), timeY, textPaint)
            }
        }
    }
}

private fun DrawScope.drawFloatingTimeChip(
    timeStr: String,
    xCenter: Float,
    chipColor: Color,
    chartHeight: Float,
    timeLabelHeight: Float,
    canvasWidth: Float
) {
    drawContext.canvas.nativeCanvas.apply {
        val textPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val bgPaint = Paint().apply {
            color = chipColor.copy(alpha = 0.9f).toArgb()
            isAntiAlias = true
        }

        val textWidth = textPaint.measureText(timeStr)
        val chipPadding = 12f
        val chipWidth = textWidth + chipPadding * 2
        val chipHeight = timeLabelHeight * 0.85f
        val chipTop = chartHeight + (timeLabelHeight - chipHeight) / 2
        val chipLeft = (xCenter - chipWidth / 2).coerceIn(0f, canvasWidth - chipWidth)

        val rect = android.graphics.RectF(chipLeft, chipTop, chipLeft + chipWidth, chipTop + chipHeight)
        drawRoundRect(rect, 8f, 8f, bgPaint)
        drawText(timeStr, chipLeft + chipWidth / 2, chipTop + chipHeight / 2 + textPaint.textSize / 3, textPaint)
    }
}
