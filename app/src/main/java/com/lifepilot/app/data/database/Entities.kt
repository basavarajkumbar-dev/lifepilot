package com.lifepilot.app.data.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/*
 * Storage conventions:
 *  - Instants are stored as epoch milliseconds (UTC), so they are unaffected by timezone changes.
 *  - Calendar dates are stored as epoch days and times of day as minutes (local, "floating"), so a
 *    task due at 20:00 stays at 20:00 local time when the user travels; the reminder engine
 *    converts to an instant using the device's current zone each time it schedules.
 *  - Enums are stored by name.
 */

@Entity(tableName = "categories", indices = [Index(value = ["name"])])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorIndex: Int,
    val iconKey: String,
    val isBuiltIn: Boolean,
    val sortOrder: Int,
)

@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("categoryId"), Index("createdAt")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val categoryId: Long?,
    val priority: String,
    /** Tags joined with the unit separator (U+001F). */
    val tags: String,
    val estimatedMinutes: Int?,
    val startDate: Long?,
    val startMinute: Int?,
    val dueMinute: Int?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 1:1 with tasks. */
@Entity(
    tableName = "reminder_configs",
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
)
data class ReminderConfigEntity(
    @PrimaryKey val taskId: Long,
    val enabled: Boolean,
    val offsetMinutes: Int,
    val repeatUntilComplete: Boolean,
    val intervalMinutes: Int,
    val maxPerDay: Int?,
    val continueAfterDue: Boolean,
    val allowOverdue: Boolean,
    val stopAfterMinutes: Int?,
    val respectQuietHours: Boolean,
    val customQuietStart: Int?,
    val customQuietEnd: Int?,
)

/** 0..1 per task; present only for recurring tasks. */
@Entity(
    tableName = "recurrence_rules",
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
)
data class RecurrenceRuleEntity(
    @PrimaryKey val taskId: Long,
    val frequency: String,
    val interval: Int,
    /** ISO day numbers (1 = Monday) comma-separated. */
    val weekdays: String,
    /** Days of month comma-separated; -1 = last day. */
    val monthDays: String,
    val startDate: Long,
    val endDate: Long?,
    val missedPolicy: String,
)

@Entity(
    tableName = "occurrences",
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [
        // Guarantees a recurring series can never get two occurrences for the same date, even if
        // generation runs twice concurrently or after a restart.
        Index(value = ["taskId", "seriesDate"], unique = true),
        Index("dueDate"),
        Index("status"),
        Index("nextReminderAt"),
    ],
)
data class OccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val seriesDate: Long,
    val dueDate: Long,
    val dueMinute: Int?,
    val status: String,
    val completedAt: Long?,
    val snoozedUntil: Long?,
    val lastReminderAt: Long?,
    val remindersOnCountDate: Int,
    val reminderCountDate: Long?,
    val totalReminders: Int,
    val snoozeCount: Int,
    val rescheduleCount: Int,
    val nextReminderAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "reminder_log",
    foreignKeys = [ForeignKey(entity = OccurrenceEntity::class, parentColumns = ["id"], childColumns = ["occurrenceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("occurrenceId"), Index("taskId"), Index("at")],
)
data class ReminderLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurrenceId: Long,
    val taskId: Long,
    val type: String,
    val at: Long,
    val detail: String?,
)

@Entity(tableName = "habits", indices = [Index("archived")])
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val iconKey: String,
    val colorIndex: Int,
    val type: String,
    val target: Double,
    val unit: String,
    val schedule: String,
    val weekdays: String,
    val timesPerWeek: Int,
    val preferredMinute: Int?,
    val reminderEnabled: Boolean,
    val startDate: Long,
    val createdAt: Long,
    val archived: Boolean,
)

@Entity(
    tableName = "habit_logs",
    foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["habitId", "date"], unique = true), Index("date")],
)
data class HabitLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val date: Long,
    val value: Double,
)

@Entity(tableName = "day_plans")
data class DayPlanEntity(
    @PrimaryKey val date: Long,
    val intention: String,
    /** Occurrence ids comma-separated. */
    val focusOccurrenceIds: String,
)

// ------------------------------------------------------------------ relations

data class TaskWithDetails(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val reminder: ReminderConfigEntity?,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val recurrence: RecurrenceRuleEntity?,
)

data class OccurrenceWithTask(
    @Embedded val occurrence: OccurrenceEntity,
    @Relation(entity = TaskEntity::class, parentColumn = "taskId", entityColumn = "id")
    val task: TaskWithDetails?,
)
