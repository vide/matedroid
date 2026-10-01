package com.matedroid.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.matedroid.R
import com.matedroid.data.api.models.TpmsDetails
import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.StatusSuccess
import com.matedroid.ui.theme.StatusWarning

@Composable
internal fun TirePressureCard(
    tpms: TpmsDetails,
    units: Units?,
    palette: CarColorPalette
) {
    // Status comes purely from Tesla's per-tyre soft-warning flag; the printed number is
    // authoritative (there's no recommended/target pressure in the data).
    val unitLabel = UnitFormatter.getPressureUnit(units)
    val pressures = listOf(tpms.pressureFl, tpms.pressureFr, tpms.pressureRl, tpms.pressureRr)
    val warnings = listOf(
        tpms.warningFl == true, tpms.warningFr == true,
        tpms.warningRl == true, tpms.warningRr == true
    )
    val anyLow = warnings.any { it }
    val statusColor = if (anyLow) StatusWarning else StatusSuccess
    val lineColor = palette.onSurface.copy(alpha = 0.08f)
    // Open by default when a tyre is low; tap the verdict line to fold/unfold. Re-keyed on
    // anyLow so a newly-detected low auto-opens (and an all-clear auto-folds).
    var expanded by remember(anyLow) { mutableStateOf(anyLow) }

    // Verdict meta: lowest warned value when low, else the range across all tyres.
    val metaText = if (anyLow) {
        pressures.filterIndexed { i, _ -> warnings[i] }.filterNotNull().minOrNull()
            ?.let { "%.1f %s".format(it, unitLabel) } ?: unitLabel
    } else {
        val present = pressures.filterNotNull()
        val mn = present.minOrNull()
        val mx = present.maxOrNull()
        when {
            mn == null || mx == null -> unitLabel
            mn == mx -> "%.1f %s".format(mn, unitLabel)
            else -> "%.1f–%.1f %s".format(mn, mx, unitLabel)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Verdict line — the plain-language answer first; tap to fold/unfold detail.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.TireRepair,
                    contentDescription = stringResource(R.string.tire_pressure_title),
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(statusColor.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (anyLow) Icons.Filled.PriorityHigh else Icons.Filled.Check,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(if (anyLow) R.string.tire_pressure_low else R.string.tire_pressure_all_ok),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (anyLow) FontWeight.Bold else FontWeight.Normal,
                    color = if (anyLow) statusColor else palette.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = palette.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Foldable per-wheel detail — open by default when a tyre is low.
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Segment bar — one connected instrument; the low tyre lights its slice.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, lineColor, RoundedCornerShape(12.dp))
                    ) {
                        TyreSegment(stringResource(R.string.tire_fl), tpms.pressureFl, tpms.warningFl == true, palette, Modifier.weight(1f).fillMaxHeight())
                        VerticalDivider(color = lineColor)
                        TyreSegment(stringResource(R.string.tire_fr), tpms.pressureFr, tpms.warningFr == true, palette, Modifier.weight(1f).fillMaxHeight())
                        VerticalDivider(color = lineColor)
                        TyreSegment(stringResource(R.string.tire_rl), tpms.pressureRl, tpms.warningRl == true, palette, Modifier.weight(1f).fillMaxHeight())
                        VerticalDivider(color = lineColor)
                        TyreSegment(stringResource(R.string.tire_rr), tpms.pressureRr, tpms.warningRr == true, palette, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

/** One segment of the tyre bar: status-coloured top edge + faint tint, position label, value. */
@Composable
private fun TyreSegment(
    label: String,
    pressure: Double?,
    warning: Boolean,
    palette: CarColorPalette,
    modifier: Modifier = Modifier
) {
    val statusColor = if (warning) StatusWarning else StatusSuccess
    Column(
        modifier = modifier.background(statusColor.copy(alpha = if (warning) 0.12f else 0.07f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(statusColor)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = palette.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = pressure?.let { "%.1f".format(it) } ?: "--",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (warning) statusColor else palette.onSurface,
                maxLines = 1
            )
        }
    }
}
