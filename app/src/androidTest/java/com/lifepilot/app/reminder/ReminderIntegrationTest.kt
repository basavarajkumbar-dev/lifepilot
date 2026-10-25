package com.lifepilot.app.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifepilot.app.LifePilotApp
import com.lifepilot.app.data.database.LifePilotDatabase
import com.lifepilot.app.data.repository.RoomCategoryRepository
import com.lifepilot.app.data.repository.RoomTaskRepository
import com.lifepilot.app.domain.model.AppSettings
import com.lifepilot.app.domain.model.OccurrenceStatus
import com.lifepilot.app.domain.model.RecurrenceFrequency
import com.lifepilot.app.domain.model.RecurrenceRule
import com.lifepilot.app.domain.model.ReminderConfig
import com.lifepilot.app.domain.model.Habit
import com.lifepilot.app.domain.model.Occurrence
import com.lifepilot.app.domain.model.Task
import com.lifepilot.app.domain.repository.SettingsRepository
import com.lifepilot.app.domain.scheduling.AlarmKey
import com.lifepilot.app.domain.scheduling.AlarmScheduler
import com.lifepilot.app.domain.scheduling.ReminderNotifier
import com.lifepilot.app.domain.service.TaskDraft
import com.lifepilot.app.domain.service.TaskService
import com.lifepilot.app.domain.time.SystemTimeProvider
import com.lifepilot.app.scheduling.AndroidAlarmScheduler
import com.lifepilot.app.scheduling.NotificationActionReceiver
import com.lifepilot.app.scheduling.ReminderAlarmReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

private class RecordingScheduler : AlarmScheduler {
    val alarms = linkedMapOf<AlarmKey, Instant>()
    override fun schedule(key: AlarmKey, at: Instant) { alarms[key] = at }
    override fun cancel(key: AlarmKey) { alarms.remove(key) }
    override fun canScheduleExact() = true
}

private class NoopNotifier : ReminderNotifier {
    val shown = mutableListOf<Long>()
    override fun showTaskReminder(task: Task, occurrence: Occurrence, categoryName: String?) { shown += occurrence.id }
    override fun cancelTaskReminder(occurrenceId: Long) {}
    override fun showHabitReminder(habit: Habit) {}
    override fun cancelHabitReminder(habitId: Long) {}
}

private class MemorySettings : SettingsRepository {
    private val s = MutableStateFlow(AppSettings(quietHours = AppSettings().quietHours.copy(enabled = false)))
    override val settings = s
    override suspend fun current() = s.value
    override suspend fun update(transform: (AppSettings) -> AppSettings) { s.value = transform(s.value) }
}

/** Real Room database + TaskService, with a recording scheduler (repository/scheduling coordination). */
@RunWith(AndroidJUnit4::class)
class RepositorySchedulingIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun createCompleteAndRecurOnRealDatabase() = runBlocking {
        val db = LifePilotDatabase.build(context, null)
        val repo = RoomTaskRepository(db)
        val scheduler = RecordingScheduler()
        val notifier = NoopNotifier()
        val service = TaskService(repo, RoomCategoryRepository(db), MemorySettings(), scheduler, notifier, SystemTimeProvider)
        val today = LocalDate.now()
        val minute = (LocalTime.now().hour * 60 + LocalTime.now().minute + 120).coerceAtMost(23 * 60 + 59)

        val oneOff = service.createTask(TaskDraft(title = "One-off", dueDate = today.plusDays(1), dueMinute = minute, reminder = ReminderConfig(intervalMinutes = 5)))
        val occ = repo.occurrencesForTask(oneOff).single()
        assertNotNull(scheduler.alarms[AlarmKey.OccurrenceReminder(occ.id)])
        assertNotNull(repo.getOccurrence(occ.id)!!.nextReminderAt)

        val daily = service.createTask(TaskDraft(title = "Daily", dueDate = today, dueMinute = minute, recurrence = RecurrenceRule(RecurrenceFrequency.DAILY, startDate = today)))
        val dailyOccs = repo.occurrencesForTask(daily)
        assertEquals(2, dailyOccs.size)

        service.completeOccurrence(dailyOccs[0].id)
        assertEquals(OccurrenceStatus.COMPLETED, repo.getOccurrence(dailyOccs[0].id)!!.status)
        assertEquals(OccurrenceStatus.PENDING, repo.getOccurrence(dailyOccs[1].id)!!.status)
        assertNull(scheduler.alarms[AlarmKey.OccurrenceReminder(dailyOccs[0].id)])

        repeat(3) { service.reconcileAll() }
        assertEquals(2, repo.occurrencesForTask(daily).size)

        service.deleteTask(oneOff)
        assertNull(scheduler.alarms[AlarmKey.OccurrenceReminder(occ.id)])
        db.close()
    }
}

/** Uses the real AlarmManager-backed scheduler: one PendingIntent per key, replace and cancel. */
@RunWith(AndroidJUnit4::class)
class AndroidAlarmSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun exists(key: AlarmKey): Boolean = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderAlarmReceiver::class.java).setAction(ReminderAlarmReceiver.ACTION_ALARM).setData(AndroidAlarmScheduler.uriFor(key)),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    ) != null

    @Test fun scheduleReplaceAndCancel() {
        val scheduler = AndroidAlarmScheduler(context)
        val key = AlarmKey.OccurrenceReminder(987_654L)
        val other = AlarmKey.OccurrenceReminder(987_655L)
        scheduler.schedule(key, Instant.now().plusSeconds(3600))
        scheduler.schedule(key, Instant.now().plusSeconds(7200)) // replaces, same identity
        assertTrue(exists(key))
        assertTrue(!exists(other))
        scheduler.cancel(key)
        assertTrue(!exists(key))
        assertEquals(key, AndroidAlarmScheduler.keyFor(AndroidAlarmScheduler.uriFor(key)))
    }
}

/** Sends the real broadcast that a notification action button would send, with the app's container. */
@RunWith(AndroidJUnit4::class)
class NotificationActionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context as LifePilotApp).container

    private suspend fun awaitStatus(id: Long, status: OccurrenceStatus, timeoutMs: Long = 15_000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (container.tasks.getOccurrence(id)?.status == status) return true
            delay(100)
        }
        return false
    }

    private fun send(action: String, id: Long) = context.sendBroadcast(
        Intent(context, NotificationActionReceiver::class.java).setAction(action).putExtra(NotificationActionReceiver.EXTRA_ID, id)
    )

    @Test fun snoozeKeepsPendingAndCompleteCompletes() = runBlocking {
        val taskId = container.taskService.createTask(TaskDraft(title = "Action test", dueDate = LocalDate.now().plusDays(1), dueMinute = 9 * 60))
        val occId = container.tasks.occurrencesForTask(taskId).single().id

        send(NotificationActionReceiver.ACTION_SNOOZE, occId)
        val end = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < end && container.tasks.getOccurrence(occId)?.snoozeCount != 1) delay(100)
        val snoozed = container.tasks.getOccurrence(occId)!!
        assertEquals(1, snoozed.snoozeCount)
        assertEquals(OccurrenceStatus.PENDING, snoozed.status)

        send(NotificationActionReceiver.ACTION_COMPLETE, occId)
        assertTrue(awaitStatus(occId, OccurrenceStatus.COMPLETED))
        assertNull(container.tasks.getOccurrence(occId)!!.nextReminderAt)
        container.taskService.deleteTask(taskId)
    }
}
