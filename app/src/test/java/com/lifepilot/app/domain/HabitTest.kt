package com.lifepilot.app.domain

import com.lifepilot.app.domain.habit.HabitStats
import com.lifepilot.app.domain.model.AppSettings
import com.lifepilot.app.domain.model.Habit
import com.lifepilot.app.domain.model.HabitLog
import com.lifepilot.app.domain.model.HabitSchedule
import com.lifepilot.app.domain.model.HabitType
import com.lifepilot.app.domain.model.StreakUnit
import com.lifepilot.app.domain.scheduling.AlarmKey
import com.lifepilot.app.domain.service.HabitService
import com.lifepilot.app.testing.FakeAlarmScheduler
import com.lifepilot.app.testing.FakeHabitRepository
import com.lifepilot.app.testing.FakeNotifier
import com.lifepilot.app.testing.FakeSettings
import com.lifepilot.app.testing.FakeTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class HabitTest {

    private val today = LocalDate.of(2026, 10, 25) // Sunday
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun habit(
        id: Long = 1, schedule: HabitSchedule = HabitSchedule.DAILY, type: HabitType = HabitType.CHECK, target: Double = 1.0,
        weekdays: Set<DayOfWeek> = emptySet(), timesPerWeek: Int = 3, start: LocalDate = today.minusDays(30), minute: Int? = 7 * 60,
    ) = Habit(id, "Read", "", "book", 0, type, target, "", schedule, weekdays, timesPerWeek, minute, true, start, Instant.EPOCH, false)

    private fun logs(habitId: Long, vararg daysAgo: Long, value: Double = 1.0) = daysAgo.map { HabitLog(0, habitId, today.minusDays(it), value) }

    @Test fun currentStreakCountsConsecutiveDaysIncludingToday() {
        val s = HabitStats.streak(habit(), logs(1, 0, 1, 2, 4), today, DayOfWeek.MONDAY)
        assertEquals(3, s.current)
        assertEquals(StreakUnit.DAYS, s.unit)
    }

    @Test fun unfinishedTodayDoesNotBreakStreak() {
        val s = HabitStats.streak(habit(), logs(1, 1, 2, 3), today, DayOfWeek.MONDAY)
        assertEquals(3, s.current)
    }

    @Test fun missedDayResetsCurrentButKeepsBest() {
        val s = HabitStats.streak(habit(), logs(1, 0, 2, 3, 4, 5, 6), today, DayOfWeek.MONDAY)
        assertEquals(1, s.current)
        assertEquals(5, s.best)
    }

    @Test fun weekdayHabitSkipsUnscheduledDays() {
        // Mon/Wed/Fri habit. Today is Sunday; logs on Fri(2 ago), Wed(4), Mon(6).
        val h = habit(schedule = HabitSchedule.WEEKDAYS, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val s = HabitStats.streak(h, logs(1, 2, 4, 6), today, DayOfWeek.MONDAY)
        assertEquals(3, s.current)
        assertFalse(HabitStats.isScheduled(h, today))
    }

    @Test fun partialProgressDoesNotCountAsMet() {
        val h = habit(type = HabitType.MEASURABLE, target = 30.0)
        assertFalse(HabitStats.isMet(h, 20.0))
        assertTrue(HabitStats.isMet(h, 30.0))
        assertEquals(0.5f, HabitStats.progressFraction(h, 15.0), 0.0001f)
        val s = HabitStats.streak(h, logs(1, 0, value = 20.0) + logs(1, 1, value = 30.0), today, DayOfWeek.MONDAY)
        assertEquals(1, s.current)
    }

    @Test fun weeklyTargetStreakInWeeks() {
        val h = habit(schedule = HabitSchedule.WEEKLY_TARGET, timesPerWeek = 2, start = today.minusDays(60))
        // Week starting Monday Oct 19 contains today (Sun 25). Two logs this week, two last week.
        val l = logs(1, 0, 3) + logs(1, 7, 8)
        val s = HabitStats.streak(h, l, today, DayOfWeek.MONDAY)
        assertEquals(2, s.current)
        assertEquals(StreakUnit.WEEKS, s.unit)
    }

    @Test fun completionRateOverScheduledDays() {
        val h = habit(start = today.minusDays(9))
        val rate = HabitStats.completionRate(h, logs(1, 0, 1, 2, 3, 4), today.minusDays(9), today, DayOfWeek.MONDAY)!!
        assertEquals(0.5, rate, 1e-9)
        assertNull(HabitStats.completionRate(habit(start = today.plusDays(1)), emptyList(), today.minusDays(5), today, DayOfWeek.MONDAY))
    }

    @Test fun nextReminderIsNextScheduledPreferredTime() {
        val now = LocalDateTime.of(today, LocalTime.of(8, 0)).atZone(zone).toInstant()
        val next = HabitStats.nextReminder(habit(minute = 7 * 60), now, zone)!!
        assertEquals(LocalDateTime.of(today.plusDays(1), LocalTime.of(7, 0)), next.atZone(zone).toLocalDateTime())
        val wd = habit(schedule = HabitSchedule.WEEKDAYS, weekdays = setOf(DayOfWeek.WEDNESDAY), minute = 7 * 60)
        assertEquals(DayOfWeek.WEDNESDAY, HabitStats.nextReminder(wd, now, zone)!!.atZone(zone).dayOfWeek)
        assertNull(HabitStats.nextReminder(habit(minute = null), now, zone))
    }

    @Test fun habitServiceToggleAndReminders() = runBlocking {
        val repo = FakeHabitRepository()
        val alarms = FakeAlarmScheduler()
        val notifier = FakeNotifier()
        val time = FakeTime.at(LocalDateTime.of(today, LocalTime.of(6, 0)), zone)
        val service = HabitService(repo, FakeSettings(AppSettings()), alarms, notifier, time)
        val id = service.saveHabit(habit(id = 0))
        assertTrue(alarms.alarms.containsKey(AlarmKey.HabitReminder(id)))

        service.toggle(id)
        assertTrue(HabitStats.isMet(repo.getHabit(id)!!, repo.getLog(id, today)?.value))
        service.toggle(id)
        assertNull(repo.getLog(id, today))

        // Alarm at 07:00 notifies when not done, then re-arms for tomorrow.
        time.current = LocalDateTime.of(today, LocalTime.of(7, 0)).atZone(zone).toInstant()
        service.onHabitAlarm(id)
        assertEquals(listOf(id), notifier.habitShown)
        assertEquals(LocalDateTime.of(today.plusDays(1), LocalTime.of(7, 0)).atZone(zone).toInstant(), alarms.alarms[AlarmKey.HabitReminder(id)])

        // Done today -> no notification.
        service.toggle(id)
        service.onHabitAlarm(id)
        assertEquals(1, notifier.habitShown.size)

        service.deleteHabit(id)
        assertFalse(alarms.alarms.containsKey(AlarmKey.HabitReminder(id)))
    }

    @Test fun habitProgressRejectsFutureAndClearsOnZero() = runBlocking {
        val repo = FakeHabitRepository()
        val time = FakeTime.at(LocalDateTime.of(today, LocalTime.of(6, 0)), zone)
        val service = HabitService(repo, FakeSettings(), FakeAlarmScheduler(), FakeNotifier(), time)
        val id = service.saveHabit(habit(id = 0, type = HabitType.MEASURABLE, target = 30.0))
        service.setProgress(id, today, 12.5)
        assertEquals(12.5, repo.getLog(id, today)!!.value, 0.0)
        service.setProgress(id, today.plusDays(1), 10.0)
        assertNull(repo.getLog(id, today.plusDays(1)))
        service.setProgress(id, today, 0.0)
        assertNull(repo.getLog(id, today))
    }
}
