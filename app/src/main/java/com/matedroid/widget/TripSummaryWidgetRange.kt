package com.matedroid.widget

import androidx.annotation.StringRes
import com.matedroid.R

enum class TripSummaryWidgetRange(
    val days: Int,
    @StringRes val labelRes: Int,
) {
    SevenDays(7, R.string.trip_widget_period_last_7_days),
    ThirtyDays(30, R.string.trip_widget_period_last_30_days),
    NinetyDays(90, R.string.trip_widget_period_last_90_days);

    companion object {
        val DEFAULT = ThirtyDays

        fun fromDays(days: Int): TripSummaryWidgetRange {
            return values().firstOrNull { it.days == days } ?: DEFAULT
        }
    }
}
