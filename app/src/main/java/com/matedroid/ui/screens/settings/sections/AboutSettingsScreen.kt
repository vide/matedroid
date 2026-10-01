package com.matedroid.ui.screens.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.matedroid.BuildConfig
import com.matedroid.R
import com.matedroid.ui.screens.settings.SettingsGroupHeader
import com.matedroid.ui.screens.settings.SettingsLinkRow
import com.matedroid.ui.screens.settings.SettingsSectionScaffold
import com.matedroid.ui.screens.settings.SettingsSpacer
import com.matedroid.ui.theme.MateDroidTheme

private const val ISSUES_URL = "https://github.com/vide/matedroid/issues"
private const val SOURCE_URL = "https://github.com/vide/matedroid"

/** App identity and where to go for help. */
@Composable
fun AboutSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    AboutSettingsContent(
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onOpenIssues = { uriHandler.openUri(ISSUES_URL) },
        onOpenSource = { uriHandler.openUri(SOURCE_URL) }
    )
}

@Composable
private fun AboutSettingsContent(
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onOpenIssues: () -> Unit,
    onOpenSource: () -> Unit
) {
    SettingsSectionScaffold(
        title = stringResource(R.string.settings_section_about),
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_SHA})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }

        SettingsSpacer(24)

        SettingsGroupHeader(stringResource(R.string.settings_group_support))

        SettingsLinkRow(
            title = stringResource(R.string.settings_report_issue),
            hint = stringResource(R.string.settings_report_issue_hint),
            icon = Icons.Filled.BugReport,
            onClick = onOpenIssues
        )

        HorizontalDivider()

        SettingsLinkRow(
            title = stringResource(R.string.settings_source_code),
            hint = stringResource(R.string.settings_source_code_hint),
            icon = Icons.Filled.Code,
            onClick = onOpenSource
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AboutSettingsPreview() {
    MateDroidTheme {
        AboutSettingsContent(
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onOpenIssues = {},
            onOpenSource = {}
        )
    }
}
