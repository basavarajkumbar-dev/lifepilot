package com.lifepilot.app.domain

import com.lifepilot.app.domain.model.AppSettings
import com.lifepilot.app.domain.model.MissedPolicy
import com.lifepilot.app.domain.model.OccurrenceStatus
import com.lifepilot.app.domain.model.Priority
import com.lifepilot.app.domain.model.QuietHours
import com.lifepilot.app.domain.model.RecurrenceFrequency
import com.lifepilot.app.domain.model.RecurrenceRule
import com.lifepilot.app.domain.model.ReminderConfig
import com.lifepilot.app.domain.model.ReminderEventType
import com.lifepilot.app.domain.scheduling.AlarmKey
import com.lifepilot.app.domain.service.ActionSource
import com.lifepilot.app.domain.service.TaskDraft
import com.lifepilot.app.domain.service.TaskService
import com.lifepilot.app.testing.FakeAlarmScheduler
import com.lifepilot.app.testing.FakeCategories
import com.lifepilot.app.testing.FakeNotifier
import com.lifepilot.app.testing.FakeSettings
import com.lifepilot.app.testing.FakeTaskRepository
import com.lifepilot.app.testing.FakeTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class TaskServiceTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 25)
    private lateinit var time: FakeTime
    private lateinit var repo: FakeTaskRepository
    private lateinit var alarms: FakeAlarmScheduler
    private lateinit var notifier: FakeNotifier
    private lateinit var settings: FakeSettings
    private lateinit var service: TaskService

    @Before fun setUp() {
        time = FakeTime.at(LocalDateTime.of(today, LocalTime.of(8, 0)), zone)
        repo = FakeTaskRepository()
        alarms = FakeAlarmScheduler()
        notifier = FakeNotifier()
        settings = FakeSettings()
        service = newService()
    }

    private fun newService() = TaskService(repo, FakeCategories(), settings, alarms, notifier, time)
    private fun at(h: Int, m: Int, d: LocalDate = today): Instant = LocalDateTime.of(d, LocalTime.of(h, m)).atZone(zone).toInstant()
    private fun draft(
        title: String = "Pay rent",
        due: LocalDate = today,
        minute: Int? = 9 * 60,
        reminder: ReminderConfig = ReminderConfig(intervalMinutes = 10),
        recurrence: RecurrenceRule? = null,
    ) = TaskDraft(title = title, dueDate = due, dueMinute = minute, reminder = reminder, recurrence = recurrence, priority = Priority.HIGH)

    private suspend fun onlyOccurrenceId(taskId: Long) = repo.occurrencesForTask(taskId).single().id

    /** Simulates AlarmManager delivering the alarm for [occId] at its scheduled time. */
    private suspend fun fire(occId: Long) {
        val at = alarms.at(occId) ?: error("no alarm for $occId")
        if (at.isAfter(time.now())) time.current = at
        service.onReminderAlarm(occId)
    }

    @Test fun createTaskPersistsAndSchedulesFirstReminder() = runBlocking {
        val id = service.createTask(draft())
        val task = repo.getTask(id)!!
        assertEquals("Pay rent", task.title)
        val occ = repo.occurrencesForTask(id).single()
        assertEquals(OccurrenceStatus.PENDING, occ.status)
        assertEquals(at(9, 0), alarms.at(occ.id))
        assertEquals(at(9, 0), repo.getOccurrence(occ.id)!!.nextReminderAt)
    }

    @Test fun invalidDraftIsRejected() = runBlocking {
        val result = runCatching { service.createTask(draft(title = "  ")) }
        assertTrue(result.isFailure)
        assertTrue(repo.getAllTasks().isEmpty())
    }

    @Test fun taskWithoutTimeHasRemindersDisabledAndNoAlarm() = runBlocking {
        val id = service.createTask(draft(minute = null))
        assertFalse(repo.getTask(id)!!.reminder.enabled)
        assertTrue(alarms.alarms.isEmpty())
    }

    @Test fun remindsRepeatedlyUntilCompleted() = runBlocking {
        val id = service.createTask(draft())
        val occId = onlyOccurrenceId(id)
        fire(occId)
        assertEquals(listOf(occId), notifier.shown)
        assertEquals(at(9, 10), alarms.at(occId))
        fire(occId)
        fire(occId)
        assertEquals(3, notifier.shown.size)
        assertEquals(at(9, 30), alarms.at(occId))
        val occ = repo.getOccurrence(occId)!!
        assertEquals(OccurrenceStatus.PENDING, occ.status)
        assertEquals(3, occ.totalReminders)
        assertEquals(3, repo.logs.count { it.type == ReminderEventType.FIRED })
    }

    @Test fun dismissingNotificationDoesNotCompleteTask() = runBlocking {
        val id = service.createTask(draft())
        val occId = onlyOccurrenceId(id)
        fire(occId)
        notifier.cancelTaskReminder(occId) // user swipes the notification away: no app callback
        assertEquals(OccurrenceStatus.PENDING, repo.getOccurrence(occId)!!.status)
        assertNotNull(alarms.at(occId))
    }

    @Test fun snoozeDoesNotCompleteAndPostponesNextReminder() = runBlocking {
        val id = service.createTask(draft())
        val occId = onlyOccurrenceId(id)
        fire(occId)
        service.snoozeOccurrence(occId, 30, ActionSource.NOTIFICATION)
        val occ = repo.getOccurrence(occId)!!
        assertEquals(OccurrenceStatus.PENDING, occ.status)
        assertEquals(at(9, 30), alarms.at(occId))
        assertEquals(1, occ.snoozeCount)
        assertFalse(occId in notifier.active)
        // After the snooze fires, normal interval resumes.
        fire(occId)
        assertEquals(at(9, 40), alarms.at(occId))
        assertNull(repo.getOccurrence(occId)!!.snoozedUntil)
    }

    @Test fun snoozeUsesDefaultMinutesFromSettings() = runBlocking {
        settings.update { it.copy(defaultSnoozeMinutes = 25) }
        val id = service.createTask(draft())
        val occId = onlyOccurrenceId(id)
        fire(occId)
        service.snoozeOccurrence(occId)
        assertEquals(at(9, 25), alarms.at(occId))
    }

    @Test fun completingCancelsFutureRemindersAndNotification() = runBlocking {
        val id = service.createTask(draft())
        val occId = onlyOccurrenceId(id)
        fire(occId)
        assertTrue(service.completeOccurrence(occId, ActionSource.NOTIFICATION))
        val occ = repo.getOccurrence(occId)!!
        assertEquals(OccurrenceStatus.COMPLETED, occ.status)
        assertEquals(time.now(), occ.completedAt)
        assertNull(alarms.at(occId))
        assertNull(occ.nextReminderAt)
        assertFalse(occId in notifier.active)
    }

    @Test fun staleAlarmAfterCompletionDoesNotNotify() = runBlocking {
        val id = service.createTask(draft())
        val occId = onlyOccurrenceId(id)
        service.completeOccurrence(occId)
        time.current = at(9, 0)
        service.onReminderAlarm(occId) // alarm delivered anyway (race with cancel)
        assertTrue(notifier.shown.isEmpty())
    }

    @Test fun completingTwiceIsIdempotent() = runBlocking {
        val id = service.createTask(draft())
        val occId = onlyOccurrenceId(id)
        assertTrue(service.completeOccurrence(occId))
        assertFalse(service.completeOccurrence(occId))
        assertEquals(1, repo.logs.count { it.type == ReminderEventType.COMPLETED })
    }

    @Test fun concurrentCompleteAndSnoozeLeaveConsistentState() = runBlocking {
        val id = service.createTask(draft())
        val occId = onlyOccurrenceId(id)
        fire(occId)
        listOf(
            async { service.completeOccurrence(occId, ActionSource.NOTIFICATION) },
            async { service.snoozeOccurrence(occId, 10) },
            async { service.completeOccurrence(occId) },
        ).awaitAll()
        val occ = repo.getOccurrence(occId)!!
        assertEquals(OccurrenceStatus.COMPLETED, occ.status)
        assertNull(alarms.at(occId))
        assertEquals(1, repo.logs.count { it.type == ReminderEventType.COMPLETED })
    }

    @Test fun reopenRestoresReminders() = runBlocking {
        val id = service.createTask(draft())
        val occId = onlyOccurrenceId(id)
        service.completeOccurrence(occId)
        service.reopenOccurrence(occId)
        assertEquals(OccurrenceStatus.PENDING, repo.getOccurrence(occId)!!.status)
        assertEquals(at(9, 0), alarms.at(occId))
    }

    @Test fun reschedulingReplacesOldAlarm() = runBlocking {
        val id = service.createTask(draft())
        val occId = onlyOccurrenceId(id)
        fire(occId)
        val tomorrow = today.plusDays(1)
        service.rescheduleOccurrence(occId, tomorrow, 18 * 60)
        assertEquals(1, alarms.alarms.size)
        assertEquals(at(18, 0, tomorrow), alarms.at(occId))
        val occ = repo.getOccurrence(occId)!!
        assertEquals(tomorrow, occ.dueDate)
        assertEquals(1, occ.rescheduleCount)
        assertNull(occ.lastReminderAt)
        // The old 09:10 alarm, if delivered late, does not notify — it re-plans to the new time.
        time.current = at(9, 10)
        val before = notifier.shown.size
        service.onReminderAlarm(occId)
        assertEquals(before, notifier.shown.size)
        assertEquals(at(18, 0, tomorrow), alarms.at(occId))
    }

    @Test fun editingTaskDueTimeReschedulesAlarm() = runBlocking {
        val id = service.createTask(draft())
        val occId = onlyOccurrenceId(id)
        service.updateTask(id, draft(minute = 11 * 60))
        assertEquals(at(11, 0), alarms.at(occId))
        assertEquals(1, repo.getOccurrence(occId)!!.rescheduleCount)
    }

    @Test fun editingReminderIntervalUpdatesFutureScheduling() = runBlocking {
        val id = service.createTask(draft())
        val occId = onlyOccurrenceId(id)
        fire(occId)
        assertEquals(at(9, 10), alarms.at(occId))
        service.updateTask(id, draft(reminder = ReminderConfig(intervalMinutes = 30)))
        assertEquals(at(9, 30), alarms.at(occId))
    }

    @Test fun disablingTaskRemindersCancelsAlarm() = runBlocking {
        val id = service.createTask(draft())
        val occId = onlyOccurrenceId(id)
        service.setTaskRemindersEnabled(id, false)
        assertNull(alarms.at(occId))
        service.setTaskRemindersEnabled(id, true)
        assertEquals(at(9, 0), alarms.at(occId))
    }

    @Test fun masterSwitchOffSuppressesAllReminders() = runBlocking {
        val id = service.createTask(draft())
        val occId = onlyOccurrenceId(id)
        settings.update { it.copy(remindersEnabled = false) }
        time.current = at(9, 0)
        service.onReminderAlarm(occId)
        assertTrue(notifier.shown.isEmpty())
        assertNull(alarms.at(occId))
        settings.update { it.copy(remindersEnabled = true) }
        service.reconcileAll()
        assertNotNull(alarms.at(occId))
    }

    @Test fun deletingTaskCancelsAlarmsAndNotifications() = runBlocking {
        val id = service.createTask(draft())
        val occId = onlyOccurrenceId(id)
        fire(occId)
        service.deleteTask(id)
        assertNull(repo.getTask(id))
        assertTrue(alarms.alarms.isEmpty())
        assertFalse(occId in notifier.active)
        // An alarm that slipped through for a deleted occurrence is a no-op.
        service.onReminderAlarm(occId)
        assertEquals(1, notifier.shown.size)
    }

    @Test fun duplicateCreatesIndependentTask() = runBlocking {
        val id = service.createTask(draft())
        val copyId = service.duplicateTask(id)!!
        assertTrue(copyId != id)
        assertEquals("Pay rent (copy)", repo.getTask(copyId)!!.title)
        assertEquals(2, alarms.alarms.size)
        service.completeOccurrence(onlyOccurrenceId(id))
        assertEquals(OccurrenceStatus.PENDING, repo.occurrencesForTask(copyId).single().status)
    }

    @Test fun maxRemindersPerDayStopsButKeepsPending() = runBlocking {
        val id = service.createTask(draft(reminder = ReminderConfig(intervalMinutes = 10, maxPerDay = 2, allowOverdue = false)))
        val occId = onlyOccurrenceId(id)
        fire(occId)
        fire(occId)
        assertEquals(2, notifier.shown.size)
        assertNull(alarms.at(occId))
        assertEquals(OccurrenceStatus.PENDING, repo.getOccurrence(occId)!!.status)
    }

    @Test fun quietHoursDeferInsteadOfDropping() = runBlocking {
        settings.update { it.copy(quietHours = QuietHours(true, 22 * 60, 7 * 60)) }
        time.current = at(21, 0)
        val id = service.createTask(draft(minute = 21 * 60 + 50, reminder = ReminderConfig(intervalMinutes = 15)))
        val occId = onlyOccurrenceId(id)
        fire(occId) // 21:50
        assertEquals(at(7, 0, today.plusDays(1)), alarms.at(occId))
    }

    @Test fun quietHoursEnabledAfterSchedulingDefersAtFireTime() = runBlocking {
        val id = service.createTask(draft(minute = 23 * 60))
        val occId = onlyOccurrenceId(id)
        settings.update { it.copy(quietHours = QuietHours(true, 22 * 60, 7 * 60)) }
        time.current = at(23, 0)
        service.onReminderAlarm(occId)
        assertTrue(notifier.shown.isEmpty())
        assertEquals(at(7, 0, today.plusDays(1)), alarms.at(occId))
    }

    @Test fun reconcileIsIdempotentAndCreatesNoDuplicates() = runBlocking {
        val a = service.createTask(draft(title = "A"))
        val b = service.createTask(draft(title = "B", minute = 10 * 60))
        val snapshot = alarms.alarms.toMap()
        repeat(3) { service.reconcileAll() }
        assertEquals(snapshot, alarms.alarms.toMap())
        assertEquals(2, alarms.alarms.size)
        assertEquals(1, repo.occurrencesForTask(a).size)
        assertEquals(1, repo.occurrencesForTask(b).size)
    }

    @Test fun appRestartRestoresAlarmsFromDatabase() = runBlocking {
        val id = service.createTask(draft())
        val occId = onlyOccurrenceId(id)
        fire(occId)
        // Simulate reboot: OS alarms are wiped, process restarts with a fresh service instance.
        alarms.alarms.clear()
        service = newService()
        service.reconcileAll()
        assertEquals(at(9, 10), alarms.at(occId))
        assertEquals(OccurrenceStatus.PENDING, repo.getOccurrence(occId)!!.status)
    }

    @Test fun rebootAfterLongDowntimeDeliversOneCatchUpReminder() = runBlocking {
        val id = service.createTask(draft())
        val occId = onlyOccurrenceId(id)
        alarms.alarms.clear()
        time.current = at(15, 0) // device was off from 08:00 to 15:00
        service = newService()
        service.reconcileAll()
        assertEquals(at(15, 0), alarms.at(occId))
        service.onReminderAlarm(occId)
        assertEquals(1, notifier.shown.size)
        assertEquals(at(15, 10), alarms.at(occId))
    }

    @Test fun timezoneChangeReconcilesToNewLocalTime() = runBlocking {
        val id = service.createTask(draft(minute = 20 * 60))
        val occId = onlyOccurrenceId(id)
        assertEquals(at(20, 0), alarms.at(occId))
        time.zoneId = ZoneId.of("Asia/Dubai")
        service.reconcileAll()
        val expected = LocalDateTime.of(today, LocalTime.of(20, 0)).atZone(ZoneId.of("Asia/Dubai")).toInstant()
        assertEquals(expected, alarms.at(occId))
    }

    // ------------------------------------------------------------ recurring

    private fun daily(missed: MissedPolicy = MissedPolicy.MARK_MISSED) =
        RecurrenceRule(RecurrenceFrequency.DAILY, startDate = today, missedPolicy = missed)

    @Test fun recurringTaskCreatesTodayAndNextOccurrence() = runBlocking {
        val id = service.createTask(draft(title = "Study Python", minute = 20 * 60, recurrence = daily()))
        val occs = repo.occurrencesForTask(id)
        assertEquals(listOf(today, today.plusDays(1)), occs.map { it.seriesDate })
        assertEquals(2, alarms.alarms.size)
        assertEquals(at(20, 0), alarms.at(occs[0].id))
        assertEquals(at(20, 0, today.plusDays(1)), alarms.at(occs[1].id))
    }

    @Test fun completingRecurringOccurrenceCompletesOnlyThatOne() = runBlocking {
        val id = service.createTask(draft(title = "Exercise", minute = 6 * 60 + 30, recurrence = daily()))
        val (first, second) = repo.occurrencesForTask(id)
        service.completeOccurrence(first.id)
        assertEquals(OccurrenceStatus.COMPLETED, repo.getOccurrence(first.id)!!.status)
        assertEquals(OccurrenceStatus.PENDING, repo.getOccurrence(second.id)!!.status)
        assertNull(alarms.at(first.id))
        assertEquals(at(6, 30, today.plusDays(1)), alarms.at(second.id))
    }

    @Test fun nextDayMaterializesFollowingOccurrenceWithoutDuplicates() = runBlocking {
        val id = service.createTask(draft(minute = 20 * 60, recurrence = daily()))
        time.current = at(8, 0, today.plusDays(1))
        service.reconcileAll()
        service.reconcileAll()
        service.materializeAll()
        val dates = repo.occurrencesForTask(id).map { it.seriesDate }
        assertEquals(listOf(today, today.plusDays(1), today.plusDays(2)), dates)
        assertEquals(dates.size, dates.toSet().size)
    }

    @Test fun missedOccurrenceIsMarkedMissedWhenPolicySaysSo() = runBlocking {
        val id = service.createTask(draft(minute = 20 * 60, recurrence = daily(MissedPolicy.MARK_MISSED)))
        val firstId = repo.occurrencesForTask(id).first().id
        time.current = at(8, 0, today.plusDays(1))
        service.reconcileAll()
        assertEquals(OccurrenceStatus.MISSED, repo.getOccurrence(firstId)!!.status)
        assertNull(alarms.at(firstId))
        assertTrue(repo.logs.any { it.type == ReminderEventType.MISSED && it.occurrenceId == firstId })
    }

    @Test fun missedOccurrenceStaysOverdueWhenPolicyKeeps() = runBlocking {
        val id = service.createTask(draft(minute = 20 * 60, recurrence = daily(MissedPolicy.KEEP_OVERDUE)))
        val firstId = repo.occurrencesForTask(id).first().id
        time.current = at(8, 0, today.plusDays(1))
        service.reconcileAll()
        assertEquals(OccurrenceStatus.PENDING, repo.getOccurrence(firstId)!!.status)
    }

    @Test fun longAbsenceCatchUpIsBounded() = runBlocking {
        val id = service.createTask(draft(minute = 20 * 60, recurrence = daily()))
        time.current = at(8, 0, today.plusDays(200))
        service.reconcileAll()
        val occs = repo.occurrencesForTask(id)
        assertTrue("bounded catch-up, was ${occs.size}", occs.size <= 2 + 33)
        assertEquals(1, occs.count { it.status == OccurrenceStatus.PENDING && !it.seriesDate.isAfter(today.plusDays(200)) })
    }

    @Test fun monthlyBillOnFifth() = runBlocking {
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY, monthDays = setOf(5), startDate = today)
        val id = service.createTask(draft(title = "Pay bill", minute = 10 * 60, recurrence = rule))
        val occs = repo.occurrencesForTask(id)
        assertEquals(listOf(LocalDate.of(2026, 11, 5)), occs.map { it.seriesDate })
        assertEquals(at(10, 0, LocalDate.of(2026, 11, 5)), alarms.at(occs.single().id))
    }

    @Test fun skippingRecurringOccurrenceKeepsSeries() = runBlocking {
        val id = service.createTask(draft(minute = 20 * 60, recurrence = daily()))
        val first = repo.occurrencesForTask(id).first()
        service.skipOccurrence(first.id)
        assertEquals(OccurrenceStatus.SKIPPED, repo.getOccurrence(first.id)!!.status)
        assertNull(alarms.at(first.id))
        assertEquals(1, alarms.alarms.size)
    }

    @Test fun changingRecurrencePreservesHistoryAndRegeneratesFuture() = runBlocking {
        val id = service.createTask(draft(minute = 20 * 60, recurrence = daily()))
        val first = repo.occurrencesForTask(id).first()
        service.completeOccurrence(first.id)
        service.updateTask(id, draft(minute = 21 * 60, recurrence = daily()))
        val occs = repo.occurrencesForTask(id)
        assertEquals(OccurrenceStatus.COMPLETED, occs.first { it.id == first.id }.status)
        val future = occs.filter { it.status == OccurrenceStatus.PENDING }
        assertEquals(listOf(today.plusDays(1)), future.map { it.seriesDate })
        assertEquals(21 * 60, future.single().dueMinute)
        assertEquals(at(21, 0, today.plusDays(1)), alarms.at(future.single().id))
        assertEquals(1, alarms.alarms.size)
    }

    @Test fun deletingRecurringTaskCancelsEveryOccurrenceAlarm() = runBlocking {
        val id = service.createTask(draft(minute = 20 * 60, recurrence = daily()))
        assertEquals(2, alarms.alarms.size)
        service.deleteTask(id)
        assertTrue(alarms.alarms.isEmpty())
        assertTrue(repo.allOccurrences().isEmpty())
    }

    @Test fun alarmKeysAreUniquePerOccurrence() = runBlocking {
        service.createTask(draft(title = "A"))
        service.createTask(draft(title = "B"))
        service.createTask(draft(title = "C", recurrence = daily()))
        val keys = alarms.alarms.keys.filterIsInstance<AlarmKey.OccurrenceReminder>().map { it.occurrenceId }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(repo.allOccurrences().size, keys.size)
    }

    @Test fun defaultSettingsReminderConfig() {
        val s = AppSettings(defaultIntervalMinutes = 30, defaultOffsetMinutes = 5)
        assertEquals(30, s.defaultReminderConfig().intervalMinutes)
        assertEquals(5, s.defaultReminderConfig().offsetMinutes)
    }
}
