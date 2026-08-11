package com.matedroid.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.roundToInt

private val overlayDashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))

/** Pre-built render geometry for one curve, so paths aren't rebuilt on every draw frame. */
private class RenderedCurve(
    val curve: OverlayCurve,
    val path: Path,
    val fillPath: Path?,
    val fillBrush: Brush?
)

/** One session's curve. [points] are (x, y). [dashed] draws it as a thin dashed reference line. */
data class OverlayCurve(
    val label: String,
    val color: Color,
    val isBase: Boolean,
    val points: List<Offset>,
    val dashed: Boolean = false
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
    valueUnit: String = "kW",
    dismissKey: Any? = null
) {
    val valid = curves.filter { it.points.size >= 2 }
    if (valid.isEmpty()) return
    // Sort each curve's points by SoC once (reused by drawing and interpolation instead of
    // re-sorting on every draw/crosshair frame) and pre-order so the base curve draws last (on top).
    val renderCurves = remember(curves) {
        valid.map { it.copy(points = it.points.sortedBy { p -> p.x }) }
            .sortedBy { it.isBase }
    }

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
    // Track the container width so the tooltip can follow the crosshair X (clamped on screen).
    var containerWidthPx by remember { mutableStateOf(0) }

    // Parent bumps dismissKey (on an outside tap or a scroll) to clear the tooltip.
    LaunchedEffect(dismissKey) { selectedSoc = null }

    // Label size in sp so it respects density and the user's font scale.
    val labelTextSizePx = with(density) { 10.sp.toPx() }
    val labelPaint = remember(labelColor, labelTextSizePx) {
        android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = labelTextSizePx
            isAntiAlias = true
        }
    }

    // Curve geometry (paths, base fill + gradient brush) is rebuilt only when the data or
    // canvas geometry changes — not on every draw frame during crosshair drags. The Canvas
    // below fills the container, so containerWidthPx (tracked via onSizeChanged) is its width.
    val renderedCurves = remember(renderCurves, containerWidthPx, socMin, socMax, powerMax, chartHeightPx) {
        val w = containerWidthPx.toFloat()
        if (w <= 0f) return@remember emptyList()
        val plotH = chartHeightPx - topPad
        fun xFor(soc: Float) = (soc - socMin) / socRange * w
        fun yFor(power: Float) = topPad + (1f - power / powerMax) * plotH
        renderCurves.map { c ->
            val pts = c.points
            val path = Path()
            pts.forEachIndexed { idx, p ->
                val px = xFor(p.x)
                val py = yFor(p.y)
                if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            val fillPath = if (c.isBase) {
                Path().apply {
                    addPath(path)
                    lineTo(xFor(pts.last().x), yFor(0f))
                    lineTo(xFor(pts.first().x), yFor(0f))
                    close()
                }
            } else null
            val fillBrush = if (c.isBase) {
                Brush.verticalGradient(listOf(c.color.copy(alpha = 0.25f), c.color.copy(alpha = 0f)))
            } else null
            RenderedCurve(c, path, fillPath, fillBrush)
        }
    }

    Box(modifier = modifier
        .fillMaxWidth()
        .onSizeChanged { containerWidthPx = it.width }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight + 18.dp)
                // Tap or scrub sets the crosshair at that SoC (continuous, no index snapping
                // and no tap-to-dismiss toggle: dismissal comes from the parent's dismissKey).
                .chartScrubber(
                    valid, socMin, socMax,
                    scrubOnDragStart = false,
                    onTap = { fraction, _ -> selectedSoc = socMin + fraction * socRange },
                    onScrub = { fraction, _ -> selectedSoc = socMin + fraction * socRange }
                )
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

            // Curves — others first so the base draws on top (geometry pre-built above)
            renderedCurves.forEach { rc ->
                if (rc.fillPath != null && rc.fillBrush != null) {
                    drawPath(rc.fillPath, rc.fillBrush)
                }
                drawPath(
                    rc.path,
                    rc.curve.color,
                    style = Stroke(
                        width = if (rc.curve.isBase) 7f else if (rc.curve.dashed) 3f else 4f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = if (rc.curve.dashed) overlayDashEffect else null
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
                renderCurves.forEach { c ->
                    val power = interpolatePower(c.points, soc) ?: return@forEach
                    drawCircle(c.color, radius = if (c.isBase) 7f else 5f, center = Offset(cx, yFor(power)))
                }
            }
        }

        // Tooltip — SoC and each session's power at the crosshair. Tracks the crosshair X
        // (clamped on screen) and uses the same inverse-surface style as the other chart tooltips.
        selectedSoc?.let { soc ->
            val rows = renderCurves.mapNotNull { c -> interpolatePower(c.points, soc)?.let { c to it } }
            if (rows.isNotEmpty()) {
                val tooltipFg = MaterialTheme.colorScheme.inverseOnSurface
                ChartTooltip(
                    anchorX = {
                        if (containerWidthPx > 0) {
                            ((soc - socMin) / socRange) * containerWidthPx
                        } else 0f
                    },
                    containerWidthPx = { containerWidthPx.toFloat() },
                    anchorY = null, // pinned near the top of the chart
                    elevated = false,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "${soc.roundToInt()}$xUnit$xCaption",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = tooltipFg
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
                                color = tooltipFg
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Linear interpolation of power at [soc]; [sorted] must already be ordered by SoC. Null when out of range. */
private fun interpolatePower(sorted: List<Offset>, soc: Float): Float? {
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
