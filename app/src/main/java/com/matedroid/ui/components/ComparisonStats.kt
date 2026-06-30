package com.matedroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Comparison delta colours: green = better, amber = worse, regardless of the sign of the change. */
val DeltaGood = Color(0xFF4CAF50)
val DeltaBad = Color(0xFFE0884A)

enum class DeltaTone { GOOD, BAD, NEUTRAL }

/** A signed percentage difference of one run versus the base, plus whether that's an improvement. */
data class RowDelta(val signedPercent: Int, val better: Boolean)

/** Signed percentage difference of [value] from [reference] (rounded to whole percent). */
fun percentDelta(value: Double, reference: Double): Int =
    if (reference == 0.0) 0 else (((value - reference) / reference) * 100).roundToInt()

/** Whether [value] beats [reference] given the metric's direction. */
fun isBetter(value: Double, reference: Double, higherIsBetter: Boolean): Boolean =
    if (higherIsBetter) value > reference else value < reference

/** Small coloured "+12% / −8%" chip shown on a leaderboard row, relative to the base run. */
@Composable
fun DeltaChip(delta: RowDelta?, modifier: Modifier = Modifier) {
    if (delta == null || delta.signedPercent == 0) return
    val color = if (delta.better) DeltaGood else DeltaBad
    val sign = if (delta.signedPercent > 0) "+" else ""
    Text(
        text = "$sign${delta.signedPercent}%",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/**
 * Headline verdict for the comparison screen: the base run versus the set's average ([primary]),
 * a quiet [secondary] line (rank and gap to the best), and an optional [costLine] (charges only).
 */
@Composable
fun ComparisonVerdict(
    accent: Color,
    primary: String,
    tone: DeltaTone,
    secondary: String?,
    costLine: String?,
    badge: String? = null,
    modifier: Modifier = Modifier
) {
    val primaryColor = when (tone) {
        DeltaTone.GOOD -> DeltaGood
        DeltaTone.BAD -> DeltaBad
        DeltaTone.NEUTRAL -> accent
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (costLine != null) {
                Text(
                    text = costLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Special badge when this run is the best on the route / at this location.
        if (badge != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "🏆 $badge",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(accent.copy(alpha = 0.22f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
