package com.matedroid.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.local.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StartDestinationViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _startDestination = MutableStateFlow<Screen?>(null)
    val startDestination: StateFlow<Screen?> = _startDestination.asStateFlow()

    private val _notificationPermissionAsked = MutableStateFlow(true) // default true to avoid flash
    val notificationPermissionAsked: StateFlow<Boolean> = _notificationPermissionAsked.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsDataStore.settings.first()
            _startDestination.value = if (settings.isConfigured) {
                Screen.Dashboard
            } else {
                Screen.Settings
            }
        }
        viewModelScope.launch {
            settingsDataStore.notificationPermissionAsked.collect {
                _notificationPermissionAsked.value = it
            }
        }
    }

    suspend fun markNotificationPermissionAsked() {
        settingsDataStore.saveNotificationPermissionAsked()
    }
}
