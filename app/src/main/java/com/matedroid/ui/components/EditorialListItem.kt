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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
        Box(modifier = Modifier.fillMaxWidth()) {
            // Soft accent halo, top-right corner. Card clips to its rounded shape so the
            // negative-offset circle bleeds into the corner without escaping the card.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 40.dp, y = (-40).dp)
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.14f), Color.Transparent)
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
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
                        .padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = dateline,
                                color = accent,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                letterSpacing = 1.2.sp,
                                maxLines = 1
                            )
                            if (datelineTrailing != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                datelineTrailing()
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            letterSpacing = (-0.3).sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            content = pills
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = heroValue,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1).sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = heroUnit.uppercase(Locale.getDefault()),
                            color = accent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            letterSpacing = 1.2.sp,
                            maxLines = 1
                        )
                    }
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
            .padding(horizontal = 7.dp, vertical = 3.dp),
        color = color,
        fontWeight = fontWeight,
        fontSize = 11.sp,
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
