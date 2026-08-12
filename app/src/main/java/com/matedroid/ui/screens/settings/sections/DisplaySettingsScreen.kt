package com.matedroid.ui.screens.settings.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.matedroid.ui.screens.settings.SettingsGroupHeader
import com.matedroid.ui.screens.settings.SettingsSectionScaffold
import com.matedroid.ui.screens.settings.SettingsSpacer
import com.matedroid.ui.screens.settings.SettingsSwitchRow
import com.matedroid.ui.screens.settings.SettingsViewModel
import com.matedroid.ui.theme.MateDroidTheme

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
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onCurrencyChange = viewModel::updateCurrency,
        onShowShortDrivesChargesChange = viewModel::updateShowShortDrivesCharges
    )
}

@Composable
private fun DisplaySettingsContent(
    currencyCode: String,
    showShortDrivesCharges: Boolean,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onCurrencyChange: (String) -> Unit,
    onShowShortDrivesChargesChange: (Boolean) -> Unit
) {
    var currencyDropdownExpanded by remember { mutableStateOf(false) }
    var showShortDrivesChargesInfoDialog by remember { mutableStateOf(false) }

    SettingsSectionScaffold(
        title = stringResource(R.string.settings_section_display),
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState
    ) {
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
    }
}

@Preview(showBackground = true)
@Composable
private fun DisplaySettingsPreview() {
    MateDroidTheme {
        DisplaySettingsContent(
            currencyCode = "EUR",
            showShortDrivesCharges = false,
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onCurrencyChange = {},
            onShowShortDrivesChargesChange = {}
        )
    }
}
