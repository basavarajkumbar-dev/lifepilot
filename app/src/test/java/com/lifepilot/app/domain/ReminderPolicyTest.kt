package com.lifepilot.app.domain

import com.lifepilot.app.domain.model.Occurrence
import com.lifepilot.app.domain.model.OccurrenceStatus
import com.lifepilot.app.domain.model.QuietHours
import com.lifepilot.app.domain.model.ReminderConfig
import com.lifepilot.app.domain.reminder.ReminderDecision
import com.lifepilot.app.domain.reminder.ReminderPolicy
import com.lifepilot.app.domain.reminder.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderPolicyTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val date = LocalDate.of(2026, 10, 25)
    private val noQuiet = QuietHours(false, 0, 0)

    private fun at(h: Int, m: Int, d: LocalDate = date, z: ZoneId = zone): Instant = LocalDateTime.of(d, java.time.LocalTime.of(h, m)).atZone(z).toInstant()
    private fun occ(dueMinute: Int? = 9 * 60, last: Instant? = null, snooze: Instant? = null, count: Int = 0, countDate: LocalDate? = null, status: OccurrenceStatus = OccurrenceStatus.PENDING, dueDate: LocalDate = date) =
        Occurrence.new(1, dueDate, dueMinute, Instant.EPOCH).copy(lastReminderAt = last, snoozedUntil = snooze, remindersOnCountDate = count, reminderCountDate = countDate, status = status)

    private fun next(config: ReminderConfig, o: Occurrence, now: Instant, quiet: QuietHours = noQuiet, master: Boolean = true, z: ZoneId = zone) =
        ReminderPolicy.next(config, o, quiet, master, now, z)

    private fun scheduledAt(d: ReminderDecision): Instant = (d as ReminderDecision.Schedule).at

    @Test fun firstReminderAtDueTimeWhenInFuture() {
        assertEquals(at(9, 0), scheduledAt(next(ReminderConfig(), occ(), at(8, 0))))
    }

    @Test fun firstReminderHonoursOffset() {
        assertEquals(at(8, 30), scheduledAt(next(ReminderConfig(offsetMinutes = 30), occ(), at(8, 0))))
    }

    @Test fun missedFirstReminderIsDeliveredImmediatelyNotLost() {
        val now = at(11, 0)
        assertEquals(now, scheduledAt(next(ReminderConfig(), occ(), now)))
    }

    @Test fun repeatsAtConfiguredInterval() {
        listOf(5, 10, 15, 30, 60, 45).forEach { interval ->
            val d = next(ReminderConfig(intervalMinutes = interval), occ(last = at(9, 0)), at(9, 0))
            assertEquals("interval $interval", at(9, 0).plusSeconds(interval * 60L), scheduledAt(d))
        }
    }

    @Test fun intervalNeverJumpsOverExactDueTime() {
        // First reminder 08:50 (offset 10), interval 15 -> next should be 09:00, not 09:05.
        val d = next(ReminderConfig(offsetMinutes = 10, intervalMinutes = 15), occ(last = at(8, 50)), at(8, 50))
        assertEquals(at(9, 0), scheduledAt(d))
    }

    @Test fun overdueRepeatAfterLongDowntimeFiresNowOnce() {
        val now = at(13, 0)
        assertEquals(now, scheduledAt(next(ReminderConfig(intervalMinutes = 10), occ(last = at(9, 0)), now)))
    }

    @Test fun noRepeatWhenRepeatDisabled() {
        val d = next(ReminderConfig(repeatUntilComplete = false), occ(last = at(9, 0)), at(9, 1))
        assertEquals(ReminderDecision.None(StopReason.SINGLE_REMINDER_SENT), d)
    }

    @Test fun activeSnoozeWins() {
        val snooze = at(9, 37)
        assertEquals(snooze, scheduledAt(next(ReminderConfig(intervalMinutes = 5), occ(last = at(9, 0), snooze = snooze), at(9, 7))))
    }

    @Test fun snoozeCalculation() {
        val now = at(9, 0)
        assertEquals(at(9, 10), ReminderPolicy.snoozeUntil(now, 10))
        assertEquals(now.plusSeconds(60), ReminderPolicy.snoozeUntil(now, 0)) // clamped to >= 1 minute
    }

    @Test fun stopsWhenNotPendingOrDisabled() {
        assertEquals(ReminderDecision.None(StopReason.NOT_PENDING), next(ReminderConfig(), occ(status = OccurrenceStatus.COMPLETED), at(8, 0)))
        assertEquals(ReminderDecision.None(StopReason.TASK_REMINDERS_OFF), next(ReminderConfig(enabled = false), occ(), at(8, 0)))
        assertEquals(ReminderDecision.None(StopReason.MASTER_SWITCH_OFF), next(ReminderConfig(), occ(), at(8, 0), master = false))
        assertEquals(ReminderDecision.None(StopReason.NO_DUE_TIME), next(ReminderConfig(), occ(dueMinute = null), at(8, 0)))
    }

    @Test fun continueAfterDueFalseStopsAtDueTime() {
        val config = ReminderConfig(offsetMinutes = 20, intervalMinutes = 10, continueAfterDue = false)
        assertEquals(at(9, 0), scheduledAt(next(config, occ(last = at(8, 50)), at(8, 50))))
        assertEquals(ReminderDecision.None(StopReason.DUE_TIME_PASSED), next(config, occ(last = at(9, 0)), at(9, 0)))
    }

    @Test fun stopAfterMinutesIsAHardStop() {
        val config = ReminderConfig(intervalMinutes = 30, stopAfterMinutes = 60)
        assertEquals(at(10, 0), scheduledAt(next(config, occ(last = at(9, 30)), at(9, 30))))
        assertEquals(ReminderDecision.None(StopReason.STOP_TIME_REACHED), next(config, occ(last = at(10, 0)), at(10, 0)))
    }

    @Test fun overdueRemindersContinueNextDayOnlyWhenAllowed() {
        val lateNight = at(23, 55)
        val allowed = next(ReminderConfig(intervalMinutes = 10, allowOverdue = true), occ(last = lateNight), lateNight)
        assertEquals(at(0, 5, date.plusDays(1)), scheduledAt(allowed))
        val notAllowed = next(ReminderConfig(intervalMinutes = 10, allowOverdue = false), occ(last = lateNight), lateNight)
        assertEquals(ReminderDecision.None(StopReason.OVERDUE_NOT_ALLOWED), notAllowed)
    }

    @Test fun quietHoursDeferRepeatToWindowEnd() {
        val quiet = QuietHours(true, 22 * 60, 7 * 60)
        val d = next(ReminderConfig(intervalMinutes = 30), occ(dueMinute = 21 * 60 + 45, last = at(21, 45)), at(21, 45), quiet)
        val s = d as ReminderDecision.Schedule
        assertEquals(at(7, 0, date.plusDays(1)), s.at)
        assertTrue(s.deferredByQuietHours)
    }

    @Test fun quietHoursBeforeMidnightPartAndAfterMidnightPart() {
        val quiet = QuietHours(true, 22 * 60, 7 * 60)
        assertTrue(quiet.contains(java.time.LocalTime.of(23, 0)))
        assertTrue(quiet.contains(java.time.LocalTime.of(3, 0)))
        assertTrue(!quiet.contains(java.time.LocalTime.of(7, 0)))
        assertTrue(!quiet.contains(java.time.LocalTime.of(12, 0)))
        // A first reminder due at 03:00 is deferred to 07:00 the same day, not dropped.
        val d = next(ReminderConfig(), occ(dueMinute = 3 * 60), at(1, 0), quiet)
        assertEquals(at(7, 0), scheduledAt(d))
    }

    @Test fun firstReminderInQuietHoursIsDeferredEvenIfPastDueAndOverdueDisallowed() {
        val quiet = QuietHours(true, 22 * 60, 7 * 60)
        val config = ReminderConfig(allowOverdue = false, continueAfterDue = false)
        val d = next(config, occ(dueMinute = 23 * 60), at(22, 30), quiet)
        assertEquals(at(7, 0, date.plusDays(1)), scheduledAt(d))
    }

    @Test fun perTaskQuietWindowOverridesGlobal() {
        val global = QuietHours(true, 22 * 60, 7 * 60)
        val config = ReminderConfig(intervalMinutes = 15, customQuietStart = 12 * 60, customQuietEnd = 14 * 60)
        val d = next(config, occ(dueMinute = 11 * 60 + 50, last = at(11, 50)), at(11, 50), global)
        assertEquals(at(14, 0), scheduledAt(d))
        // Global window does not apply to this task.
        val night = next(config, occ(dueMinute = 23 * 60, last = at(23, 0)), at(23, 0), global)
        assertEquals(at(23, 15), scheduledAt(night))
    }

    @Test fun ignoresQuietHoursWhenTaskOptsOut() {
        val quiet = QuietHours(true, 22 * 60, 7 * 60)
        val d = next(ReminderConfig(intervalMinutes = 30, respectQuietHours = false), occ(dueMinute = 22 * 60, last = at(22, 0)), at(22, 0), quiet)
        assertEquals(at(22, 30), scheduledAt(d))
    }

    @Test fun maxPerDayDefersToNextDayAndKeepsPending() {
        val config = ReminderConfig(intervalMinutes = 10, maxPerDay = 3)
        val d = next(config, occ(last = at(9, 20), count = 3, countDate = date), at(9, 20))
        val s = d as ReminderDecision.Schedule
        assertEquals(date.plusDays(1).atStartOfDay(zone).toInstant(), s.at)
        assertTrue(s.deferredByDailyLimit)
    }

    @Test fun maxPerDayStopsWhenOverdueNotAllowed() {
        val config = ReminderConfig(intervalMinutes = 10, maxPerDay = 2, allowOverdue = false)
        val d = next(config, occ(last = at(9, 10), count = 2, countDate = date), at(9, 10))
        assertEquals(ReminderDecision.None(StopReason.OVERDUE_NOT_ALLOWED), d)
    }

    @Test fun maxPerDayCountResetsOnNewDay() {
        val config = ReminderConfig(intervalMinutes = 10, maxPerDay = 2)
        val now = at(9, 10, date.plusDays(1))
        val d = next(config, occ(last = at(9, 0, date.plusDays(1)), count = 5, countDate = date), now)
        assertEquals(at(9, 10, date.plusDays(1)), scheduledAt(d))
    }

    @Test fun maxPerDayAndQuietHoursCombine() {
        // Limit reached -> next day 00:00 -> inside quiet window -> 07:00.
        val quiet = QuietHours(true, 22 * 60, 7 * 60)
        val config = ReminderConfig(intervalMinutes = 10, maxPerDay = 1)
        val d = next(config, occ(last = at(9, 0), count = 1, countDate = date), at(9, 0), quiet)
        assertEquals(at(7, 0, date.plusDays(1)), scheduledAt(d))
    }

    @Test fun countAfterFiringTracksPerDay() {
        val o = occ(count = 2, countDate = date)
        assertEquals(date to 3, ReminderPolicy.countAfterFiring(o, at(10, 0), zone))
        assertEquals(date.plusDays(1) to 1, ReminderPolicy.countAfterFiring(o, at(10, 0, date.plusDays(1)), zone))
    }

    @Test fun dstSpringForwardGapShiftsReminderForward() {
        val ny = ZoneId.of("America/New_York")
        val dstDay = LocalDate.of(2026, 3, 8) // 02:00 -> 03:00
        val due = ReminderPolicy.dueInstant(dstDay, 2 * 60 + 30, ny)
        assertEquals(LocalDateTime.of(2026, 3, 8, 3, 30), due.atZone(ny).toLocalDateTime())
    }

    @Test fun dstIntervalUsesElapsedTime() {
        val ny = ZoneId.of("America/New_York")
        val dstDay = LocalDate.of(2026, 3, 8)
        val last = LocalDateTime.of(2026, 3, 8, 1, 50).atZone(ny).toInstant()
        val d = next(ReminderConfig(intervalMinutes = 15), occ(dueMinute = 60, last = last, dueDate = dstDay), last, z = ny)
        // 15 real minutes after 01:50 EST is 03:05 EDT.
        assertEquals(LocalDateTime.of(2026, 3, 8, 3, 5), scheduledAt(d).atZone(ny).toLocalDateTime())
    }

    @Test fun timezoneChangeKeepsLocalWallClockTime() {
        val o = occ(dueMinute = 20 * 60)
        val kolkata = scheduledAt(next(ReminderConfig(), o, at(8, 0)))
        val london = ZoneId.of("Europe/London")
        val londonNow = LocalDateTime.of(2026, 10, 25, 8, 0).atZone(london).toInstant()
        val inLondon = scheduledAt(next(ReminderConfig(), o, londonNow, z = london))
        assertEquals(LocalDateTime.of(2026, 10, 25, 20, 0), kolkata.atZone(zone).toLocalDateTime())
        assertEquals(LocalDateTime.of(2026, 10, 25, 20, 0), inLondon.atZone(london).toLocalDateTime())
        assertTrue(kolkata != inLondon)
    }

    @Test fun clockMovedBackwardStillSchedulesInFuture() {
        // lastReminderAt is "in the future" after the user moved the clock back.
        val last = at(10, 0)
        val now = at(8, 0)
        val d = next(ReminderConfig(intervalMinutes = 10), occ(last = last), now)
        assertEquals(now.plusSeconds(600), scheduledAt(d))
    }
}
