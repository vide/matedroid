package com.matedroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * "Magazine editorial" list-item layout shared by the drives and charges screens.
 *
 * Layout: 4 dp accent strip on the leading edge, soft accent halo bleeding in from the
 * top-right corner, left column with an ALL-CAPS dateline (and optional trailing badge)
 * over the title and a FlowRow of supporting pills, right column with the headline
 * metric in display weight and its unit beneath in the accent color.
 */

/**
 * Tight text style: disables Compose's default `includeFontPadding` (which adds ~10% of
 * the font size as half-leading above/below every glyph) and trims line-height padding
 * to both sides. Without this, a Text reads visually compact but still measures taller
 * than its `lineHeight`, leaving phantom whitespace inside cards.
 */
private val tightLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)
private val tightPlatformStyle = PlatformTextStyle(includeFontPadding = false)
private fun tightStyle(
    fontSize: TextUnit,
    lineHeight: TextUnit = fontSize,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
): TextStyle = TextStyle(
    fontSize = fontSize,
    lineHeight = lineHeight,
    fontWeight = fontWeight,
    color = color,
    letterSpacing = letterSpacing,
    platformStyle = tightPlatformStyle,
    lineHeightStyle = tightLineHeightStyle,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorialListItem(
    accent: Color,
    dateline: String,
    title: String,
    heroValue: String,
    heroUnit: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    datelineTrailing: (@Composable RowScope.() -> Unit)? = null,
    pills: @Composable FlowRowScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        // Halo is drawn as a paint effect via drawBehind, NOT as a child Box. A child
        // Box with `size(140.dp)` would have contributed 140 dp to the parent's measured
        // height (align/offset only affect positioning, not measurement) and forced
        // every card to be ≥ 140 dp tall regardless of its content. drawBehind has no
        // layout impact — the row sizes itself purely from its content.
        //
        // Halo position is anchored to the hero column on the right edge: a soft accent
        // glow sits behind the headline km/kWh, visually linking the accent color to
        // the most important number in the row.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .drawBehind {
                    val r = 80.dp.toPx()
                    val center = Offset(
                        x = size.width - 28.dp.toPx(),
                        y = size.height * 0.35f,
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.38f),
                                accent.copy(alpha = 0.12f),
                                accent.copy(alpha = 0.02f),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = r,
                        ),
                        radius = r,
                        center = center,
                    )
                }
        ) {
            // Accent edge — fillMaxHeight inside an IntrinsicSize.Min row picks up the
            // body's intrinsic height without circularity.
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(accent, accent.copy(alpha = 0.4f))
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                ) {
                    // Line 1 — ALL-CAPS dateline (with optional trailing badge).
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = dateline,
                            style = tightStyle(
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = accent,
                                letterSpacing = 1.2.sp,
                            ),
                            maxLines = 1
                        )
                        if (datelineTrailing != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            datelineTrailing()
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    // Line 2 — title (bold), the row's primary identifier.
                    Text(
                        text = title,
                        style = tightStyle(
                            fontSize = 15.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = (-0.3).sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    // Line 3 — supporting pills.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        content = pills
                    )
                }

                // Hero column fills the row height with SpaceBetween so the number sits
                // at the top (next to the dateline) and the unit drops to the bottom
                // (next to the pills). The halo glow drawn behind the right side of the
                // row visually anchors both.
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = heroValue,
                        style = tightStyle(
                            fontSize = 26.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = (-0.8).sp,
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = heroUnit.uppercase(Locale.getDefault()),
                        style = tightStyle(
                            fontSize = 10.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accent,
                            letterSpacing = 1.2.sp,
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Small rounded pill used in the FlowRow under an editorial item's title. Default
 * styling is a faint white-tint background with onSurface text — pass `background` /
 * `color` for category-tinted variants (e.g. accent-tinted battery delta, AC/DC cost).
 */
@Composable
fun EditorialPill(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = Color.White.copy(alpha = 0.05f),
    color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
    fontWeight: FontWeight = FontWeight.SemiBold,
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = tightStyle(
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontWeight = fontWeight,
            color = color,
        ),
        maxLines = 1
    )
}

/**
 * Format an ISO-8601 datetime (with or without offset, as TeslaMate may emit either)
 * as a locale-aware "MON · 14 APR · 09:42" dateline. Falls back to the raw input on
 * parse failure rather than swallowing it — better to show a weird value than to hide
 * it entirely while debugging.
 */
internal fun formatEditorialDate(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return ""
    return try {
        val dt = try {
            OffsetDateTime.parse(dateStr).toLocalDateTime()
        } catch (_: DateTimeParseException) {
            LocalDateTime.parse(dateStr.replace("Z", ""))
        }
        // Middle dots are literal because they're outside the pattern alphabet.
        dt.format(DateTimeFormatter.ofPattern("EEE · d MMM · HH:mm", Locale.getDefault()))
            .uppercase(Locale.getDefault())
    } catch (_: Exception) {
        dateStr
    }
}
