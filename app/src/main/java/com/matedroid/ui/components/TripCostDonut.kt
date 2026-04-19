package com.matedroid.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Immutable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.matedroid.ui.theme.CarColorPalette
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

@Immutable
data class CostDonutStop(
    val chargeId: Int,
    val label: String,
    val cost: Double,
    val durationMin: Int,
    val energyAddedKwh: Double,
    val isDc: Boolean
)

/**
 * Assign each stop a visually distinct shade of the AC/DC palette color. Single-type groups
 * get a lightness ramp; cross-type lists map DC entries to DC shades and AC entries to AC
 * shades independently. The returned list is index-aligned with [stops].
 */
fun computeCostShades(
    stops: List<CostDonutStop>,
    palette: CarColorPalette
): List<Color> {
    if (stops.isEmpty()) return emptyList()
    val out = arrayOfNulls<Color>(stops.size)
    val dcIdx = stops.withIndex().filter { it.value.isDc }.map { it.index }
    val acIdx = stops.withIndex().filter { !it.value.isDc }.map { it.index }
    val dcShades = generateShades(palette.dcColor, dcIdx.size)
    val acShades = generateShades(palette.acColor, acIdx.size)
    dcIdx.forEachIndexed { i, origIdx -> out[origIdx] = dcShades[i] }
    acIdx.forEachIndexed { i, origIdx -> out[origIdx] = acShades[i] }
    return out.map { it ?: palette.accent }
}

/**
 * Centered cost donut. Shades for each segment are provided by the caller so the legend/list
 * rendered outside this composable can use the same colors. Tap a segment to focus it: the
 * segment pops outward and the center swaps from total to that stop's details.
 */
@Composable
fun TripCostDonut(
    stops: List<CostDonutStop>,
    shades: List<Color>,
    palette: CarColorPalette,
    currencySymbol: String,
    modifier: Modifier = Modifier
) {
    if (stops.isEmpty()) return

    val totalCost = stops.sumOf { it.cost }.coerceAtLeast(0.01)

    var selectedIndex by remember(stops) { mutableStateOf<Int?>(null) }

    // Only animate the ring sweep the FIRST time we see this particular set of stops.
    // On a fresh composition from back-nav the saveable flag is restored, so the animation
    // is skipped and we snap straight to the fully-drawn ring.
    var hasAnimated by rememberSaveable(stops.hashCode()) { mutableStateOf(false) }
    val sweepProgress = remember(stops) { Animatable(if (hasAnimated) 1f else 0f) }
    LaunchedEffect(stops) {
        if (hasAnimated) {
            sweepProgress.snapTo(1f)
        } else {
            sweepProgress.animateTo(1f, animationSpec = tween(durationMillis = 650))
            hasAnimated = true
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        DonutCanvas(
            stops = stops,
            totalCost = totalCost,
            shades = shades,
            sweepProgress = sweepProgress.value,
            selectedIndex = selectedIndex,
            onSegmentTap = { idx ->
                selectedIndex = if (selectedIndex == idx) null else idx
            },
            palette = palette,
            currencySymbol = currencySymbol
        )
    }
}

@Composable
private fun DonutCanvas(
    stops: List<CostDonutStop>,
    totalCost: Double,
    shades: List<Color>,
    sweepProgress: Float,
    selectedIndex: Int?,
    onSegmentTap: (Int) -> Unit,
    palette: CarColorPalette,
    currencySymbol: String
) {
    val size = 224.dp
    val strokeBase = 44.dp
    val popOut = 5.dp

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(stops) {
                    detectTapGestures { offset ->
                        val cx = this.size.width / 2f
                        val cy = this.size.height / 2f
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        val dist = sqrt(dx * dx + dy * dy)
                        val outer = this.size.width / 2f
                        val strokePx = strokeBase.toPx() + popOut.toPx()
                        val inner = outer - strokePx
                        if (dist < inner * 0.85f || dist > outer * 1.05f) {
                            onSegmentTap(-1)
                            return@detectTapGestures
                        }
                        var angle = atan2(dy, dx) * 180f / PI.toFloat()
                        angle = ((angle + 90f) % 360f + 360f) % 360f
                        var cumulative = 0f
                        for ((i, stop) in stops.withIndex()) {
                            val sweep = (stop.cost / totalCost * 360).toFloat()
                            if (angle <= cumulative + sweep) {
                                onSegmentTap(i)
                                return@detectTapGestures
                            }
                            cumulative += sweep
                        }
                    }
                }
        ) {
            val canvasSize = this.size.minDimension
            val strokePx = strokeBase.toPx()
            val popPx = popOut.toPx()
            val gapDeg = 1.8f

            val baseRadius = (canvasSize - strokePx) / 2f - popPx

            var startAngle = -90f
            stops.forEachIndexed { index, stop ->
                val fullSweep = (stop.cost / totalCost * 360).toFloat() - gapDeg
                val visibleSweep = (fullSweep * sweepProgress).coerceAtLeast(0f)
                val isSelected = index == selectedIndex
                val effectiveStroke = if (isSelected) strokePx + popPx else strokePx
                val radius = if (isSelected) baseRadius + popPx / 2f else baseRadius
                val color = shades.getOrNull(index) ?: palette.accent

                if (isSelected) {
                    drawArc(
                        color = color.copy(alpha = 0.28f),
                        startAngle = startAngle,
                        sweepAngle = visibleSweep,
                        useCenter = false,
                        style = Stroke(width = effectiveStroke + 10.dp.toPx()),
                        topLeft = Offset(
                            (this.size.width - 2 * radius) / 2f,
                            (this.size.height - 2 * radius) / 2f
                        ),
                        size = Size(2 * radius, 2 * radius)
                    )
                }

                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = visibleSweep,
                    useCenter = false,
                    style = Stroke(width = effectiveStroke),
                    topLeft = Offset(
                        (this.size.width - 2 * radius) / 2f,
                        (this.size.height - 2 * radius) / 2f
                    ),
                    size = Size(2 * radius, 2 * radius)
                )

                startAngle += fullSweep + gapDeg
            }
        }

        if (selectedIndex != null && selectedIndex in stops.indices) {
            val stop = stops[selectedIndex]
            val color = shades.getOrNull(selectedIndex) ?: palette.accent
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.22f))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (stop.isDc) "DC" else "AC",
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.size(3.dp))
                Text(
                    text = stop.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = "%.2f %s".format(stop.cost, currencySymbol),
                    style = MaterialTheme.typography.headlineSmall,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "+%.1f kWh · %s".format(stop.energyAddedKwh, formatDonutDuration(stop.durationMin)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "%.2f %s".format(totalCost, currencySymbol),
                    style = MaterialTheme.typography.headlineSmall,
                    color = palette.accent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${stops.size} stops",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun generateShades(base: Color, count: Int): List<Color> {
    if (count <= 0) return emptyList()
    if (count == 1) return listOf(base)
    val minT = -0.35f
    val maxT = 0.30f
    return (0 until count).map { i ->
        val t = minT + (maxT - minT) * (i.toFloat() / (count - 1))
        if (t < 0f) {
            val factor = 1f + t
            Color(
                red = (base.red * factor).coerceIn(0f, 1f),
                green = (base.green * factor).coerceIn(0f, 1f),
                blue = (base.blue * factor).coerceIn(0f, 1f),
                alpha = base.alpha
            )
        } else {
            Color(
                red = (base.red + (1f - base.red) * t).coerceIn(0f, 1f),
                green = (base.green + (1f - base.green) * t).coerceIn(0f, 1f),
                blue = (base.blue + (1f - base.blue) * t).coerceIn(0f, 1f),
                alpha = base.alpha
            )
        }
    }
}

private fun formatDonutDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h >= 24 -> {
            val d = h / 24
            val rh = h % 24
            if (rh > 0) "${d}d${rh}h" else "${d}d"
        }
        h > 0 && m > 0 -> "${h}h${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}
