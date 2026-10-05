package com.matedroid.ui.screens.battery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.screens.battery.CellSpec.CELL_X
import com.matedroid.ui.theme.CarColorPalette
import java.util.Locale
import kotlin.math.sqrt

private const val MINUS = "−"

/**
 * The Battery Health pictogram: a battery cell whose outline is the pack when new, whose accent
 * fill is the usable capacity now, with the lost share hatched above the fill and today's charge
 * as a knocked-out line inside it. Energy figures hang off leader lines on the right, range
 * figures on the left (only when the API reports a range).
 *
 * Everything textual is a real [Text] (font scaling, TalkBack); the [Canvas] draws only shapes.
 * Both are positioned from the same [layoutCell] numbers.
 */
@Composable
internal fun BatteryCellFigure(
    stats: BatteryStats,
    units: Units?,
    palette: CarColorPalette,
    modifier: Modifier = Modifier
) {
    val showRange = stats.maxRangeNew > 0
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val baseStyle = LocalTextStyle.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    val heroNumber = "%.1f".format(stats.healthPercent)
    val captionText = stringResource(R.string.battery_cell_health_caption).uppercase(Locale.getDefault())
    val todayText = stringResource(R.string.battery_today_label, stats.usableBatteryLevel)
        .uppercase(Locale.getDefault())

    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        // Scale the whole figure (shapes and type) down on narrow cards; never up.
        val scale = (maxWidth.value / CellSpec.FIG_W).coerceAtMost(1f)
        fun u(v: Float) = (v * scale).dp
        fun s(v: Float) = (v * scale).sp

        val heroStyle = baseStyle.merge(
            TextStyle(
                fontSize = s(44f),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = s(-1.6f),
                lineHeight = s(46f),
                textAlign = TextAlign.Center
            )
        )
        val heroUnitSpan = SpanStyle(fontSize = s(20f), fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp)
        val capsStyle = baseStyle.merge(
            TextStyle(
                fontSize = s(9.5f),
                fontWeight = FontWeight.Bold,
                letterSpacing = s(1f),
                lineHeight = s(12f),
                textAlign = TextAlign.Center
            )
        )
        val todayStyle = capsStyle.merge(TextStyle(fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End))
        val heroAnnotated = buildAnnotatedString {
            append(heroNumber)
            withStyle(heroUnitSpan) { append(" %") }
        }

        // Measure the texts that must fit inside the fill, in figure units, so the placement rules
        // see the real (font-scaled, localised) sizes.
        val pxPerUnit = scale * density.density
        val innerWidthPx = (CellSpec.INNER_W * pxPerUnit).toInt().coerceAtLeast(1)
        val heroHeight = remember(heroAnnotated, captionText, heroStyle, capsStyle, innerWidthPx) {
            val c = Constraints(maxWidth = innerWidthPx)
            val h = measurer.measure(heroAnnotated, heroStyle, constraints = c).size.height +
                measurer.measure(captionText, capsStyle, constraints = c).size.height
            h / pxPerUnit
        }
        val todayHeight = remember(todayText, todayStyle, innerWidthPx) {
            measurer.measure(todayText, todayStyle, maxLines = 1, constraints = Constraints(maxWidth = innerWidthPx))
                .size.height / pxPerUnit
        }
        val cell = remember(stats.healthPercent, stats.usableBatteryLevel, heroHeight, todayHeight) {
            layoutCell(stats.healthPercent, stats.usableBatteryLevel, heroHeight, todayHeight)
        }

        Box(modifier = Modifier.size(u(CellSpec.FIG_W), u(CellSpec.FIG_H))) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCell(cell, palette, size.width / CellSpec.FIG_W)
                drawLeaders(
                    cell = cell,
                    left = showRange,
                    lineColor = outlineVariant,
                    strongColor = palette.onSurfaceVariant,
                    haloColor = palette.surface,
                    unit = size.width / CellSpec.FIG_W
                )
            }

            // Today caption inside the fill, right-aligned
            cell.todayLabelTop?.let { top ->
                Text(
                    text = todayText,
                    style = todayStyle,
                    color = palette.surface,
                    maxLines = 1,
                    modifier = Modifier
                        .offset(x = u(CELL_X + CellSpec.INNER_LEFT + 6f), y = u(top))
                        .width(u(CellSpec.INNER_W - 12f))
                )
            }

            // Hero: knocked out of the fill, or in on-surface above it when the fill is too short
            val heroColor = if (cell.heroInFill) palette.surface else palette.onSurface
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset(x = u(CELL_X + CellSpec.INNER_LEFT), y = u(cell.heroTop))
                    .width(u(CellSpec.INNER_W))
            ) {
                Text(text = heroAnnotated, style = heroStyle, color = heroColor, maxLines = 1)
                Text(text = captionText, style = capsStyle, color = heroColor)
            }

            // Right column: energy
            val loss = stats.lossKwh.coerceAtLeast(0.0)
            val rightRows = listOf(
                Triple("%.1f".format(stats.originalCapacity), stringResource(R.string.battery_when_new), false),
                Triple(MINUS + "%.1f".format(loss), stringResource(R.string.battery_lost), true),
                Triple("%.1f".format(stats.currentCapacity), stringResource(R.string.battery_usable_now), false)
            )
            rightRows.forEachIndexed { i, (value, caption, isLoss) ->
                LeaderLabel(
                    value = value,
                    unit = "kWh",
                    caption = caption,
                    alignEnd = false,
                    isLoss = isLoss,
                    palette = palette,
                    scale = scale,
                    modifier = Modifier
                        .offset(x = u(CellSpec.RIGHT_LABEL_X), y = u(cell.labelTops[i]))
                        .width(u(CellSpec.LABEL_W))
                )
            }

            // Left column: range at 100 %
            if (showRange) {
                val distanceUnit = UnitFormatter.getDistanceUnit(units)
                val leftRows = listOf(
                    Triple("%,.0f".format(stats.maxRangeNew), stringResource(R.string.battery_when_new), false),
                    Triple(MINUS + "%,.0f".format(stats.rangeLoss.coerceAtLeast(0.0)), stringResource(R.string.battery_lost), true),
                    Triple("%,.0f".format(stats.maxRangeNow), stringResource(R.string.battery_range_now), false)
                )
                leftRows.forEachIndexed { i, (value, caption, isLoss) ->
                    LeaderLabel(
                        value = value,
                        unit = distanceUnit,
                        caption = caption,
                        alignEnd = true,
                        isLoss = isLoss,
                        palette = palette,
                        scale = scale,
                        modifier = Modifier
                            .offset(y = u(cell.labelTops[i]))
                            .width(u(CellSpec.LABEL_W))
                    )
                }
            }

            // Column keys at the foot of the cell
            if (cell.showKeys) {
                val keyStyle = baseStyle.merge(
                    TextStyle(
                        fontSize = s(10f),
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = s(1.2f),
                        lineHeight = s(13f)
                    )
                )
                if (showRange) {
                    Box(
                        contentAlignment = Alignment.BottomEnd,
                        modifier = Modifier.size(u(CellSpec.LABEL_W), u(CellSpec.FIG_H))
                    ) {
                        Text(
                            text = stringResource(R.string.battery_key_range_at_100).uppercase(Locale.getDefault()),
                            style = keyStyle.merge(TextStyle(textAlign = TextAlign.End)),
                            color = palette.accent,
                            maxLines = 2
                        )
                    }
                }
                Box(
                    contentAlignment = Alignment.BottomStart,
                    modifier = Modifier
                        .offset(x = u(CellSpec.RIGHT_LABEL_X))
                        .size(u(CellSpec.LABEL_W), u(CellSpec.FIG_H))
                ) {
                    Text(
                        text = stringResource(R.string.battery_key_capacity_usable).uppercase(Locale.getDefault()),
                        style = keyStyle,
                        color = palette.accent,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

/** A figure hanging off a leader: bold value with a small accent unit, caps caption below. */
@Composable
private fun LeaderLabel(
    value: String,
    unit: String,
    caption: String,
    alignEnd: Boolean,
    isLoss: Boolean,
    palette: CarColorPalette,
    scale: Float,
    modifier: Modifier = Modifier
) {
    fun s(v: Float) = (v * scale).sp
    val align = if (alignEnd) TextAlign.End else TextAlign.Start
    val text: AnnotatedString = buildAnnotatedString {
        append(value)
        withStyle(
            SpanStyle(
                fontSize = s(10f),
                fontWeight = FontWeight.Bold,
                letterSpacing = s(1f),
                color = palette.accent
            )
        ) { append(" $unit") }
    }
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = LocalTextStyle.current.merge(
                TextStyle(
                    fontSize = s(15f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = s(-0.3f),
                    lineHeight = s(18f),
                    textAlign = align
                )
            ),
            color = if (isLoss) palette.accent else palette.onSurface,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = caption.uppercase(Locale.getDefault()),
            style = LocalTextStyle.current.merge(
                TextStyle(
                    fontSize = s(9.5f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = s(1f),
                    lineHeight = s(11.5f),
                    textAlign = align
                )
            ),
            color = palette.onSurfaceVariant,
            maxLines = 2,
            modifier = Modifier.padding(top = (3f * scale).dp)
        )
    }
}

/** Outline, cap, fill, hatched sliver and today line. [unit] is px per figure unit. */
private fun DrawScope.drawCell(cell: CellLayout, palette: CarColorPalette, unit: Float) {
    fun x(v: Float) = (CELL_X + v) * unit
    fun y(v: Float) = v * unit

    // Cap and outline: the pack when new
    drawRoundRect(
        color = palette.onSurfaceVariant,
        topLeft = Offset(x(CellSpec.CAP_X), y(CellSpec.CAP_Y)),
        size = Size(CellSpec.CAP_W * unit, CellSpec.CAP_H * unit),
        cornerRadius = CornerRadius(CellSpec.CAP_RADIUS * unit)
    )
    drawRoundRect(
        color = palette.onSurfaceVariant,
        topLeft = Offset(x(CellSpec.OUTLINE_LEFT), y(CellSpec.OUTLINE_TOP)),
        size = Size(CellSpec.OUTLINE_W * unit, CellSpec.OUTLINE_H * unit),
        cornerRadius = CornerRadius(CellSpec.OUTLINE_RADIUS * unit),
        style = Stroke(width = CellSpec.OUTLINE_STROKE * unit)
    )

    val innerLeft = x(CellSpec.INNER_LEFT)
    val innerRight = x(CellSpec.INNER_RIGHT)
    val innerTop = y(CellSpec.INNER_TOP)
    val innerBottom = y(CellSpec.INNER_BOTTOM)
    val fillTop = y(cell.fillTop)
    val inner = Path().apply {
        addRoundRect(
            RoundRect(
                left = innerLeft,
                top = innerTop,
                right = innerRight,
                bottom = innerBottom,
                cornerRadius = CornerRadius(CellSpec.INNER_RADIUS * unit)
            )
        )
    }

    clipPath(inner) {
        // Lost sliver: 1dp diagonal hatch, 6dp apart
        if (fillTop > innerTop) {
            clipRect(left = innerLeft, top = innerTop, right = innerRight, bottom = fillTop) {
                val h = fillTop - innerTop
                val step = 6f * unit * sqrt(2f)
                var sx = innerLeft - h
                while (sx < innerRight) {
                    drawLine(
                        color = palette.accentDim,
                        start = Offset(sx, fillTop),
                        end = Offset(sx + h, innerTop),
                        strokeWidth = 1f * unit
                    )
                    sx += step
                }
            }
        }
        // Usable now
        if (innerBottom > fillTop) {
            drawRect(
                color = palette.accent,
                topLeft = Offset(innerLeft, fillTop),
                size = Size(innerRight - innerLeft, innerBottom - fillTop)
            )
        }
    }

    // Dashed edge between lost and usable
    if (fillTop > innerTop && fillTop < innerBottom) {
        drawLine(
            color = palette.accent.copy(alpha = 0.38f),
            start = Offset(innerLeft, fillTop - 0.5f * unit),
            end = Offset(innerRight, fillTop - 0.5f * unit),
            strokeWidth = 1f * unit,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * unit, 3f * unit))
        )
    }

    // Today: a knocked-out line across the fill
    if (innerBottom - fillTop > 2f * unit) {
        val ty = y(cell.todayY)
        drawLine(
            color = palette.surface,
            start = Offset(innerLeft, ty),
            end = Offset(innerRight, ty),
            strokeWidth = 1.5f * unit
        )
    }
}

/** Leader lines and dots; elbowed where the label had to move away from its anchor. */
private fun DrawScope.drawLeaders(
    cell: CellLayout,
    left: Boolean,
    lineColor: Color,
    strongColor: Color,
    haloColor: Color,
    unit: Float
) {
    fun leader(dotX: Float, dir: Float, anchor: Float, end: Float, strong: Boolean) {
        val color = if (strong) strongColor else lineColor
        val path = Path().apply {
            moveTo(dotX * unit, anchor * unit)
            if (kotlin.math.abs(anchor - end) < 0.5f) {
                lineTo((dotX + dir * 28f) * unit, anchor * unit)
            } else {
                lineTo((dotX + dir * 16f) * unit, anchor * unit)
                lineTo((dotX + dir * 26f) * unit, end * unit)
                lineTo((dotX + dir * 30f) * unit, end * unit)
            }
        }
        drawPath(path, color = color, style = Stroke(width = 1f * unit))
        val center = Offset(dotX * unit, anchor * unit)
        drawCircle(color = if (strong) strongColor else lineColor, radius = 2f * unit, center = center)
        drawCircle(color = haloColor, radius = 2f * unit, center = center, style = Stroke(width = 1.5f * unit))
    }

    cell.anchors.forEachIndexed { i, a ->
        val strong = i == cell.anchors.lastIndex
        leader(CellSpec.RIGHT_DOT_X, 1f, a, cell.labelCenter(i), strong)
        if (left) leader(CellSpec.LEFT_DOT_X, -1f, a, cell.labelCenter(i), strong)
    }
}
