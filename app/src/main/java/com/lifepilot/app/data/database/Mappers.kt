package com.lifepilot.app.data.database

import com.lifepilot.app.domain.model.Category
import com.lifepilot.app.domain.model.DayPlan
import com.lifepilot.app.domain.model.Habit
import com.lifepilot.app.domain.model.HabitLog
import com.lifepilot.app.domain.model.HabitSchedule
import com.lifepilot.app.domain.model.HabitType
import com.lifepilot.app.domain.model.MissedPolicy
import com.lifepilot.app.domain.model.Occurrence
import com.lifepilot.app.domain.model.OccurrenceStatus
import com.lifepilot.app.domain.model.Priority
import com.lifepilot.app.domain.model.RecurrenceFrequency
import com.lifepilot.app.domain.model.RecurrenceRule
import com.lifepilot.app.domain.model.ReminderConfig
import com.lifepilot.app.domain.model.ReminderEventType
import com.lifepilot.app.domain.model.ReminderLogEntry
import com.lifepilot.app.domain.model.Task
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

private const val TAG_SEPARATOR = '\u001F'

internal fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)
internal fun Long.toDate(): LocalDate = LocalDate.ofEpochDay(this)
internal fun LocalDate.toEpoch(): Long = toEpochDay()

internal fun parseInts(s: String): List<Int> = s.split(',').mapNotNull { it.trim().toIntOrNull() }
internal fun parseLongs(s: String): List<Long> = s.split(',').mapNotNull { it.trim().toLongOrNull() }

fun CategoryEntity.toDomain() = Category(id, name, colorIndex, iconKey, isBuiltIn, sortOrder)
fun Category.toEntity() = CategoryEntity(id, name, colorIndex, iconKey, isBuiltIn, sortOrder)

fun TaskWithDetails.toDomain(): Task = Task(
    id = task.id,
    title = task.title,
    description = task.description,
    categoryId = task.categoryId,
    priority = Priority.fromName(task.priority) ?: Priority.MEDIUM,
    tags = if (task.tags.isEmpty()) emptyList() else task.tags.split(TAG_SEPARATOR),
    estimatedMinutes = task.estimatedMinutes,
    startDate = task.startDate?.toDate(),
    startMinute = task.startMinute,
    dueMinute = task.dueMinute,
    reminder = reminder?.toDomain() ?: ReminderConfig(enabled = false),
    recurrence = recurrence?.toDomain(),
    createdAt = task.createdAt.toInstant(),
    updatedAt = task.updatedAt.toInstant(),
)

fun ReminderConfigEntity.toDomain() = ReminderConfig(
    enabled, offsetMinutes, repeatUntilComplete, intervalMinutes, maxPerDay, continueAfterDue, allowOverdue,
    stopAfterMinutes, respectQuietHours, customQuietStart, customQuietEnd,
)

fun RecurrenceRuleEntity.toDomain() = RecurrenceRule(
    frequency = RecurrenceFrequency.entries.firstOrNull { it.name == frequency } ?: RecurrenceFrequency.DAILY,
    interval = interval.coerceAtLeast(1),
    weekdays = parseInts(weekdays).filter { it in 1..7 }.map { DayOfWeek.of(it) }.toSet(),
    monthDays = parseInts(monthDays).filter { it == -1 || it in 1..31 }.toSet(),
    startDate = startDate.toDate(),
    endDate = endDate?.toDate(),
    missedPolicy = MissedPolicy.entries.firstOrNull { it.name == missedPolicy } ?: MissedPolicy.MARK_MISSED,
)

fun Task.toEntity() = TaskEntity(
    id = id, title = title, description = description, categoryId = categoryId, priority = priority.name,
    tags = tags.joinToString(TAG_SEPARATOR.toString()), estimatedMinutes = estimatedMinutes,
    startDate = startDate?.toEpoch(), startMinute = startMinute, dueMinute = dueMinute,
    createdAt = createdAt.toEpochMilli(), updatedAt = updatedAt.toEpochMilli(),
)

fun ReminderConfig.toEntity(taskId: Long) = ReminderConfigEntity(
    taskId, enabled, offsetMinutes, repeatUntilComplete, intervalMinutes, maxPerDay, continueAfterDue, allowOverdue,
    stopAfterMinutes, respectQuietHours, customQuietStart, customQuietEnd,
)

fun RecurrenceRule.toEntity(taskId: Long) = RecurrenceRuleEntity(
    taskId = taskId, frequency = frequency.name, interval = interval,
    weekdays = weekdays.map { it.value }.sorted().joinToString(","),
    monthDays = monthDays.sorted().joinToString(","),
    startDate = startDate.toEpoch(), endDate = endDate?.toEpoch(), missedPolicy = missedPolicy.name,
)

fun OccurrenceEntity.toDomain() = Occurrence(
    id = id, taskId = taskId, seriesDate = seriesDate.toDate(), dueDate = dueDate.toDate(), dueMinute = dueMinute,
    status = OccurrenceStatus.entries.firstOrNull { it.name == status } ?: OccurrenceStatus.PENDING,
    completedAt = completedAt?.toInstant(), snoozedUntil = snoozedUntil?.toInstant(), lastReminderAt = lastReminderAt?.toInstant(),
    remindersOnCountDate = remindersOnCountDate, reminderCountDate = reminderCountDate?.toDate(), totalReminders = totalReminders,
    snoozeCount = snoozeCount, rescheduleCount = rescheduleCount, nextReminderAt = nextReminderAt?.toInstant(),
    createdAt = createdAt.toInstant(), updatedAt = updatedAt.toInstant(),
)

fun Occurrence.toEntity() = OccurrenceEntity(
    id = id, taskId = taskId, seriesDate = seriesDate.toEpoch(), dueDate = dueDate.toEpoch(), dueMinute = dueMinute,
    status = status.name, completedAt = completedAt?.toEpochMilli(), snoozedUntil = snoozedUntil?.toEpochMilli(),
    lastReminderAt = lastReminderAt?.toEpochMilli(), remindersOnCountDate = remindersOnCountDate,
    reminderCountDate = reminderCountDate?.toEpoch(), totalReminders = totalReminders, snoozeCount = snoozeCount,
    rescheduleCount = rescheduleCount, nextReminderAt = nextReminderAt?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(), updatedAt = updatedAt.toEpochMilli(),
)

fun ReminderLogEntity.toDomain() = ReminderLogEntry(
    id, occurrenceId, taskId, ReminderEventType.entries.firstOrNull { it.name == type } ?: ReminderEventType.FIRED, at.toInstant(), detail,
)

fun ReminderLogEntry.toEntity() = ReminderLogEntity(id, occurrenceId, taskId, type.name, at.toEpochMilli(), detail)

fun HabitEntity.toDomain() = Habit(
    id = id, name = name, description = description, iconKey = iconKey, colorIndex = colorIndex,
    type = HabitType.entries.firstOrNull { it.name == type } ?: HabitType.CHECK,
    target = target, unit = unit,
    schedule = HabitSchedule.entries.firstOrNull { it.name == schedule } ?: HabitSchedule.DAILY,
    weekdays = parseInts(weekdays).filter { it in 1..7 }.map { DayOfWeek.of(it) }.toSet(),
    timesPerWeek = timesPerWeek, preferredMinute = preferredMinute, reminderEnabled = reminderEnabled,
    startDate = startDate.toDate(), createdAt = createdAt.toInstant(), archived = archived,
)

fun Habit.toEntity() = HabitEntity(
    id = id, name = name, description = description, iconKey = iconKey, colorIndex = colorIndex, type = type.name,
    target = target, unit = unit, schedule = schedule.name, weekdays = weekdays.map { it.value }.sorted().joinToString(","),
    timesPerWeek = timesPerWeek, preferredMinute = preferredMinute, reminderEnabled = reminderEnabled,
    startDate = startDate.toEpoch(), createdAt = createdAt.toEpochMilli(), archived = archived,
)

fun HabitLogEntity.toDomain() = HabitLog(id, habitId, date.toDate(), value)

fun DayPlanEntity.toDomain() = DayPlan(date.toDate(), intention, parseLongs(focusOccurrenceIds))
fun DayPlan.toEntity() = DayPlanEntity(date.toEpoch(), intention, focusOccurrenceIds.joinToString(","))
