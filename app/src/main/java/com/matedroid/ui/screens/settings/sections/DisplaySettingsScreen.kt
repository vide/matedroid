package com.matedroid.ui.screens.settings.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matedroid.R
import com.matedroid.data.model.Currency
import com.matedroid.domain.AppTimeZone
import com.matedroid.domain.ShortEntryFilter
import com.matedroid.domain.UnitSystem
import com.matedroid.ui.screens.settings.SettingsGroupHeader
import com.matedroid.ui.screens.settings.SettingsSectionScaffold
import com.matedroid.ui.screens.settings.SettingsSpacer
import com.matedroid.ui.screens.settings.SettingsSwitchRow
import com.matedroid.ui.screens.settings.SettingsViewModel
import com.matedroid.ui.theme.MateDroidTheme
import java.util.Locale
import kotlin.math.floor

/**
 * How data is presented: currency for costs, and which entries the lists include.
 * Every control here writes through immediately — there is no save button.
 */
@Composable
fun DisplaySettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    DisplaySettingsContent(
        currencyCode = uiState.currencyCode,
        showShortDrivesCharges = uiState.showShortDrivesCharges,
        shortDriveMinDurationMin = uiState.shortDriveMinDurationMin,
        shortDriveMinDistance = uiState.shortDriveMinDistance,
        shortChargeMinEnergyKwh = uiState.shortChargeMinEnergyKwh,
        timeZoneMode = uiState.timeZoneMode,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onTimeZoneModeChange = viewModel::updateTimeZoneMode,
        onCurrencyChange = viewModel::updateCurrency,
        onShowShortDrivesChargesChange = viewModel::updateShowShortDrivesCharges,
        onShortDriveMinDurationChange = viewModel::updateShortDriveMinDuration,
        onShortDriveMinDistanceChange = viewModel::updateShortDriveMinDistance,
        onShortChargeMinEnergyChange = viewModel::updateShortChargeMinEnergy
    )
}

@Composable
private fun DisplaySettingsContent(
    currencyCode: String,
    showShortDrivesCharges: Boolean,
    shortDriveMinDurationMin: Int,
    shortDriveMinDistance: Double,
    shortChargeMinEnergyKwh: Double,
    timeZoneMode: String,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onTimeZoneModeChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onShowShortDrivesChargesChange: (Boolean) -> Unit,
    onShortDriveMinDurationChange: (Int) -> Unit,
    onShortDriveMinDistanceChange: (Double) -> Unit,
    onShortChargeMinEnergyChange: (Double) -> Unit
) {
    var currencyDropdownExpanded by remember { mutableStateOf(false) }
    var showShortDrivesChargesInfoDialog by remember { mutableStateOf(false) }

    SettingsSectionScaffold(
        title = stringResource(R.string.settings_section_display),
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState
    ) {
        SettingsGroupHeader(stringResource(R.string.settings_group_datetime))

        TimeZonePicker(
            mode = timeZoneMode,
            onModeChange = onTimeZoneModeChange
        )

        Text(
            text = stringResource(R.string.settings_timezone_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        SettingsSpacer(24)

        SettingsGroupHeader(stringResource(R.string.settings_group_costs))

        val selectedCurrency = Currency.findByCode(currencyCode)

        Box {
            OutlinedTextField(
                value = "${selectedCurrency.symbol} ${selectedCurrency.code} - ${selectedCurrency.name}",
                onValueChange = {},
                label = { Text(stringResource(R.string.settings_currency_label)) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { currencyDropdownExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = stringResource(R.string.settings_currency_select)
                        )
                    }
                }
            )

            DropdownMenu(
                expanded = currencyDropdownExpanded,
                onDismissRequest = { currencyDropdownExpanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Currency.ALL.forEach { currency ->
                    DropdownMenuItem(
                        text = {
                            Text("${currency.symbol} ${currency.code} - ${currency.name}")
                        },
                        onClick = {
                            onCurrencyChange(currency.code)
                            currencyDropdownExpanded = false
                        }
                    )
                }
            }
        }

        SettingsSpacer(24)

        SettingsGroupHeader(stringResource(R.string.settings_group_lists))

        SettingsSwitchRow(
            title = stringResource(R.string.settings_show_short_label),
            hint = stringResource(R.string.settings_show_short_hint),
            checked = showShortDrivesCharges,
            onCheckedChange = onShowShortDrivesChargesChange,
            trailingTitleContent = {
                IconButton(
                    onClick = { showShortDrivesChargesInfoDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = stringResource(R.string.more_information),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )

        if (showShortDrivesChargesInfoDialog) {
            AlertDialog(
                onDismissRequest = { showShortDrivesChargesInfoDialog = false },
                title = { Text(stringResource(R.string.settings_short_dialog_title)) },
                text = { Text(stringResource(R.string.settings_short_dialog_text)) },
                confirmButton = {
                    TextButton(onClick = { showShortDrivesChargesInfoDialog = false }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }

        // The thresholds only do anything while short entries are being hidden, so they are
        // disabled (not removed) when the switch is on — leaving them visible keeps the rule
        // discoverable and stops the section from reflowing as the switch is toggled.
        val thresholdsEnabled = !showShortDrivesCharges

        SettingsSpacer()

        Text(
            text = stringResource(R.string.settings_thresholds_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SettingsSpacer()

        ThresholdPicker(
            label = stringResource(R.string.settings_threshold_drive_duration_label),
            value = shortDriveMinDurationMin,
            options = ShortEntryFilter.DRIVE_DURATION_PRESETS_MIN,
            enabled = thresholdsEnabled,
            optionLabel = { minutes ->
                if (minutes <= 0) {
                    stringResource(R.string.settings_threshold_no_minimum)
                } else {
                    stringResource(R.string.settings_threshold_value_minutes, minutes)
                }
            },
            onValueChange = onShortDriveMinDurationChange
        )

        SettingsSpacer()

        ThresholdPicker(
            label = stringResource(R.string.settings_threshold_drive_distance_label),
            value = shortDriveMinDistance,
            options = ShortEntryFilter.DRIVE_DISTANCE_PRESETS,
            enabled = thresholdsEnabled,
            optionLabel = { distance ->
                if (distance <= 0.0) {
                    stringResource(R.string.settings_threshold_no_minimum)
                } else {
                    stringResource(
                        if (UnitSystem.isImperial) {
                            R.string.settings_threshold_value_mi
                        } else {
                            R.string.settings_threshold_value_km
                        },
                        formatThreshold(distance)
                    )
                }
            },
            onValueChange = onShortDriveMinDistanceChange
        )

        SettingsSpacer()

        ThresholdPicker(
            label = stringResource(R.string.settings_threshold_charge_energy_label),
            value = shortChargeMinEnergyKwh,
            options = ShortEntryFilter.CHARGE_ENERGY_PRESETS_KWH,
            enabled = thresholdsEnabled,
            optionLabel = { energy ->
                if (energy <= 0.0) {
                    stringResource(R.string.settings_threshold_no_minimum)
                } else {
                    stringResource(R.string.settings_threshold_value_kwh, formatThreshold(energy))
                }
            },
            onValueChange = onShortChargeMinEnergyChange
        )
    }
}

/**
 * Read-only field opening a dropdown of preset values. Generic over the value type so the
 * minute (Int) and distance/energy (Double) pickers share one implementation.
 */
@Composable
private fun <T> ThresholdPicker(
    label: String,
    value: T,
    options: List<T>,
    enabled: Boolean,
    optionLabel: @Composable (T) -> String,
    onValueChange: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedTextField(
            value = optionLabel(value),
            onValueChange = {},
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            enabled = enabled,
            trailingIcon = {
                IconButton(onClick = { expanded = true }, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = stringResource(R.string.settings_threshold_select)
                    )
                }
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Formats a threshold for display, dropping the decimal on whole values ("1" not "1.0") and
 * using the device locale's decimal separator ("0,5" in Italian/Spanish/Catalan).
 */
private fun formatThreshold(value: Double): String =
    if (value == floor(value)) {
        value.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", value)
    }

@Preview(showBackground = true)
@Composable
private fun DisplaySettingsPreview() {
    MateDroidTheme {
        DisplaySettingsContent(
            currencyCode = "EUR",
            showShortDrivesCharges = false,
            shortDriveMinDurationMin = ShortEntryFilter.DEFAULT_MIN_DRIVE_DURATION_MIN,
            shortDriveMinDistance = ShortEntryFilter.DEFAULT_MIN_DRIVE_DISTANCE,
            shortChargeMinEnergyKwh = ShortEntryFilter.DEFAULT_MIN_CHARGE_ENERGY_KWH,
            timeZoneMode = AppTimeZone.MODE_SERVER,
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onTimeZoneModeChange = {},
            onCurrencyChange = {},
            onShowShortDrivesChargesChange = {},
            onShortDriveMinDurationChange = {},
            onShortDriveMinDistanceChange = {},
            onShortChargeMinEnergyChange = {}
        )
    }
}
