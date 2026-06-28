package com.matedroid.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt

/** One session's charging curve in power-vs-SoC space. [points] are (SoC %, power kW). */
data class OverlayCurve(
    val label: String,
    val color: Color,
    val isBase: Boolean,
    val points: List<Offset>
)

/**
 * Overlays several charging sessions' power curves against state-of-charge. The base session is
 * drawn bold with a soft fill; tap/drag shows a crosshair and each session's power at that SoC.
 * Follows the project chart rules: 4 Y labels (¼/½/¾/end), 5 X labels (start/¼/½/¾/end), tap tooltip.
 */
@Composable
fun PowerSocOverlayChart(
    curves: List<OverlayCurve>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 190.dp,
    xUnit: String = "%",
    xCaption: String = " SoC",
    valueUnit: String = "kW"
) {
    val valid = curves.filter { it.points.size >= 2 }
    if (valid.isEmpty()) return

    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val crosshairColor = MaterialTheme.colorScheme.onSurface

    val socMin = valid.minOf { c -> c.points.minOf { it.x } }
    val socMax = valid.maxOf { c -> c.points.maxOf { it.x } }
    val socRange = (socMax - socMin).coerceAtLeast(1f)
    val powerMaxRaw = valid.maxOf { c -> c.points.maxOf { it.y } }
    val powerMax = (ceil(powerMaxRaw / 20f) * 20f).coerceAtLeast(20f)

    val density = LocalDensity.current
    val chartHeightPx = with(density) { chartHeight.toPx() }
    val labelStripPx = with(density) { 18.dp.toPx() }
    val topPad = with(density) { 6.dp.toPx() }

    var selectedSoc by remember { mutableStateOf<Float?>(null) }

    val labelPaint = remember(labelColor) {
        android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = 26f
            isAntiAlias = true
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight + 18.dp)
                .pointerInput(valid, socMin, socMax) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        fun update(x: Float) {
                            val frac = (x / size.width).coerceIn(0f, 1f)
                            selectedSoc = socMin + frac * socRange
                        }
                        update(down.position.x)
                        drag(down.id) { change ->
                            change.consume()
                            update(change.position.x)
                        }
                    }
                }
        ) {
            val w = size.width
            val plotH = chartHeightPx - topPad
            fun xFor(soc: Float) = (soc - socMin) / socRange * w
            fun yFor(power: Float) = topPad + (1f - power / powerMax) * plotH

            // Horizontal gridlines + Y labels at 25/50/75/100% of the power range
            for (i in 1..4) {
                val v = powerMax * i / 4f
                val y = yFor(v)
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                drawContext.canvas.nativeCanvas.drawText("${v.roundToInt()}", 8f, y - 6f, labelPaint)
            }

            // X labels at 5 SoC positions
            for (i in 0..4) {
                val soc = socMin + socRange * i / 4f
                val x = xFor(soc)
                val txt = "${soc.roundToInt()}$xUnit"
                val tw = labelPaint.measureText(txt)
                val tx = when (i) {
                    0 -> 0f
                    4 -> (x - tw).coerceAtMost(w - tw)
                    else -> x - tw / 2
                }
                drawContext.canvas.nativeCanvas.drawText(
                    txt, tx.coerceAtLeast(0f), chartHeightPx + labelStripPx - 2f, labelPaint
                )
            }

            // Curves — others first so the base draws on top
            valid.sortedBy { it.isBase }.forEach { c ->
                val pts = c.points.sortedBy { it.x }
                val path = Path()
                pts.forEachIndexed { idx, p ->
                    val px = xFor(p.x)
                    val py = yFor(p.y)
                    if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                if (c.isBase) {
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(xFor(pts.last().x), yFor(0f))
                        lineTo(xFor(pts.first().x), yFor(0f))
                        close()
                    }
                    drawPath(
                        fill,
                        Brush.verticalGradient(listOf(c.color.copy(alpha = 0.25f), c.color.copy(alpha = 0f)))
                    )
                }
                drawPath(
                    path,
                    c.color,
                    style = Stroke(
                        width = if (c.isBase) 7f else 4f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Crosshair + per-curve dots
            selectedSoc?.let { soc ->
                val cx = xFor(soc)
                drawLine(
                    crosshairColor.copy(alpha = 0.4f),
                    Offset(cx, topPad),
                    Offset(cx, chartHeightPx),
                    strokeWidth = 1.5f
                )
                valid.forEach { c ->
                    val power = interpolatePower(c.points, soc) ?: return@forEach
                    drawCircle(c.color, radius = if (c.isBase) 7f else 5f, center = Offset(cx, yFor(power)))
                }
            }
        }

        // Tooltip — SoC and each session's power at the crosshair
        selectedSoc?.let { soc ->
            val rows = valid.mapNotNull { c -> interpolatePower(c.points, soc)?.let { c to it } }
            if (rows.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "${soc.roundToInt()}$xUnit$xCaption",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    rows.forEach { (curve, power) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(curve.color)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${power.roundToInt()} $valueUnit",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Linear interpolation of power at [soc] along a curve sorted by SoC; null when out of range. */
private fun interpolatePower(points: List<Offset>, soc: Float): Float? {
    val sorted = points.sortedBy { it.x }
    if (sorted.isEmpty() || soc < sorted.first().x || soc > sorted.last().x) return null
    for (i in 1 until sorted.size) {
        val a = sorted[i - 1]
        val b = sorted[i]
        if (soc <= b.x) {
            val span = (b.x - a.x)
            if (span <= 0f) return a.y
            val t = (soc - a.x) / span
            return a.y + t * (b.y - a.y)
        }
    }
    return sorted.last().y
}
