package com.matedroid.ui.screens.settings

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
                title = { Text("MateDroid Settings") }
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
                onApiTokenChange = viewModel::updateApiToken,
                onAcceptInvalidCertsChange = viewModel::updateAcceptInvalidCerts,
                onCurrencyChange = viewModel::updateCurrency,
                onShowShortDrivesChargesChange = viewModel::updateShowShortDrivesCharges,
                onTeslamateBaseUrlChange = viewModel::updateTeslamateBaseUrl,
                onTestConnection = viewModel::testConnection,
                onSave = { viewModel.saveSettings(onNavigateToDashboard) },
                onPalettePreview = onNavigateToPalettePreview,
                onForceResync = viewModel::forceResync
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
        Text("Loading settings...")
    }
}

@Composable
private fun SettingsContent(
    modifier: Modifier = Modifier,
    uiState: SettingsUiState,
    onServerUrlChange: (String) -> Unit,
    onApiTokenChange: (String) -> Unit,
    onAcceptInvalidCertsChange: (Boolean) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onShowShortDrivesChargesChange: (Boolean) -> Unit,
    onTeslamateBaseUrlChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit,
    onPalettePreview: () -> Unit = {},
    onForceResync: () -> Unit = {}
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var currencyDropdownExpanded by remember { mutableStateOf(false) }
    var showShortDrivesChargesInfoDialog by remember { mutableStateOf(false) }
    var showResyncConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Connect to TeslamateApi",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Enter your TeslamateApi server URL and optional API token to connect.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = uiState.serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text("Server URL") },
            placeholder = { Text("https://teslamate.example.com") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("urlInput"),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            enabled = !uiState.isTesting && !uiState.isSaving
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.apiToken,
            onValueChange = onApiTokenChange,
            label = { Text("API Token (optional)") },
            placeholder = { Text("Your API token") },
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
                        contentDescription = if (passwordVisible) {
                            "Hide token"
                        } else {
                            "Show token"
                        }
                    )
                }
            },
            enabled = !uiState.isTesting && !uiState.isSaving
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Leave empty if your TeslamateApi doesn't require authentication.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    text = "Accept invalid certificates",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Enable for self-signed certificates",
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
                        text = "Disabling certificate validation makes connections vulnerable to man-in-the-middle attacks. Only use on trusted networks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusWarning
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Teslamate Settings
        Text(
            text = "Teslamate Settings",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.teslamateBaseUrl,
            onValueChange = onTeslamateBaseUrlChange,
            label = { Text("Teslamate Base URL") },
            placeholder = { Text("https://teslamate.example.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )

        Text(
            text = "Used for editing charge costs directly in Teslamate.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Currency selection
        Text(
            text = "Display Settings",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        val selectedCurrency = Currency.findByCode(uiState.currencyCode)

        Box {
            OutlinedTextField(
                value = "${selectedCurrency.symbol} ${selectedCurrency.code} - ${selectedCurrency.name}",
                onValueChange = {},
                label = { Text("Currency for costs") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { currencyDropdownExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "Select currency"
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
                        text = "Show short drives / charges",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(
                        onClick = { showShortDrivesChargesInfoDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "More information",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = "Display very short drives and minimal charges in lists",
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
                title = { Text("Short drives & charges") },
                text = {
                    Text(
                        "When disabled (default), the following are hidden from the lists:\n\n" +
                        "• Drives under 1 minute or less than 0.1 km\n" +
                        "• Charges of 0.1 kWh or less\n\n" +
                        "These entries are still included in totals, averages, and statistics. " +
                        "Enable this setting to see all entries in the lists."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showShortDrivesChargesInfoDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Data Management section
        Text(
            text = "Data Management",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

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
            Text(if (uiState.isResyncing) "Resyncing..." else "Force Full Resync")
        }

        Text(
            text = "Re-downloads all drive and charge details. Use if stats seem incorrect.",
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
                title = { Text("Force Full Resync?") },
                text = {
                    Text(
                        buildAnnotatedString {
                            append("This will ")
                            withStyle(SpanStyle(color = StatusError, fontWeight = FontWeight.Bold)) {
                                append("DELETE")
                            }
                            append(" all cached drives, charges, and statistics, then re-download everything from the server.\n\n")
                            append("The process may take several minutes depending on how much data you have.")
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
                        Text("Resync")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResyncConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
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
                Text("Test Connection")
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
                Text("Save & Continue")
            }
        }

        // Debug: Palette Preview button (only visible in debug builds)
        if (com.matedroid.BuildConfig.DEBUG) {
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedButton(
                onClick = onPalettePreview,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Preview Color Palettes (Debug)")
            }
        }

        // Version number at bottom
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "v${com.matedroid.BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TestResultCard(result: TestResult) {
    val (icon, color, text) = when (result) {
        is TestResult.Success -> Triple(
            Icons.Filled.CheckCircle,
            StatusSuccess,
            "Connection successful!"
        )
        is TestResult.Failure -> Triple(
            Icons.Filled.Error,
            StatusError,
            "Connection failed: ${result.message}"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.bodyMedium
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
                testResult = TestResult.Success
            ),
            onServerUrlChange = {},
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
