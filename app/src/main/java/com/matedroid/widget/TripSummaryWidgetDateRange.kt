package com.matedroid.widget

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object TripSummaryWidgetDateRange {
    private val API_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    fun lastDays(today: LocalDate = LocalDate.now(), days: Long = 30): Pair<String, String> {
        require(days > 0) { "days must be positive" }

        val start = today.minusDays(days - 1).atStartOfDay()
        val end = today.plusDays(1).atStartOfDay()
        return start.toApiDateString() to end.toApiDateString()
    }

    fun currentAndPreviousDays(
        today: LocalDate = LocalDate.now(),
        days: Long = 30,
    ): TripSummaryWidgetDateRanges {
        require(days > 0) { "days must be positive" }

        val currentStart = today.minusDays(days - 1).atStartOfDay()
        val currentEnd = today.plusDays(1).atStartOfDay()
        val previousStart = currentStart.minusDays(days)
        return TripSummaryWidgetDateRanges(
            currentStart = currentStart.toApiDateString(),
            currentEnd = currentEnd.toApiDateString(),
            previousStart = previousStart.toApiDateString(),
            previousEnd = currentStart.toApiDateString(),
        )
    }

    private fun LocalDateTime.toApiDateString(): String = format(API_DATE_FORMATTER)
}

data class TripSummaryWidgetDateRanges(
    val currentStart: String,
    val currentEnd: String,
    val previousStart: String,
    val previousEnd: String,
)
