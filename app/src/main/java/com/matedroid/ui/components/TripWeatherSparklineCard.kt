package com.matedroid.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.data.repository.TripWeatherPoint
import com.matedroid.data.repository.WeatherCondition
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.ui.theme.CarColorPalette
import androidx.compose.ui.res.stringResource
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

/**
 * A temperature sparkline for a trip: temperature over time as a smoothed line with an
 * area fill, weather condition icons floating on the line at each sample, and temp labels
 * above each icon. Y-axis shows 3 temp ticks, X-axis shows start/end clock times.
 *
 * Hides itself if [samples] is empty.
 */
@Composable
fun TripWeatherSparklineCard(
    samples: List<TripWeatherPoint>,
    palette: CarColorPalette,
    units: Units?,
    modifier: Modifier = Modifier
) {
    if (samples.isEmpty()) return

    val isFahrenheit = units?.unitOfTemperature == "F"
    val tempUnitLabel = if (isFahrenheit) "°F" else "°C"
    fun displayTemp(c: Double): Double = if (isFahrenheit) c * 1.8 + 32 else c

    // Derive Y-axis range with a small margin so the line never touches the top/bottom.
    val displayedTemps = remember(samples, isFahrenheit) { samples.map { displayTemp(it.temperatureCelsius) } }
    val minTemp = displayedTemps.min()
    val maxTemp = displayedTemps.max()
    val spanBase = (maxTemp - minTemp).coerceAtLeast(if (isFahrenheit) 10.0 else 6.0)
    val padY = spanBase * 0.18
    val axisMin = minTemp - padY
    val axisMax = maxTemp + padY
    val axisSpan = (axisMax - axisMin).coerceAtLeast(1.0)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = CustomIcons.WeatherPartlyCloudy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.weather_during_trip),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))

            Sparkline(
                samples = samples,
                displayedTemps = displayedTemps,
                axisMin = axisMin,
                axisSpan = axisSpan,
                tempUnitLabel = tempUnitLabel,
                palette = palette
            )

            Spacer(Modifier.height(4.dp))

            // Endpoints row — first and last sample times for context
            val first = samples.first()
            val last = samples.last()
            val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = first.timestamp.format(timeFmt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = last.timestamp.format(timeFmt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Sparkline(
    samples: List<TripWeatherPoint>,
    displayedTemps: List<Double>,
    axisMin: Double,
    axisSpan: Double,
    tempUnitLabel: String,
    palette: CarColorPalette
) {
    val chartHeight = 120.dp
    val labelHeight = 22.dp  // reserved above the chart for temp labels
    val totalHeight = chartHeight + labelHeight
    val yAxisWidth = 28.dp
    val chartRightPad = 6.dp

    val density = LocalDensity.current
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val lineColor = palette.accent
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
    ) {
        val totalW = maxWidth
        val chartRegionLeftDp = yAxisWidth
        val chartRegionWidthDp = totalW - yAxisWidth - chartRightPad

        val yAxisWidthPx = with(density) { yAxisWidth.toPx() }
        val chartRightPadPx = with(density) { chartRightPad.toPx() }
        val labelHeightPx = with(density) { labelHeight.toPx() }

        // Normalized [0..1] position of each sample along x and y.
        val xs = remember(samples) {
            if (samples.size <= 1) listOf(0.5f) else samples.indices.map { it.toFloat() / (samples.size - 1) }
        }
        val ys = remember(displayedTemps, axisMin, axisSpan) {
            displayedTemps.map { t ->
                val frac = ((t - axisMin) / axisSpan).toFloat().coerceIn(0f, 1f)
                1f - frac  // invert — higher temp → lower y (drawn near top)
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val chartLeftPx = yAxisWidthPx
            val chartTopPx = labelHeightPx
            val chartRightPx = size.width - chartRightPadPx
            val chartBottomPx = size.height
            val chartWPx = chartRightPx - chartLeftPx
            val chartHPx = chartBottomPx - chartTopPx

            // Y-axis gridlines (top, middle, bottom)
            for (frac in listOf(0f, 0.5f, 1f)) {
                val y = chartTopPx + chartHPx * frac
                drawLine(
                    color = gridColor,
                    start = Offset(chartLeftPx, y),
                    end = Offset(chartRightPx, y),
                    strokeWidth = 1f
                )
            }

            // Y-axis labels (max, mid, min) — rendered right-aligned into the y-axis gutter
            val axisMax = axisMin + axisSpan
            val axisMid = axisMin + axisSpan / 2.0
            fun drawYAxisLabel(value: Double, y: Float, alignTop: Boolean) {
                val text = "${value.toInt()}$tempUnitLabel"
                val layout = textMeasurer.measure(
                    text = text,
                    style = TextStyle(fontSize = 9.sp, textAlign = TextAlign.End)
                )
                val yOffset = if (alignTop) 0f else -layout.size.height.toFloat()
                drawText(
                    textLayoutResult = layout,
                    color = axisColor,
                    topLeft = Offset(
                        x = chartLeftPx - 6.dp.toPx() - layout.size.width,
                        y = y + yOffset
                    )
                )
            }
            drawYAxisLabel(axisMax, chartTopPx, alignTop = true)
            drawYAxisLabel(axisMid, chartTopPx + chartHPx / 2f, alignTop = true)
            drawYAxisLabel(axisMin, chartTopPx + chartHPx, alignTop = false)

            // Build the smoothed line through (xs, ys) via Catmull-Rom-like cubic bezier tangents.
            val pointsPx = xs.zip(ys) { xNorm, yNorm ->
                Offset(
                    chartLeftPx + chartWPx * xNorm,
                    chartTopPx + chartHPx * yNorm
                )
            }
            if (pointsPx.isEmpty()) return@Canvas

            val linePath = Path()
            val areaPath = Path()
            linePath.moveTo(pointsPx.first().x, pointsPx.first().y)
            areaPath.moveTo(pointsPx.first().x, chartBottomPx)
            areaPath.lineTo(pointsPx.first().x, pointsPx.first().y)

            if (pointsPx.size == 1) {
                // Single point: nothing to stroke. Still draw the marker later.
                linePath.lineTo(pointsPx.first().x, pointsPx.first().y)
                areaPath.lineTo(pointsPx.first().x, chartBottomPx)
            } else {
                val tension = 0.35f // how curvy the line is
                for (i in 0 until pointsPx.size - 1) {
                    val p0 = pointsPx.getOrNull(i - 1) ?: pointsPx[i]
                    val p1 = pointsPx[i]
                    val p2 = pointsPx[i + 1]
                    val p3 = pointsPx.getOrNull(i + 2) ?: p2
                    val c1 = Offset(
                        p1.x + (p2.x - p0.x) * tension,
                        p1.y + (p2.y - p0.y) * tension
                    )
                    val c2 = Offset(
                        p2.x - (p3.x - p1.x) * tension,
                        p2.y - (p3.y - p1.y) * tension
                    )
                    linePath.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
                    areaPath.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
                }
                areaPath.lineTo(pointsPx.last().x, chartBottomPx)
                areaPath.close()
            }

            // Area fill (accent gradient fading down)
            drawPath(
                areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0f)),
                    startY = chartTopPx,
                    endY = chartBottomPx
                )
            )
            // Line stroke
            drawPath(
                linePath,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Overlay: weather icons + temp labels at each sample point
        samples.forEachIndexed { i, sample ->
            val xNorm = xs[i]
            val yNorm = ys[i]

            val iconSize = 26.dp
            val centerX = chartRegionLeftDp + chartRegionWidthDp * xNorm
            val centerY = labelHeight + chartHeight * yNorm
            // Offset so the icon is centered on (centerX, centerY)
            val iconLeftDp = centerX - iconSize / 2
            val iconTopDp = centerY - iconSize / 2

            // Icon disc (surface bg matches card so the line "dips under" the icon cleanly)
            val condColor = colorForCondition(sample.weatherCondition)
            Box(
                modifier = Modifier
                    .offset(x = iconLeftDp, y = iconTopDp)
                    .size(iconSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconForCondition(sample.weatherCondition),
                    contentDescription = null,
                    tint = condColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Temp label above the icon
            val tempStr = "${displayedTemps[i].toInt()}$tempUnitLabel"
            val labelW = 36.dp
            Box(
                modifier = Modifier
                    .offset(
                        x = centerX - labelW / 2,
                        y = (iconTopDp - 18.dp).coerceAtLeast(0.dp)
                    )
                    .width(labelW)
            ) {
                Text(
                    text = tempStr,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun iconForCondition(c: WeatherCondition): ImageVector = when (c) {
    WeatherCondition.CLEAR -> CustomIcons.WeatherSunny
    WeatherCondition.PARTLY_CLOUDY -> CustomIcons.WeatherPartlyCloudy
    WeatherCondition.FOG -> CustomIcons.WeatherFog
    WeatherCondition.DRIZZLE -> CustomIcons.WeatherDrizzle
    WeatherCondition.RAIN -> CustomIcons.WeatherRain
    WeatherCondition.SNOW -> CustomIcons.WeatherSnow
    WeatherCondition.THUNDERSTORM -> CustomIcons.WeatherThunderstorm
}

private fun colorForCondition(c: WeatherCondition): Color = when (c) {
    WeatherCondition.CLEAR -> Color(0xFFFFC107)
    WeatherCondition.PARTLY_CLOUDY -> Color(0xFF8BAEE8)
    WeatherCondition.FOG -> Color(0xFF90A4AE)
    WeatherCondition.DRIZZLE -> Color(0xFF64B5F6)
    WeatherCondition.RAIN -> Color(0xFF1E88E5)
    WeatherCondition.SNOW -> Color(0xFF42A5F5)
    WeatherCondition.THUNDERSTORM -> Color(0xFF7E57C2)
}
