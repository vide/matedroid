package com.matedroid.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.ui.theme.CarColorPalette
import java.util.Locale

/**
 * Text style for the small caps labels and heavy numbers of the trophy room: explicit size,
 * weight and tracking, line height equal to the size and no font padding so stacked labels
 * sit as tight as in the Drives/Charges editorial rows.
 */
internal fun capsStyle(fontSize: TextUnit, fontWeight: FontWeight, letterSpacing: TextUnit): TextStyle =
    TextStyle(
        fontSize = fontSize,
        lineHeight = fontSize * 1.2f,
        fontWeight = fontWeight,
        letterSpacing = letterSpacing,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

/**
 * The three headline records — top speed, most distance in a day, biggest charge — as peer
 * cards of equal height. Records that do not exist are dropped; nothing is drawn without any.
 */
@Composable
internal fun HeadlineTrophies(rows: List<RecordRow>, palette: CarColorPalette) {
    val headlines = rows.filter { it.headline != null }.sortedBy { it.headline }
    if (headlines.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        headlines.forEach { row ->
            HeadlineTrophyCard(
                row = row,
                icon = when (row.headline) {
                    HeadlineTrophy.TOP_SPEED -> Icons.Default.Speed
                    HeadlineTrophy.MOST_DISTANCE_DAY -> CustomIcons.Road
                    else -> Icons.Default.BatteryChargingFull
                },
                palette = palette,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun HeadlineTrophyCard(
    row: RecordRow,
    icon: ImageVector,
    palette: CarColorPalette,
    modifier: Modifier = Modifier
) {
    val locale = Locale.getDefault()
    Box(
        modifier = modifier
            .heightIn(min = 150.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .then(if (row.onClick != null) Modifier.clickable(onClick = row.onClick) else Modifier)
    ) {
        // The icon is the card's background: large, in a faint accent, kept 12 dp inside the
        // card, and masked by a diagonal gradient so it dissolves into nothing towards its
        // top-left and is fully there at its bottom-right. The mask needs an offscreen layer
        // so DstIn only cuts the icon, not what is behind it.
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.accent.copy(alpha = 0.18f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(96.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.linearGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.22f to Color.Transparent,
                                0.6f to Color.Black.copy(alpha = 0.55f),
                                1f to Color.Black
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        )
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 12.dp, top = 14.dp, end = 12.dp, bottom = 12.dp)
        ) {
            Text(
                text = row.title.uppercase(locale),
                style = capsStyle(10.sp, FontWeight.ExtraBold, 1.2.sp),
                color = palette.accent,
                minLines = 2,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = row.heroValue,
                style = capsStyle(26.sp, FontWeight.ExtraBold, (-0.9).sp).copy(lineHeight = 26.sp),
                color = palette.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = row.heroUnit.uppercase(locale),
                style = capsStyle(10.sp, FontWeight.Bold, 1.2.sp),
                color = palette.accent,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = row.dateline,
                style = capsStyle(9.5.sp, FontWeight.Bold, 1.sp),
                color = palette.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.weight(1f))
            if (row.onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = palette.onSurfaceVariant
                )
            }
        }
    }
}
