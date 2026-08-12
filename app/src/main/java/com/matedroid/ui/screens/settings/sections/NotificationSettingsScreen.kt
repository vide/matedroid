package com.matedroid.ui.screens.settings.sections

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.matedroid.R
import com.matedroid.notification.ChargingNotificationManager
import com.matedroid.notification.SentryNotificationManager
import com.matedroid.data.sync.TpmsPressureWorker
import com.matedroid.ui.screens.settings.SettingsGroupHeader
import com.matedroid.ui.screens.settings.SettingsLinkRow
import com.matedroid.ui.screens.settings.SettingsSectionScaffold
import com.matedroid.ui.screens.settings.SettingsSpacer
import com.matedroid.ui.theme.MateDroidTheme

/**
 * Notification settings hand off to the Android system UI rather than duplicating the
 * per-channel toggles in-app: the OS already owns sound, importance and Do Not Disturb
 * for each channel, and an in-app copy would drift out of sync with it.
 */
@Composable
fun NotificationSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    NotificationSettingsContent(
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onOpenChannel = { channelId -> context.openNotificationChannelSettings(channelId) },
        onOpenAppNotifications = { context.openAppNotificationSettings() }
    )
}

@Composable
private fun NotificationSettingsContent(
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenAppNotifications: () -> Unit
) {
    SettingsSectionScaffold(
        title = stringResource(R.string.settings_section_notifications),
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState
    ) {
        Text(
            text = stringResource(R.string.settings_notifications_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SettingsSpacer(24)

        SettingsGroupHeader(stringResource(R.string.settings_group_channels))

        SettingsLinkRow(
            title = stringResource(R.string.charging_channel_name),
            hint = stringResource(R.string.charging_channel_description),
            icon = Icons.Filled.Bolt,
            onClick = { onOpenChannel(ChargingNotificationManager.CHANNEL_ID) }
        )

        SettingsLinkRow(
            title = stringResource(R.string.sentry_channel_name),
            hint = stringResource(R.string.sentry_channel_description),
            icon = Icons.Filled.Security,
            onClick = { onOpenChannel(SentryNotificationManager.CHANNEL_ID) }
        )

        SettingsLinkRow(
            title = stringResource(R.string.tpms_channel_name),
            hint = stringResource(R.string.tpms_channel_description),
            icon = Icons.Filled.TireRepair,
            onClick = { onOpenChannel(TpmsPressureWorker.CHANNEL_ID) }
        )

        SettingsSpacer(8)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsLinkRow(
            title = stringResource(R.string.settings_notifications_all_title),
            hint = stringResource(R.string.settings_notifications_all_hint),
            icon = Icons.Filled.NotificationsActive,
            onClick = onOpenAppNotifications
        )
    }
}

/**
 * Opens the system page for one channel. A channel only exists once it has been created,
 * which happens the first time that kind of notification fires — until then the intent is
 * rejected, so fall back to the app-level notification page.
 */
private fun Context.openNotificationChannelSettings(channelId: String) {
    val channelIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    val launched = runCatching { startActivity(channelIntent) }.isSuccess
    if (!launched) {
        openAppNotificationSettings()
    }
}

private fun Context.openAppNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

@Preview(showBackground = true)
@Composable
private fun NotificationSettingsPreview() {
    MateDroidTheme {
        NotificationSettingsContent(
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onOpenChannel = {},
            onOpenAppNotifications = {}
        )
    }
}
