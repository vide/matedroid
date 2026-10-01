package com.matedroid.ui.screens.dashboard

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.domain.SinceLastChargeStats
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.ui.theme.CarColorPalette
import java.time.OffsetDateTime

/**
 * "Since last charge" summary — page 2 of the dashboard carousel (issue #339).
 * The kWh consumed is the hero figure: it answers "will one night of charging
 * be enough?" at a glance. Tapping opens the Drives list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SinceLastChargeCard(
    stats: SinceLastChargeStats,
    currentBatteryLevel: Int?,
    units: Units?,
    palette: CarColorPalette,
    onClick: () -> Unit
) {
    val dark = isSystemInDarkTheme()

    // Localized relative time of the anchoring charge ("2 days ago").
    val relativeTime = remember(stats.chargeEndDate) {
        try {
            val epochMs = OffsetDateTime.parse(stats.chargeEndDate).toInstant().toEpochMilli()
            DateUtils.getRelativeTimeSpanString(
                epochMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            ).toString()
        } catch (_: Exception) {
            null
        }
    }

    // SoC spent this cycle: level at the end of the anchoring charge minus now.
    // Includes vampire drain on purpose — that energy is gone from the cycle too.
    val socUsed = if (stats.chargeEndBatteryLevel != null && currentBatteryLevel != null) {
        (stats.chargeEndBatteryLevel - currentBatteryLevel).takeIf { it >= 0 }
    } else null

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DASHBOARD_CAROUSEL_HEIGHT)
                // Accent wash from the top-right corner, echoing the Trips hero tile
                // so the page has visual weight matching the map without imagery.
                .background(
                    Brush.linearGradient(
                        colors = listOf(palette.accent.copy(alpha = 0.26f), Color.Transparent),
                        start = Offset(Float.POSITIVE_INFINITY, 0f),
                        end = Offset(0f, Float.POSITIVE_INFINITY)
                    )
                )
        ) {
            // Label row, top-left.
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 14.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.ElectricBolt,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.since_last_charge_title).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 2,
                    color = palette.onSurfaceVariant
                )
                if (relativeTime != null) {
                    Text(
                        text = " · $relativeTime",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }

            // Hero + chips, bottom-left — mirroring where the map card puts its headline.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp, end = 48.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "%.1f".format(stats.energyConsumedKwh),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "kWh",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
                Text(
                    text = pluralStringResource(
                        R.plurals.since_last_charge_drives, stats.driveCount, stats.driveCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CarouselChip(
                        icon = CustomIcons.Road,
                        text = UnitFormatter.formatDistance(stats.distance, units, decimals = 0)
                    )
                    stats.avgConsumptionWh?.let { avg ->
                        CarouselChip(
                            icon = Icons.Filled.ElectricBolt,
                            text = UnitFormatter.formatEfficiency(avg, units, decimals = 0)
                        )
                    }
                    socUsed?.let { soc ->
                        CarouselChip(
                            icon = Icons.Filled.ArrowDownward,
                            text = stringResource(R.string.since_last_charge_soc_used, soc)
                        )
                    }
                }
            }

            // Chevron affordance — same treatment as the map card.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(32.dp)
                    .background(
                        if (dark) Color.Black.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.55f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = palette.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
