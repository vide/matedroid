package com.matedroid.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matedroid.BuildConfig
import com.matedroid.R
import com.matedroid.data.local.AppSettings
import com.matedroid.data.model.Currency
import com.matedroid.ui.theme.MateDroidTheme
import androidx.compose.ui.tooling.preview.Preview

/**
 * Settings hub: a category list that navigates to one detail page per [SettingsSection].
 *
 * Each row carries a live summary of what the section currently holds, so the value a
 * user is looking for is visible without opening the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToSection: (SettingsSection) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SettingsHubViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        SettingsHubContent(
            modifier = Modifier.padding(paddingValues),
            settings = settings,
            onNavigateToSection = onNavigateToSection
        )
    }
}

@Composable
private fun SettingsHubContent(
    settings: AppSettings,
    onNavigateToSection: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsSection.visibleSections().forEach { section ->
            SettingsCategoryCard(
                icon = section.icon,
                title = stringResource(section.titleRes),
                summary = sectionSummary(section, settings),
                onClick = { onNavigateToSection(section) }
            )
        }
    }
}

/**
 * Summary line for a hub row: the current value where there is a single meaningful one,
 * the static description otherwise.
 */
@Composable
private fun sectionSummary(section: SettingsSection, settings: AppSettings): String =
    when (section) {
        SettingsSection.CONNECTION -> settings.serverUrl.takeIf { it.isNotBlank() }
            ?.removePrefix("https://")
            ?.removePrefix("http://")
            ?: stringResource(R.string.settings_not_configured)

        SettingsSection.DISPLAY -> Currency.findByCode(settings.currencyCode).let {
            "${it.symbol} ${it.code}"
        }

        SettingsSection.ABOUT -> "v${BuildConfig.VERSION_NAME}"

        else -> stringResource(section.summaryRes)
    }

@Preview(showBackground = true)
@Composable
private fun SettingsHubPreview() {
    MateDroidTheme {
        SettingsHubContent(
            settings = AppSettings(serverUrl = "https://teslamate.example.com"),
            onNavigateToSection = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsHubUnconfiguredPreview() {
    MateDroidTheme {
        SettingsHubContent(
            settings = AppSettings(),
            onNavigateToSection = {}
        )
    }
}
