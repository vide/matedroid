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

    private fun LocalDateTime.toApiDateString(): String = format(API_DATE_FORMATTER)
}
