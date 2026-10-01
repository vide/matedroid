package com.matedroid.ui.screens.common

import androidx.annotation.StringRes
import com.matedroid.R

/** Time-bucket granularity for the charges/drives list charts. */
enum class ChartGranularity {
    DAILY, WEEKLY, MONTHLY
}

/** Date-range filter shared by the charges and drives lists. */
enum class DateFilter(@get:StringRes val labelRes: Int, val days: Long?) {
    TODAY(R.string.filter_today, 0),
    LAST_7_DAYS(R.string.filter_last_7_days, 7),
    LAST_30_DAYS(R.string.filter_last_30_days, 30),
    LAST_90_DAYS(R.string.filter_last_90_days, 90),
    LAST_YEAR(R.string.filter_last_year, 365),
    ALL_TIME(R.string.filter_all_time, null),
    CUSTOM(R.string.filter_custom, -1)
}
