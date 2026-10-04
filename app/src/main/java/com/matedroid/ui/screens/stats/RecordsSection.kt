package com.matedroid.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.domain.CostPerKwhBasis
import com.matedroid.domain.model.DeepStats
import com.matedroid.domain.model.MaxDistanceBetweenChargesRecord
import com.matedroid.domain.model.QuickStats
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.domain.model.YearFilter
import com.matedroid.ui.components.EditorialListItem
import com.matedroid.ui.components.EditorialPill
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.ui.screens.trips.extractCity
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.util.formatDurationCompact
import com.matedroid.util.formatMedium
import com.matedroid.util.formatMediumNoYear
import com.matedroid.util.parseIsoDate
import java.time.LocalDate
import java.util.Locale

/** The record categories, in the order their chips appear. */
internal enum class RecordCategory { DRIVES, BATTERY, WEATHER, MISC }

/** A supporting pill under a record's title; [highlighted] draws it accent-tinted. */
internal data class RecordPill(val text: String, val highlighted: Boolean = false)

/** One record, fully resolved to display strings, rendered as an [EditorialListItem]. */
internal data class RecordRow(
    val category: RecordCategory,
    val dateline: String,
    val title: String,
    val heroValue: String,
    val heroUnit: String,
    val pills: List<RecordPill>,
    val onClick: (() -> Unit)?
)

/**
 * Build every record row for the current stats. All string resources are resolved up front
 * because the row list itself is a pure derivation computed inside [remember].
 */
@Composable
internal fun rememberRecordRows(
    quickStats: QuickStats,
    deepStats: DeepStats?,
    units: Units?,
    currencySymbol: String,
    yearFilter: YearFilter,
    onDriveClick: (Int) -> Unit,
    onChargeClick: (Int) -> Unit,
    onDayClick: (String) -> Unit,
    onCountriesVisitedClick: () -> Unit,
    onRangeRecordClick: (MaxDistanceBetweenChargesRecord) -> Unit,
    onGapRecordClick: (Double, String, String, String) -> Unit // gapDays, fromDate, toDate, title
): List<RecordRow> {
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
    val labelPriciestKwh = stringResource(
        if (CostPerKwhBasis.current == CostPerKwhBasis.ENERGY_USED) {
            R.string.record_priciest_kwh_used
        } else {
            R.string.record_priciest_kwh_added
        }
    )
    val labelHighestPoint = stringResource(R.string.record_highest_point)
    val labelMostClimbing = stringResource(R.string.record_most_climbing)
    val labelHottestDrive = stringResource(R.string.record_hottest_drive)
    val labelColdestDrive = stringResource(R.string.record_coldest_drive)
    val labelHottestCharge = stringResource(R.string.record_hottest_charge)
    val labelColdestCharge = stringResource(R.string.record_coldest_charge)
    val labelHottestCabin = stringResource(R.string.record_hottest_cabin)
    val labelColdestCabin = stringResource(R.string.record_coldest_cabin)
    val labelLongestRange = stringResource(R.string.record_longest_range)
    val labelNoCharging = stringResource(R.string.record_longest_no_charging)
    val labelNoDriving = stringResource(R.string.record_longest_no_driving)
    val labelMostDistanceDay = stringResource(R.string.record_most_distance_day)
    val gapTypeCharging = stringResource(R.string.gap_type_charging)
    val gapTypeDriving = stringResource(R.string.gap_type_driving)
    val valueNotAvailable = stringResource(R.string.value_not_available)
    val unitDays = stringResource(R.string.record_unit_days)
    val unitDrives = stringResource(R.string.record_unit_drives)
    val unitCountries = deepStats?.countriesVisitedCount?.let { pluralStringResource(R.plurals.record_unit_countries, it) }
    // Records that have no date of their own (countries, cabin temperatures) are datelined
    // with the period they cover.
    val allTimeLabel = stringResource(R.string.filter_all_time)

    return remember(quickStats, deepStats, units, currencySymbol, yearFilter) {
        val locale = Locale.getDefault()
        val periodLabel = when (yearFilter) {
            is YearFilter.AllTime -> allTimeLabel.uppercase(locale)
            is YearFilter.Year -> yearFilter.year.toString()
        }
        fun dateline(date: String?): String =
            toLocalDate(date)?.formatMedium(locale)?.uppercase(locale) ?: periodLabel
        fun rangeDateline(from: String, to: String): String {
            val start = toLocalDate(from)
            val end = toLocalDate(to) ?: return dateline(from)
            if (start == null) return dateline(to)
            val startText = if (start.year == end.year) start.formatMediumNoYear(locale) else start.formatMedium(locale)
            return "$startText – ${end.formatMedium(locale)}".uppercase(locale)
        }
        fun split(formatted: String) = UnitFormatter.splitValueUnit(formatted)
        fun route(drive: DriveSummary): RecordPill? {
            if (drive.startAddress.isBlank() && drive.endAddress.isBlank()) return null
            return RecordPill("${extractCity(drive.startAddress)} → ${extractCity(drive.endAddress)}")
        }
        fun place(address: String): RecordPill? =
            address.takeIf { it.isNotBlank() }?.let { RecordPill(extractCity(it)) }

        val rows = mutableListOf<RecordRow>()
        fun add(
            category: RecordCategory,
            dateline: String,
            title: String,
            hero: Pair<String, String>,
            pills: List<RecordPill?> = emptyList(),
            onClick: (() -> Unit)?
        ) {
            rows += RecordRow(category, dateline, title, hero.first, hero.second, pills.filterNotNull(), onClick)
        }

        // ===== Drives =====
        val drives = RecordCategory.DRIVES
        quickStats.longestDrive?.let { drive ->
            add(
                drives, dateline(drive.startDate), labelLongestDrive,
                split(UnitFormatter.formatDistance(drive.distance, units)),
                listOf(
                    route(drive),
                    RecordPill(formatDurationCompact(drive.durationMin)),
                    drive.efficiency?.let { RecordPill(UnitFormatter.formatEfficiency(it, units, 0)) }
                )
            ) { onDriveClick(drive.driveId) }
        }
        quickStats.fastestDrive?.let { drive ->
            add(
                drives, dateline(drive.startDate), labelTopSpeed,
                split(UnitFormatter.formatSpeed(drive.speedMax.toDouble(), units)),
                listOf(
                    route(drive),
                    RecordPill(formatDurationCompact(drive.durationMin)),
                    RecordPill(UnitFormatter.formatDistance(drive.distance, units))
                )
            ) { onDriveClick(drive.driveId) }
        }
        quickStats.mostEfficientDrive?.let { drive ->
            add(
                drives, dateline(drive.startDate), labelMostEfficient,
                split(UnitFormatter.formatEfficiency(drive.efficiency ?: 0.0, units, 0)),
                listOf(
                    route(drive),
                    RecordPill(formatDurationCompact(drive.durationMin)),
                    RecordPill(UnitFormatter.formatDistance(drive.distance, units))
                )
            ) { onDriveClick(drive.driveId) }
        }
        quickStats.longestDrivingStreak?.let { streak ->
            add(
                drives, rangeDateline(streak.startDate, streak.endDate), labelLongestStreak,
                streak.streakDays.toString() to unitDays,
                onClick = null
            )
        }
        quickStats.busiestDay?.let { day ->
            add(
                drives, dateline(day.day), labelBusiestDay,
                day.count.toString() to unitDrives
            ) { onDayClick(day.day) }
        }
        deepStats?.countriesVisitedCount?.let { count ->
            add(
                drives, periodLabel, labelCountriesVisited,
                count.toString() to (unitCountries ?: "")
            ) { onCountriesVisitedClick() }
        }

        // ===== Battery =====
        val battery = RecordCategory.BATTERY
        quickStats.biggestBatteryGainCharge?.let { record ->
            add(
                battery, dateline(record.date), labelBiggestGain,
                "+${record.percentChange}" to "%",
                listOf(RecordPill("${record.startLevel}→${record.endLevel}%", highlighted = true))
            ) { onChargeClick(record.recordId) }
        }
        quickStats.biggestBatteryDrainDrive?.let { record ->
            add(
                battery, dateline(record.date), labelBiggestDrain,
                "-${record.percentChange}" to "%",
                listOf(RecordPill("${record.startLevel}→${record.endLevel}%", highlighted = true))
            ) { onDriveClick(record.recordId) }
        }
        quickStats.biggestCharge?.let { charge ->
            add(
                battery, dateline(charge.startDate), labelBiggestCharge,
                "%.1f".format(charge.energyAdded) to "kWh",
                listOf(place(charge.address), RecordPill(formatDurationCompact(charge.durationMin)))
            ) { onChargeClick(charge.chargeId) }
        }
        deepStats?.chargeWithMaxPower?.let { record ->
            add(
                battery, dateline(record.date), labelPeakPower,
                record.powerKw.toString() to "kW"
            ) { onChargeClick(record.chargeId) }
        }
        quickStats.mostExpensiveCharge?.let { charge ->
            charge.cost?.let { cost ->
                add(
                    battery, dateline(charge.startDate), labelMostExpensive,
                    split(UnitFormatter.formatCost(cost, currencySymbol)),
                    listOf(place(charge.address), RecordPill("+%.1f kWh".format(charge.energyAdded)))
                ) { onChargeClick(charge.chargeId) }
            }
        }
        quickStats.mostExpensivePerKwhCharge?.let { charge ->
            charge.cost?.let { cost ->
                val energyBasis = CostPerKwhBasis.energyFor(charge.energyAdded, charge.energyUsed)
                if (energyBasis > 0) {
                    val price = split(UnitFormatter.formatCost(cost / energyBasis, currencySymbol, perKwh = true))
                    add(
                        battery, dateline(charge.startDate), labelPriciestKwh,
                        price.first to "${price.second}/kWh",
                        listOf(place(charge.address))
                    ) { onChargeClick(charge.chargeId) }
                }
            }
        }

        // ===== Weather & Altitude =====
        val weather = RecordCategory.WEATHER
        deepStats?.driveWithMaxElevation?.let { record ->
            add(
                weather, dateline(record.date), labelHighestPoint,
                split(UnitFormatter.formatElevation(record.elevationM, units))
            ) { onDriveClick(record.driveId) }
        }
        deepStats?.driveWithMostClimbing?.let { record ->
            val hero = record.elevationGainM
                ?.let { split(UnitFormatter.formatElevation(it, units)).let { (v, u) -> "+$v" to u } }
                ?: (valueNotAvailable to "")
            add(weather, dateline(record.date), labelMostClimbing, hero) { onDriveClick(record.driveId) }
        }
        deepStats?.hottestDrive?.let { record ->
            add(
                weather, dateline(record.date), labelHottestDrive,
                split(UnitFormatter.formatTemperature(record.tempC, units, 1))
            ) { onDriveClick(record.driveId) }
        }
        deepStats?.coldestDrive?.let { record ->
            add(
                weather, dateline(record.date), labelColdestDrive,
                split(UnitFormatter.formatTemperature(record.tempC, units, 1))
            ) { onDriveClick(record.driveId) }
        }
        deepStats?.hottestCharge?.let { record ->
            add(
                weather, dateline(record.date), labelHottestCharge,
                split(UnitFormatter.formatTemperature(record.tempC, units, 1))
            ) { onChargeClick(record.chargeId) }
        }
        deepStats?.coldestCharge?.let { record ->
            add(
                weather, dateline(record.date), labelColdestCharge,
                split(UnitFormatter.formatTemperature(record.tempC, units, 1))
            ) { onChargeClick(record.chargeId) }
        }
        deepStats?.maxCabinTempC?.let { tempC ->
            add(
                weather, periodLabel, labelHottestCabin,
                split(UnitFormatter.formatTemperature(tempC, units, 1)),
                onClick = null
            )
        }
        deepStats?.minCabinTempC?.let { tempC ->
            add(
                weather, periodLabel, labelColdestCabin,
                split(UnitFormatter.formatTemperature(tempC, units, 1)),
                onClick = null
            )
        }

        // ===== Misc =====
        val misc = RecordCategory.MISC
        quickStats.maxDistanceBetweenCharges?.let { record ->
            add(
                misc, rangeDateline(record.fromDate, record.toDate), labelLongestRange,
                split(UnitFormatter.formatDistance(record.distance, units))
            ) { onRangeRecordClick(record) }
        }
        quickStats.longestGapWithoutCharging?.let { gap ->
            add(
                misc, rangeDateline(gap.fromDate, gap.toDate), labelNoCharging,
                "%.1f".format(gap.gapDays) to unitDays
            ) { onGapRecordClick(gap.gapDays, gap.fromDate, gap.toDate, gapTypeCharging) }
        }
        quickStats.longestGapWithoutDriving?.let { gap ->
            add(
                misc, rangeDateline(gap.fromDate, gap.toDate), labelNoDriving,
                "%.1f".format(gap.gapDays) to unitDays
            ) { onGapRecordClick(gap.gapDays, gap.fromDate, gap.toDate, gapTypeDriving) }
        }
        quickStats.mostDistanceDay?.let { day ->
            add(
                misc, dateline(day.day), labelMostDistanceDay,
                split(UnitFormatter.formatDistance(day.totalDistance, units))
            ) { onDayClick(day.day) }
        }

        rows
    }
}

/**
 * Parse a record date, which is either a full ISO datetime or a bare "yyyy-MM-dd" day
 * (busiest day, streaks and the like come from SQL `date()` aggregates).
 */
private fun toLocalDate(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return parseIsoDate(value)
        ?: runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
}

/** The categories that have at least one record, in [RecordCategory] order. */
internal fun List<RecordRow>.categories(): List<RecordCategory> {
    val present = mapTo(HashSet()) { it.category }
    return RecordCategory.entries.filter { it in present }
}

/**
 * Emit the Records section into the stats list: the section header, the category chips and
 * one editorial row per record of [selectedCategory]. Nothing is emitted without records.
 */
internal fun LazyListScope.recordsSection(
    rows: List<RecordRow>,
    categories: List<RecordCategory>,
    selectedCategory: RecordCategory,
    palette: CarColorPalette,
    onCategorySelected: (RecordCategory) -> Unit
) {
    if (rows.isEmpty()) return

    item(key = "records-header") {
        Column {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = CustomIcons.Trophy,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = palette.accent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.stats_records),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    item(key = "records-chips") {
        RecordCategoryChips(
            categories = categories,
            selected = selectedCategory,
            palette = palette,
            onSelected = onCategorySelected
        )
    }

    items(rows.filter { it.category == selectedCategory }) { row ->
        RecordItem(row = row, palette = palette)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordCategoryChips(
    categories: List<RecordCategory>,
    selected: RecordCategory,
    palette: CarColorPalette,
    onSelected: (RecordCategory) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categories) { category ->
            FilterChip(
                selected = category == selected,
                onClick = { onSelected(category) },
                label = { Text(stringResource(category.labelRes())) },
                leadingIcon = {
                    Icon(
                        imageVector = category.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.surface,
                    selectedLabelColor = palette.onSurface,
                    selectedLeadingIconColor = palette.accent
                )
            )
        }
    }
}

@Composable
private fun RecordItem(row: RecordRow, palette: CarColorPalette) {
    val accent = palette.accent
    EditorialListItem(
        accent = accent,
        dateline = row.dateline,
        title = row.title,
        heroValue = row.heroValue,
        heroUnit = row.heroUnit,
        onClick = row.onClick
    ) {
        row.pills.forEach { pill ->
            if (pill.highlighted) {
                EditorialPill(
                    text = pill.text,
                    background = accent.copy(alpha = 0.12f),
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
            } else {
                EditorialPill(pill.text)
            }
        }
    }
}

private fun RecordCategory.labelRes(): Int = when (this) {
    RecordCategory.DRIVES -> R.string.stats_category_drives
    RecordCategory.BATTERY -> R.string.stats_category_battery
    RecordCategory.WEATHER -> R.string.stats_category_weather
    RecordCategory.MISC -> R.string.stats_category_misc
}

private fun RecordCategory.icon(): ImageVector = when (this) {
    RecordCategory.DRIVES -> Icons.Default.DirectionsCar
    RecordCategory.BATTERY -> Icons.Default.BatteryChargingFull
    RecordCategory.WEATHER -> Icons.Default.Thermostat
    RecordCategory.MISC -> Icons.Default.Place
}
