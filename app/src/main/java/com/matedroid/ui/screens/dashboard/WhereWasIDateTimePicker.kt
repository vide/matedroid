package com.matedroid.ui.screens.dashboard

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.matedroid.R

/**
 * Two-step date → time picker for the "Where was I?" feature. Picks a past date, then a
 * time of day, and emits an OffsetDateTime timestamp string in the device time zone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WhereWasIDateTimePicker(
    onDismiss: () -> Unit,
    onConfirm: (timestamp: String) -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val zoneId = java.time.ZoneId.systemDefault()
                val todayUtcDateMillis = java.time.LocalDate.now(zoneId)
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
                return utcTimeMillis <= todayUtcDateMillis
            }
        }
    )
    val timePickerState = rememberTimePickerState()

    if (!showTimePicker) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { showTimePicker = true }) {
                    Text(stringResource(R.string.where_was_i_go))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis ?: return@TextButton
                    val selectedDate = java.time.Instant.ofEpochMilli(selectedMillis)
                        .atZone(java.time.ZoneOffset.UTC)
                        .toLocalDate()

                    val localDateTime = selectedDate.atTime(timePickerState.hour, timePickerState.minute)
                    val zonedDateTime = localDateTime.atZone(java.time.ZoneId.systemDefault())
                    onConfirm(zonedDateTime.toOffsetDateTime().toString())
                }) {
                    Text(stringResource(R.string.where_was_i_go))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            title = {
                val selectedMillis = datePickerState.selectedDateMillis
                val dateText = if (selectedMillis != null) {
                    val date = java.time.Instant.ofEpochMilli(selectedMillis)
                        .atZone(java.time.ZoneOffset.UTC)
                        .toLocalDate()
                    date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
                } else ""
                Text(dateText)
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }
}
