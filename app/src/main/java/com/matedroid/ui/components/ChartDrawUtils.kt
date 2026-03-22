package com.matedroid.ui.components

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Maximum number of data points to display on the chart.
 * Higher values mean more detail but slower rendering.
 * 150 points is a good balance between visual quality and performance.
 */
const val MAX_DISPLAY_POINTS = 150

// ── Data Classes ────────────────────────────────────────────────────────────

data class ChartData(
    val displayPoints: List<Float>,
    val minValue: Float,
    val maxValue: Float,
    val range: Float
)

data class SelectedPoint(
    val index: Int,
    val value: Float,
    val position: Offset
)

data class DualChartData(
    val displayPoints: List<Float>,
    val minValue: Float,
    val maxValue: Float,
    val range: Float
)

data class DualSelectedPoint(
    val index: Int,
    val valueLeft: Float?,
    val valueRight: Float?,
    val position: Offset
)

/**
 * Represents an annotation range to highlight on a chart.
 * Fractions are normalized 0.0–1.0 across the X axis.
 */
data class AnnotationRange(
    val startFraction: Float,
    val endFraction: Float,
    val color: Color,
    val label: String? = null
)

// ── Data Preparation ────────────────────────────────────────────────────────

fun prepareChartData(
    data: List<Float>,
    fixedMinMax: Pair<Float, Float>?,
    convertValue: (Float) -> Float
): ChartData {
    val convertedData = data.map { convertValue(it) }
    val displayPoints = if (convertedData.size > MAX_DISPLAY_POINTS) {
        downsampleLTTB(convertedData, MAX_DISPLAY_POINTS)
    } else {
        convertedData
    }
    val minValue = fixedMinMax?.first ?: displayPoints.minOrNull() ?: 0f
    val maxValue = fixedMinMax?.second ?: displayPoints.maxOrNull() ?: 1f
    val range = (maxValue - minValue).coerceAtLeast(1f)
    return ChartData(displayPoints, minValue, maxValue, range)
}

fun prepareDualChartData(data: List<Float>): DualChartData {
    val displayPoints = if (data.size > MAX_DISPLAY_POINTS) {
        downsampleLTTB(data, MAX_DISPLAY_POINTS)
    } else {
        data
    }
    val minValue = displayPoints.minOrNull() ?: 0f
    val maxValue = displayPoints.maxOrNull() ?: 1f
    val range = (maxValue - minValue).coerceAtLeast(1f)
    return DualChartData(displayPoints, minValue, maxValue, range)
}

// ── LTTB Downsampling ───────────────────────────────────────────────────────

/**
 * Largest Triangle Three Buckets (LTTB) downsampling algorithm.
 * Reduces the number of data points while preserving the visual shape of the line chart.
 *
 * Reference: Sveinn Steinarsson, "Downsampling Time Series for Visual Representation"
 */
fun downsampleLTTB(data: List<Float>, targetPoints: Int): List<Float> {
    if (data.size <= targetPoints) return data
    if (targetPoints < 3) return listOf(data.first(), data.last())

    val result = mutableListOf<Float>()
    result.add(data.first())

    val bucketSize = (data.size - 2).toFloat() / (targetPoints - 2)
    var prevSelectedIndex = 0

    for (i in 0 until targetPoints - 2) {
        val bucketStart = ((i * bucketSize) + 1).toInt()
        val bucketEnd = (((i + 1) * bucketSize) + 1).toInt().coerceAtMost(data.size - 1)

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
        if (count > 0) { avgX /= count; avgY /= count }
        else { avgX = nextBucketStart.toFloat(); avgY = data.getOrElse(nextBucketStart) { data.last() } }

        val prevX = prevSelectedIndex.toFloat()
        val prevY = data[prevSelectedIndex]
        var maxArea = -1f
        var selectedIndex = bucketStart

        for (j in bucketStart until bucketEnd) {
            val area = abs(
                (prevX - avgX) * (data[j] - prevY) -
                (prevX - j.toFloat()) * (avgY - prevY)
            ) * 0.5f
            if (area > maxArea) { maxArea = area; selectedIndex = j }
        }

        result.add(data[selectedIndex])
        prevSelectedIndex = selectedIndex
    }

    result.add(data.last())
    return result
}

// ── Path Creation ───────────────────────────────────────────────────────────

/**
 * Creates a smooth cubic Bezier path using monotone cubic (Fritsch-Carlson) interpolation.
 * Guarantees no overshoot — the curve never creates false peaks or valleys.
 */
fun createSmoothPath(
    points: List<Float>,
    width: Float,
    height: Float,
    minValue: Float,
    range: Float
): Path {
    val path = Path()
    if (points.size < 2) return path

    val n = points.size
    val stepX = width / (n - 1).coerceAtLeast(1)

    // Convert data to screen coordinates
    val xs = FloatArray(n) { it * stepX }
    val ys = FloatArray(n) { height * (1 - (points[it] - minValue) / range) }

    path.moveTo(xs[0], ys[0])

    if (n == 2) {
        path.lineTo(xs[1], ys[1])
        return path
    }

    // Compute tangent slopes using Fritsch-Carlson monotone cubic method
    val tangents = computeMonotoneTangents(xs, ys)

    for (i in 0 until n - 1) {
        val dx = xs[i + 1] - xs[i]
        val cp1x = xs[i] + dx / 3f
        val cp1y = ys[i] + tangents[i] * dx / 3f
        val cp2x = xs[i + 1] - dx / 3f
        val cp2y = ys[i + 1] - tangents[i + 1] * dx / 3f
        path.cubicTo(cp1x, cp1y, cp2x, cp2y, xs[i + 1], ys[i + 1])
    }

    return path
}

/**
 * Creates the fill path by closing the line path to the bottom of the chart.
 */
fun createFillPath(linePath: Path, width: Float, chartHeight: Float): Path {
    val fillPath = Path()
    fillPath.addPath(linePath)
    fillPath.lineTo(width, chartHeight)
    fillPath.lineTo(0f, chartHeight)
    fillPath.close()
    return fillPath
}

/**
 * Computes monotone cubic tangent slopes at each point (Fritsch-Carlson method).
 * This ensures the interpolated curve is monotone between data points — no false
 * peaks or valleys are introduced.
 */
private fun computeMonotoneTangents(xs: FloatArray, ys: FloatArray): FloatArray {
    val n = xs.size
    val tangents = FloatArray(n)

    // Compute slopes of secant lines between consecutive points
    val deltas = FloatArray(n - 1) { i ->
        (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i])
    }

    // Initial tangents: average of adjacent secants (or one-sided at endpoints)
    tangents[0] = deltas[0]
    tangents[n - 1] = deltas[n - 2]
    for (i in 1 until n - 1) {
        tangents[i] = (deltas[i - 1] + deltas[i]) / 2f
    }

    // Apply Fritsch-Carlson monotonicity constraints
    for (i in 0 until n - 1) {
        if (abs(deltas[i]) < 1e-6f) {
            // Flat segment: set tangents to zero
            tangents[i] = 0f
            tangents[i + 1] = 0f
        } else {
            val alpha = tangents[i] / deltas[i]
            val beta = tangents[i + 1] / deltas[i]
            // Ensure the vector (alpha, beta) lies within the circle of radius 3
            val magnitude = sqrt(alpha * alpha + beta * beta)
            if (magnitude > 3f) {
                val scale = 3f / magnitude
                tangents[i] = scale * alpha * deltas[i]
                tangents[i + 1] = scale * beta * deltas[i]
            }
        }
    }

    return tangents
}

// ── Drawing Functions ───────────────────────────────────────────────────────

private val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
private val crosshairDashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))

/**
 * Draws Grafana-style annotation bands on the chart.
 * Each range is rendered as a semi-transparent vertical band spanning the full chart height,
 * with thin border lines at the start and end edges.
 */
fun DrawScope.drawAnnotationRanges(
    ranges: List<AnnotationRange>,
    width: Float,
    chartHeight: Float
) {
    for (range in ranges) {
        val startX = range.startFraction * width
        val endX = range.endFraction * width
        val bandWidth = (endX - startX).coerceAtLeast(1f)

        // Semi-transparent fill band
        drawRect(
            color = range.color.copy(alpha = 0.12f),
            topLeft = Offset(startX, 0f),
            size = Size(bandWidth, chartHeight)
        )

        // Thin border lines at edges
        val edgeColor = range.color.copy(alpha = 0.4f)
        drawLine(
            color = edgeColor,
            start = Offset(startX, 0f),
            end = Offset(startX, chartHeight),
            strokeWidth = 1f
        )
        drawLine(
            color = edgeColor,
            start = Offset(endX, 0f),
            end = Offset(endX, chartHeight),
            strokeWidth = 1f
        )
    }
}

/**
 * Draws dashed grid lines at 25%, 50%, 75% positions (3 interior lines).
 */
fun DrawScope.drawGridLines(gridColor: Color, width: Float, height: Float) {
    val gridLineCount = 4
    // Only draw interior lines (skip 0 = top border, skip 4 = bottom border)
    for (i in 1 until gridLineCount) {
        val y = height * i / gridLineCount
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 0.5f,
            pathEffect = dashEffect
        )
    }
}

/**
 * Draws the line path with optional entrance animation.
 * @param progress Animation progress from 0.0 to 1.0. When 1.0, draws the full path.
 */
fun DrawScope.drawAnimatedLine(
    path: Path,
    color: Color,
    width: Float,
    progress: Float
) {
    if (progress >= 1f) {
        drawPath(path, color = color, style = Stroke(width = 2.5f))
    } else {
        clipRect(right = width * progress) {
            drawPath(path, color = color, style = Stroke(width = 2.5f))
        }
    }
}

/**
 * Draws the gradient fill under the line path.
 * @param progress Animation progress for clipping during entrance animation.
 */
fun DrawScope.drawGradientFill(
    fillPath: Path,
    color: Color,
    width: Float,
    alpha: Float = 0.3f,
    progress: Float = 1f
) {
    val brush = Brush.verticalGradient(
        listOf(color.copy(alpha = alpha), Color.Transparent)
    )
    if (progress >= 1f) {
        drawPath(fillPath, brush = brush)
    } else {
        clipRect(right = width * progress) {
            drawPath(fillPath, brush = brush)
        }
    }
}

/**
 * Draws a zero reference line for charts with positive and negative values (e.g. power with regen).
 */
fun DrawScope.drawZeroLine(surfaceColor: Color, minValue: Float, range: Float, width: Float, height: Float) {
    val zeroY = height * (1 - (0f - minValue) / range)
    drawLine(
        color = surfaceColor.copy(alpha = 0.5f),
        start = Offset(0f, zeroY),
        end = Offset(width, zeroY),
        strokeWidth = 2f
    )
}

/**
 * Draws a dashed vertical crosshair line at the selected point's X position.
 */
fun DrawScope.drawCrosshair(surfaceColor: Color, x: Float, chartHeight: Float) {
    drawLine(
        color = surfaceColor.copy(alpha = 0.3f),
        start = Offset(x, 0f),
        end = Offset(x, chartHeight),
        strokeWidth = 1.dp.toPx(),
        pathEffect = crosshairDashEffect
    )
}

/**
 * Draws a glowing data point indicator: outer glow + main dot + bright center.
 */
fun DrawScope.drawGlowIndicator(center: Offset, color: Color) {
    // Outer glow
    drawCircle(
        color = color.copy(alpha = 0.2f),
        radius = 10.dp.toPx(),
        center = center
    )
    // Main dot
    drawCircle(
        color = color,
        radius = 5.dp.toPx(),
        center = center
    )
    // Inner bright center
    drawCircle(
        color = Color.White,
        radius = 2.dp.toPx(),
        center = center
    )
}

/**
 * Draws Y-axis labels at 5 positions (including max), with automatic decimal
 * precision when the range is too small to differentiate integer values.
 */
fun DrawScope.drawYAxisLabels(
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

        val gridLineCount = 4

        val rawValues = (0..gridLineCount).map { i ->
            chartData.maxValue - (chartData.range * i / gridLineCount)
        }
        val needsDecimal = rawValues.zipWithNext().any { (a, b) ->
            "%.0f".format(a) == "%.0f".format(b)
        }
        val format = if (needsDecimal) "%.1f" else "%.0f"

        for (i in 0..gridLineCount) {
            val y = height * i / gridLineCount
            val label = format.format(rawValues[i]) + " $unit"
            // Position the label above line
            val textY = y - 4f
            drawText(label, 8f, textY, textPaint)
        }
    }
}

/**
 * Draws Y-axis labels for dual-axis chart (left or right side).
 */
fun DrawScope.drawDualYAxisLabels(
    chartData: DualChartData,
    unit: String,
    height: Float,
    isLeft: Boolean,
    color: Color,
    width: Float = 0f
) {
    drawContext.canvas.nativeCanvas.apply {
        val textPaint = Paint().apply {
            this.color = color.copy(alpha = 0.8f).toArgb()
            textSize = 24f
            isAntiAlias = true
            textAlign = if (isLeft) Paint.Align.LEFT else Paint.Align.RIGHT
        }
        val gridLineCount = 4

        val rawValues = (0..gridLineCount).map { i ->
            chartData.maxValue - (chartData.range * i / gridLineCount)
        }
        val needsDecimal = rawValues.zipWithNext().any { (a, b) ->
            "%.0f".format(a) == "%.0f".format(b)
        }
        val format = if (needsDecimal) "%.1f" else "%.0f"

        for (i in 0..gridLineCount) {
            val y = height * i / gridLineCount
            val label = format.format(rawValues[i]) + " $unit"
            // Position the label above line
            val textY = y - 4f
            val x = if (isLeft) 8f else width - 8f
            drawText(label, x, textY, textPaint)
        }
    }
}

/**
 * Draws X-axis time labels at 5 positions: start (0%), 25%, 50%, 75%, end (100%).
 */
fun DrawScope.drawTimeLabels(
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
        val positions = listOf(0f, width * 0.25f, width * 0.5f, width * 0.75f, width)

        timeLabels.forEachIndexed { index, label ->
            if (label.isNotEmpty()) {
                val textWidth = textPaint.measureText(label)
                val x = when (index) {
                    0 -> 0f
                    4 -> positions[index] - textWidth
                    else -> positions[index] - textWidth / 2
                }
                drawText(label, x.coerceAtLeast(0f), timeY, textPaint)
            }
        }
    }
}

/**
 * Draws a floating time chip at the selected X position in the time label zone.
 */
fun DrawScope.drawFloatingTimeChip(
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
