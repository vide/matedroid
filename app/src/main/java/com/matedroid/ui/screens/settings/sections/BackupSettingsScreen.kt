package com.matedroid.ui.screens.settings.sections

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matedroid.R
import com.matedroid.data.backup.BackupCar
import com.matedroid.data.backup.BackupCounts
import com.matedroid.data.backup.BackupExporter
import com.matedroid.data.backup.BackupHeader
import com.matedroid.data.backup.BackupPreview
import com.matedroid.data.backup.BackupSection
import com.matedroid.data.backup.ImportMode
import com.matedroid.data.backup.ImportReport
import com.matedroid.ui.screens.settings.SettingsGroupHeader
import com.matedroid.ui.screens.settings.SettingsSectionScaffold
import com.matedroid.ui.screens.settings.SettingsSpacer
import com.matedroid.ui.screens.settings.SettingsSwitchRow
import com.matedroid.ui.theme.MateDroidTheme
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Saving and restoring the data that only exists on this phone.
 *
 * The app deliberately has no idea where a backup ends up: it writes one and passes it to
 * the share sheet, and whether that means Drive, a chat or a cable is the user's business.
 * Restoring comes back the same way, through the system file picker, which reaches every
 * one of those places.
 */
@Composable
fun BackupSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Any type: storage providers label a .json anything from application/json to
    // octet-stream, and a greyed-out backup file is a dead end. Junk is caught on read.
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.onFilePicked(it) } }

    val errorMessage = uiState.error?.let { stringResource(it.messageRes()) }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.fileToShare) {
        uiState.fileToShare?.let { uri ->
            context.shareBackup(uri)
            viewModel.onShareHandled()
        }
    }

    BackupSettingsContent(
        state = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onToggleExportSection = viewModel::toggleExportSection,
        onToggleExportCar = viewModel::toggleExportCar,
        onExport = viewModel::export,
        onPickFile = { filePicker.launch(arrayOf("*/*")) },
        onToggleImportSection = viewModel::toggleImportSection,
        onToggleImportCar = viewModel::toggleImportCar,
        onImportModeChange = viewModel::setImportMode,
        onRestore = viewModel::restore,
        onDismissPreview = viewModel::dismissPreview,
        onDismissReport = viewModel::dismissReport
    )
}

@Composable
private fun BackupSettingsContent(
    state: BackupUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onToggleExportSection: (BackupSection) -> Unit,
    onToggleExportCar: (Int) -> Unit,
    onExport: () -> Unit,
    onPickFile: () -> Unit,
    onToggleImportSection: (BackupSection) -> Unit,
    onToggleImportCar: (Int) -> Unit,
    onImportModeChange: (ImportMode) -> Unit,
    onRestore: () -> Unit,
    onDismissPreview: () -> Unit,
    onDismissReport: () -> Unit
) {
    SettingsSectionScaffold(
        title = stringResource(R.string.settings_section_backup),
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState
    ) {
        Text(
            text = stringResource(R.string.backup_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SettingsSpacer(24)
        SettingsGroupHeader(stringResource(R.string.backup_export_header))
        Text(
            text = stringResource(R.string.backup_export_always),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.cars.isNotEmpty()) {
            SettingsSpacer(20)
            SettingsGroupHeader(stringResource(R.string.backup_cars_header))
            Text(
                text = stringResource(R.string.backup_cars_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SettingsSpacer(8)
            state.cars.forEach { car ->
                SettingsSwitchRow(
                    title = car.displayName(),
                    hint = car.vinLine(),
                    checked = car.carId in state.exportCarIds,
                    onCheckedChange = { onToggleExportCar(car.carId) }
                )
            }
        }

        SettingsSpacer(20)
        BackupSection.optional.forEach { section ->
            SettingsSwitchRow(
                title = stringResource(section.titleRes()),
                hint = stringResource(section.hintRes()),
                checked = section in state.exportSelection,
                onCheckedChange = { onToggleExportSection(section) },
                trailingTitleContent = { CountBadge(state.counts.of(section)) }
            )
        }

        SettingsSpacer(8)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.backup_export_no_secrets),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsSpacer(16)
        Button(
            onClick = onExport,
            enabled = !state.isExporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isExporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.backup_export_preparing))
            } else {
                Text(stringResource(R.string.backup_export_button))
            }
        }

        SettingsSpacer(32)
        HorizontalDivider()
        SettingsSpacer(24)

        SettingsGroupHeader(stringResource(R.string.backup_import_header))
        Text(
            text = stringResource(R.string.backup_import_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SettingsSpacer(12)
        OutlinedButton(
            onClick = onPickFile,
            enabled = !state.isReadingFile,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isReadingFile) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.backup_import_reading))
            } else {
                Text(stringResource(R.string.backup_import_button))
            }
        }
        SettingsSpacer(24)
    }

    state.preview?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            selection = state.importSelection,
            carIds = state.importCarIds,
            mode = state.importMode,
            isRestoring = state.isRestoring,
            onToggleSection = onToggleImportSection,
            onToggleCar = onToggleImportCar,
            onModeChange = onImportModeChange,
            onRestore = onRestore,
            onDismiss = onDismissPreview
        )
    }

    state.report?.let { report ->
        ImportReportDialog(report = report, onDismiss = onDismissReport)
    }
}

/** How much of a section there is, tucked next to its name. Hidden when there is none. */
@Composable
private fun CountBadge(count: Int) {
    if (count <= 0) return
    Spacer(modifier = Modifier.width(8.dp))
    Text(
        text = NumberFormat.getIntegerInstance().format(count),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ImportPreviewDialog(
    preview: BackupPreview,
    selection: Set<BackupSection>,
    carIds: Set<Int>,
    mode: ImportMode,
    isRestoring: Boolean,
    onToggleSection: (BackupSection) -> Unit,
    onToggleCar: (Int) -> Unit,
    onModeChange: (ImportMode) -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isRestoring) onDismiss() },
        title = { Text(stringResource(R.string.backup_preview_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = preview.savedOnText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (preview.cars.isNotEmpty()) {
                    SettingsSpacer(16)
                    Text(
                        text = stringResource(R.string.backup_cars_header),
                        style = MaterialTheme.typography.labelLarge
                    )
                    preview.cars.forEach { car ->
                        CheckRow(
                            checked = car.carId in carIds,
                            enabled = !isRestoring,
                            onToggle = { onToggleCar(car.carId) },
                            count = preview.countOfCar(car.carId)
                        ) {
                            Text(
                                text = car.displayName(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = car.vinLine(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SettingsSpacer(16)
                preview.availableSections.sortedBy { it.ordinal }.forEach { section ->
                    CheckRow(
                        checked = section in selection,
                        enabled = !isRestoring,
                        onToggle = { onToggleSection(section) },
                        count = preview.countOf(section, carIds)
                    ) {
                        Text(
                            text = stringResource(section.titleRes()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (preview.hasUnreadableStats) {
                    SettingsSpacer(8)
                    Text(
                        text = stringResource(R.string.backup_preview_stats_unusable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SettingsSpacer(16)
                Text(
                    text = stringResource(R.string.backup_preview_mode),
                    style = MaterialTheme.typography.labelLarge
                )
                ImportMode.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isRestoring) { onModeChange(option) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == mode,
                            onClick = { onModeChange(option) },
                            enabled = !isRestoring
                        )
                        Text(
                            text = stringResource(option.labelRes()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onRestore,
                enabled = !isRestoring && selection.isNotEmpty()
            ) {
                Text(
                    stringResource(
                        if (isRestoring) {
                            R.string.backup_preview_restoring
                        } else {
                            R.string.backup_preview_restore
                        }
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isRestoring) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** One tickable line in the import dialog: box, label block, and how much it stands for. */
@Composable
private fun CheckRow(
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    count: Int,
    label: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onToggle() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
        Column(modifier = Modifier.weight(1f), content = label)
        CountBadge(count)
    }
}

@Composable
private fun ImportReportDialog(report: ImportReport, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_result_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val rows = report.rows()
                if (rows.isEmpty() && !report.settingsRestored) {
                    Text(
                        text = stringResource(R.string.backup_result_nothing),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                rows.forEach { (labelRes, value) ->
                    ResultRow(label = stringResource(labelRes), value = value)
                }
                if (report.settingsRestored) {
                    ResultRow(
                        label = stringResource(R.string.backup_section_settings),
                        value = stringResource(R.string.backup_result_restored)
                    )
                }
                if (report.tripsIncomplete > 0 || report.tripsSkipped > 0) {
                    SettingsSpacer(12)
                    Text(
                        text = stringResource(R.string.backup_result_note_unmatched),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (report.settingsRestored) {
                    SettingsSpacer(12)
                    Text(
                        text = stringResource(R.string.backup_result_note_token),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** Label/value pairs for the sections a restore touched. Untouched ones are left out. */
private fun ImportReport.rows(): List<Pair<Int, String>> = buildList {
    fun addRow(@StringRes labelRes: Int, count: Int) {
        if (count > 0) add(labelRes to NumberFormat.getIntegerInstance().format(count))
    }
    addRow(R.string.backup_section_trips, tripsRestored)
    addRow(R.string.backup_result_already_here, tripsAlreadyHere + sentryAlreadyHere)
    addRow(R.string.backup_result_trips_incomplete, tripsIncomplete)
    addRow(R.string.backup_result_trips_unmatched, tripsSkipped)
    addRow(R.string.backup_section_sentry, sentryRestored)
    addRow(R.string.backup_section_places, placesRestored)
    addRow(R.string.backup_section_trip_maps, tripMapsRestored)
    addRow(R.string.backup_section_stats, drivesAndChargesRestored)
}

/** The car's name on the server, or its id when the app has never been told one. */
@Composable
private fun BackupCar.displayName(): String =
    name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.backup_car_unnamed, carId)

@Composable
private fun BackupCar.vinLine(): String =
    vin?.takeIf { it.isNotBlank() } ?: stringResource(R.string.backup_car_no_vin)

@Composable
private fun BackupPreview.savedOnText(): String {
    val date = Instant.ofEpochMilli(header.exportedAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
    val version = header.appVersionName
    return if (version.isNullOrBlank()) {
        stringResource(R.string.backup_preview_saved_on, date)
    } else {
        stringResource(R.string.backup_preview_saved_on_version, date, version)
    }
}

private fun Context.shareBackup(uri: Uri) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(BackupExporter.MIME_TYPE)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = Intent.createChooser(send, null)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(chooser) }
}

@StringRes
private fun BackupSection.titleRes(): Int = when (this) {
    BackupSection.TRIPS -> R.string.backup_section_trips
    BackupSection.SETTINGS -> R.string.backup_section_settings
    BackupSection.SENTRY -> R.string.backup_section_sentry
    BackupSection.PLACES -> R.string.backup_section_places
    BackupSection.TRIP_MAPS -> R.string.backup_section_trip_maps
    BackupSection.STATS -> R.string.backup_section_stats
}

/** Only the optional sections are ever explained; the other two are always in. */
@StringRes
private fun BackupSection.hintRes(): Int = when (this) {
    BackupSection.SENTRY -> R.string.backup_section_sentry_hint
    BackupSection.PLACES -> R.string.backup_section_places_hint
    BackupSection.TRIP_MAPS -> R.string.backup_section_trip_maps_hint
    BackupSection.STATS -> R.string.backup_section_stats_hint
    BackupSection.TRIPS, BackupSection.SETTINGS -> R.string.backup_export_always
}

@StringRes
private fun ImportMode.labelRes(): Int = when (this) {
    ImportMode.MERGE -> R.string.backup_preview_mode_merge
    ImportMode.REPLACE -> R.string.backup_preview_mode_replace
}

@StringRes
private fun BackupError.messageRes(): Int = when (this) {
    BackupError.EXPORT_FAILED -> R.string.backup_error_export
    BackupError.NOT_A_BACKUP -> R.string.backup_error_not_a_backup
    BackupError.BACKUP_TOO_NEW -> R.string.backup_error_too_new
    BackupError.RESTORE_FAILED -> R.string.backup_error_restore
}

private fun BackupCounts.of(section: BackupSection): Int = when (section) {
    BackupSection.TRIPS -> trips
    BackupSection.SETTINGS -> 0
    BackupSection.SENTRY -> sentryEvents
    BackupSection.PLACES -> places
    BackupSection.TRIP_MAPS -> tripMaps
    BackupSection.STATS -> drivesAndCharges
}

private val previewCars = listOf(
    BackupCar(carId = 1, vin = "5YJ3E1EA7KF000001", name = "Kitt"),
    BackupCar(carId = 2, vin = "7SAYGDEF9NF000002", name = "Bandit")
)

@Preview(showBackground = true)
@Composable
private fun BackupSettingsPreview() {
    MateDroidTheme {
        BackupSettingsContent(
            state = BackupUiState(
                cars = previewCars,
                exportCarIds = setOf(1, 2),
                counts = BackupCounts(
                    trips = 14,
                    sentryEvents = 342,
                    places = 1204,
                    tripMaps = 56,
                    drivesAndCharges = 12340
                )
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onToggleExportSection = {},
            onToggleExportCar = {},
            onExport = {},
            onPickFile = {},
            onToggleImportSection = {},
            onToggleImportCar = {},
            onImportModeChange = {},
            onRestore = {},
            onDismissPreview = {},
            onDismissReport = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ImportPreviewDialogPreview() {
    MateDroidTheme {
        ImportPreviewDialog(
            preview = BackupPreview(
                header = BackupHeader(
                    exportedAt = System.currentTimeMillis(),
                    appVersionName = "1.11.3",
                    cars = previewCars
                ),
                cars = previewCars,
                tripsByCar = mapOf(1 to 12, 2 to 2),
                sentryByCar = mapOf(1 to 342),
                places = 1204,
                hasSettings = true
            ),
            selection = setOf(BackupSection.TRIPS, BackupSection.SETTINGS),
            carIds = setOf(1, 2),
            mode = ImportMode.MERGE,
            isRestoring = false,
            onToggleSection = {},
            onToggleCar = {},
            onModeChange = {},
            onRestore = {},
            onDismiss = {}
        )
    }
}
