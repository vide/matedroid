package com.matedroid.util

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
 * Examples for week 23:
 *   en-US: "W23"
 *   zh-CN: "第23周"
 *   it-IT: "W23"
 *   es-ES: "W23"
 *   ca-ES: "W23"
 */
fun formatWeekLabel(weekOfYear: Int, locale: Locale = Locale.getDefault()): String =
    when (locale.language) {
        "zh" -> "第${weekOfYear}周"
        else -> "W$weekOfYear"
    }

/**
 * Format an integer minute count as a human-readable, locale-aware duration.
 *
 * Scale adapts to the magnitude: minutes → hours+min → days+hours →
 * weeks+days → months+weeks.
 *
 * Examples for 648 min (10h 48m = 0d 10h 48m):
 *   en-US: "10h 48m"
 *   zh-CN: "10小时48分钟"
 *   it-IT: "10o 48min"
 *   es-ES: "10h 48min"
 *   ca-ES: "10h 48min"
 *
 * Examples for 1560 min (26h = 1d 2h):
 *   en-US: "1d 2h"
 *   zh-CN: "1天2小时"
 *   it-IT: "1g 2o"
 *   es-ES: "1d 2h"
 *   ca-ES: "1d 2h"
 */
fun formatDuration(minutes: Int, locale: Locale = Locale.getDefault()): String {
    val total = minutes.coerceAtLeast(0)
    val hours = total / 60
    val mins = total % 60
    val days = hours / 24
    val remHours = hours - days * 24
    val weeks = days / 7
    val remDays = days - weeks * 7
    val months = weeks / 4
    val remWeeks = weeks - months * 4

    return when (locale.language) {
        "zh" -> when {
            months >= 1 -> if (remWeeks > 0) "${months}个月${remWeeks}周" else "${months}个月"
            weeks >= 1 -> if (remDays > 0) "${weeks}周${remDays}天" else "${weeks}周"
            days >= 1 -> if (remHours > 0) "${days}天${remHours}小时" else "${days}天"
            hours >= 1 -> if (mins > 0) "${hours}小时${mins}分钟" else "${hours}小时"
            else -> "${total}分钟"
        }
        "it" -> when {
            months >= 1 -> if (remWeeks > 0) "${months}mesi ${remWeeks}sett" else "${months}mesi"
            weeks >= 1 -> if (remDays > 0) "${weeks}sett ${remDays}g" else "${weeks}sett"
            days >= 1 -> if (remHours > 0) "${days}g ${remHours}o" else "${days}g"
            hours >= 1 -> if (mins > 0) "${hours}o ${mins}min" else "${hours}o"
            else -> "${total}min"
        }
        else -> when {
            months >= 1 -> if (remWeeks > 0) "${months}mo ${remWeeks}w" else "${months}mo"
            weeks >= 1 -> if (remDays > 0) "${weeks}w ${remDays}d" else "${weeks}w"
            days >= 1 -> if (remHours > 0) "${days}d ${remHours}h" else "${days}d"
            hours >= 1 -> if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
            else -> "${total}m"
        }
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
