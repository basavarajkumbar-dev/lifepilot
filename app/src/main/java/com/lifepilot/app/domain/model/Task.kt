package com.lifepilot.app.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

enum class Priority(val level: Int, val label: String) {
    LOW(0, "Low"),
    MEDIUM(1, "Medium"),
    HIGH(2, "High"),
    CRITICAL(3, "Critical");

    companion object {
        fun fromName(name: String?): Priority? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Lifecycle of a single occurrence. A one-off task has exactly one occurrence; a recurring task
 * has one occurrence per scheduled date. Completion state lives ONLY on the occurrence, so it is
 * the single source of truth for both the UI and the reminder engine.
 */
enum class OccurrenceStatus { PENDING, COMPLETED, SKIPPED, MISSED }

/** What happens to an unfinished occurrence of a recurring task once the next one arrives. */
enum class MissedPolicy(val label: String) {
    /** Leave it pending; it shows as overdue (and keeps reminding if overdue reminders are allowed). */
    KEEP_OVERDUE("Keep as overdue"),
    /** Record it as missed (visible in history and insights) and stop reminding for it. */
    MARK_MISSED("Mark as missed"),
}

enum class RecurrenceFrequency(val label: String) {
    DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly")
}

/**
 * Recurrence rule. Examples:
 *  - every day: DAILY, interval 1
 *  - every 3 days: DAILY, interval 3
 *  - Mon/Wed/Fri: WEEKLY, weekdays {MON, WED, FRI}
 *  - every Sunday: WEEKLY, weekdays {SUN}
 *  - 5th of every month: MONTHLY, monthDays {5}
 *  - last day of month: MONTHLY, monthDays {-1}
 */
data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val monthDays: Set<Int> = emptySet(),
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val missedPolicy: MissedPolicy = MissedPolicy.MARK_MISSED,
) {
    init {
        require(interval >= 1) { "interval must be >= 1" }
        require(monthDays.all { it == LAST_DAY_OF_MONTH || it in 1..31 }) { "invalid month day" }
    }

    companion object {
        const val LAST_DAY_OF_MONTH = -1
    }
}

/**
 * Per-task reminder configuration ("remind me until completed").
 *
 * @param offsetMinutes first reminder fires this many minutes before the due time (0 = at due time).
 * @param repeatUntilComplete keep reminding every [intervalMinutes] until the occurrence is completed.
 * @param maxPerDay optional cap on reminders delivered per local day; the task stays pending when reached.
 * @param continueAfterDue keep repeating after the due time has passed (on the due date).
 * @param allowOverdue keep repeating on days after the due date.
 * @param stopAfterMinutes optional hard stop: no repeats later than due + this many minutes.
 * @param respectQuietHours defer reminders that fall into quiet hours to the end of the window.
 * @param customQuietStart/customQuietEnd optional per-task quiet window (minutes of day) overriding the global one.
 */
data class ReminderConfig(
    val enabled: Boolean = true,
    val offsetMinutes: Int = 0,
    val repeatUntilComplete: Boolean = true,
    val intervalMinutes: Int = 15,
    val maxPerDay: Int? = null,
    val continueAfterDue: Boolean = true,
    val allowOverdue: Boolean = true,
    val stopAfterMinutes: Int? = null,
    val respectQuietHours: Boolean = true,
    val customQuietStart: Int? = null,
    val customQuietEnd: Int? = null,
) {
    companion object {
        val INTERVAL_PRESETS = listOf(5, 10, 15, 30, 60)
        const val MIN_INTERVAL = 1
        const val MAX_INTERVAL = 24 * 60
    }
}

data class Category(
    val id: Long,
    val name: String,
    val colorIndex: Int,
    val iconKey: String,
    val isBuiltIn: Boolean,
    val sortOrder: Int,
)

/** A task definition (one-off or the template of a recurring series). */
data class Task(
    val id: Long,
    val title: String,
    val description: String,
    val categoryId: Long?,
    val priority: Priority,
    val tags: List<String>,
    val estimatedMinutes: Int?,
    val startDate: LocalDate?,
    val startMinute: Int?,
    /** Time of day (minutes) used for each occurrence; null for all-day tasks. */
    val dueMinute: Int?,
    val reminder: ReminderConfig,
    val recurrence: RecurrenceRule?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val isRecurring: Boolean get() = recurrence != null
}

/** One scheduled instance of a task. */
data class Occurrence(
    val id: Long,
    val taskId: Long,
    /** The date this occurrence belongs to in the series (stable; unique per task). */
    val seriesDate: LocalDate,
    /** The (possibly rescheduled) due date. */
    val dueDate: LocalDate,
    val dueMinute: Int?,
    val status: OccurrenceStatus,
    val completedAt: Instant?,
    val snoozedUntil: Instant?,
    val lastReminderAt: Instant?,
    val remindersOnCountDate: Int,
    val reminderCountDate: LocalDate?,
    val totalReminders: Int,
    val snoozeCount: Int,
    val rescheduleCount: Int,
    /** Instant the engine has scheduled the next alarm for; null when none is scheduled. */
    val nextReminderAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun new(taskId: Long, date: LocalDate, dueMinute: Int?, now: Instant) = Occurrence(
            id = 0, taskId = taskId, seriesDate = date, dueDate = date, dueMinute = dueMinute,
            status = OccurrenceStatus.PENDING, completedAt = null, snoozedUntil = null,
            lastReminderAt = null, remindersOnCountDate = 0, reminderCountDate = null,
            totalReminders = 0, snoozeCount = 0, rescheduleCount = 0, nextReminderAt = null,
            createdAt = now, updatedAt = now,
        )
    }
}

/** An occurrence joined with its task and category, as consumed by UI and analytics. */
data class OccurrenceItem(
    val occurrence: Occurrence,
    val task: Task,
    val category: Category?,
)

enum class ReminderEventType { FIRED, SNOOZED, COMPLETED, REOPENED, RESCHEDULED, SKIPPED, MISSED }

data class ReminderLogEntry(
    val id: Long,
    val occurrenceId: Long,
    val taskId: Long,
    val type: ReminderEventType,
    val at: Instant,
    val detail: String?,
)

data class DayPlan(
    val date: LocalDate,
    val intention: String,
    val focusOccurrenceIds: List<Long>,
)
