package com.lifepilot.app.domain.habit

import com.lifepilot.app.domain.model.Habit
import com.lifepilot.app.domain.model.HabitLog
import com.lifepilot.app.domain.model.HabitSchedule
import com.lifepilot.app.domain.model.StreakInfo
import com.lifepilot.app.domain.model.StreakUnit
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** Pure habit calculations: schedule, completion, streaks and rates. */
object HabitStats {

    fun isScheduled(habit: Habit, date: LocalDate): Boolean {
        if (date.isBefore(habit.startDate)) return false
        return when (habit.schedule) {
            HabitSchedule.DAILY, HabitSchedule.WEEKLY_TARGET -> true
            HabitSchedule.WEEKDAYS -> date.dayOfWeek in habit.weekdays.ifEmpty { DayOfWeek.entries.toSet() }
        }
    }

    fun isMet(habit: Habit, value: Double?): Boolean = value != null && value >= habit.target.coerceAtLeast(0.0001)

    fun progressFraction(habit: Habit, value: Double?): Float =
        if (value == null || habit.target <= 0) 0f else (value / habit.target).toFloat().coerceIn(0f, 1f)

    fun weekStartOf(date: LocalDate, weekStart: DayOfWeek): LocalDate = date.with(TemporalAdjusters.previousOrSame(weekStart))

    fun metDaysInWeek(habit: Habit, logs: Map<LocalDate, Double>, anyDayInWeek: LocalDate, weekStart: DayOfWeek): Int {
        val start = weekStartOf(anyDayInWeek, weekStart)
        return (0L until 7L).count { isMet(habit, logs[start.plusDays(it)]) }
    }

    fun streak(habit: Habit, logs: List<HabitLog>, today: LocalDate, weekStart: DayOfWeek): StreakInfo {
        val byDate = logs.filter { it.habitId == habit.id }.associate { it.date to it.value }
        return if (habit.schedule == HabitSchedule.WEEKLY_TARGET) weeklyStreak(habit, byDate, today, weekStart)
        else dailyStreak(habit, byDate, today)
    }

    private fun dailyStreak(habit: Habit, logs: Map<LocalDate, Double>, today: LocalDate): StreakInfo {
        // Current: walk back over scheduled days. Today only counts once met, so an unfinished
        // today never "breaks" the streak while the day is still in progress.
        var current = 0
        var d = today
        if (isScheduled(habit, d) && !isMet(habit, logs[d])) d = d.minusDays(1)
        while (!d.isBefore(habit.startDate)) {
            if (isScheduled(habit, d)) {
                if (isMet(habit, logs[d])) current++ else break
            }
            d = d.minusDays(1)
        }
        // Best: single forward pass.
        var best = 0
        var run = 0
        d = habit.startDate
        while (!d.isAfter(today)) {
            if (isScheduled(habit, d)) {
                if (isMet(habit, logs[d])) { run++; best = maxOf(best, run) }
                else if (d != today) run = 0
            }
            d = d.plusDays(1)
        }
        return StreakInfo(current, maxOf(best, current), StreakUnit.DAYS)
    }

    private fun weeklyStreak(habit: Habit, logs: Map<LocalDate, Double>, today: LocalDate, weekStart: DayOfWeek): StreakInfo {
        val target = habit.timesPerWeek.coerceIn(1, 7)
        val firstWeek = weekStartOf(habit.startDate, weekStart)
        var week = weekStartOf(today, weekStart)
        var current = 0
        if (metDaysInWeek(habit, logs, week, weekStart) < target) week = week.minusWeeks(1)
        while (!week.isBefore(firstWeek)) {
            if (metDaysInWeek(habit, logs, week, weekStart) >= target) current++ else break
            week = week.minusWeeks(1)
        }
        var best = 0
        var run = 0
        week = firstWeek
        val thisWeek = weekStartOf(today, weekStart)
        while (!week.isAfter(thisWeek)) {
            if (metDaysInWeek(habit, logs, week, weekStart) >= target) { run++; best = maxOf(best, run) }
            else if (week != thisWeek) run = 0
            week = week.plusWeeks(1)
        }
        return StreakInfo(current, maxOf(best, current), StreakUnit.WEEKS)
    }

    /**
     * Share of scheduled days met in [from, to] (clamped to the habit's start). For weekly-target
     * habits, the share of the weekly target achieved, averaged over the weeks in range.
     * Returns null when there is nothing to measure yet.
     */
    fun completionRate(habit: Habit, logs: List<HabitLog>, from: LocalDate, to: LocalDate, weekStart: DayOfWeek): Double? {
        val byDate = logs.filter { it.habitId == habit.id }.associate { it.date to it.value }
        val start = maxOf(from, habit.startDate)
        if (start.isAfter(to)) return null
        if (habit.schedule == HabitSchedule.WEEKLY_TARGET) {
            val target = habit.timesPerWeek.coerceIn(1, 7)
            var week = weekStartOf(start, weekStart)
            val ratios = mutableListOf<Double>()
            while (!week.isAfter(to)) {
                val met = (0L until 7L).map { week.plusDays(it) }.filter { !it.isBefore(start) && !it.isAfter(to) }
                    .count { isMet(habit, byDate[it]) }
                ratios += (met.toDouble() / target).coerceAtMost(1.0)
                week = week.plusWeeks(1)
            }
            return if (ratios.isEmpty()) null else ratios.average()
        }
        var scheduled = 0
        var met = 0
        var d = start
        while (!d.isAfter(to)) {
            if (isScheduled(habit, d)) {
                scheduled++
                if (isMet(habit, byDate[d])) met++
            }
            d = d.plusDays(1)
        }
        return if (scheduled == 0) null else met.toDouble() / scheduled
    }

    /** Next instant strictly after [now] at the habit's preferred time on a scheduled day. */
    fun nextReminder(habit: Habit, now: Instant, zone: ZoneId): Instant? {
        val minute = habit.preferredMinute ?: return null
        if (!habit.reminderEnabled || habit.archived) return null
        var date = now.atZone(zone).toLocalDate()
        repeat(370) {
            if (isScheduled(habit, date)) {
                val at = ZonedDateTime.of(date, LocalTime.of(minute / 60, minute % 60), zone).toInstant()
                if (at.isAfter(now)) return at
            }
            date = date.plusDays(1)
        }
        return null
    }
}
