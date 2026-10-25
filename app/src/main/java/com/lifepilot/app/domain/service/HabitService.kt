package com.lifepilot.app.domain.service

import com.lifepilot.app.domain.habit.HabitStats
import com.lifepilot.app.domain.model.Habit
import com.lifepilot.app.domain.model.HabitType
import com.lifepilot.app.domain.repository.HabitRepository
import com.lifepilot.app.domain.repository.SettingsRepository
import com.lifepilot.app.domain.scheduling.AlarmKey
import com.lifepilot.app.domain.scheduling.AlarmScheduler
import com.lifepilot.app.domain.scheduling.ReminderNotifier
import com.lifepilot.app.domain.time.TimeProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** Habit operations. Habit completion is stored separately from task completion. */
class HabitService(
    private val habits: HabitRepository,
    private val settings: SettingsRepository,
    private val scheduler: AlarmScheduler,
    private val notifier: ReminderNotifier,
    private val time: TimeProvider,
) {
    private val mutex = Mutex()

    suspend fun saveHabit(habit: Habit): Long = mutex.withLock {
        require(habit.name.isNotBlank()) { "Name is required" }
        val normalized = if (habit.type == HabitType.CHECK) habit.copy(target = 1.0, unit = "") else habit
        val id = habits.upsertHabit(normalized)
        syncLocked(normalized.copy(id = id))
        id
    }

    suspend fun deleteHabit(id: Long) = mutex.withLock {
        scheduler.cancel(AlarmKey.HabitReminder(id))
        notifier.cancelHabitReminder(id)
        habits.deleteHabit(id)
    }

    suspend fun setArchived(id: Long, archived: Boolean) = mutex.withLock {
        val h = habits.getHabit(id) ?: return@withLock
        val updated = h.copy(archived = archived)
        habits.upsertHabit(updated)
        syncLocked(updated)
    }

    /** Toggles a yes/no habit (or fills/clears a measurable habit) for [date]. */
    suspend fun toggle(habitId: Long, date: LocalDate = time.today()) = mutex.withLock {
        val habit = habits.getHabit(habitId) ?: return@withLock
        val current = habits.getLog(habitId, date)?.value
        if (HabitStats.isMet(habit, current)) habits.deleteLog(habitId, date)
        else habits.setLogValue(habitId, date, habit.target)
        if (date == time.today()) notifier.cancelHabitReminder(habitId)
    }

    /** Records partial or full progress for a measurable habit. 0 clears the entry. */
    suspend fun setProgress(habitId: Long, date: LocalDate, value: Double) = mutex.withLock {
        require(value >= 0 && value.isFinite()) { "Invalid value" }
        if (date.isAfter(time.today())) return@withLock
        if (value == 0.0) habits.deleteLog(habitId, date) else habits.setLogValue(habitId, date, value)
    }

    suspend fun onHabitAlarm(habitId: Long) = mutex.withLock {
        val habit = habits.getHabit(habitId) ?: run {
            scheduler.cancel(AlarmKey.HabitReminder(habitId)); return@withLock
        }
        val s = settings.current()
        val today = time.today()
        val done = HabitStats.isMet(habit, habits.getLog(habitId, today)?.value)
        if (s.remindersEnabled && s.habitRemindersEnabled && habit.reminderEnabled && !habit.archived &&
            HabitStats.isScheduled(habit, today) && !done
        ) {
            notifier.showHabitReminder(habit)
        }
        syncLocked(habit)
    }

    suspend fun reconcileAll() = mutex.withLock { habits.getHabits().forEach { syncLocked(it) } }

    private suspend fun syncLocked(habit: Habit) {
        val s = settings.current()
        val key = AlarmKey.HabitReminder(habit.id)
        val next = if (s.remindersEnabled && s.habitRemindersEnabled) HabitStats.nextReminder(habit, time.now(), time.zone()) else null
        if (next != null) scheduler.schedule(key, next) else scheduler.cancel(key)
    }
}
