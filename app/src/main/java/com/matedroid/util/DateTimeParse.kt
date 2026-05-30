package com.matedroid.util

import android.content.res.Resources
import com.matedroid.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Parse an ISO-8601 datetime string into a [LocalDateTime].
 *
 * Accepts datetime strings with or without a timezone offset, and with or
 * without a trailing "Z" suffix. TeslaMate emits both forms (RFC 3339
 * with offset, or ISO with a trailing Z).
 *
 * Examples:
 *   "2026-05-10T15:39:00Z"      → 2026-05-10T15:39
 *   "2026-05-10T15:39:00+02:00" → 2026-05-10T15:39
 *   "" or null                   → null
 */
fun parseIsoDateTime(dateStr: String?): LocalDateTime? {
    if (dateStr.isNullOrBlank()) return null
    return try {
        try {
            OffsetDateTime.parse(dateStr).toLocalDateTime()
        } catch (_: DateTimeParseException) {
            LocalDateTime.parse(dateStr.replace("Z", ""))
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Parse an ISO-8601 datetime string to a [LocalDate], discarding the time
 * component.
 *
 * Examples:
 *   "2026-05-10T15:39:00Z" → 2026-05-10
 *   "" or null              → null
 */
fun parseIsoDate(dateStr: String?): LocalDate? =
    parseIsoDateTime(dateStr)?.toLocalDate()

/**
 * Format a [LocalDate] as locale-aware short date without the year.
 *
 * Formats with the locale's [FormatStyle.SHORT] numeric pattern, then
 * removes the year segment (2–4 digits) and its adjacent separator.
 *
 * Examples for 2026-05-10:
 *   en-US: "5/10/26"   → "5/10"
 *   zh-CN: "2026/5/10" → "5/10"
 *   it-IT: "10/5/26"   → "10/5"
 *   es-ES: "10/5/26"   → "10/5"
 *   ca-ES: "10/5/26"   → "10/5"
 */
fun LocalDate.formatShortNoYear(locale: Locale = Locale.getDefault()): String {
    val full = this.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale))
    val sep = full.firstOrNull { !it.isDigit() } ?: return full
    val parts = full.split(sep)
    val year4 = this.year.toString()
    val year2 = (this.year % 100).toString()
    // Year is always at the first or last position in SHORT numeric formats; remove it.
    val rest = when {
        parts.firstOrNull() == year4 || parts.firstOrNull() == year2 -> parts.drop(1)
        parts.lastOrNull() == year4 || parts.lastOrNull() == year2 -> parts.dropLast(1)
        else -> return full
    }
    return rest.joinToString(sep.toString())
}

/**
 * Format a [LocalDateTime] as locale-aware short time.
 *
 * When [is24Hour] is `null` (default), uses the locale's built-in
 * 12h/24h convention. Pass explicit `true`/`false` to override the locale
 * default — useful for respecting Android's system-level 12/24 hour
 * setting via [android.text.format.DateFormat.is24HourFormat].
 *
 * Examples for 15:39:
 *   en-US: "3:39 PM"
 *   zh-CN: "15:39"
 *   it-IT: "15:39"
 *   es-ES: "15:39"
 *   ca-ES: "15:39"
 */
fun LocalDateTime.formatTime(
    locale: Locale = Locale.getDefault(),
    is24Hour: Boolean? = null
): String {
    val fmt = when (is24Hour) {
        null -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        true -> DateTimeFormatter.ofPattern("HH:mm", locale)
        false -> DateTimeFormatter.ofPattern("hh:mm a", locale)
    }
    return this.format(fmt)
}

/**
 * Format a [LocalDate] as locale-aware medium date.
 *
 * Uses the locale's [FormatStyle.MEDIUM] format.
 *
 * Examples for 2026-05-10:
 *   en-US: "May 10, 2026"
 *   zh-CN: "2026年5月10日"
 *   it-IT: "10 mag 2026"
 *   es-ES: "10 may 2026"
 *   ca-ES: "10 de maig de 2026"
 */
fun LocalDate.formatMedium(locale: Locale = Locale.getDefault()): String =
    this.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

/**
 * Format a [LocalDate] as medium date without the year.
 *
 * Formats with [formatMedium], then strips the year digits and
 * any adjacent year-specific text (prefix or suffix).
 *
 * Examples for 2026-05-10:
 *   en-US: "May 10, 2026"          → "May 10"
 *   zh-CN: "2026年5月10日"          → "5月10日"
 *   it-IT: "10 mag 2026"           → "10 mag"
 *   es-ES: "10 may 2026"           → "10 may"
 *   ca-ES: "10 de maig de 2026"    → "10 de maig"
 */
fun LocalDate.formatMediumNoYear(locale: Locale = Locale.getDefault()): String {
    val full = formatMedium(locale)
    // Strip year prefix (e.g. "2026年") and suffix (e.g. ", 2026", " de 2026")
    return full
        .replace(Regex("""^\d{4}\p{L}*\s*"""), "")
        .replace(Regex("""\s*,?\s*\d{4}\s*\p{L}*\s*$"""), "")
        .trim()
}

/**
 * Format a [LocalDateTime] as locale-aware editorial dateline.
 *
 * Produces a "DOW · date · time" string uppercased for the locale.
 * Time respects 12h/24h via [formatTime]; pass [is24Hour] to override the
 * locale default with the Android system setting.
 *
 * Examples for 2026-05-10T15:39 (Sunday):
 *   en-US: "SUN · MAY 10 · 3:39 PM"
 *   zh-CN: "周日 · 5月10日 · 15:39"
 *   it-IT: "DOM · 10 MAG · 15:39"
 *   es-ES: "DOM · 10 MAY · 15:39"
 *   ca-ES: "DG · 10 DE MAIG · 15:39"
 */
fun LocalDateTime.formatEditorial(
    locale: Locale = Locale.getDefault(),
    is24Hour: Boolean? = null
): String {
    val dow = this.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale)
    val date = this.toLocalDate().formatMediumNoYear(locale)
    val time = this.formatTime(locale, is24Hour)
    return "$dow · $date · $time".uppercase(locale)
}

/**
 * Format a [LocalDate] as a compact chart label.
 *
 * Uses "MMM yy" for most locales. For Chinese (zh), uses "M月 d日"
 * (month+day) since an abbreviated year number is ambiguous in Chinese
 * without the year-context prefix.
 *
 * Examples for 2026-05-10:
 *   en-US: "May 26"
 *   zh-CN: "5月 10日"
 *   it-IT: "mag 26"
 *   es-ES: "may 26"
 *   ca-ES: "maig 26"
 */
fun LocalDate.formatMonthYear(locale: Locale = Locale.getDefault()): String =
    if (locale.language == "zh")
        this.format(DateTimeFormatter.ofPattern("M月 d日", locale))
    else
        this.format(DateTimeFormatter.ofPattern("MMM yy", locale))

/**
 * Format a week-of-year number as a locale-aware chart label.
 *
 * The label text comes from the `chart_week_label` string resource, so it is
 * translated per locale (e.g. "W23" in English, "第23周" in Chinese).
 */
fun formatWeekLabel(resources: Resources, weekOfYear: Int): String =
    resources.getString(R.string.chart_week_label, weekOfYear)

/**
 * Format an integer minute count as a human-readable, locale-aware duration.
 *
 * Scale adapts to the magnitude: minutes → hours+min → days+hours →
 * weeks+days → months+weeks. The unit abbreviations and their ordering come
 * from the `duration_*` string resources, so they are fully translated
 * (e.g. 648 min → "10h 48m" in English, "10小时48分钟" in Chinese).
 */
fun formatDuration(resources: Resources, minutes: Int): String {
    val total = minutes.coerceAtLeast(0)
    val hours = total / 60
    val mins = total % 60
    val days = hours / 24
    val remHours = hours - days * 24
    val weeks = days / 7
    val remDays = days - weeks * 7
    val months = weeks / 4
    val remWeeks = weeks - months * 4

    return when {
        months >= 1 -> if (remWeeks > 0) resources.getString(R.string.duration_months_weeks, months, remWeeks)
            else resources.getString(R.string.duration_months, months)
        weeks >= 1 -> if (remDays > 0) resources.getString(R.string.duration_weeks_days, weeks, remDays)
            else resources.getString(R.string.duration_weeks, weeks)
        days >= 1 -> if (remHours > 0) resources.getString(R.string.duration_days_hours, days, remHours)
            else resources.getString(R.string.duration_days, days)
        hours >= 1 -> if (mins > 0) resources.getString(R.string.duration_hours_minutes, hours, mins)
            else resources.getString(R.string.duration_hours, hours)
        else -> resources.getString(R.string.duration_minutes, total)
    }
}

/**
 * Format an integer minute count as a compact "H:MM" string.
 *
 * Universally understood across locales; suited for chart tooltips and
 * detail-stat cards where space is limited.
 *
 * Examples for 648 min:
 *   All locales: "10:48"
 */
fun formatDurationCompact(minutes: Int): String {
    val h = (minutes.coerceAtLeast(0)) / 60
    val m = minutes % 60
    return "%d:%02d".format(h, m)
}
