package com.lifepilot.app.domain.scheduling

import com.lifepilot.app.domain.model.Habit
import com.lifepilot.app.domain.model.Occurrence
import com.lifepilot.app.domain.model.Task
import java.time.Instant

/**
 * Identity of a system alarm. Each key maps to exactly one PendingIntent, so scheduling the same
 * key again replaces the previous alarm instead of adding a second one.
 */
sealed interface AlarmKey {
    data class OccurrenceReminder(val occurrenceId: Long) : AlarmKey
    data class HabitReminder(val habitId: Long) : AlarmKey
    data object TestReminder : AlarmKey
}

interface AlarmScheduler {
    /** Schedules or replaces the alarm for [key]. */
    fun schedule(key: AlarmKey, at: Instant)
    fun cancel(key: AlarmKey)
    /** True when the OS currently allows exact alarms for this app. */
    fun canScheduleExact(): Boolean
}

interface ReminderNotifier {
    fun showTaskReminder(task: Task, occurrence: Occurrence, categoryName: String?)
    fun cancelTaskReminder(occurrenceId: Long)
    fun showHabitReminder(habit: Habit)
    fun cancelHabitReminder(habitId: Long)
}
