package com.matedroid.ui.screens.settings.sections

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matedroid.R
import com.matedroid.ui.screens.settings.SettingsGroupHeader
import com.matedroid.ui.screens.settings.SettingsSectionScaffold
import com.matedroid.ui.screens.settings.SettingsSpacer
import com.matedroid.ui.screens.settings.SettingsViewModel
import com.matedroid.ui.theme.MateDroidTheme
import com.matedroid.ui.theme.StatusError
import com.matedroid.ui.theme.StatusWarning

/**
 * Maintenance actions on the local cache. Destructive operations confirm first.
 */
@Composable
fun DataSyncSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

    DataSyncSettingsContent(
        isResyncing = uiState.isResyncing,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onForceResync = viewModel::forceResync
    )
}

@Composable
private fun DataSyncSettingsContent(
    isResyncing: Boolean,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onForceResync: () -> Unit
) {
    var showResyncConfirmDialog by remember { mutableStateOf(false) }

    SettingsSectionScaffold(
        title = stringResource(R.string.settings_section_data),
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState
    ) {
        SettingsGroupHeader(stringResource(R.string.settings_group_maintenance))

        OutlinedButton(
            onClick = { showResyncConfirmDialog = true },
            enabled = !isResyncing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isResyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                stringResource(
                    if (isResyncing) R.string.settings_resyncing else R.string.settings_resync_button
                )
            )
        }

        Text(
            text = stringResource(R.string.settings_resync_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        SettingsSpacer()

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
    }
}

@Preview(showBackground = true)
@Composable
private fun DataSyncSettingsPreview() {
    MateDroidTheme {
        DataSyncSettingsContent(
            isResyncing = false,
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onForceResync = {}
        )
    }
}
