package com.matedroid.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.matedroid.util.formatShortNoYear
import com.matedroid.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * Format a [LocalDate] as a locale-aware short date without year.
 * en-US: "5/10", zh-CN: "5/10", it-IT: "10/05".
 */
fun formatShortDate(date: LocalDate): String = date.formatShortNoYear(Locale.getDefault())

/**
 * Two-step date picker dialog: first picks the "from" date, then the "to" date.
 * Dates cannot be in the future. The "to" date cannot be before the "from" date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onRangeSelected: (start: LocalDate, end: LocalDate) -> Unit,
    initialStart: LocalDate? = null,
    initialEnd: LocalDate? = null
) {
    val todayMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    // Step 0 = picking "from", step 1 = picking "to"
    var step by remember { mutableIntStateOf(0) }
    var selectedStart by remember { mutableStateOf(initialStart) }

    val notFuture = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
    }

    if (step == 0) {
        val fromState = rememberDatePickerState(
            initialSelectedDateMillis = initialStart
                ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            selectableDates = notFuture
        )

        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        fromState.selectedDateMillis?.let { millis ->
                            selectedStart = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC).toLocalDate()
                            step = 1
                        }
                    },
                    enabled = fromState.selectedDateMillis != null
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        ) {
            DatePicker(
                state = fromState,
                title = {
                    Text(
                        stringResource(R.string.filter_custom_from),
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                    )
                }
            )
        }
    } else {
        val startMillis = selectedStart!!
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val afterStart = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                utcTimeMillis in startMillis..todayMillis
        }

        val toState = rememberDatePickerState(
            initialSelectedDateMillis = initialEnd
                ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            selectableDates = afterStart
        )

        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        toState.selectedDateMillis?.let { millis ->
                            val end = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC).toLocalDate()
                            onRangeSelected(selectedStart!!, end)
                        }
                    },
                    enabled = toState.selectedDateMillis != null
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        ) {
            DatePicker(
                state = toState,
                title = {
                    Text(
                        stringResource(R.string.filter_custom_to),
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                    )
                }
            )
        }
    }
}
