package com.matedroid.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.local.AppSettings
import com.matedroid.data.local.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Backs the settings hub only. Deliberately lighter than [SettingsViewModel]: the hub
 * just needs the current values to render each category's summary line, so it depends on
 * the DataStore alone rather than the repository, sync manager and notification managers.
 */
@HiltViewModel
class SettingsHubViewModel @Inject constructor(
    settingsDataStore: SettingsDataStore
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsDataStore.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )
}
