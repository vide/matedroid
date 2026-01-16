package com.matedroid.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    convertValue: (Float) -> Float = { it }
) {
    if (data.size < 2) return

    val surfaceColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    // Cache all computed values to avoid recalculation during scroll
    val chartData = remember(data, fixedMinMax, convertValue) {
        prepareChartData(data, fixedMinMax, convertValue)
    }

    // State for tooltip on tap
    var selectedPoint by remember { mutableStateOf<SelectedPoint?>(null) }

    // Calculate heights
    val chartHeightDp = 120.dp
    val timeLabelHeightDp = if (timeLabels.isNotEmpty()) 20.dp else 0.dp
    val totalHeightDp = chartHeightDp + timeLabelHeightDp

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeightDp)
                .pointerInput(chartData) {
                    detectTapGestures { offset ->
                        val width = size.width.toFloat()
                        val chartHeightPx = chartHeightDp.toPx()
                        val points = chartData.displayPoints

                        if (points.isEmpty()) return@detectTapGestures

                        // Only respond to taps in the chart area (not the time labels)
                        if (offset.y > chartHeightPx) return@detectTapGestures

                        // Find closest point to tap
                        val stepX = width / (points.size - 1).coerceAtLeast(1)
                        val tappedIndex = ((offset.x / stepX).roundToInt()).coerceIn(0, points.lastIndex)
                        val pointValue = points[tappedIndex]

                        val pointX = tappedIndex * stepX
                        val pointY = chartHeightPx * (1 - (pointValue - chartData.minValue) / chartData.range)

                        selectedPoint = if (selectedPoint?.index == tappedIndex) {
                            null // Toggle off
                        } else {
                            SelectedPoint(tappedIndex, pointValue, Offset(pointX, pointY))
                        }
                    }
                }
        ) {
            val width = size.width
            val chartHeightPx = chartHeightDp.toPx()
            val timeLabelHeightPx = timeLabelHeightDp.toPx()

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
            selectedPoint?.let { point ->
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
            }
        }

        // Tooltip overlay
        selectedPoint?.let { point ->
            TooltipOverlay(
                value = point.value,
                unit = unit,
                position = point.position,
                modifier = Modifier.fillMaxSize()
            )
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

@Composable
private fun TooltipOverlay(
    value: Float,
    unit: String,
    position: Offset,
    modifier: Modifier = Modifier
) {
    // Simple tooltip using Canvas text drawing for performance
    Canvas(modifier = modifier) {
        val text = "%.1f $unit".format(value)

        drawContext.canvas.nativeCanvas.apply {
            val textPaint = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 32f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            val bgPaint = Paint().apply {
                color = android.graphics.Color.argb(200, 50, 50, 50)
                isAntiAlias = true
            }

            val textWidth = textPaint.measureText(text)
            val padding = 16f
            val tooltipY = (position.y - 40f).coerceAtLeast(50f)
            val tooltipX = position.x.coerceIn(textWidth / 2 + padding, size.width - textWidth / 2 - padding)

            // Draw background
            drawRoundRect(
                tooltipX - textWidth / 2 - padding,
                tooltipY - 30f,
                tooltipX + textWidth / 2 + padding,
                tooltipY + 8f,
                12f,
                12f,
                bgPaint
            )

            // Draw text
            drawText(text, tooltipX, tooltipY, textPaint)
        }
    }
}
