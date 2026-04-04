package com.matedroid.ui.screens.sentry

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)
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

/**
 * Positions the tooltip centered horizontally on the anchor cell,
 * either above or below it with a pixel gap. Clamps horizontally
 * to stay within the window.
 */
private class HeatmapTooltipPosition(
    private val placeAbove: Boolean,
    private val gapPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        // Center horizontally on anchor, clamp to window edges
        val x = (anchorBounds.left + anchorBounds.width / 2 - popupContentSize.width / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = if (placeAbove) {
            anchorBounds.top - popupContentSize.height - gapPx
        } else {
            anchorBounds.bottom + gapPx
        }
        return IntOffset(x, y)
    }
}

private fun heatmapColor(count: Int, emptyColor: Color, fullColor: Color): Color {
    if (count <= 0) return emptyColor
    val fraction = (count.coerceAtMost(20) / 20f)
    return Color(
        red = emptyColor.red + (fullColor.red - emptyColor.red) * fraction,
        green = emptyColor.green + (fullColor.green - emptyColor.green) * fraction,
        blue = emptyColor.blue + (fullColor.blue - emptyColor.blue) * fraction,
        alpha = emptyColor.alpha + (fullColor.alpha - emptyColor.alpha) * fraction
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
    val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    // Tooltip state: -1 = none selected
    var selectedIndex by remember { mutableIntStateOf(-1) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        val cellShape = RoundedCornerShape(3.dp)
        val emptyColor = palette.onSurfaceVariant.copy(alpha = 0.1f)
        val fullColor = StatusError
        val gapPx = with(LocalDensity.current) { 4.dp.roundToPx() }
        val today = LocalDate.now(zone)
        val nowIndex = ((System.currentTimeMillis() - heatmapStartMillis) / HEATMAP_BUCKET_MS).toInt()

        for (row in 0 until HEATMAP_ROWS) {
            val blockOffset = row * HEATMAP_COLS
            val rowDate = startInstant.plusMillis(blockOffset.toLong() * HEATMAP_BUCKET_MS)
                .atZone(zone).toLocalDate()

            val dayLabel = when (rowDate) {
                today -> stringResource(R.string.sentry_history_today)
                today.minusDays(1) -> stringResource(R.string.sentry_history_yesterday)
                else -> rowDate.format(DateTimeFormatter.ofPattern("EEE d"))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.width(48.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (col in 0 until HEATMAP_COLS) {
                        val index = blockOffset + col
                        val isFuture = index > nowIndex

                        if (isFuture) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            )
                        } else {
                            val count = if (index in counts.indices) counts[index] else 0
                            val isSelected = selectedIndex == index
                            val placeAbove = row >= HEATMAP_ROWS / 2

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(cellShape)
                                    .background(heatmapColor(count, emptyColor, fullColor))
                                    .then(
                                        if (isSelected) Modifier.border(
                                            2.dp,
                                            palette.onSurface,
                                            cellShape
                                        )
                                        else Modifier
                                    )
                                    .clickable {
                                        selectedIndex = if (isSelected) -1 else index
                                    }
                            ) {
                                // Tooltip anchored to this cell
                                if (isSelected) {
                                    val blockStart = startInstant.plusMillis(index.toLong() * HEATMAP_BUCKET_MS)
                                        .atZone(zone)
                                    val blockEnd = blockStart.plusHours(HEATMAP_BUCKET_HOURS.toLong())
                                    val dateLine = blockStart.toLocalDate().format(dateFormatter)
                                    val timeLine = "${blockStart.toLocalTime().format(timeFormatter)} – ${blockEnd.toLocalTime().format(timeFormatter)}"
                                    val eventsLine = pluralStringResource(
                                        R.plurals.sentry_notification_body, count, count
                                    )

                                    Popup(
                                        popupPositionProvider = HeatmapTooltipPosition(
                                            placeAbove = placeAbove,
                                            gapPx = gapPx
                                        ),
                                        onDismissRequest = { selectedIndex = -1 },
                                        properties = PopupProperties(focusable = true)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .background(
                                                    color = MaterialTheme.colorScheme.inverseSurface,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    val idx = selectedIndex
                                                    selectedIndex = -1
                                                    onHourTapped(idx)
                                                }
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = dateLine,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = timeLine,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = eventsLine,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (row < HEATMAP_ROWS - 1) {
                Spacer(modifier = Modifier.height(2.dp))
            }
        }

        // Hour scale: 12 cols × 2h = 24h, locale-aware time format
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (h in listOf(0, 6, 12, 18, 23)) {
                val label = java.time.LocalTime.of(h, 0).format(timeFormatter)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = palette.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
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
