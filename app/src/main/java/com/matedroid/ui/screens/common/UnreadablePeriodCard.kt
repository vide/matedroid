package com.matedroid.ui.screens.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.matedroid.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * TeslamateAPI rejected the selected period because entries in it can't be read (see
 * [com.matedroid.domain.UnreadableDaySearch]). [days] are those entries' days once the search
 * has found them; [searching] is true while it is still looking; [mayBeMore] when the search
 * stopped before ruling out further bad days.
 */
data class UnreadablePeriod(
    val searching: Boolean = true,
    val days: List<LocalDate> = emptyList(),
    val mayBeMore: Boolean = false
)

/**
 * Shown in place of the empty-list card, so a server failure never reads as "nothing here".
 *
 * @param titleRes e.g. "TeslaMateApi couldn't load the charges for this period"
 * @param foundRes the explanation naming the day, with the formatted date as `%1$s`
 * @param foundManyRes the same for several days, with the formatted dates as `%1$s`
 */
@Composable
fun UnreadablePeriodCard(
    period: UnreadablePeriod,
    @StringRes titleRes: Int,
    @StringRes foundRes: Int,
    @StringRes foundManyRes: Int,
    modifier: Modifier = Modifier
) {
    ServerDataErrorCard(titleRes, modifier) {
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        val dates = period.days.joinToString(" · ") { it.format(formatter) }
        when {
            period.searching -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = stringResource(R.string.unreadable_period_searching),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            period.days.isNotEmpty() -> {
                Text(
                    text = stringResource(if (period.days.size == 1) foundRes else foundManyRes, dates),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                if (period.mayBeMore) {
                    Text(
                        text = stringResource(R.string.unreadable_period_may_be_more),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            else -> Text(
                text = stringResource(R.string.unreadable_period_not_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/**
 * Shown on a charge or drive detail screen when TeslamateAPI can't read that one entry —
 * the list loaded, but a NULL in one of its data points or positions breaks the detail query.
 */
@Composable
fun UnreadableEntryCard(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    modifier: Modifier = Modifier
) {
    ServerDataErrorCard(titleRes, modifier) {
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun ServerDataErrorCard(
    @StringRes titleRes: Int,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            content()
        }
    }
}
