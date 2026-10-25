package com.lifepilot.app.domain.backup

/**
 * Backup DTOs. They mirror the database tables with primitive types (enums as strings, dates as
 * ISO strings / epoch millis) so that untrusted files can be fully validated before anything
 * touches the database.
 */
data class BackupData(
    val schemaVersion: Int,
    val exportedAtMillis: Long,
    val appVersion: String,
    val categories: List<CategoryDto>,
    val tasks: List<TaskDto>,
    val occurrences: List<OccurrenceDto>,
    val habits: List<HabitDto>,
    val habitLogs: List<HabitLogDto>,
    val reminderLogs: List<ReminderLogDto>,
    val dayPlans: List<DayPlanDto>,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val FORMAT_ID = "lifepilot-backup"
    }
}

data class CategoryDto(val id: Long, val name: String, val colorIndex: Int, val iconKey: String, val isBuiltIn: Boolean, val sortOrder: Int)

data class TaskDto(
    val id: Long,
    val title: String,
    val description: String,
    val categoryId: Long?,
    val priority: String,
    val tags: List<String>,
    val estimatedMinutes: Int?,
    val startDate: String?,
    val startMinute: Int?,
    val dueMinute: Int?,
    val reminderEnabled: Boolean,
    val reminderOffsetMinutes: Int,
    val reminderRepeat: Boolean,
    val reminderIntervalMinutes: Int,
    val reminderMaxPerDay: Int?,
    val reminderContinueAfterDue: Boolean,
    val reminderAllowOverdue: Boolean,
    val reminderStopAfterMinutes: Int?,
    val reminderRespectQuiet: Boolean,
    val reminderQuietStart: Int?,
    val reminderQuietEnd: Int?,
    val recurrenceFrequency: String?,
    val recurrenceInterval: Int?,
    val recurrenceWeekdays: List<Int>,
    val recurrenceMonthDays: List<Int>,
    val recurrenceStartDate: String?,
    val recurrenceEndDate: String?,
    val recurrenceMissedPolicy: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class OccurrenceDto(
    val id: Long,
    val taskId: Long,
    val seriesDate: String,
    val dueDate: String,
    val dueMinute: Int?,
    val status: String,
    val completedAtMillis: Long?,
    val snoozedUntilMillis: Long?,
    val lastReminderAtMillis: Long?,
    val remindersOnCountDate: Int,
    val reminderCountDate: String?,
    val totalReminders: Int,
    val snoozeCount: Int,
    val rescheduleCount: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class HabitDto(
    val id: Long,
    val name: String,
    val description: String,
    val iconKey: String,
    val colorIndex: Int,
    val type: String,
    val target: Double,
    val unit: String,
    val schedule: String,
    val weekdays: List<Int>,
    val timesPerWeek: Int,
    val preferredMinute: Int?,
    val reminderEnabled: Boolean,
    val startDate: String,
    val createdAtMillis: Long,
    val archived: Boolean,
)

data class HabitLogDto(val id: Long, val habitId: Long, val date: String, val value: Double)

data class ReminderLogDto(val id: Long, val occurrenceId: Long, val taskId: Long, val type: String, val atMillis: Long, val detail: String?)

data class DayPlanDto(val date: String, val intention: String, val focusOccurrenceIds: List<Long>)
