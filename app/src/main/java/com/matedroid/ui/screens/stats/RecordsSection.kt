package com.matedroid.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.domain.CostPerKwhBasis
import com.matedroid.domain.model.DeepStats
import com.matedroid.domain.model.MaxDistanceBetweenChargesRecord
import com.matedroid.domain.model.QuickStats
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.domain.model.YearFilter
import com.matedroid.ui.screens.trips.extractCity
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.util.formatMedium
import com.matedroid.util.formatMediumNoYear
import com.matedroid.util.parseIsoDate
import java.time.LocalDate
import java.util.Locale

/** The record categories, in the order their shelves appear. */
internal enum class RecordCategory { DRIVES, BATTERY, WEATHER, MISC }

/**
 * The three records promoted to the big trophy cards at the top of the screen, in the order
 * the cards appear. They are left off their category shelf.
 */
internal enum class HeadlineTrophy { TOP_SPEED, MOST_DISTANCE_DAY, BIGGEST_CHARGE }

/** One record, fully resolved to display strings. */
internal data class RecordRow(
    val category: RecordCategory,
    val dateline: String,
    val title: String,
    val heroValue: String,
    val heroUnit: String,
    /** Optional context after the dateline: the route, the place, the battery from→to. */
    val detail: String?,
    val onClick: (() -> Unit)?,
    val headline: HeadlineTrophy? = null
)

/**
 * Build every record row for the current stats — the single place records are built, for
 * both the headline cards and the shelves. All string resources are resolved up front
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
        fun route(drive: DriveSummary): String? {
            if (drive.startAddress.isBlank() && drive.endAddress.isBlank()) return null
            return "${extractCity(drive.startAddress)} → ${extractCity(drive.endAddress)}"
        }
        fun place(address: String): String? = address.takeIf { it.isNotBlank() }?.let { extractCity(it) }

        val rows = mutableListOf<RecordRow>()
        fun add(
            category: RecordCategory,
            dateline: String,
            title: String,
            hero: Pair<String, String>,
            detail: String? = null,
            headline: HeadlineTrophy? = null,
            onClick: (() -> Unit)?
        ) {
            rows += RecordRow(
                category, dateline, title, hero.first, hero.second,
                detail?.uppercase(locale), onClick, headline
            )
        }

        // ===== Drives =====
        val drives = RecordCategory.DRIVES
        quickStats.longestDrive?.let { drive ->
            add(
                drives, dateline(drive.startDate), labelLongestDrive,
                split(UnitFormatter.formatDistance(drive.distance, units)),
                route(drive)
            ) { onDriveClick(drive.driveId) }
        }
        quickStats.fastestDrive?.let { drive ->
            add(
                drives, dateline(drive.startDate), labelTopSpeed,
                split(UnitFormatter.formatSpeed(drive.speedMax.toDouble(), units)),
                route(drive), HeadlineTrophy.TOP_SPEED
            ) { onDriveClick(drive.driveId) }
        }
        quickStats.mostEfficientDrive?.let { drive ->
            add(
                drives, dateline(drive.startDate), labelMostEfficient,
                split(UnitFormatter.formatEfficiency(drive.efficiency ?: 0.0, units, 0)),
                route(drive)
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
                "${record.startLevel}→${record.endLevel}%"
            ) { onChargeClick(record.recordId) }
        }
        quickStats.biggestBatteryDrainDrive?.let { record ->
            add(
                battery, dateline(record.date), labelBiggestDrain,
                "-${record.percentChange}" to "%",
                "${record.startLevel}→${record.endLevel}%"
            ) { onDriveClick(record.recordId) }
        }
        quickStats.biggestCharge?.let { charge ->
            add(
                battery, dateline(charge.startDate), labelBiggestCharge,
                "%.1f".format(charge.energyAdded) to "kWh",
                place(charge.address), HeadlineTrophy.BIGGEST_CHARGE
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
                    place(charge.address)
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
                        place(charge.address)
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
                // Whole units: the headline card has no room for a decimal
                split(UnitFormatter.formatDistance(day.totalDistance, units, 0)),
                headline = HeadlineTrophy.MOST_DISTANCE_DAY
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

/** The shelf records (headline records excluded), grouped by category in enum order. */
internal fun List<RecordRow>.shelves(): List<Pair<RecordCategory, List<RecordRow>>> {
    val shelfRows = filter { it.headline == null }
    return RecordCategory.entries.mapNotNull { category ->
        shelfRows.filter { it.category == category }.takeIf { it.isNotEmpty() }?.let { category to it }
    }
}

/**
 * Emit the trophy shelves into the stats list: per category with records, a header with
 * the trophy count, then the trophies two per row, each row resting on a thin accent line.
 */
internal fun LazyListScope.trophyShelves(
    shelves: List<Pair<RecordCategory, List<RecordRow>>>,
    palette: CarColorPalette
) {
    shelves.forEach { (category, rows) ->
        item(key = "shelf-${category.name}") {
            ShelfHeader(category = category, count = rows.size, palette = palette)
        }
        rows.chunked(2).forEachIndexed { index, pair ->
            item(key = "shelf-${category.name}-$index") {
                ShelfRow(rows = pair, palette = palette)
            }
        }
    }
}

@Composable
private fun ShelfHeader(category: RecordCategory, count: Int, palette: CarColorPalette) {
    val locale = Locale.getDefault()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = category.icon(),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = palette.accent
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(category.labelRes()),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = pluralStringResource(R.plurals.stats_trophies_count, count, count).uppercase(locale),
            style = capsStyle(10.sp, FontWeight.Bold, 1.2.sp),
            color = palette.onSurfaceVariant
        )
    }
}

@Composable
private fun ShelfRow(rows: List<RecordRow>, palette: CarColorPalette) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rows.forEach { row ->
                TrophyTile(row = row, palette = palette, modifier = Modifier.weight(1f).fillMaxHeight())
            }
            // An odd last trophy keeps half the width
            if (rows.size == 1) Spacer(modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(6.dp))
        // The shelf the row of trophies rests on
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(palette.accent.copy(alpha = 0.20f))
        )
    }
}

@Composable
private fun TrophyTile(row: RecordRow, palette: CarColorPalette, modifier: Modifier = Modifier) {
    val tappable = row.onClick != null
    val locale = Locale.getDefault()
    val background = if (tappable) {
        palette.accent.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .then(if (row.onClick != null) Modifier.clickable(onClick = row.onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = row.title.uppercase(locale),
                style = capsStyle(10.sp, FontWeight.ExtraBold, 1.2.sp),
                color = palette.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = if (tappable) 16.dp else 0.dp)
            )
            Row {
                Text(
                    text = row.heroValue,
                    style = capsStyle(20.sp, FontWeight.ExtraBold, (-0.6).sp),
                    color = if (tappable) palette.accent else palette.onSurface,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline()
                )
                if (row.heroUnit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = row.heroUnit.uppercase(locale),
                        style = capsStyle(10.sp, FontWeight.Bold, 1.2.sp),
                        color = if (tappable) palette.accent else palette.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.alignByBaseline()
                    )
                }
            }
            Text(
                text = listOfNotNull(row.dateline, row.detail).joinToString(" · "),
                style = capsStyle(9.5.sp, FontWeight.Bold, 1.sp),
                color = palette.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (tappable) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.TopEnd),
                tint = palette.onSurfaceVariant
            )
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
