package com.matedroid.ui.screens.trips

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matedroid.R
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.data.local.entity.SavedTripLeg
import com.matedroid.domain.EligibleLegs
import com.matedroid.domain.LegRef
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.ui.theme.CarColorPalette
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AddLegSheet(
    eligible: EligibleLegs,
    dcChargeIds: Set<Int>,
    palette: CarColorPalette,
    onPickLegs: (List<LegRef>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var multiMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<LegRef>>(emptySet()) }

    val merged = remember(eligible) { buildCandidateList(eligible) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Static top bar — never scrolls
            StaticHeader(
                multiMode = multiMode,
                selectedCount = selected.size,
                onEnterMulti = { multiMode = true },
                onCancelMulti = {
                    multiMode = false
                    selected = emptySet()
                },
                onConfirmMulti = {
                    if (selected.isNotEmpty()) {
                        onPickLegs(selected.toList())
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            if (merged.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.trip_edit_add_leg_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(merged) { candidate ->
                        val ref = candidate.toRef()
                        CandidateRow(
                            candidate = candidate,
                            palette = palette,
                            isDc = candidate is Candidate.Charge && candidate.charge.chargeId in dcChargeIds,
                            multiMode = multiMode,
                            isSelected = ref in selected,
                            onTap = {
                                if (multiMode) {
                                    selected = if (ref in selected) selected - ref else selected + ref
                                } else {
                                    onPickLegs(listOf(ref))
                                }
                            },
                            onLongPress = {
                                if (!multiMode) {
                                    multiMode = true
                                    selected = setOf(ref)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaticHeader(
    multiMode: Boolean,
    selectedCount: Int,
    onEnterMulti: () -> Unit,
    onCancelMulti: () -> Unit,
    onConfirmMulti: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (multiMode) {
            TextButton(onClick = onCancelMulti) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.trip_edit_selected_count, selectedCount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onConfirmMulti,
                enabled = selectedCount > 0
            ) {
                Text(stringResource(R.string.add))
            }
        } else {
            Text(
                text = stringResource(R.string.trip_edit_add_leg_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onEnterMulti) {
                Icon(
                    Icons.Filled.Checklist,
                    contentDescription = stringResource(R.string.trip_edit_select_multiple)
                )
            }
        }
    }
}

private sealed class Candidate(val startDate: String) {
    class Drive(val drive: DriveSummary) : Candidate(drive.startDate)
    class Charge(val charge: ChargeSummary) : Candidate(charge.startDate)

    fun toRef(): LegRef = when (this) {
        is Drive -> LegRef(SavedTripLeg.TYPE_DRIVE, drive.driveId)
        is Charge -> LegRef(SavedTripLeg.TYPE_CHARGE, charge.chargeId)
    }
}

private fun buildCandidateList(eligible: EligibleLegs): List<Candidate> {
    val all = mutableListOf<Candidate>()
    eligible.drives.forEach { all.add(Candidate.Drive(it)) }
    eligible.charges.forEach { all.add(Candidate.Charge(it)) }
    all.sortBy { it.startDate }
    return all
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CandidateRow(
    candidate: Candidate,
    palette: CarColorPalette,
    isDc: Boolean,
    multiMode: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val rowBackground =
        if (isSelected) palette.accent.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowBackground)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (multiMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onTap() }
            )
            Spacer(Modifier.width(4.dp))
        }
        when (candidate) {
            is Candidate.Drive -> {
                Icon(
                    CustomIcons.SteeringWheel,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = palette.accent
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${extractCity(candidate.drive.startAddress)} → ${extractCity(candidate.drive.endAddress)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatCandidateDate(candidate.drive.startDate),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "%.1f km".format(candidate.drive.distance),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatMinutes(candidate.drive.durationMin),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is Candidate.Charge -> {
                val chipColor = if (isDc) palette.dcColor else palette.acColor
                Icon(
                    Icons.Filled.ElectricBolt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = chipColor
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = extractCity(candidate.charge.address),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatCandidateDate(candidate.charge.startDate),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                ChargeTypeChip(isDc = isDc, chipColor = chipColor)
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "+%.1f kWh".format(candidate.charge.energyAdded),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = chipColor
                    )
                    Text(
                        text = formatMinutes(candidate.charge.durationMin),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ChargeTypeChip(isDc: Boolean, chipColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(chipColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isDc) "DC" else "AC",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatCandidateDate(dateStr: String): String {
    return try {
        val dt = try {
            OffsetDateTime.parse(dateStr).toLocalDateTime()
        } catch (e: DateTimeParseException) {
            LocalDateTime.parse(dateStr.replace("Z", ""))
        }
        dt.format(DateTimeFormatter.ofPattern("d MMM HH:mm"))
    } catch (e: Exception) {
        dateStr
    }
}

private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
