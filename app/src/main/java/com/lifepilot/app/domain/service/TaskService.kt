package com.lifepilot.app.domain.service

import com.lifepilot.app.domain.model.MissedPolicy
import com.lifepilot.app.domain.model.Occurrence
import com.lifepilot.app.domain.model.OccurrenceStatus
import com.lifepilot.app.domain.model.Priority
import com.lifepilot.app.domain.model.RecurrenceRule
import com.lifepilot.app.domain.model.ReminderConfig
import com.lifepilot.app.domain.model.ReminderEventType
import com.lifepilot.app.domain.model.ReminderLogEntry
import com.lifepilot.app.domain.model.Task
import com.lifepilot.app.domain.recurrence.RecurrenceEngine
import com.lifepilot.app.domain.reminder.ReminderDecision
import com.lifepilot.app.domain.reminder.ReminderPolicy
import com.lifepilot.app.domain.repository.CategoryRepository
import com.lifepilot.app.domain.repository.SettingsRepository
import com.lifepilot.app.domain.repository.TaskRepository
import com.lifepilot.app.domain.scheduling.AlarmKey
import com.lifepilot.app.domain.scheduling.AlarmScheduler
import com.lifepilot.app.domain.scheduling.ReminderNotifier
import com.lifepilot.app.domain.time.TimeProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** User-editable fields of a task, as produced by the editor or quick capture. */
data class TaskDraft(
    val title: String,
    val description: String = "",
    val categoryId: Long? = null,
    val priority: Priority = Priority.MEDIUM,
    val tags: List<String> = emptyList(),
    val estimatedMinutes: Int? = null,
    val dueDate: LocalDate,
    val dueMinute: Int? = null,
    val startDate: LocalDate? = null,
    val startMinute: Int? = null,
    val reminder: ReminderConfig = ReminderConfig(),
    /** For recurring tasks; its startDate is forced to [dueDate]. */
    val recurrence: RecurrenceRule? = null,
) {
    fun validate(): List<String> = buildList {
        if (title.isBlank()) add("Title is required")
        if (title.length > 500) add("Title is too long")
        if (dueMinute != null && dueMinute !in 0 until 1440) add("Invalid due time")
        if (reminder.intervalMinutes !in ReminderConfig.MIN_INTERVAL..ReminderConfig.MAX_INTERVAL) add("Reminder interval must be 1–1440 minutes")
        if (reminder.maxPerDay != null && reminder.maxPerDay < 1) add("Maximum reminders must be at least 1")
        if (recurrence?.endDate != null && recurrence.endDate.isBefore(dueDate)) add("Repeat end date is before the start date")
        if (estimatedMinutes != null && estimatedMinutes < 0) add("Invalid duration")
    }
}

enum class ActionSource { APP, NOTIFICATION }

/**
 * Orchestrates every state change that affects reminders. All mutations go through a single
 * mutex so that a notification action and a UI tap processed at the same time cannot interleave
 * (both run in the app process), and the database update + alarm update happen as one unit.
 *
 * Alarm scheduling is derived from persisted state by [ReminderPolicy], so any path (boot,
 * time-zone change, app start, periodic safety net) can call [reconcileAll] safely and repeatedly.
 */
class TaskService(
    private val tasks: TaskRepository,
    private val categories: CategoryRepository,
    private val settings: SettingsRepository,
    private val scheduler: AlarmScheduler,
    private val notifier: ReminderNotifier,
    private val time: TimeProvider,
) {
    private val mutex = Mutex()

    /** How far back a long-unopened recurring task is back-filled with (missed) occurrences. */
    private val maxCatchUpDays = 31L

    // ---------------------------------------------------------------- task CRUD

    suspend fun createTask(draft: TaskDraft): Long = mutex.withLock {
        val errors = draft.validate()
        require(errors.isEmpty()) { errors.joinToString() }
        val now = time.now()
        val task = draft.toTask(id = 0, createdAt = now, updatedAt = now)
        val id = tasks.inTransaction {
            val id = tasks.insertTask(task)
            if (task.recurrence == null) {
                tasks.insertOccurrence(Occurrence.new(id, draft.dueDate, draft.dueMinute, now))
            } else {
                materializeLocked(task.copy(id = id))
            }
            id
        }
        syncTaskLocked(id)
        id
    }

    suspend fun updateTask(taskId: Long, draft: TaskDraft) = mutex.withLock {
        val errors = draft.validate()
        require(errors.isEmpty()) { errors.joinToString() }
        val old = tasks.getTask(taskId) ?: return@withLock
        val now = time.now()
        val updated = draft.toTask(id = taskId, createdAt = old.createdAt, updatedAt = now)
        val today = time.today()
        tasks.inTransaction {
            tasks.updateTask(updated)
            val occs = tasks.occurrencesForTask(taskId)
            if (old.recurrence == null && updated.recurrence == null) {
                val occ = occs.firstOrNull { it.status == OccurrenceStatus.PENDING } ?: occs.maxByOrNull { it.seriesDate }
                if (occ == null) {
                    tasks.insertOccurrence(Occurrence.new(taskId, draft.dueDate, draft.dueMinute, now))
                } else if (occ.dueDate != draft.dueDate || occ.dueMinute != draft.dueMinute) {
                    // Rescheduled through the editor: reset reminder progress so the new time starts fresh.
                    tasks.updateOccurrence(
                        occ.copy(
                            dueDate = draft.dueDate, dueMinute = draft.dueMinute,
                            seriesDate = if (occ.status == OccurrenceStatus.PENDING) draft.dueDate else occ.seriesDate,
                            rescheduleCount = occ.rescheduleCount + 1, lastReminderAt = null, snoozedUntil = null,
                            remindersOnCountDate = 0, reminderCountDate = null, updatedAt = now,
                        )
                    )
                    tasks.addLog(log(occ, ReminderEventType.RESCHEDULED, "${occ.dueDate} → ${draft.dueDate}"))
                }
            } else {
                // The series definition changed: drop future pending occurrences and regenerate.
                // Completed/missed history is preserved.
                val stale = occs.filter { it.status == OccurrenceStatus.PENDING && !it.seriesDate.isBefore(today) }
                stale.forEach { cancelOccurrenceAlarm(it.id) }
                tasks.deleteOccurrences(stale.map { it.id })
                if (updated.recurrence == null) {
                    tasks.insertOccurrence(Occurrence.new(taskId, draft.dueDate, draft.dueMinute, now))
                } else {
                    materializeLocked(updated)
                }
            }
        }
        syncTaskLocked(taskId)
    }

    suspend fun deleteTask(taskId: Long) = mutex.withLock {
        // Cancel alarms and notifications first so nothing fires for a deleted task.
        tasks.occurrencesForTask(taskId).forEach { cancelOccurrenceAlarm(it.id) }
        tasks.deleteTask(taskId)
    }

    suspend fun deleteTasks(taskIds: Collection<Long>) = taskIds.distinct().forEach { deleteTask(it) }

    suspend fun duplicateTask(taskId: Long): Long? {
        val task = tasks.getTask(taskId) ?: return null
        val occ = tasks.occurrencesForTask(taskId).let { list ->
            list.firstOrNull { it.status == OccurrenceStatus.PENDING } ?: list.maxByOrNull { it.seriesDate }
        }
        val date = occ?.dueDate?.takeIf { !it.isBefore(time.today()) } ?: time.today()
        return createTask(
            TaskDraft(
                title = "${task.title} (copy)".take(500), description = task.description, categoryId = task.categoryId,
                priority = task.priority, tags = task.tags, estimatedMinutes = task.estimatedMinutes,
                dueDate = date, dueMinute = occ?.dueMinute ?: task.dueMinute, startDate = task.startDate,
                startMinute = task.startMinute, reminder = if (task.dueMinute == null && occ?.dueMinute == null) task.reminder.copy(enabled = false) else task.reminder,
                recurrence = task.recurrence?.copy(startDate = date),
            )
        )
    }

    suspend fun setTaskRemindersEnabled(taskId: Long, enabled: Boolean) = mutex.withLock {
        val task = tasks.getTask(taskId) ?: return@withLock
        tasks.updateTask(task.copy(reminder = task.reminder.copy(enabled = enabled), updatedAt = time.now()))
        syncTaskLocked(taskId)
    }

    // ---------------------------------------------------------------- occurrence actions

    /** Returns true if this call completed the occurrence (false if it was already done). */
    suspend fun completeOccurrence(occurrenceId: Long, source: ActionSource = ActionSource.APP): Boolean = mutex.withLock {
        val now = time.now()
        val completed = tasks.completeIfPending(occurrenceId, now)
        // Cancel regardless: if it was already completed elsewhere, make sure nothing is left behind.
        cancelOccurrenceAlarm(occurrenceId)
        if (!completed) return@withLock false
        val occ = tasks.getOccurrence(occurrenceId) ?: return@withLock true
        tasks.addLog(log(occ, ReminderEventType.COMPLETED, source.name))
        val task = tasks.getTask(occ.taskId)
        if (task?.recurrence != null) {
            // Only this occurrence is completed; make sure the next one exists and is scheduled.
            materializeLocked(task)
            syncTaskLocked(task.id)
        }
        true
    }

    suspend fun completeOccurrences(ids: Collection<Long>) = ids.forEach { completeOccurrence(it) }

    suspend fun reopenOccurrence(occurrenceId: Long) = mutex.withLock {
        val occ = tasks.getOccurrence(occurrenceId) ?: return@withLock
        if (occ.status == OccurrenceStatus.PENDING) return@withLock
        val reopened = occ.copy(
            status = OccurrenceStatus.PENDING, completedAt = null, snoozedUntil = null, lastReminderAt = null,
            remindersOnCountDate = 0, reminderCountDate = null, updatedAt = time.now(),
        )
        tasks.updateOccurrence(reopened)
        tasks.addLog(log(occ, ReminderEventType.REOPENED, null))
        scheduleLocked(reopened)
    }

    suspend fun snoozeOccurrence(occurrenceId: Long, minutes: Int? = null, source: ActionSource = ActionSource.APP) = mutex.withLock {
        val occ = tasks.getOccurrence(occurrenceId) ?: return@withLock
        notifier.cancelTaskReminder(occurrenceId)
        if (occ.status != OccurrenceStatus.PENDING) return@withLock // snoozing never changes completion
        val mins = minutes ?: settings.current().defaultSnoozeMinutes
        val now = time.now()
        val snoozed = occ.copy(
            snoozedUntil = ReminderPolicy.snoozeUntil(now, mins),
            snoozeCount = occ.snoozeCount + 1,
            updatedAt = now,
        )
        tasks.updateOccurrence(snoozed)
        tasks.addLog(log(occ, ReminderEventType.SNOOZED, "$mins min via ${source.name.lowercase()}"))
        scheduleLocked(snoozed)
    }

    suspend fun rescheduleOccurrence(occurrenceId: Long, newDate: LocalDate, newMinute: Int?) = mutex.withLock {
        val occ = tasks.getOccurrence(occurrenceId) ?: return@withLock
        val now = time.now()
        val task = tasks.getTask(occ.taskId)
        val moved = occ.copy(
            dueDate = newDate, dueMinute = newMinute,
            status = OccurrenceStatus.PENDING, completedAt = null,
            rescheduleCount = occ.rescheduleCount + 1, lastReminderAt = null, snoozedUntil = null,
            remindersOnCountDate = 0, reminderCountDate = null, updatedAt = now,
        )
        tasks.updateOccurrence(moved)
        if (task != null && task.recurrence == null && task.dueMinute != newMinute) {
            tasks.updateTask(task.copy(dueMinute = newMinute, updatedAt = now))
        }
        tasks.addLog(log(occ, ReminderEventType.RESCHEDULED, "${occ.dueDate} → $newDate"))
        notifier.cancelTaskReminder(occurrenceId)
        // Replaces the alarm under the same key, so the stale one can never fire.
        scheduleLocked(moved)
    }

    suspend fun skipOccurrence(occurrenceId: Long) = mutex.withLock {
        val occ = tasks.getOccurrence(occurrenceId) ?: return@withLock
        if (occ.status != OccurrenceStatus.PENDING) return@withLock
        val now = time.now()
        tasks.updateOccurrence(occ.copy(status = OccurrenceStatus.SKIPPED, nextReminderAt = null, snoozedUntil = null, updatedAt = now))
        tasks.addLog(log(occ, ReminderEventType.SKIPPED, null))
        cancelOccurrenceAlarm(occurrenceId)
        tasks.getTask(occ.taskId)?.takeIf { it.recurrence != null }?.let {
            materializeLocked(it)
            syncTaskLocked(it.id)
        }
    }

    // ---------------------------------------------------------------- alarm entry points

    /**
     * Called by the alarm receiver. The decision is recomputed from persisted state rather than
     * trusting the alarm: a stale alarm (task completed, rescheduled, quiet hours changed, master
     * switch off, clock moved) is simply re-planned instead of producing a wrong notification.
     */
    suspend fun onReminderAlarm(occurrenceId: Long) = mutex.withLock {
        val occ = tasks.getOccurrence(occurrenceId)
        if (occ == null) {
            scheduler.cancel(AlarmKey.OccurrenceReminder(occurrenceId))
            return@withLock
        }
        val task = tasks.getTask(occ.taskId)
        if (task == null || occ.status != OccurrenceStatus.PENDING) {
            cancelOccurrenceAlarm(occurrenceId)
            return@withLock
        }
        val now = time.now()
        val s = settings.current()
        when (val d = ReminderPolicy.next(task.reminder, occ, s.quietHours, s.remindersEnabled, now, time.zone())) {
            is ReminderDecision.None -> {
                if (occ.nextReminderAt != null) tasks.updateOccurrence(occ.copy(nextReminderAt = null))
                scheduler.cancel(AlarmKey.OccurrenceReminder(occurrenceId))
            }
            is ReminderDecision.Schedule -> {
                if (d.at.isAfter(now.plus(ReminderPolicy.FIRE_TOLERANCE))) {
                    // Too early for the current plan (e.g. snoozed again, clock changed): re-arm.
                    scheduleLocked(occ)
                } else {
                    val (day, count) = ReminderPolicy.countAfterFiring(occ, now, time.zone())
                    val fired = occ.copy(
                        lastReminderAt = now, snoozedUntil = null, reminderCountDate = day,
                        remindersOnCountDate = count, totalReminders = occ.totalReminders + 1,
                        nextReminderAt = null, updatedAt = now,
                    )
                    tasks.updateOccurrence(fired)
                    tasks.addLog(log(occ, ReminderEventType.FIRED, null))
                    val categoryName = task.categoryId?.let { id -> categories.getCategories().firstOrNull { it.id == id }?.name }
                    notifier.showTaskReminder(task, fired, categoryName)
                    scheduleLocked(fired)
                }
            }
        }
    }

    /**
     * Rebuilds every alarm from the database. Safe to call any number of times: alarms are keyed by
     * occurrence id, so re-scheduling replaces rather than duplicates. Called on app start, boot,
     * package update, time / timezone change, exact-alarm permission change and by a periodic worker.
     */
    suspend fun reconcileAll() = mutex.withLock {
        materializeAllLocked()
        val pending = tasks.pendingOccurrences()
        val pendingIds = pending.map { it.id }.toSet()
        // Clear alarms recorded for occurrences that are no longer pending.
        tasks.occurrencesWithScheduledReminder().filter { it.id !in pendingIds }.forEach {
            tasks.updateOccurrence(it.copy(nextReminderAt = null))
            scheduler.cancel(AlarmKey.OccurrenceReminder(it.id))
        }
        pending.forEach { scheduleLocked(it) }
    }

    /** Generates occurrences for recurring tasks up to today (plus the next upcoming one). */
    suspend fun materializeAll() = mutex.withLock { materializeAllLocked() }

    // ---------------------------------------------------------------- internals (mutex held)

    private suspend fun materializeAllLocked() {
        tasks.getRecurringTasks().forEach { task ->
            val created = tasks.inTransaction { materializeLocked(task) }
            if (created) syncTaskLocked(task.id)
        }
    }

    /**
     * Ensures occurrences exist for every series date up to today and for the first date after
     * today. Inserts are idempotent thanks to the unique (taskId, seriesDate) index, so running
     * this twice (duplicate worker, app restart) never creates duplicates.
     * Returns true if anything changed.
     */
    private suspend fun materializeLocked(task: Task): Boolean {
        val rule = task.recurrence ?: return false
        val today = time.today()
        val now = time.now()
        val existing = tasks.occurrencesForTask(task.id)
        val lastSeries = existing.maxOfOrNull { it.seriesDate }
        var cursor: LocalDate = lastSeries?.plusDays(1) ?: rule.startDate
        val earliest = today.minusDays(maxCatchUpDays)
        if (cursor.isBefore(earliest)) cursor = earliest
        var changed = false
        // Only one upcoming (after today) occurrence is kept ahead at a time.
        var hasUpcoming = existing.any { it.seriesDate.isAfter(today) }
        var guard = 0
        while (guard++ < 400) {
            val date = RecurrenceEngine.nextOnOrAfter(rule, cursor) ?: break
            if (date.isAfter(today) && hasUpcoming) break
            if (tasks.insertOccurrence(Occurrence.new(task.id, date, task.dueMinute, now)) != null) changed = true
            if (date.isAfter(today)) { hasUpcoming = true; break }
            cursor = date.plusDays(1)
        }
        if (rule.missedPolicy == MissedPolicy.MARK_MISSED) {
            val all = tasks.occurrencesForTask(task.id)
            val latestStarted = all.filter { !it.seriesDate.isAfter(today) }.maxOfOrNull { it.seriesDate }
            if (latestStarted != null) {
                all.filter {
                    it.status == OccurrenceStatus.PENDING && it.dueDate.isBefore(today) && it.seriesDate.isBefore(latestStarted)
                }.forEach {
                    tasks.updateOccurrence(it.copy(status = OccurrenceStatus.MISSED, nextReminderAt = null, snoozedUntil = null, updatedAt = now))
                    tasks.addLog(log(it, ReminderEventType.MISSED, null))
                    cancelOccurrenceAlarm(it.id)
                    changed = true
                }
            }
        }
        return changed
    }

    private suspend fun syncTaskLocked(taskId: Long) {
        tasks.occurrencesForTask(taskId).forEach { occ ->
            if (occ.status == OccurrenceStatus.PENDING) scheduleLocked(occ)
            else if (occ.nextReminderAt != null) {
                tasks.updateOccurrence(occ.copy(nextReminderAt = null))
                scheduler.cancel(AlarmKey.OccurrenceReminder(occ.id))
            }
        }
    }

    private suspend fun scheduleLocked(occ: Occurrence) {
        val task = tasks.getTask(occ.taskId) ?: return
        val s = settings.current()
        val key = AlarmKey.OccurrenceReminder(occ.id)
        when (val d = ReminderPolicy.next(task.reminder, occ, s.quietHours, s.remindersEnabled, time.now(), time.zone())) {
            is ReminderDecision.Schedule -> {
                if (occ.nextReminderAt != d.at) tasks.updateOccurrence(occ.copy(nextReminderAt = d.at))
                scheduler.schedule(key, d.at)
            }
            is ReminderDecision.None -> {
                if (occ.nextReminderAt != null) tasks.updateOccurrence(occ.copy(nextReminderAt = null))
                scheduler.cancel(key)
            }
        }
    }

    private fun cancelOccurrenceAlarm(id: Long) {
        scheduler.cancel(AlarmKey.OccurrenceReminder(id))
        notifier.cancelTaskReminder(id)
    }

    private fun log(occ: Occurrence, type: ReminderEventType, detail: String?) =
        ReminderLogEntry(0, occ.id, occ.taskId, type, time.now(), detail)
}

fun TaskDraft.toTask(id: Long, createdAt: java.time.Instant, updatedAt: java.time.Instant) = Task(
    id = id,
    title = title.trim(),
    description = description.trim(),
    categoryId = categoryId,
    priority = priority,
    tags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
    estimatedMinutes = estimatedMinutes,
    startDate = startDate,
    startMinute = startMinute,
    dueMinute = dueMinute,
    reminder = if (dueMinute == null) reminder.copy(enabled = false) else reminder,
    recurrence = recurrence?.copy(startDate = dueDate),
    createdAt = createdAt,
    updatedAt = updatedAt,
)
