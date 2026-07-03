package com.matedroid.ui.screens.common

import android.content.res.Resources
import com.matedroid.util.formatMonthYear
import com.matedroid.util.formatShortNoYear
import com.matedroid.util.formatWeekLabel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Bucket [items] into a contiguous daily/weekly/monthly time series (no gaps), from
 * [startDate] (or the earliest item when null) through today, and hand each bucket to
 * [aggregate] to build the chart point.
 *
 * This is the shared engine behind the charges and drives list charts; the two differ
 * only in the item type, the [dateOf] accessor, and what [aggregate] produces.
 *
 * @param dateOf the item's start timestamp in ISO date-time format (null items are skipped).
 * @param aggregate builds a point from the bucket's display label, epoch-day sort key and items.
 */
fun <T, R> buildTimeSeries(
    items: List<T>,
    granularity: ChartGranularity,
    startDate: LocalDate?,
    resources: Resources,
    dateOf: (T) -> String?,
    aggregate: (label: String, sortKey: Long, bucket: List<T>) -> R,
): List<R> {
    if (items.isEmpty()) return emptyList()

    val formatter = DateTimeFormatter.ISO_DATE_TIME
    val weekFields = WeekFields.of(Locale.getDefault())

    fun localDateOf(item: T): LocalDate? = dateOf(item)?.let {
        try {
            // LocalDateTime to support the full ISO format
            LocalDateTime.parse(it, formatter).toLocalDate()
        } catch (e: Exception) {
            null
        }
    }

    // Group by day up front; used directly for DAILY and for the earliest-date fallback.
    val itemsByDay = items.mapNotNull { item -> localDateOf(item)?.let { it.toEpochDay() to item } }
        .groupBy({ it.first }, { it.second })

    val earliest = { itemsByDay.keys.minOrNull()?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now() }

    return when (granularity) {
        ChartGranularity.DAILY -> {
            val start = startDate ?: earliest()
            val end = LocalDate.now()
            val result = mutableListOf<R>()
            var current = start
            while (!current.isAfter(end)) {
                val key = current.toEpochDay()
                result.add(aggregate(current.formatShortNoYear(Locale.getDefault()), key, itemsByDay[key] ?: emptyList()))
                current = current.plusDays(1)
            }
            result
        }

        ChartGranularity.WEEKLY -> {
            val start = startDate ?: earliest()
            val end = LocalDate.now()

            // First day of the week for the start date; advance if it fell before start.
            var weekStart = start.with(weekFields.dayOfWeek(), 1)
            if (weekStart.isBefore(start)) {
                weekStart = weekStart.plusWeeks(1)
            }

            val itemsByWeek = items.mapNotNull { item ->
                localDateOf(item)?.let { it.with(weekFields.dayOfWeek(), 1).toEpochDay() to item }
            }.groupBy({ it.first }, { it.second })

            val result = mutableListOf<R>()
            var currentWeek = weekStart
            while (!currentWeek.isAfter(end)) {
                val key = currentWeek.toEpochDay()
                val weekOfYear = currentWeek.get(weekFields.weekOfYear())
                result.add(aggregate(formatWeekLabel(resources, weekOfYear), key, itemsByWeek[key] ?: emptyList()))
                currentWeek = currentWeek.plusWeeks(1)
            }
            result
        }

        ChartGranularity.MONTHLY -> {
            val start = startDate ?: earliest()
            val monthEnd = YearMonth.from(LocalDate.now())

            val itemsByMonth = items.mapNotNull { item ->
                localDateOf(item)?.let { YearMonth.from(it).atDay(1).toEpochDay() to item }
            }.groupBy({ it.first }, { it.second })

            val result = mutableListOf<R>()
            var currentMonth = YearMonth.from(start)
            while (!currentMonth.isAfter(monthEnd)) {
                val firstDay = currentMonth.atDay(1)
                val key = firstDay.toEpochDay()
                result.add(aggregate(firstDay.formatMonthYear(Locale.getDefault()), key, itemsByMonth[key] ?: emptyList()))
                currentMonth = currentMonth.plusMonths(1)
            }
            result
        }
    }
}
