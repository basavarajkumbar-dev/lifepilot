package com.lifepilot.app.domain.analytics

import com.lifepilot.app.domain.habit.HabitStats
import com.lifepilot.app.domain.model.Category
import com.lifepilot.app.domain.model.Habit
import com.lifepilot.app.domain.model.HabitLog
import com.lifepilot.app.domain.model.OccurrenceItem
import com.lifepilot.app.domain.model.OccurrenceStatus
import com.lifepilot.app.domain.reminder.ReminderPolicy
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayCount(val date: LocalDate, val created: Int, val completed: Int)

data class CategoryStat(val categoryId: Long?, val name: String, val colorIndex: Int, val total: Int, val completed: Int) {
    val rate: Double get() = if (total == 0) 0.0 else completed.toDouble() / total
}

data class WeekdayStat(val day: DayOfWeek, val completed: Int)

data class HabitConsistency(val habitId: Long, val name: String, val rate: Double)

data class Insights(
    val todayTotal: Int,
    val todayCompleted: Int,
    val weekTotal: Int,
    val weekCompleted: Int,
    val overdueCount: Int,
    val createdVsCompleted: List<DayCount>,
    val completionTrend: List<DayCount>,
    val categories: List<CategoryStat>,
    val weekdays: List<WeekdayStat>,
    val habitConsistency: List<HabitConsistency>,
    val averageHabitConsistency: Double?,
    val missedLast30: Int,
    val rescheduledLast30: Int,
    val snoozesLast30: Int,
    val totalCompletedAllTime: Int,
) {
    val todayRate: Double? get() = if (todayTotal == 0) null else todayCompleted.toDouble() / todayTotal
    val weekRate: Double? get() = if (weekTotal == 0) null else weekCompleted.toDouble() / weekTotal
    val mostProductiveDay: DayOfWeek? get() = weekdays.filter { it.completed > 0 }.maxByOrNull { it.completed }?.day
    val isEmpty: Boolean get() = totalCompletedAllTime == 0 && todayTotal == 0 && weekTotal == 0 && createdVsCompleted.all { it.created == 0 }
}

/**
 * Computes every statistic from real persisted records. Skipped occurrences are excluded from
 * rates (the user chose not to do them); missed ones count as not completed.
 */
object InsightsCalculator {

    fun isOverdue(item: OccurrenceItem, now: Instant, zone: ZoneId): Boolean {
        val o = item.occurrence
        if (o.status != OccurrenceStatus.PENDING) return false
        val today = now.atZone(zone).toLocalDate()
        return if (o.dueMinute == null) o.dueDate.isBefore(today)
        else ReminderPolicy.dueInstant(o.dueDate, o.dueMinute, zone).isBefore(now)
    }

    fun compute(
        items: List<OccurrenceItem>,
        categories: List<Category>,
        habits: List<Habit>,
        habitLogs: List<HabitLog>,
        snoozeEventTimes: List<Instant>,
        now: Instant,
        zone: ZoneId,
        weekStart: DayOfWeek,
        trendDays: Int = 14,
    ): Insights {
        val today = now.atZone(zone).toLocalDate()
        val weekStartDate = HabitStats.weekStartOf(today, weekStart)
        val counted = items.filter { it.occurrence.status != OccurrenceStatus.SKIPPED }

        val todayItems = counted.filter { it.occurrence.dueDate == today }
        val weekItems = counted.filter { !it.occurrence.dueDate.isBefore(weekStartDate) && !it.occurrence.dueDate.isAfter(today) }

        fun completedDate(item: OccurrenceItem): LocalDate? = item.occurrence.completedAt?.atZone(zone)?.toLocalDate()

        val completedByDay = counted.filter { it.occurrence.status == OccurrenceStatus.COMPLETED }
            .mapNotNull { completedDate(it) }.groupingBy { it }.eachCount()
        // "Created" counts tasks the user created (not auto-generated recurring occurrences).
        val createdByDay = items.map { it.task }.distinctBy { it.id }
            .map { it.createdAt.atZone(zone).toLocalDate() }.groupingBy { it }.eachCount()

        fun series(days: Int) = (days - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            DayCount(d, createdByDay[d] ?: 0, completedByDay[d] ?: 0)
        }

        val from30 = today.minusDays(29)
        val last30 = counted.filter { !it.occurrence.dueDate.isBefore(from30) && !it.occurrence.dueDate.isAfter(today) }
        val catStats = last30.groupBy { it.task.categoryId }.map { (catId, list) ->
            val cat = categories.firstOrNull { it.id == catId }
            CategoryStat(catId, cat?.name ?: "Uncategorized", cat?.colorIndex ?: 0, list.size, list.count { it.occurrence.status == OccurrenceStatus.COMPLETED })
        }.sortedByDescending { it.total }

        val weekdayOrder = (0L until 7L).map { weekStart.plus(it) }
        val byWeekday = counted.filter { it.occurrence.status == OccurrenceStatus.COMPLETED }
            .mapNotNull { completedDate(it)?.dayOfWeek }.groupingBy { it }.eachCount()

        val activeHabits = habits.filter { !it.archived }
        val habitRates = activeHabits.mapNotNull { h ->
            HabitStats.completionRate(h, habitLogs, from30, today, weekStart)?.let { HabitConsistency(h.id, h.name, it) }
        }

        val from30Instant = from30.atStartOfDay(zone).toInstant()
        return Insights(
            todayTotal = todayItems.size,
            todayCompleted = todayItems.count { it.occurrence.status == OccurrenceStatus.COMPLETED },
            weekTotal = weekItems.size,
            weekCompleted = weekItems.count { it.occurrence.status == OccurrenceStatus.COMPLETED },
            overdueCount = items.count { isOverdue(it, now, zone) },
            createdVsCompleted = series(7),
            completionTrend = series(trendDays),
            categories = catStats,
            weekdays = weekdayOrder.map { WeekdayStat(it, byWeekday[it] ?: 0) },
            habitConsistency = habitRates,
            averageHabitConsistency = if (habitRates.isEmpty()) null else habitRates.map { it.rate }.average(),
            missedLast30 = last30.count { it.occurrence.status == OccurrenceStatus.MISSED },
            rescheduledLast30 = last30.count { it.occurrence.rescheduleCount > 0 },
            snoozesLast30 = snoozeEventTimes.count { !it.isBefore(from30Instant) },
            totalCompletedAllTime = counted.count { it.occurrence.status == OccurrenceStatus.COMPLETED },
        )
    }
}
