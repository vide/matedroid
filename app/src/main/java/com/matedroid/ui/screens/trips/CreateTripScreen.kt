package com.matedroid.ui.screens.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.data.local.entity.SavedTripLeg
import com.matedroid.domain.LegRef
import com.matedroid.domain.model.Trip
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.util.formatDuration
import com.matedroid.util.formatMediumNoYear
import com.matedroid.util.parseIsoDateTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTripScreen(
    carId: Int,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit = {},
    onTripCreated: (startDate: String) -> Unit = {},
    viewModel: CreateTripViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isDark)

    LaunchedEffect(carId) { viewModel.start(carId) }
    LaunchedEffect(uiState.createdStartDate) {
        uiState.createdStartDate?.let { onTripCreated(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trip_create_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = uiState.preview != null && !uiState.isSaving
                    ) {
                        Text(stringResource(R.string.trip_create_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.trip_create_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            PreviewSummary(uiState.preview, uiState.units, palette)

            if (uiState.drives.isEmpty() && uiState.charges.isEmpty()) {
                EmptyCue(palette)
            } else {
                LegList(
                    drives = uiState.drives,
                    charges = uiState.charges,
                    dcChargeIds = uiState.dcChargeIds,
                    palette = palette,
                    onRemove = viewModel::removeLeg
                )
            }

            OutlinedButton(
                onClick = { viewModel.openAddLegSheet() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.trip_create_add_legs))
            }
        }
    }

    // Step 1: pick the trip's day. Cancelling backs out of creation.
    if (uiState.showDatePicker) {
        TripDatePicker(
            onConfirm = viewModel::onDatePicked,
            onDismiss = onNavigateBack
        )
    }

    if (uiState.showAddLegSheet && uiState.eligibleLegs != null) {
        AddLegSheet(
            eligible = uiState.eligibleLegs!!,
            dcChargeIds = uiState.dcChargeIds,
            palette = palette,
            startInMultiSelect = true,
            onPickLegs = viewModel::pickLegs,
            onDismiss = viewModel::closeAddLegSheet
        )
    }
}

@Composable
private fun PreviewSummary(preview: Trip?, units: com.matedroid.data.api.models.Units?, palette: CarColorPalette) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = preview?.displayName() ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            val meta = if (preview != null) {
                val dist = "%,.0f %s".format(
                    UnitFormatter.formatDistanceValue(preview.totalDistance, units, 0),
                    UnitFormatter.getDistanceUnit(units)
                )
                val dur = formatDuration(LocalContext.current.resources, preview.totalDurationMin)
                val day = parseIsoDateTime(preview.startDate)?.toLocalDate()
                    ?.formatMediumNoYear(Locale.getDefault()) ?: ""
                "$dist · $dur · $day"
            } else {
                "—"
            }
            Text(
                text = meta,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyCue(palette: CarColorPalette) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.onSurface.copy(alpha = 0.04f))
            .padding(vertical = 20.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.trip_create_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onSurfaceVariant
        )
        GhostRow(CustomIcons.SteeringWheel, stringResource(R.string.nav_drives), palette)
        GhostRow(Icons.Filled.ElectricBolt, stringResource(R.string.nav_charges), palette)
    }
}

@Composable
private fun GhostRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, palette: CarColorPalette) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = palette.onSurfaceVariant.copy(alpha = 0.35f)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onSurfaceVariant.copy(alpha = 0.35f)
        )
    }
}

private sealed interface DraftEntry { val startDate: String }
private data class DraftDrive(val drive: DriveSummary) : DraftEntry {
    override val startDate get() = drive.startDate
}
private data class DraftCharge(val charge: ChargeSummary) : DraftEntry {
    override val startDate get() = charge.startDate
}

@Composable
private fun LegList(
    drives: List<DriveSummary>,
    charges: List<ChargeSummary>,
    dcChargeIds: Set<Int>,
    palette: CarColorPalette,
    onRemove: (LegRef) -> Unit
) {
    val context = LocalContext.current
    val entries = (drives.map { DraftDrive(it) } + charges.map { DraftCharge(it) })
        .sortedBy { it.startDate }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (entry in entries) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.onSurface.copy(alpha = 0.05f))
                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (entry) {
                    is DraftDrive -> {
                        val d = entry.drive
                        Icon(
                            CustomIcons.SteeringWheel,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = palette.accent
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${extractCity(d.startAddress)} → ${extractCity(d.endAddress)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "%.1f km · %s".format(
                                    d.distance,
                                    formatDuration(context.resources, d.durationMin)
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onRemove(LegRef(SavedTripLeg.TYPE_DRIVE, d.driveId)) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.trip_create_remove),
                                modifier = Modifier.size(18.dp),
                                tint = palette.onSurfaceVariant
                            )
                        }
                    }
                    is DraftCharge -> {
                        val c = entry.charge
                        val isDc = c.chargeId in dcChargeIds
                        val chip = if (isDc) palette.dcColor else palette.acColor
                        Icon(
                            Icons.Filled.ElectricBolt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = chip
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = extractCity(c.address),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "%s · +%.1f kWh · %s".format(
                                    if (isDc) "DC" else "AC",
                                    c.energyAdded,
                                    formatDuration(context.resources, c.durationMin)
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onRemove(LegRef(SavedTripLeg.TYPE_CHARGE, c.chargeId)) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.trip_create_remove),
                                modifier = Modifier.size(18.dp),
                                tint = palette.onSurfaceVariant
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
private fun TripDatePicker(onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val todayUtc = LocalDate.now(ZoneId.systemDefault())
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
                return utcTimeMillis <= todayUtc
            }
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) {
                    onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                } else {
                    onDismiss()
                }
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    ) {
        DatePicker(
            state = state,
            title = {
                Text(
                    text = stringResource(R.string.trip_create_pick_day),
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                )
            }
        )
    }
}
