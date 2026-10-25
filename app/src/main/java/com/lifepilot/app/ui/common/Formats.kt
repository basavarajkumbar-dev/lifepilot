package com.lifepilot.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.lifepilot.app.domain.model.AppSettings
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Date/time formatting that respects the user's 12/24h and date-format preferences. */
class Formats(private val settings: AppSettings, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val timeFmt = DateTimeFormatter.ofPattern(if (settings.use24HourTime) "HH:mm" else "h:mm a", Locale.getDefault())
    private val dateFmt = DateTimeFormatter.ofPattern(settings.dateFormat.pattern, Locale.getDefault())

    fun time(minute: Int): String = LocalTime.of(minute / 60, minute % 60).format(timeFmt)
    fun time(instant: Instant): String = instant.atZone(zone).toLocalTime().format(timeFmt)
    fun date(date: LocalDate): String = date.format(dateFmt)
    fun weekday(date: LocalDate, short: Boolean = false): String =
        date.dayOfWeek.getDisplayName(if (short) TextStyle.SHORT else TextStyle.FULL, Locale.getDefault())

    /** "Today", "Tomorrow", "Yesterday", "Mon", or the formatted date. */
    fun relativeDate(date: LocalDate, today: LocalDate = LocalDate.now(zone)): String {
        val diff = ChronoUnit.DAYS.between(today, date)
        return when {
            diff == 0L -> "Today"
            diff == 1L -> "Tomorrow"
            diff == -1L -> "Yesterday"
            diff in 2..6 -> weekday(date)
            else -> date(date)
        }
    }

    fun dateTime(instant: Instant, today: LocalDate = LocalDate.now(zone)): String {
        val local = instant.atZone(zone)
        return "${relativeDate(local.toLocalDate(), today)}, ${local.toLocalTime().format(timeFmt)}"
    }

    fun due(date: LocalDate, minute: Int?, today: LocalDate = LocalDate.now(zone)): String =
        if (minute == null) relativeDate(date, today) else "${relativeDate(date, today)}, ${time(minute)}"

    companion object {
        fun duration(minutes: Int): String = when {
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0 -> "${minutes / 60} h"
            else -> "${minutes / 60} h ${minutes % 60} min"
        }

        fun interval(minutes: Int): String = if (minutes < 60 || minutes % 60 != 0) "$minutes min" else if (minutes == 60) "1 hour" else "${minutes / 60} hours"

        fun number(d: Double): String = if (d == Math.floor(d)) d.toLong().toString() else "%.1f".format(d)
    }
}

val LocalFormats = staticCompositionLocalOf { Formats(AppSettings()) }

val formats: Formats @Composable get() = LocalFormats.current
