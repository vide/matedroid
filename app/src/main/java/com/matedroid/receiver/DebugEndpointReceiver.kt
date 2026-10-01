package com.matedroid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.matedroid.BuildConfig
import com.matedroid.data.local.SettingsDataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Debug-only receiver for switching the Teslamate API endpoint via ADB.
 *
 * Usage:
 *   adb shell am broadcast -n com.matedroid/.receiver.DebugEndpointReceiver -a com.matedroid.SET_ENDPOINT --es url "http://host:port"
 *
 * Silently ignored in release builds.
 */
class DebugEndpointReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SettingsEntryPoint {
        fun settingsDataStore(): SettingsDataStore
    }

    companion object {
        private const val TAG = "DebugEndpointReceiver"
        const val ACTION = "com.matedroid.SET_ENDPOINT"

        // Stay well under the ~10 s budget the system grants a goAsync() receiver.
        private const val TIMEOUT_MS = 8_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return
        if (intent.action != ACTION) return

        val url = intent.getStringExtra("url")
        if (url.isNullOrBlank()) {
            Log.w(TAG, "Missing or empty 'url' extra")
            return
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SettingsEntryPoint::class.java
        )
        val settingsDataStore = entryPoint.settingsDataStore()

        // goAsync pattern: one short-lived scope per broadcast, always finished
        // (finish + cancel) whether the write succeeds, fails or times out.
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                withTimeout(TIMEOUT_MS) {
                    settingsDataStore.saveServerUrl(url)
                }
                Log.i(TAG, "API endpoint switched to: $url")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to switch endpoint", e)
            } finally {
                pendingResult.finish()
            }
        }.invokeOnCompletion {
            scope.cancel()
        }
    }
}
