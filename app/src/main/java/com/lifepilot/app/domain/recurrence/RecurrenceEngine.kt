package com.lifepilot.app.domain.recurrence

import com.lifepilot.app.domain.model.RecurrenceFrequency
import com.lifepilot.app.domain.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Pure date arithmetic for recurring tasks. Works on local calendar dates only, so it is
 * unaffected by timezone or DST changes (the time of day is applied later by the reminder policy).
 */
object RecurrenceEngine {

    /** Upper bound on the search window; generous enough for "every 10 years". */
    private const val MAX_SEARCH_DAYS = 366L * 11

    fun occursOn(rule: RecurrenceRule, date: LocalDate): Boolean {
        if (date.isBefore(rule.startDate)) return false
        if (rule.endDate != null && date.isAfter(rule.endDate)) return false
        val start = rule.startDate
        return when (rule.frequency) {
            RecurrenceFrequency.DAILY ->
                ChronoUnit.DAYS.between(start, date) % rule.interval == 0L

            RecurrenceFrequency.WEEKLY -> {
                // Week buckets are anchored on Monday so "every 2 weeks" is stable regardless of
                // the user's display week-start preference.
                val startWeek = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val dateWeek = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weeks = ChronoUnit.WEEKS.between(startWeek, dateWeek)
                val days = rule.weekdays.ifEmpty { setOf(start.dayOfWeek) }
                weeks % rule.interval == 0L && date.dayOfWeek in days
            }

            RecurrenceFrequency.MONTHLY -> {
                val months = ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(date))
                if (months % rule.interval != 0L) return false
                val length = date.lengthOfMonth()
                // Days beyond the month length (e.g. 31st in April) and LAST_DAY_OF_MONTH clamp to
                // the last day so a "31st of every month" bill is never silently skipped.
                val resolved = rule.monthDays.ifEmpty { setOf(start.dayOfMonth) }
                    .map { if (it == RecurrenceRule.LAST_DAY_OF_MONTH || it > length) length else it }
                date.dayOfMonth in resolved
            }

            RecurrenceFrequency.YEARLY -> {
                val years = date.year - start.year
                if (years % rule.interval != 0) return false
                if (date.month != start.month) return false
                date.dayOfMonth == minOf(start.dayOfMonth, date.lengthOfMonth())
            }
        }
    }

    /** First date on or after [from] that matches the rule, or null if the series has ended. */
    fun nextOnOrAfter(rule: RecurrenceRule, from: LocalDate): LocalDate? {
        var d = if (from.isBefore(rule.startDate)) rule.startDate else from
        val limit = d.plusDays(MAX_SEARCH_DAYS)
        while (!d.isAfter(limit)) {
            if (rule.endDate != null && d.isAfter(rule.endDate)) return null
            if (occursOn(rule, d)) return d
            d = d.plusDays(1)
        }
        return null
    }

    fun nextAfter(rule: RecurrenceRule, date: LocalDate): LocalDate? = nextOnOrAfter(rule, date.plusDays(1))

    /** All matching dates in [from, to] inclusive. */
    fun occurrencesBetween(rule: RecurrenceRule, from: LocalDate, to: LocalDate, limit: Int = 1000): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var cursor = nextOnOrAfter(rule, from)
        while (cursor != null && !cursor.isAfter(to) && out.size < limit) {
            out += cursor
            cursor = nextAfter(rule, cursor)
        }
        return out
    }

    fun describe(rule: RecurrenceRule): String {
        val every = if (rule.interval == 1) "" else "${rule.interval} "
        val base = when (rule.frequency) {
            RecurrenceFrequency.DAILY -> if (rule.interval == 1) "Every day" else "Every ${every}days"
            RecurrenceFrequency.WEEKLY -> {
                val days = rule.weekdays.ifEmpty { setOf(rule.startDate.dayOfWeek) }
                val names = when (days) {
                    DayOfWeek.entries.toSet() -> "every day"
                    setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) -> "weekdays"
                    setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "weekends"
                    else -> days.sorted().joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
                }
                if (rule.interval == 1) "Weekly on $names" else "Every ${every}weeks on $names"
            }
            RecurrenceFrequency.MONTHLY -> {
                val days = rule.monthDays.ifEmpty { setOf(rule.startDate.dayOfMonth) }.sorted()
                    .joinToString(", ") { if (it == RecurrenceRule.LAST_DAY_OF_MONTH) "last day" else ordinal(it) }
                if (rule.interval == 1) "Monthly on the $days" else "Every ${every}months on the $days"
            }
            RecurrenceFrequency.YEARLY -> {
                val m = rule.startDate.month.name.lowercase().replaceFirstChar(Char::uppercase)
                val d = "$m ${rule.startDate.dayOfMonth}"
                if (rule.interval == 1) "Yearly on $d" else "Every ${every}years on $d"
            }
        }
        return if (rule.endDate != null) "$base until ${rule.endDate}" else base
    }

    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }
}
