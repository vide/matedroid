package com.matedroid.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.graphics.vector.ImageVector
import com.matedroid.BuildConfig
import com.matedroid.R

/**
 * The settings hub is a category list; each entry here is one detail page.
 *
 * [id] is the stable navigation argument — it is persisted in the back stack and used by
 * deep links, so renaming one is a breaking change. The enum order is the display order.
 */
enum class SettingsSection(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
    val icon: ImageVector,
    val debugOnly: Boolean = false
) {
    CONNECTION(
        id = "connection",
        titleRes = R.string.settings_section_connection,
        summaryRes = R.string.settings_section_connection_summary,
        icon = Icons.Filled.Dns
    ),
    DISPLAY(
        id = "display",
        titleRes = R.string.settings_section_display,
        summaryRes = R.string.settings_section_display_summary,
        icon = Icons.Filled.Palette
    ),
    NOTIFICATIONS(
        id = "notifications",
        titleRes = R.string.settings_section_notifications,
        summaryRes = R.string.settings_section_notifications_summary,
        icon = Icons.Filled.Notifications
    ),
    DATA(
        id = "data",
        titleRes = R.string.settings_section_data,
        summaryRes = R.string.settings_section_data_summary,
        icon = Icons.Filled.Sync
    ),
    ABOUT(
        id = "about",
        titleRes = R.string.settings_section_about,
        summaryRes = R.string.settings_section_about_summary,
        icon = Icons.Filled.Info
    ),
    DEBUG(
        id = "debug",
        titleRes = R.string.settings_section_debug,
        summaryRes = R.string.settings_section_debug_summary,
        icon = Icons.Filled.BugReport,
        debugOnly = true
    );

    companion object {
        /** Falls back to [CONNECTION] so an unknown deep link never lands on a blank page. */
        fun fromId(id: String): SettingsSection = entries.firstOrNull { it.id == id } ?: CONNECTION

        /** Sections shown in the hub for the current build type. */
        fun visibleSections(): List<SettingsSection> =
            entries.filter { !it.debugOnly || BuildConfig.DEBUG }
    }
}
