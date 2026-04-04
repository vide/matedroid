package com.matedroid.ui.screens.sentry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.local.entity.SentryAlertLog
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.ui.theme.StatusError
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Find the LazyColumn item index that corresponds to a given timestamp.
 * Returns the index of the first alert at or after that timestamp, or -1 if none found.
 */
private fun findAlertIndexForTimestamp(
    uiState: SentryHistoryUiState,
    targetMillis: Long
): Int {
    // Layout: item 0 = section header "Current Session"
    // Then current session alerts or empty card
    // Then past alerts section header + day groups
    var index = 1 // skip "Current Session" header

    // Current session alerts
    if (uiState.currentSessionAlerts.isEmpty()) {
        index++ // empty card
    } else {
        for (alert in uiState.currentSessionAlerts) {
            if (alert.detectedAt >= targetMillis && alert.detectedAt < targetMillis + HEATMAP_BUCKET_MS) {
                return index
            }
            index++
        }
    }

    // Past alerts
    if (uiState.pastAlertsByDay.isNotEmpty()) {
        index += 2 // spacer + "Past Alerts" header

        for (dayGroup in uiState.pastAlertsByDay) {
            index++ // day header
            for (alert in dayGroup.alerts) {
                if (alert.detectedAt >= targetMillis && alert.detectedAt < targetMillis + HEATMAP_BUCKET_MS) {
                    return index
                }
                index++
            }
        }
    }

    return -1
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentryHistoryScreen(
    carId: Int,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit = {},
    viewModel: SentryHistoryViewModel = hiltViewModel()
) {
    viewModel.setCarId(carId)
    val uiState by viewModel.uiState.collectAsState()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, darkTheme = true)
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sentry_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.surface,
                    titleContentColor = palette.onSurface,
                    navigationIconContentColor = palette.onSurface
                )
            )
        },
        containerColor = palette.surface
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "...",
                    color = palette.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // -- Heatmap --
                item(key = "heatmap") {
                    SentryHeatmap(
                        counts = uiState.heatmapCounts,
                        heatmapStartMillis = uiState.heatmapStartMillis,
                        palette = palette,
                        onHourTapped = { hourIndex ->
                            val targetMillis = uiState.heatmapStartMillis + hourIndex * HEATMAP_BUCKET_MS
                            val itemIndex = findAlertIndexForTimestamp(uiState, targetMillis)
                            if (itemIndex >= 0) {
                                coroutineScope.launch {
                                    // +1 to account for the heatmap item itself at position 0
                                    listState.animateScrollToItem(itemIndex + 1)
                                }
                            }
                        }
                    )
                }

                // -- Current Session --
                item {
                    SectionHeader(
                        text = stringResource(R.string.sentry_history_current_session),
                        palette = palette
                    )
                }

                if (uiState.currentSessionAlerts.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = palette.surface.copy(alpha = 0.7f)
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.sentry_history_no_alerts),
                                modifier = Modifier.padding(16.dp),
                                color = palette.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    items(uiState.currentSessionAlerts, key = { it.id }) { alert ->
                        AlertRow(alert = alert, palette = palette)
                    }
                }

                // -- Past Alerts --
                if (uiState.pastAlertsByDay.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SectionHeader(
                            text = stringResource(R.string.sentry_history_past),
                            palette = palette
                        )
                    }

                    uiState.pastAlertsByDay.forEach { dayGroup ->
                        item(key = "day_${dayGroup.dateMillis}") {
                            DayHeader(dateMillis = dayGroup.dateMillis, palette = palette)
                        }
                        items(dayGroup.alerts, key = { it.id }) { alert ->
                            AlertRow(alert = alert, palette = palette)
                        }
                    }
                } else if (uiState.currentSessionAlerts.isEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.sentry_history_empty),
                            color = palette.onSurfaceVariant.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

// -- Heatmap --

private val HeatmapWhite = Color(0xFF2A2A2A)
private val HeatmapRed = Color(0xFFE53935)

private fun heatmapColor(count: Int): Color {
    if (count <= 0) return HeatmapWhite
    val fraction = (count.coerceAtMost(20) / 20f)
    return Color(
        red = HeatmapWhite.red + (HeatmapRed.red - HeatmapWhite.red) * fraction,
        green = HeatmapWhite.green + (HeatmapRed.green - HeatmapWhite.green) * fraction,
        blue = HeatmapWhite.blue + (HeatmapRed.blue - HeatmapWhite.blue) * fraction,
        alpha = 1f
    )
}

@Composable
private fun SentryHeatmap(
    counts: IntArray,
    heatmapStartMillis: Long,
    palette: CarColorPalette,
    onHourTapped: (Int) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val startInstant = Instant.ofEpochMilli(heatmapStartMillis)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Cell size = (available width - label width - gaps) / 12
        val labelWidth = 48.dp
        val gapWidth = 2.dp * (HEATMAP_COLS - 1)
        val cellSizeDp = (maxWidth - labelWidth - gapWidth) / HEATMAP_COLS

    Column(modifier = Modifier.fillMaxWidth()) {
        // Day labels on the left, grid on the right
        val cellShape = RoundedCornerShape(3.dp)

        // Pre-compute the date for each row so we know which days span 2 rows
        val today = LocalDate.now(zone)
        val rowDates = (0 until HEATMAP_ROWS).map { row ->
            startInstant.plusMillis((row * HEATMAP_COLS).toLong() * HEATMAP_BUCKET_MS)
                .atZone(zone).toLocalDate()
        }

        for (row in 0 until HEATMAP_ROWS) {
            val hourOffset = row * HEATMAP_COLS
            val rowDate = rowDates[row]
            val isFirstRowOfDay = row == 0 || rowDate != rowDates[row - 1]
            val daySpansTwoRows = isFirstRowOfDay &&
                row + 1 < HEATMAP_ROWS && rowDates[row + 1] == rowDate

            val dayLabel = if (isFirstRowOfDay) {
                when (rowDate) {
                    today -> stringResource(R.string.sentry_history_today)
                    today.minusDays(1) -> stringResource(R.string.sentry_history_yesterday)
                    else -> rowDate.format(DateTimeFormatter.ofPattern("MMM d"))
                }
            } else ""

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Day label — offset down by half a row + gap when the day uses 2 rows
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .width(48.dp)
                        .then(
                            if (daySpansTwoRows) Modifier.offset(y = cellSizeDp / 2 + 1.dp)
                            else Modifier
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Hour cells
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (col in 0 until HEATMAP_COLS) {
                        val index = hourOffset + col
                        val count = if (index in counts.indices) counts[index] else 0

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(cellShape)
                                .background(heatmapColor(count))
                                .clickable { onHourTapped(index) }
                        )
                    }
                }
            }

            if (row < HEATMAP_ROWS - 1) {
                Spacer(modifier = Modifier.height(2.dp))
            }
        }

        // Hour scale below the grid
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Show a few hour markers: 0, 3, 6, 9
            for (h in listOf(0, 3, 6, 9)) {
                Text(
                    text = "%02d".format(h),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = palette.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    } // Column
    } // BoxWithConstraints
}

// -- Section / Day headers --

@Composable
private fun SectionHeader(
    text: String,
    palette: CarColorPalette
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = palette.onSurface,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun DayHeader(
    dateMillis: Long,
    palette: CarColorPalette
) {
    val localDate = Instant.ofEpochMilli(dateMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    val today = LocalDate.now()

    val label = when (localDate) {
        today -> stringResource(R.string.sentry_history_today)
        today.minusDays(1) -> stringResource(R.string.sentry_history_yesterday)
        else -> localDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = palette.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

// -- Alert row --

@Composable
private fun AlertRow(
    alert: SentryAlertLog,
    palette: CarColorPalette
) {
    val time = Instant.ofEpochMilli(alert.detectedAt)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
    val timeStr = time.format(DateTimeFormatter.ofPattern("HH:mm"))

    val displayText = alert.address
        ?: stringResource(R.string.sentry_alert_detected)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tesla-style sentry indicator: red dot + grey ring
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .border(2.dp, palette.onSurfaceVariant.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(StatusError, CircleShape)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = timeStr,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = palette.onSurfaceVariant
            )
        }
    }
}
