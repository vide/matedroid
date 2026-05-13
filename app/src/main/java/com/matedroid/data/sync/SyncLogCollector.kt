package com.matedroid.data.sync

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collects sync-related log messages for display in debug builds.
 * Keeps a rolling buffer of the most recent log entries.
 */
@Singleton
class SyncLogCollector @Inject constructor() {

    companion object {
        private const val MAX_LOG_ENTRIES = 500
        private val dateFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.getDefault())
    }

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val logBuffer = mutableListOf<String>()

    @Synchronized
    fun log(tag: String, message: String) {
        val timestamp = java.time.LocalTime.now().format(dateFormat)
        val entry = "$timestamp [$tag] $message"

        // Also log to Android logcat
        Log.d(tag, message)

        logBuffer.add(entry)

        // Keep only the most recent entries
        while (logBuffer.size > MAX_LOG_ENTRIES) {
            logBuffer.removeAt(0)
        }

        _logs.value = logBuffer.toList()
    }

    @Synchronized
    fun logError(tag: String, message: String, error: Throwable? = null) {
        val timestamp = java.time.LocalTime.now().format(dateFormat)
        val errorMsg = error?.message?.let { " - $it" } ?: ""
        val entry = "$timestamp [$tag] ERROR: $message$errorMsg"

        // Also log to Android logcat
        Log.e(tag, message, error)

        logBuffer.add(entry)

        while (logBuffer.size > MAX_LOG_ENTRIES) {
            logBuffer.removeAt(0)
        }

        _logs.value = logBuffer.toList()
    }

    @Synchronized
    fun clear() {
        logBuffer.clear()
        _logs.value = emptyList()
    }
}
