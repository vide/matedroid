package com.matedroid.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.local.TirePosition
import com.matedroid.data.model.Currency
import com.matedroid.ui.theme.MateDroidTheme
import com.matedroid.ui.theme.StatusWarning
import com.matedroid.ui.theme.StatusError
import com.matedroid.ui.theme.StatusSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToDashboard: () -> Unit,
    onNavigateToPalettePreview: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            LoadingContent(modifier = Modifier.padding(paddingValues))
        } else {
            SettingsContent(
                modifier = Modifier.padding(paddingValues),
                uiState = uiState,
                onServerUrlChange = viewModel::updateServerUrl,
                onSecondaryServerUrlChange = viewModel::updateSecondaryServerUrl,
                onApiTokenChange = viewModel::updateApiToken,
                onAcceptInvalidCertsChange = viewModel::updateAcceptInvalidCerts,
                onCurrencyChange = viewModel::updateCurrency,
                onShowShortDrivesChargesChange = viewModel::updateShowShortDrivesCharges,
                onTeslamateBaseUrlChange = viewModel::updateTeslamateBaseUrl,
                onTestConnection = viewModel::testConnection,
                onSave = { viewModel.saveSettings(onNavigateToDashboard) },
                onPalettePreview = onNavigateToPalettePreview,
                onForceResync = viewModel::forceResync,
                onSimulateTpmsWarning = viewModel::simulateTpmsWarning,
                onClearTpmsWarning = viewModel::clearTpmsWarning,
                onRunTpmsCheckNow = viewModel::runTpmsCheckNow
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.loading_settings))
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun SettingsContent(
    modifier: Modifier = Modifier,
    uiState: SettingsUiState,
    onServerUrlChange: (String) -> Unit,
    onSecondaryServerUrlChange: (String) -> Unit,
    onApiTokenChange: (String) -> Unit,
    onAcceptInvalidCertsChange: (Boolean) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onShowShortDrivesChargesChange: (Boolean) -> Unit,
    onTeslamateBaseUrlChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit,
    onPalettePreview: () -> Unit = {},
    onForceResync: () -> Unit = {},
    onSimulateTpmsWarning: (TirePosition) -> Unit = {},
    onClearTpmsWarning: () -> Unit = {},
    onRunTpmsCheckNow: () -> Unit = {}
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var currencyDropdownExpanded by remember { mutableStateOf(false) }
    var showShortDrivesChargesInfoDialog by remember { mutableStateOf(false) }
    var showResyncConfirmDialog by remember { mutableStateOf(false) }
    var advancedNetworkExpanded by rememberSaveable { mutableStateOf(false) }
    var extraSettingsExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // === Server Connection Section ===
        Text(
            text = stringResource(R.string.settings_connect_title),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.settings_connect_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text(stringResource(R.string.settings_server_url_label)) },
            placeholder = { Text(stringResource(R.string.settings_server_url_placeholder)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("urlInput"),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            enabled = !uiState.isTesting && !uiState.isSaving
        )

        Spacer(modifier = Modifier.height(16.dp))

        // === Advanced Network Settings (Collapsed by default) ===
        HorizontalDivider()

        CollapsibleSection(
            title = stringResource(R.string.settings_advanced_network),
            expanded = advancedNetworkExpanded,
            onToggle = { advancedNetworkExpanded = !advancedNetworkExpanded }
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Secondary Server URL
            OutlinedTextField(
                value = uiState.secondaryServerUrl,
                onValueChange = onSecondaryServerUrlChange,
                label = { Text(stringResource(R.string.settings_secondary_url_label)) },
                placeholder = { Text(stringResource(R.string.settings_secondary_url_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                enabled = !uiState.isTesting && !uiState.isSaving
            )

            Text(
                text = stringResource(R.string.settings_secondary_url_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // API Token
            OutlinedTextField(
                value = uiState.apiToken,
                onValueChange = onApiTokenChange,
                label = { Text(stringResource(R.string.settings_api_token_label)) },
                placeholder = { Text(stringResource(R.string.settings_api_token_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tokenInput"),
                singleLine = true,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = stringResource(
                                if (passwordVisible) R.string.hide_token else R.string.show_token
                            )
                        )
                    }
                },
                enabled = !uiState.isTesting && !uiState.isSaving
            )

            Text(
                text = stringResource(R.string.settings_api_token_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Accept invalid certificates toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_accept_invalid_certs),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.settings_accept_invalid_certs_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.acceptInvalidCerts,
                    onCheckedChange = onAcceptInvalidCertsChange,
                    enabled = !uiState.isTesting && !uiState.isSaving
                )
            }

            if (uiState.acceptInvalidCerts) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = StatusWarning.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = StatusWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_accept_invalid_certs_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusWarning
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === Display Settings Section ===
        Text(
            text = stringResource(R.string.settings_display_title),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Currency selection
        val selectedCurrency = Currency.findByCode(uiState.currencyCode)

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

        Spacer(modifier = Modifier.height(16.dp))

        // Teslamate Base URL
        OutlinedTextField(
            value = uiState.teslamateBaseUrl,
            onValueChange = onTeslamateBaseUrlChange,
            label = { Text(stringResource(R.string.settings_teslamate_url_label)) },
            placeholder = { Text(stringResource(R.string.settings_teslamate_url_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )

        Text(
            text = stringResource(R.string.settings_teslamate_url_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // === Extra Settings (Collapsed by default) ===
        HorizontalDivider()

        CollapsibleSection(
            title = stringResource(R.string.settings_extra),
            expanded = extraSettingsExpanded,
            onToggle = { extraSettingsExpanded = !extraSettingsExpanded }
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Show short drives / charges toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.settings_show_short_label),
                            style = MaterialTheme.typography.bodyMedium
                        )
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
                    Text(
                        text = stringResource(R.string.settings_show_short_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.showShortDrivesCharges,
                    onCheckedChange = onShowShortDrivesChargesChange
                )
            }

            // Info dialog for short drives / charges
            if (showShortDrivesChargesInfoDialog) {
                AlertDialog(
                    onDismissRequest = { showShortDrivesChargesInfoDialog = false },
                    title = { Text(stringResource(R.string.settings_short_dialog_title)) },
                    text = {
                        Text(stringResource(R.string.settings_short_dialog_text))
                    },
                    confirmButton = {
                        TextButton(onClick = { showShortDrivesChargesInfoDialog = false }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Force Full Resync button
            OutlinedButton(
                onClick = { showResyncConfirmDialog = true },
                enabled = !uiState.isResyncing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isResyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(if (uiState.isResyncing) R.string.settings_resyncing else R.string.settings_resync_button))
            }

            Text(
                text = stringResource(R.string.settings_resync_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Resync confirmation dialog
            if (showResyncConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showResyncConfirmDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = StatusWarning,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    title = { Text(stringResource(R.string.settings_resync_dialog_title)) },
                    text = {
                        Text(
                            buildAnnotatedString {
                                append(stringResource(R.string.settings_resync_dialog_text_prefix))
                                withStyle(SpanStyle(color = StatusError, fontWeight = FontWeight.Bold)) {
                                    append(stringResource(R.string.settings_resync_dialog_text_delete))
                                }
                                append(stringResource(R.string.settings_resync_dialog_text_suffix))
                            }
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showResyncConfirmDialog = false
                                onForceResync()
                            }
                        ) {
                            Text(stringResource(R.string.settings_resync_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResyncConfirmDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Test result card
        uiState.testResult?.let { result ->
            TestResultCard(result = result)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onTestConnection,
                enabled = uiState.serverUrl.isNotBlank() && !uiState.isTesting && !uiState.isSaving,
                modifier = Modifier.weight(1f)
            ) {
                if (uiState.isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.settings_test_connection))
            }

            Button(
                onClick = onSave,
                enabled = uiState.serverUrl.isNotBlank() && !uiState.isTesting && !uiState.isSaving,
                modifier = Modifier
                    .weight(1f)
                    .testTag("saveButton")
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.settings_save))
            }
        }

        // Debug: Palette Preview button (only visible in debug builds)
        if (com.matedroid.BuildConfig.DEBUG) {
            var tpmsDropdownExpanded by remember { mutableStateOf(false) }

            Spacer(modifier = Modifier.height(32.dp))
            OutlinedButton(
                onClick = onPalettePreview,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_palette_preview))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // TPMS Debug: Simulate Warning with tire selection
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

            Spacer(modifier = Modifier.height(8.dp))

            // TPMS Debug: Clear State
            OutlinedButton(
                onClick = onClearTpmsWarning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.debug_tpms_clear_state))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // TPMS Debug: Run Check Now
            OutlinedButton(
                onClick = onRunTpmsCheckNow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.debug_tpms_run_now))
            }
        }

        // Version number and issue link at bottom
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "v${com.matedroid.BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(8.dp))
        val uriHandler = LocalUriHandler.current
        Text(
            text = stringResource(R.string.settings_report_issue),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable { uriHandler.openUri("https://github.com/vide/matedroid/issues") }
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TestResultCard(result: TestResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Primary server result
            ServerTestResultRow(
                label = stringResource(R.string.settings_primary_server),
                result = result.primaryResult
            )

            // Secondary server result (if tested)
            result.secondaryResult?.let { secondaryResult ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                ServerTestResultRow(
                    label = stringResource(R.string.settings_secondary_server),
                    result = secondaryResult
                )
            }
        }
    }
}

@Composable
private fun ServerTestResultRow(
    label: String,
    result: ServerTestResult
) {
    val connectedText = stringResource(R.string.settings_connected)
    val (icon, color, statusText) = when (result) {
        is ServerTestResult.Success -> Triple(
            Icons.Filled.CheckCircle,
            StatusSuccess,
            connectedText
        )
        is ServerTestResult.Failure -> Triple(
            Icons.Filled.Error,
            StatusError,
            result.message
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = color
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MateDroidTheme {
        SettingsContent(
            uiState = SettingsUiState(isLoading = false),
            onServerUrlChange = {},
            onSecondaryServerUrlChange = {},
            onApiTokenChange = {},
            onAcceptInvalidCertsChange = {},
            onCurrencyChange = {},
            onShowShortDrivesChargesChange = {},
            onTeslamateBaseUrlChange = {},
            onTestConnection = {},
            onSave = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenWithResultPreview() {
    MateDroidTheme {
        SettingsContent(
            uiState = SettingsUiState(
                isLoading = false,
                serverUrl = "https://teslamate.example.com",
                testResult = TestResult(
                    primaryResult = ServerTestResult.Success
                )
            ),
            onServerUrlChange = {},
            onSecondaryServerUrlChange = {},
            onApiTokenChange = {},
            onAcceptInvalidCertsChange = {},
            onCurrencyChange = {},
            onShowShortDrivesChargesChange = {},
            onTeslamateBaseUrlChange = {},
            onTestConnection = {},
            onSave = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenWithBothResultsPreview() {
    MateDroidTheme {
        SettingsContent(
            uiState = SettingsUiState(
                isLoading = false,
                serverUrl = "https://teslamate.example.com",
                secondaryServerUrl = "https://teslamate.local",
                testResult = TestResult(
                    primaryResult = ServerTestResult.Failure("Connection timed out"),
                    secondaryResult = ServerTestResult.Success
                )
            ),
            onServerUrlChange = {},
            onSecondaryServerUrlChange = {},
            onApiTokenChange = {},
            onAcceptInvalidCertsChange = {},
            onCurrencyChange = {},
            onShowShortDrivesChargesChange = {},
            onTeslamateBaseUrlChange = {},
            onTestConnection = {},
            onSave = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenWithWarningPreview() {
    MateDroidTheme {
        SettingsContent(
            uiState = SettingsUiState(
                isLoading = false,
                serverUrl = "https://teslamate.example.com",
                acceptInvalidCerts = true
            ),
            onServerUrlChange = {},
            onSecondaryServerUrlChange = {},
            onApiTokenChange = {},
            onAcceptInvalidCertsChange = {},
            onCurrencyChange = {},
            onShowShortDrivesChargesChange = {},
            onTeslamateBaseUrlChange = {},
            onTestConnection = {},
            onSave = {}
        )
    }
}
