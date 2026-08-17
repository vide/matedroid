package com.matedroid.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matedroid.R
import com.matedroid.domain.ConnectionTimeout
import com.matedroid.ui.screens.settings.ServerTestResult
import com.matedroid.ui.screens.settings.SettingsGroupHeader
import com.matedroid.ui.screens.settings.SettingsPresetPicker
import com.matedroid.ui.screens.settings.SettingsSectionScaffold
import com.matedroid.ui.screens.settings.SettingsSpacer
import com.matedroid.ui.screens.settings.SettingsUiState
import com.matedroid.ui.screens.settings.SettingsViewModel
import com.matedroid.ui.screens.settings.TestResult
import com.matedroid.ui.theme.MateDroidTheme
import com.matedroid.ui.theme.StatusError
import com.matedroid.ui.theme.StatusSuccess
import com.matedroid.ui.theme.StatusWarning

/**
 * Server connection settings, laid out as flat groups (Server / Authentication /
 * Security / Network) rather than hiding the optional fields behind a disclosure. Reaching this
 * page is already one level of disclosure; nesting another inside it is what used to
 * make HTTP Basic Auth hard to find. Each optional field says so in its own label.
 *
 * The only section with an explicit save: URL and credentials need validating together,
 * so a half-typed URL must not be committed. The timeout picker sits outside that form and
 * applies as soon as it is picked, so Test Connection exercises the value just chosen.
 *
 * During first-run onboarding ([isOnboarding]) there is no back arrow and saving
 * continues to the dashboard; afterwards saving stays put and confirms with a snackbar.
 */
@Composable
fun ConnectionSettingsScreen(
    isOnboarding: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.settings_saved)

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

    ConnectionSettingsContent(
        uiState = uiState,
        isOnboarding = isOnboarding,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack.takeIf { !isOnboarding },
        onServerUrlChange = viewModel::updateServerUrl,
        onSecondaryServerUrlChange = viewModel::updateSecondaryServerUrl,
        onApiTokenChange = viewModel::updateApiToken,
        onHttpBasicAuthUsernameChange = viewModel::updateHttpBasicAuthUsername,
        onHttpBasicAuthPasswordChange = viewModel::updateHttpBasicAuthPassword,
        onAcceptInvalidCertsChange = viewModel::updateAcceptInvalidCerts,
        onConnectTimeoutChange = viewModel::updateConnectTimeoutSeconds,
        onTestConnection = viewModel::testConnection,
        onSave = {
            viewModel.saveSettings {
                if (isOnboarding) {
                    onNavigateToDashboard()
                } else {
                    viewModel.showMessage(savedMessage)
                }
            }
        }
    )
}

@Composable
private fun ConnectionSettingsContent(
    uiState: SettingsUiState,
    isOnboarding: Boolean,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: (() -> Unit)?,
    onServerUrlChange: (String) -> Unit,
    onSecondaryServerUrlChange: (String) -> Unit,
    onApiTokenChange: (String) -> Unit,
    onHttpBasicAuthUsernameChange: (String) -> Unit,
    onHttpBasicAuthPasswordChange: (String) -> Unit,
    onAcceptInvalidCertsChange: (Boolean) -> Unit,
    onConnectTimeoutChange: (Int) -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var basicAuthPasswordVisible by remember { mutableStateOf(false) }
    val fieldsEnabled = !uiState.isTesting && !uiState.isSaving

    SettingsSectionScaffold(
        title = stringResource(R.string.settings_section_connection),
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        bottomBar = {
            ConnectionActionBar(
                uiState = uiState,
                isOnboarding = isOnboarding,
                onTestConnection = onTestConnection,
                onSave = onSave
            )
        }
    ) {
        Text(
            text = stringResource(R.string.settings_connect_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SettingsSpacer(24)

        // ==================== Server ====================
        SettingsGroupHeader(stringResource(R.string.settings_group_server))

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
            enabled = fieldsEnabled
        )

        SettingsSpacer()

        OutlinedTextField(
            value = uiState.secondaryServerUrl,
            onValueChange = onSecondaryServerUrlChange,
            label = { Text(stringResource(R.string.settings_secondary_url_label)) },
            placeholder = { Text(stringResource(R.string.settings_secondary_url_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            enabled = fieldsEnabled
        )

        FieldHint(stringResource(R.string.settings_secondary_url_hint))

        SettingsSpacer(24)

        // ==================== Authentication ====================
        SettingsGroupHeader(stringResource(R.string.settings_group_authentication))

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
                VisibilityToggle(
                    visible = passwordVisible,
                    onToggle = { passwordVisible = !passwordVisible },
                    showDescription = R.string.show_token,
                    hideDescription = R.string.hide_token
                )
            },
            enabled = fieldsEnabled
        )

        FieldHint(stringResource(R.string.settings_api_token_hint))

        SettingsSpacer()

        OutlinedTextField(
            value = uiState.httpBasicAuthUsername,
            onValueChange = onHttpBasicAuthUsernameChange,
            label = { Text(stringResource(R.string.settings_http_basic_auth_username_label)) },
            placeholder = { Text(stringResource(R.string.settings_http_basic_auth_username_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = fieldsEnabled
        )

        SettingsSpacer(8)

        OutlinedTextField(
            value = uiState.httpBasicAuthPassword,
            onValueChange = onHttpBasicAuthPasswordChange,
            label = { Text(stringResource(R.string.settings_http_basic_auth_password_label)) },
            placeholder = { Text(stringResource(R.string.settings_http_basic_auth_password_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (basicAuthPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                VisibilityToggle(
                    visible = basicAuthPasswordVisible,
                    onToggle = { basicAuthPasswordVisible = !basicAuthPasswordVisible },
                    showDescription = R.string.show_password,
                    hideDescription = R.string.hide_password
                )
            },
            enabled = fieldsEnabled
        )

        FieldHint(stringResource(R.string.settings_http_basic_auth_hint))

        SettingsSpacer(24)

        // ==================== Security ====================
        SettingsGroupHeader(stringResource(R.string.settings_group_security))

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
                enabled = fieldsEnabled
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

        SettingsSpacer(24)

        // ==================== Network ====================
        SettingsGroupHeader(stringResource(R.string.settings_group_network))

        SettingsPresetPicker(
            label = stringResource(R.string.settings_connect_timeout_label),
            value = uiState.connectTimeoutSeconds,
            options = ConnectionTimeout.PRESETS,
            enabled = fieldsEnabled,
            optionLabel = { seconds ->
                if (seconds == ConnectionTimeout.AUTO) {
                    stringResource(
                        R.string.settings_connect_timeout_automatic,
                        ConnectionTimeout.resolveSeconds(
                            setting = ConnectionTimeout.AUTO,
                            hasFallbackServer = uiState.secondaryServerUrl.isNotBlank()
                        )
                    )
                } else {
                    stringResource(R.string.settings_connect_timeout_value, seconds)
                }
            },
            onValueChange = onConnectTimeoutChange
        )

        FieldHint(stringResource(R.string.settings_connect_timeout_hint))

        uiState.testResult?.let { result ->
            SettingsSpacer(24)
            TestResultCard(result = result)
        }

        SettingsSpacer()
    }
}

/** Test and Save pinned to the bottom so they stay reachable with the form scrolled. */
@Composable
private fun ConnectionActionBar(
    uiState: SettingsUiState,
    isOnboarding: Boolean,
    onTestConnection: () -> Unit,
    onSave: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // The bottomBar slot sits below Scaffold's inset padding, so it has to
                // clear the system navigation bar itself.
                .navigationBarsPadding()
                .padding(16.dp),
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
                Text(
                    stringResource(
                        if (isOnboarding) R.string.settings_save else R.string.settings_save_short
                    )
                )
            }
        }
    }
}

@Composable
private fun VisibilityToggle(
    visible: Boolean,
    onToggle: () -> Unit,
    showDescription: Int,
    hideDescription: Int
) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription = stringResource(if (visible) hideDescription else showDescription)
        )
    }
}

@Composable
private fun FieldHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
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
            ServerTestResultRow(
                label = stringResource(R.string.settings_primary_server),
                result = result.primaryResult
            )

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
private fun ConnectionSettingsPreview() {
    MateDroidTheme {
        ConnectionSettingsContent(
            uiState = SettingsUiState(
                isLoading = false,
                serverUrl = "https://teslamate.example.com"
            ),
            isOnboarding = false,
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onServerUrlChange = {},
            onSecondaryServerUrlChange = {},
            onApiTokenChange = {},
            onHttpBasicAuthUsernameChange = {},
            onHttpBasicAuthPasswordChange = {},
            onAcceptInvalidCertsChange = {},
            onConnectTimeoutChange = {},
            onTestConnection = {},
            onSave = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionSettingsOnboardingPreview() {
    MateDroidTheme {
        ConnectionSettingsContent(
            uiState = SettingsUiState(isLoading = false),
            isOnboarding = true,
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = null,
            onServerUrlChange = {},
            onSecondaryServerUrlChange = {},
            onApiTokenChange = {},
            onHttpBasicAuthUsernameChange = {},
            onHttpBasicAuthPasswordChange = {},
            onAcceptInvalidCertsChange = {},
            onConnectTimeoutChange = {},
            onTestConnection = {},
            onSave = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionSettingsWithResultPreview() {
    MateDroidTheme {
        ConnectionSettingsContent(
            uiState = SettingsUiState(
                isLoading = false,
                serverUrl = "https://teslamate.example.com",
                secondaryServerUrl = "https://teslamate.local",
                acceptInvalidCerts = true,
                testResult = TestResult(
                    primaryResult = ServerTestResult.Failure("Connection timed out"),
                    secondaryResult = ServerTestResult.Success
                )
            ),
            isOnboarding = false,
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onServerUrlChange = {},
            onSecondaryServerUrlChange = {},
            onApiTokenChange = {},
            onHttpBasicAuthUsernameChange = {},
            onHttpBasicAuthPasswordChange = {},
            onAcceptInvalidCertsChange = {},
            onConnectTimeoutChange = {},
            onTestConnection = {},
            onSave = {}
        )
    }
}
