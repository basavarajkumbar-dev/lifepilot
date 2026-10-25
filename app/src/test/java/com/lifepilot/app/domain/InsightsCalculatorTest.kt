package com.lifepilot.app.domain

import com.lifepilot.app.domain.analytics.InsightsCalculator
import com.lifepilot.app.domain.model.Category
import com.lifepilot.app.domain.model.Occurrence
import com.lifepilot.app.domain.model.OccurrenceItem
import com.lifepilot.app.domain.model.OccurrenceStatus
import com.lifepilot.app.domain.model.Priority
import com.lifepilot.app.domain.model.ReminderConfig
import com.lifepilot.app.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class InsightsCalculatorTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 25) // Sunday
    private val now = LocalDateTime.of(today, LocalTime.of(12, 0)).atZone(zone).toInstant()
    private val work = Category(1, "Work", 0, "work", true, 0)
    private val health = Category(2, "Health", 1, "health", true, 1)

    private fun at(d: LocalDate, h: Int = 10) = LocalDateTime.of(d, LocalTime.of(h, 0)).atZone(zone).toInstant()

    private var nextId = 1L
    private fun item(due: LocalDate, status: OccurrenceStatus, cat: Category? = work, completedOn: LocalDate? = null, minute: Int? = 9 * 60, created: LocalDate = due, reschedules: Int = 0): OccurrenceItem {
        val id = nextId++
        val task = Task(id, "T$id", "", cat?.id, Priority.MEDIUM, emptyList(), null, null, null, minute, ReminderConfig(), null, at(created, 8), at(created, 8))
        val occ = Occurrence.new(id, due, minute, at(created, 8)).copy(
            id = id, status = status, rescheduleCount = reschedules,
            completedAt = if (status == OccurrenceStatus.COMPLETED) at(completedOn ?: due) else null,
        )
        return OccurrenceItem(occ, task, cat)
    }

    @Test fun emptyDataIsHandled() {
        val i = InsightsCalculator.compute(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), now, zone, DayOfWeek.MONDAY)
        assertTrue(i.isEmpty)
        assertNull(i.todayRate)
        assertNull(i.weekRate)
        assertNull(i.mostProductiveDay)
        assertEquals(7, i.createdVsCompleted.size)
        assertEquals(14, i.completionTrend.size)
    }

    @Test fun ratesAndCountsFromRecords() {
        val items = listOf(
            item(today, OccurrenceStatus.COMPLETED),
            item(today, OccurrenceStatus.PENDING, minute = 8 * 60),   // overdue (08:00 < 12:00)
            item(today, OccurrenceStatus.PENDING, minute = 18 * 60),  // not yet due
            item(today, OccurrenceStatus.SKIPPED),                    // excluded from rates
            item(today.minusDays(1), OccurrenceStatus.COMPLETED, cat = health),
            item(today.minusDays(2), OccurrenceStatus.MISSED, cat = health),
            item(today.minusDays(3), OccurrenceStatus.COMPLETED, reschedules = 2),
            item(today.minusDays(10), OccurrenceStatus.PENDING),       // overdue, outside week
        )
        val i = InsightsCalculator.compute(items, listOf(work, health), emptyList(), emptyList(), listOf(now, now.minusSeconds(86400L * 40)), now, zone, DayOfWeek.MONDAY)
        assertEquals(3, i.todayTotal)
        assertEquals(1, i.todayCompleted)
        // Week = Mon Oct 19 .. Sun Oct 25: today's 3 + 3 earlier days.
        assertEquals(6, i.weekTotal)
        assertEquals(3, i.weekCompleted)
        assertEquals(2, i.overdueCount)
        assertEquals(1, i.missedLast30)
        assertEquals(1, i.rescheduledLast30)
        assertEquals(1, i.snoozesLast30)
        assertEquals(3, i.totalCompletedAllTime)
        val healthStat = i.categories.first { it.categoryId == 2L }
        assertEquals(2, healthStat.total)
        assertEquals(1, healthStat.completed)
        assertEquals(1, i.completionTrend.last().completed)
        assertEquals(today, i.completionTrend.last().date)
    }

    @Test fun mostProductiveDayAndWeekdayOrder() {
        val sat = today.minusDays(1)
        val items = listOf(
            item(sat, OccurrenceStatus.COMPLETED), item(sat, OccurrenceStatus.COMPLETED),
            item(today, OccurrenceStatus.COMPLETED),
        )
        val i = InsightsCalculator.compute(items, listOf(work), emptyList(), emptyList(), emptyList(), now, zone, DayOfWeek.SUNDAY)
        assertEquals(DayOfWeek.SATURDAY, i.mostProductiveDay)
        assertEquals(DayOfWeek.SUNDAY, i.weekdays.first().day)
        assertEquals(DayOfWeek.SATURDAY, i.weekdays.last().day)
    }

    @Test fun createdCountsTasksNotGeneratedOccurrences() {
        val a = item(today, OccurrenceStatus.PENDING)
        // Second occurrence of the same task (recurring) must not count as another "created".
        val b = a.copy(occurrence = a.occurrence.copy(id = 99, seriesDate = today.plusDays(1), dueDate = today.plusDays(1)))
        val i = InsightsCalculator.compute(listOf(a, b), listOf(work), emptyList(), emptyList(), emptyList(), now, zone, DayOfWeek.MONDAY)
        assertEquals(1, i.createdVsCompleted.last().created)
    }

    @Test fun completedOnDifferentDayCountsOnCompletionDay() {
        val items = listOf(item(today.minusDays(2), OccurrenceStatus.COMPLETED, completedOn = today))
        val i = InsightsCalculator.compute(items, listOf(work), emptyList(), emptyList(), emptyList(), now, zone, DayOfWeek.MONDAY)
        assertEquals(1, i.completionTrend.last().completed)
        assertEquals(Instant::class, i.createdVsCompleted.first().date.atStartOfDay(zone).toInstant()::class)
    }
}
