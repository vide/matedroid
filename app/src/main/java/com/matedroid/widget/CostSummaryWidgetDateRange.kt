package com.matedroid.widget

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Date-range helper for the Cost Summary widget. Kept intentionally simple —
 * the widget only needs a half-open [start, end) window covering the last
 * [days] days, aligned to local midnight. Format matches TeslaMate's DateTime
 * format so it can be handed straight to Room queries expecting an ISO string.
 */
object CostSummaryWidgetDateRange {
    private val API_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    fun lastDays(
        today: LocalDate = LocalDate.now(),
        days: Long = CostSummaryWidgetRange.DEFAULT.days.toLong(),
    ): CostSummaryWidgetDateRangeValues {
        require(days > 0) { "days must be positive" }
        val start = today.minusDays(days - 1).atStartOfDay()
        val end = today.plusDays(1).atStartOfDay()
        return CostSummaryWidgetDateRangeValues(
            startDate = start.toApiDateString(),
            endDate = end.toApiDateString(),
        )
    }

    private fun LocalDateTime.toApiDateString(): String = format(API_DATE_FORMATTER)
}

data class CostSummaryWidgetDateRangeValues(
    val startDate: String,
    val endDate: String,
)
