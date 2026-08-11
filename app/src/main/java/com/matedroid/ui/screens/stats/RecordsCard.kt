package com.matedroid.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.DeepStats
import com.matedroid.domain.model.MaxDistanceBetweenChargesRecord
import com.matedroid.domain.model.QuickStats
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.ui.theme.CarColorPalette

/** Data class for a single record item */
private data class RecordData(
    val emoji: String,
    val label: String,
    val value: String,
    val subtext: String,
    val onClick: (() -> Unit)?
)

/**
 * HARD CONSTRAINT: Each page displays exactly 6 record slots (3 rows × 2 columns).
 * If a category has more than 6 records, it MUST be split into multiple pages.
 * This ensures consistent page height and smooth swiping experience.
 */
private const val RECORDS_PER_PAGE = 6

/** A page of records to display in the pager */
private data class RecordPage(
    val categoryTitle: String,
    val categoryEmoji: String,
    val records: List<RecordData>, // Max RECORDS_PER_PAGE items
    val pageIndex: Int, // 0-based index within the category (for multi-page categories)
    val totalPagesInCategory: Int // Total pages for this category
)

@Composable
internal fun RecordsCard(
    quickStats: QuickStats,
    deepStats: DeepStats?,
    palette: CarColorPalette,
    currencySymbol: String,
    units: Units?,
    selectedCategory: String,
    onCategoryChanged: (String) -> Unit,
    onDriveClick: (Int) -> Unit,
    onChargeClick: (Int) -> Unit,
    onDayClick: (String) -> Unit,
    onCountriesVisitedClick: () -> Unit,
    onRangeRecordClick: (MaxDistanceBetweenChargesRecord) -> Unit,
    onGapRecordClick: (Double, String, String, String) -> Unit // gapDays, fromDate, toDate, title
) {
    // Pre-compute localized strings for use in lambdas
    val labelLongestDrive = stringResource(R.string.record_longest_drive)
    val labelTopSpeed = stringResource(R.string.record_top_speed)
    val labelMostEfficient = stringResource(R.string.record_most_efficient)
    val labelLongestStreak = stringResource(R.string.record_longest_streak)
    val labelBusiestDay = stringResource(R.string.record_busiest_day)
    val labelCountriesVisited = stringResource(R.string.record_countries_visited)
    val labelBiggestGain = stringResource(R.string.record_biggest_gain)
    val labelBiggestDrain = stringResource(R.string.record_biggest_drain)
    val labelBiggestCharge = stringResource(R.string.record_biggest_charge)
    val labelPeakPower = stringResource(R.string.record_peak_power)
    val labelMostExpensive = stringResource(R.string.record_most_expensive)
    val labelPriciestKwh = stringResource(R.string.record_priciest_kwh)
    val labelHighestPoint = stringResource(R.string.record_highest_point)
    val labelMostClimbing = stringResource(R.string.record_most_climbing)
    val labelHottestDrive = stringResource(R.string.record_hottest_drive)
    val labelColdestDrive = stringResource(R.string.record_coldest_drive)
    val labelHottestCharge = stringResource(R.string.record_hottest_charge)
    val labelColdestCharge = stringResource(R.string.record_coldest_charge)
    val labelLongestRange = stringResource(R.string.record_longest_range)
    val labelNoCharging = stringResource(R.string.record_longest_no_charging)
    val labelNoDriving = stringResource(R.string.record_longest_no_driving)
    val labelMostDistanceDay = stringResource(R.string.record_most_distance_day)
    val categoryDrives = stringResource(R.string.stats_category_drives)
    val categoryBattery = stringResource(R.string.stats_category_battery)
    val categoryWeather = stringResource(R.string.stats_category_weather)
    val categoryMisc = stringResource(R.string.stats_category_misc)
    val gapTypeCharging = stringResource(R.string.gap_type_charging)
    val gapTypeDriving = stringResource(R.string.gap_type_driving)
    val valueNotAvailable = stringResource(R.string.value_not_available)
    // Data-dependent strings resolved here because stringResource is composable and
    // cannot be called inside the remember block below
    val valueLongestStreak = quickStats.longestDrivingStreak?.let { stringResource(R.string.format_days_count, it.streakDays) }
    val valueBusiestDay = quickStats.busiestDay?.let { stringResource(R.string.format_drives_count, it.count) }
    val valueCountriesVisited = deepStats?.countriesVisitedCount?.let { pluralStringResource(R.plurals.format_countries_count, it, it) }
    val valueGapCharging = quickStats.longestGapWithoutCharging?.let { stringResource(R.string.format_days, it.gapDays) }
    val valueGapDriving = quickStats.longestGapWithoutDriving?.let { stringResource(R.string.format_days, it.gapDays) }

    // Records and paging are pure derivations of the stats — rebuild only when inputs change,
    // not on every pager swipe recomposition
    val pages = remember(quickStats, deepStats, units, currencySymbol) {
        // Category 1: Drives
        val driveRecords = mutableListOf<RecordData>()
        quickStats.longestDrive?.let { drive ->
            driveRecords.add(RecordData("📏", labelLongestDrive, UnitFormatter.formatDistance(drive.distance, units), drive.startDate.take(10)) { onDriveClick(drive.driveId) })
        }
        quickStats.fastestDrive?.let { drive ->
            driveRecords.add(RecordData("🏎️", labelTopSpeed, UnitFormatter.formatSpeed(drive.speedMax.toDouble(), units), drive.startDate.take(10)) { onDriveClick(drive.driveId) })
        }
        quickStats.mostEfficientDrive?.let { drive ->
            driveRecords.add(RecordData("🌱", labelMostEfficient, UnitFormatter.formatEfficiency(drive.efficiency ?: 0.0, units, 0), drive.startDate.take(10)) { onDriveClick(drive.driveId) })
        }
        quickStats.longestDrivingStreak?.let { streak ->
            driveRecords.add(RecordData("🔥", labelLongestStreak, valueLongestStreak ?: "", "${streak.startDate} → ${streak.endDate}", null))
        }
        quickStats.busiestDay?.let { day ->
            driveRecords.add(RecordData("📅", labelBusiestDay, valueBusiestDay ?: "", day.day) { onDayClick(day.day) })
        }
        deepStats?.countriesVisitedCount?.let {
            driveRecords.add(RecordData("🌍", labelCountriesVisited, valueCountriesVisited ?: "", "") { onCountriesVisitedClick() })
        }

        // Category 2: Battery
        val batteryRecords = mutableListOf<RecordData>()
        quickStats.biggestBatteryGainCharge?.let { record ->
            batteryRecords.add(RecordData("🔋", labelBiggestGain, "+${record.percentChange}%", "${record.startLevel}% → ${record.endLevel}%") { onChargeClick(record.recordId) })
        }
        quickStats.biggestBatteryDrainDrive?.let { record ->
            batteryRecords.add(RecordData("📉", labelBiggestDrain, "-${record.percentChange}%", "${record.startLevel}% → ${record.endLevel}%") { onDriveClick(record.recordId) })
        }
        quickStats.biggestCharge?.let { charge ->
            batteryRecords.add(RecordData("⚡", labelBiggestCharge, "%.0f kWh".format(charge.energyAdded), charge.startDate.take(10)) { onChargeClick(charge.chargeId) })
        }
        deepStats?.chargeWithMaxPower?.let { record ->
            batteryRecords.add(RecordData("⚡", labelPeakPower, "${record.powerKw} kW", record.date?.take(10) ?: "") { onChargeClick(record.chargeId) })
        }
        quickStats.mostExpensiveCharge?.let { charge ->
            charge.cost?.let { cost ->
                batteryRecords.add(RecordData("💸", labelMostExpensive, UnitFormatter.formatCost(cost, currencySymbol), charge.startDate.take(10)) { onChargeClick(charge.chargeId) })
            }
        }
        quickStats.mostExpensivePerKwhCharge?.let { charge ->
            charge.cost?.let { cost ->
                if (charge.energyAdded > 0) {
                    batteryRecords.add(RecordData("📈", labelPriciestKwh, UnitFormatter.formatCost(cost / charge.energyAdded, currencySymbol, perKwh = true), charge.startDate.take(10)) { onChargeClick(charge.chargeId) })
                }
            }
        }

        // Category 3: Weather & Altitude
        val weatherRecords = mutableListOf<RecordData>()
        deepStats?.driveWithMaxElevation?.let { record ->
            weatherRecords.add(RecordData("🏔️", labelHighestPoint, UnitFormatter.formatElevation(record.elevationM, units), record.date?.take(10) ?: "") { onDriveClick(record.driveId) })
        }
        deepStats?.driveWithMostClimbing?.let { record ->
            weatherRecords.add(RecordData("⛰️", labelMostClimbing, record.elevationGainM?.let { "+" + UnitFormatter.formatElevation(it, units) } ?: valueNotAvailable, record.date?.take(10) ?: "") { onDriveClick(record.driveId) })
        }
        deepStats?.hottestDrive?.let { record ->
            weatherRecords.add(RecordData("🌡️", labelHottestDrive, UnitFormatter.formatTemperature(record.tempC, units, 1), record.date?.take(10) ?: "") { onDriveClick(record.driveId) })
        }
        deepStats?.coldestDrive?.let { record ->
            weatherRecords.add(RecordData("🧊", labelColdestDrive, UnitFormatter.formatTemperature(record.tempC, units, 1), record.date?.take(10) ?: "") { onDriveClick(record.driveId) })
        }
        deepStats?.hottestCharge?.let { record ->
            weatherRecords.add(RecordData("☀️", labelHottestCharge, UnitFormatter.formatTemperature(record.tempC, units, 1), record.date?.take(10) ?: "") { onChargeClick(record.chargeId) })
        }
        deepStats?.coldestCharge?.let { record ->
            weatherRecords.add(RecordData("❄️", labelColdestCharge, UnitFormatter.formatTemperature(record.tempC, units, 1), record.date?.take(10) ?: "") { onChargeClick(record.chargeId) })
        }

        // Category 4: Miscelaneous
        val miscRecords = mutableListOf<RecordData>()
        quickStats.maxDistanceBetweenCharges?.let { record ->
            miscRecords.add(RecordData("🔋", labelLongestRange, UnitFormatter.formatDistance(record.distance, units), "${record.fromDate.take(10)} → ${record.toDate.take(10)}") { onRangeRecordClick(record) })
        }
        quickStats.longestGapWithoutCharging?.let { gap ->
            miscRecords.add(RecordData("⏰", labelNoCharging, valueGapCharging ?: "", "${gap.fromDate.take(10)} → ${gap.toDate.take(10)}") { onGapRecordClick(gap.gapDays, gap.fromDate, gap.toDate, gapTypeCharging) })
        }
        quickStats.longestGapWithoutDriving?.let { gap ->
            miscRecords.add(RecordData("🅿️", labelNoDriving, valueGapDriving ?: "", "${gap.fromDate.take(10)} → ${gap.toDate.take(10)}") { onGapRecordClick(gap.gapDays, gap.fromDate, gap.toDate, gapTypeDriving) })
        }
        quickStats.mostDistanceDay?.let { day ->
            miscRecords.add(RecordData("🛣️", labelMostDistanceDay, UnitFormatter.formatDistance(day.totalDistance, units), day.day) { onDayClick(day.day) })
        }

        // Build list of all categories with their records
        data class CategoryData(val title: String, val emoji: String, val records: List<RecordData>)
        val allCategories = mutableListOf<CategoryData>()
        if (driveRecords.isNotEmpty()) allCategories.add(CategoryData(categoryDrives, "🚗", driveRecords))
        if (batteryRecords.isNotEmpty()) allCategories.add(CategoryData(categoryBattery, "🔋", batteryRecords))
        if (weatherRecords.isNotEmpty()) allCategories.add(CategoryData(categoryWeather, "🌡️", weatherRecords))
        if (miscRecords.isNotEmpty()) allCategories.add(CategoryData(categoryMisc, "📍", miscRecords))

        // Split categories into pages of max RECORDS_PER_PAGE records each
        val recordPages = mutableListOf<RecordPage>()
        allCategories.forEach { category ->
            val chunks = category.records.chunked(RECORDS_PER_PAGE)
            chunks.forEachIndexed { index, chunk ->
                recordPages.add(RecordPage(
                    categoryTitle = category.title,
                    categoryEmoji = category.emoji,
                    records = chunk,
                    pageIndex = index,
                    totalPagesInCategory = chunks.size
                ))
            }
        }
        recordPages
    }

    // Don't render anything if no categories produced records
    if (pages.isEmpty()) return

    // Category summaries for the page indicator (pages are grouped per category, in order)
    val categorySummaries = remember(pages) {
        pages.groupBy { it.categoryTitle }.map { (title, categoryPages) ->
            Triple(title, categoryPages.first().categoryEmoji, categoryPages.size)
        }
    }

// Find the first page of the selected category, or default to 0
    val initialPage = if (selectedCategory.isNotEmpty()) {
        pages.indexOfFirst { it.categoryTitle == selectedCategory }.takeIf { it >= 0 } ?: 0
    } else {
        0
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { pages.size }
    )

// Update selected category when page changes
    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.currentPage }
            .collect { page ->
                if (page < pages.size) {
                    onCategoryChanged(pages[page].categoryTitle)
                }
            }
    }

    Column {
        // Section header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = CustomIcons.Trophy,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = palette.accent
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.stats_records),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Pager with pages (fixed height for 6 records = 3 rows)
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { pageIndex ->
                    val page = pages[pageIndex]
                    RecordCategoryPage(
                        page = page,
                        palette = palette
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Page indicators - group by category with sub-dots for multi-page categories
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var pageOffset = 0
                    categorySummaries.forEach { (categoryTitle, categoryEmoji, categoryPageCount) ->
                        val isCurrentCategory = pagerState.currentPage >= pageOffset &&
                                pagerState.currentPage < pageOffset + categoryPageCount
                        val currentPageInCategory = if (isCurrentCategory) pagerState.currentPage - pageOffset else -1

                        Row(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isCurrentCategory) palette.accent.copy(alpha = 0.2f)
                                    else Color.Transparent
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = categoryEmoji,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (isCurrentCategory) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = categoryTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.accent,
                                    fontWeight = FontWeight.Bold
                                )
                                // Show page dots for multi-page categories
                                if (categoryPageCount > 1) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    repeat(categoryPageCount) { dotIndex ->
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 2.dp)
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (dotIndex == currentPageInCategory) palette.accent
                                                    else palette.accent.copy(alpha = 0.3f)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                        pageOffset += categoryPageCount
                    }
                }
            }
        }
    }
}

/**
 * Base height for each record card row.
 * Scales with system font size to prevent vertical text clipping.
 */
private const val RECORD_CARD_HEIGHT_BASE = 72

/**
 * A single page showing records for one category.
 * HARD CONSTRAINT: Always renders exactly 3 rows (space for 6 records) to maintain fixed height.
 */
@Composable
private fun RecordCategoryPage(
    page: RecordPage,
    palette: CarColorPalette
) {
    // Pad records to exactly RECORDS_PER_PAGE (6) slots for consistent height
    val paddedRecords = page.records + List(RECORDS_PER_PAGE - page.records.size) { null }
    val rows = paddedRecords.chunked(2) // Always 3 rows of 2

    // Scale card height with system font size to prevent vertical text clipping
    val fontScale = LocalDensity.current.fontScale
    val scaledCardHeight = (RECORD_CARD_HEIGHT_BASE * fontScale).dp

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Records in 2-column grid - always 3 rows for fixed height
        // Note: Category title removed - the swipe indicator at the bottom shows current category
        rows.forEach { rowRecords ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(scaledCardHeight),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowRecords.forEach { record ->
                    if (record != null) {
                        RecordCard(
                            emoji = record.emoji,
                            label = record.label,
                            value = record.value,
                            subtext = record.subtext,
                            palette = palette,
                            onClick = record.onClick,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    } else {
                        // Empty placeholder to maintain grid layout - same size as RecordCard
                        Box(modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordCard(
    emoji: String,
    label: String,
    value: String,
    subtext: String,
    palette: CarColorPalette,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtext.isNotEmpty()) {
                    Text(
                        text = subtext,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.view_details),
                    modifier = Modifier.size(18.dp),
                    tint = palette.onSurfaceVariant
                )
            }
        }
    }
}
