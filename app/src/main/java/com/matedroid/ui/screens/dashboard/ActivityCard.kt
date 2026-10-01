package com.matedroid.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matedroid.R
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.Trip
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.ui.screens.trips.displayName
import com.matedroid.ui.theme.CarColorPalette

@Composable
internal fun VehicleInfoCard(
    status: CarStatus,
    units: Units?,
    palette: CarColorPalette,
    totalCharges: Int?,
    totalDrives: Int?,
    totalTrips: Int? = null,
    latestTrip: Trip? = null,
    onNavigateToCharges: () -> Unit,
    onNavigateToDrives: () -> Unit,
    onNavigateToMileage: () -> Unit,
    onNavigateToUpdates: () -> Unit,
    onNavigateToTrips: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.activity_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bento: tall Trips hero (left) + Mileage / Charges stacked (right).
            // IntrinsicSize.Min lets the hero match the combined height of the two
            // right-hand tiles so the three cells line up flush.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TripsHeroTile(
                    totalTrips = totalTrips,
                    latestTrip = latestTrip,
                    units = units,
                    palette = palette,
                    onClick = onNavigateToTrips,
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NavButton(
                        title = stringResource(R.string.nav_mileage),
                        value = status.odometer?.let {
                            val value = it
                            "%,.0f %s".format(value, UnitFormatter.getDistanceUnit(units))
                        } ?: "--",
                        icon = CustomIcons.Road,
                        palette = palette,
                        onClick = onNavigateToMileage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    NavButton(
                        title = stringResource(R.string.nav_charges),
                        value = totalCharges?.let { "%,d".format(it) } ?: "--",
                        icon = Icons.Filled.ElectricBolt,
                        palette = palette,
                        onClick = onNavigateToCharges,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer strip: Drives + Software. Weights match the bento row above
            // (1.15 : 1) so Drives lines up under Trips and Software under the tiles.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NavButton(
                    title = stringResource(R.string.nav_drives),
                    value = totalDrives?.let { "%,d".format(it) } ?: "--",
                    icon = CustomIcons.SteeringWheel,
                    palette = palette,
                    onClick = onNavigateToDrives,
                    modifier = Modifier.weight(1.15f)
                )
                NavButton(
                    title = stringResource(R.string.nav_software),
                    value = status.version ?: "--",
                    icon = Icons.Filled.Settings,
                    palette = palette,
                    onClick = onNavigateToUpdates,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * The Trips hero tile: a tall, accent-washed cell that anchors the Activity card.
 * Shows the trip count with a "latest trip" teaser, or an inviting empty state when
 * no road-trips (auto-detected drives ≥300 km) have been recorded yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripsHeroTile(
    totalTrips: Int?,
    latestTrip: Trip?,
    units: Units?,
    palette: CarColorPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = palette.onSurface.copy(alpha = 0.05f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (totalTrips == 0) {
                    // Empty state — no road-trips detected yet.
                    Text(
                        text = stringResource(R.string.trips_empty_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.trips_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onSurfaceVariant
                    )
                } else {
                    // Big count in the top-left, with "Trips" beside it.
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = totalTrips?.let { "%,d".format(it) } ?: "--",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.nav_trips),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (latestTrip != null) {
                        Text(
                            text = stringResource(R.string.trip_latest_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onSurfaceVariant
                        )
                        Text(
                            text = latestTrip.displayName(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = run {
                                    val v = latestTrip.totalDistance
                                    "%,.0f %s".format(v, UnitFormatter.getDistanceUnit(units))
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavButton(
    title: String,
    value: String,
    icon: ImageVector,
    palette: CarColorPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = palette.onSurface.copy(alpha = 0.05f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading icon, vertically centered so it spans the value + label lines.
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = palette.accent
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface,
                    maxLines = 1
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant,
                    // Shrink long localized labels (e.g. Spanish "Trayectos",
                    // Catalan "Trajectes") to fit one line instead of wrapping.
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 9.sp,
                        maxFontSize = 11.sp,
                        stepSize = 0.5.sp
                    )
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = palette.onSurfaceVariant
            )
        }
    }
}
