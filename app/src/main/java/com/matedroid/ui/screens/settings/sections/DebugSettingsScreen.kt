package com.matedroid.ui.screens.settings.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.matedroid.data.local.TirePosition
import com.matedroid.ui.screens.settings.SettingsGroupHeader
import com.matedroid.ui.screens.settings.SettingsSectionScaffold
import com.matedroid.ui.screens.settings.SettingsSpacer
import com.matedroid.ui.screens.settings.SettingsViewModel
import com.matedroid.ui.theme.MateDroidTheme

/**
 * Developer tools. Only reachable in debug builds — [com.matedroid.ui.screens.settings.SettingsSection.DEBUG]
 * is filtered out of the hub for release builds.
 */
@Composable
fun DebugSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPalettePreview: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    DebugSettingsContent(
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onPalettePreview = onNavigateToPalettePreview,
        onSimulateTpmsWarning = viewModel::simulateTpmsWarning,
        onClearTpmsWarning = viewModel::clearTpmsWarning,
        onRunTpmsCheckNow = viewModel::runTpmsCheckNow,
        onSimulateSentryEvent = viewModel::simulateSentryEvent
    )
}

@Composable
private fun DebugSettingsContent(
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onPalettePreview: () -> Unit,
    onSimulateTpmsWarning: (TirePosition) -> Unit,
    onClearTpmsWarning: () -> Unit,
    onRunTpmsCheckNow: () -> Unit,
    onSimulateSentryEvent: () -> Unit
) {
    var tpmsDropdownExpanded by remember { mutableStateOf(false) }

    SettingsSectionScaffold(
        title = stringResource(R.string.settings_section_debug),
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState
    ) {
        SettingsGroupHeader(stringResource(R.string.settings_group_appearance_debug))

        OutlinedButton(
            onClick = onPalettePreview,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_palette_preview))
        }

        SettingsSpacer(24)

        SettingsGroupHeader(stringResource(R.string.settings_group_tpms_debug))

        Box {
            OutlinedButton(
                onClick = { tpmsDropdownExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.debug_tpms_simulate_warning))
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            DropdownMenu(
                expanded = tpmsDropdownExpanded,
                onDismissRequest = { tpmsDropdownExpanded = false }
            ) {
                TirePosition.entries.forEach { tire ->
                    val tireName = when (tire) {
                        TirePosition.FL -> stringResource(R.string.tire_fl_full)
                        TirePosition.FR -> stringResource(R.string.tire_fr_full)
                        TirePosition.RL -> stringResource(R.string.tire_rl_full)
                        TirePosition.RR -> stringResource(R.string.tire_rr_full)
                    }
                    DropdownMenuItem(
                        text = { Text(tireName) },
                        onClick = {
                            tpmsDropdownExpanded = false
                            onSimulateTpmsWarning(tire)
                        }
                    )
                }
            }
        }

        SettingsSpacer(8)

        OutlinedButton(
            onClick = onClearTpmsWarning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.debug_tpms_clear_state))
        }

        SettingsSpacer(8)

        OutlinedButton(
            onClick = onRunTpmsCheckNow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.debug_tpms_run_now))
        }

        SettingsSpacer(24)

        SettingsGroupHeader(stringResource(R.string.settings_group_sentry_debug))

        OutlinedButton(
            onClick = onSimulateSentryEvent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.debug_sentry_simulate_event))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DebugSettingsPreview() {
    MateDroidTheme {
        DebugSettingsContent(
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onPalettePreview = {},
            onSimulateTpmsWarning = {},
            onClearTpmsWarning = {},
            onRunTpmsCheckNow = {},
            onSimulateSentryEvent = {}
        )
    }
}
