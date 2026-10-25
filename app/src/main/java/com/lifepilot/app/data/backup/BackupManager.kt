package com.lifepilot.app.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.lifepilot.app.BuildConfig
import com.lifepilot.app.data.database.CategoryEntity
import com.lifepilot.app.data.database.DayPlanEntity
import com.lifepilot.app.data.database.HabitEntity
import com.lifepilot.app.data.database.HabitLogEntity
import com.lifepilot.app.data.database.LifePilotDatabase
import com.lifepilot.app.data.database.OccurrenceEntity
import com.lifepilot.app.data.database.RecurrenceRuleEntity
import com.lifepilot.app.data.database.ReminderConfigEntity
import com.lifepilot.app.data.database.ReminderLogEntity
import com.lifepilot.app.data.database.TaskEntity
import com.lifepilot.app.data.database.parseInts
import com.lifepilot.app.data.database.parseLongs
import com.lifepilot.app.domain.backup.BackupData
import com.lifepilot.app.domain.backup.BackupValidation
import com.lifepilot.app.domain.backup.BackupValidator
import com.lifepilot.app.domain.backup.CategoryDto
import com.lifepilot.app.domain.backup.CsvExporter
import com.lifepilot.app.domain.backup.DayPlanDto
import com.lifepilot.app.domain.backup.HabitDto
import com.lifepilot.app.domain.backup.HabitLogDto
import com.lifepilot.app.domain.backup.OccurrenceDto
import com.lifepilot.app.domain.backup.ReminderLogDto
import com.lifepilot.app.domain.backup.TaskDto
import com.lifepilot.app.domain.repository.HabitRepository
import com.lifepilot.app.domain.repository.TaskRepository
import com.lifepilot.app.domain.scheduling.AlarmKey
import com.lifepilot.app.domain.scheduling.AlarmScheduler
import com.lifepilot.app.domain.time.TimeProvider
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.LocalDate

/** Result of reading a backup file, shown to the user before anything is replaced. */
data class BackupPreview(
    val data: BackupData?,
    val validation: BackupValidation,
) {
    val summary: String get() = data?.let {
        "${it.tasks.size} tasks, ${it.occurrences.size} task entries, ${it.habits.size} habits, ${it.habitLogs.size} habit records"
    } ?: ""
}

/**
 * Export / import of all local data. Import is a two-step flow: [preview] parses and validates
 * without touching the database; [restore] then (1) saves a safety copy of the current data to
 * app storage, (2) replaces everything inside one database transaction (all-or-nothing), and
 * (3) the caller re-plans alarms.
 */
class BackupManager(
    private val context: Context,
    private val db: LifePilotDatabase,
    private val tasks: TaskRepository,
    private val habits: HabitRepository,
    private val scheduler: AlarmScheduler,
    private val time: TimeProvider,
) {
    suspend fun exportJson(): String = BackupJson.encode(snapshot())

    suspend fun exportTasksCsv(): String = CsvExporter.tasksCsv(tasks.observeItems().first(), time.zone())

    suspend fun exportHabitsCsv(): String = CsvExporter.habitsCsv(habits.getHabits(), habits.observeLogs().first())

    fun preview(text: String): BackupPreview = try {
        val data = BackupJson.decode(text)
        BackupPreview(data, BackupValidator.validate(data))
    } catch (e: BackupFormatException) {
        BackupPreview(null, BackupValidation(listOf(e.message ?: "Unreadable file"), emptyList()))
    }

    /** Replaces all local data with a validated backup. Returns the path of the safety copy. */
    suspend fun restore(data: BackupData): File {
        val validation = BackupValidator.validate(data)
        require(validation.isValid) { validation.errors.joinToString() }
        val safety = writeSafetyCopy()
        cancelAllTaskAlarms()
        db.withTransaction {
            wipeTables()
            insertAll(data)
            LifePilotDatabase.seedCategories(db.openHelper.writableDatabase)
        }
        return safety
    }

    /** Deletes every task, habit and history record (settings are kept). */
    suspend fun clearAll() {
        writeSafetyCopy()
        cancelAllTaskAlarms()
        habits.getHabits().forEach { scheduler.cancel(AlarmKey.HabitReminder(it.id)) }
        db.withTransaction {
            wipeTables()
            LifePilotDatabase.seedCategories(db.openHelper.writableDatabase)
        }
    }

    private suspend fun cancelAllTaskAlarms() {
        tasks.occurrencesWithScheduledReminder().forEach { scheduler.cancel(AlarmKey.OccurrenceReminder(it.id)) }
        tasks.pendingOccurrences().forEach { scheduler.cancel(AlarmKey.OccurrenceReminder(it.id)) }
        habits.getHabits().forEach { scheduler.cancel(AlarmKey.HabitReminder(it.id)) }
    }

    private suspend fun writeSafetyCopy(): File {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val file = File(dir, "pre-change-${System.currentTimeMillis()}.json")
        file.writeText(exportJson())
        // Keep only the three most recent safety copies.
        dir.listFiles { f -> f.name.startsWith("pre-change-") }?.sortedByDescending { it.name }?.drop(3)?.forEach { it.delete() }
        return file
    }

    private suspend fun wipeTables() {
        db.dayPlanDao().deleteAll()
        db.reminderLogDao().deleteAll()
        db.occurrenceDao().deleteAll()
        db.taskDao().deleteAll()
        db.categoryDao().deleteAll()
        db.habitDao().deleteAll()
    }

    private suspend fun snapshot(): BackupData {
        val cats = db.categoryDao().getAll()
        val taskRows = db.taskDao().getAll()
        val occs = db.occurrenceDao().getAll()
        val habitRows = db.habitDao().getAll()
        val logs = db.habitDao().getAllLogs()
        val rlogs = db.reminderLogDao().getAll()
        val plans = db.dayPlanDao().getAll()
        return BackupData(
            schemaVersion = BackupData.CURRENT_SCHEMA_VERSION,
            exportedAtMillis = time.now().toEpochMilli(),
            appVersion = BuildConfig.VERSION_NAME,
            categories = cats.map { CategoryDto(it.id, it.name, it.colorIndex, it.iconKey, it.isBuiltIn, it.sortOrder) },
            tasks = taskRows.map { row ->
                val t = row.task
                val r = row.reminder
                val rec = row.recurrence
                TaskDto(
                    id = t.id, title = t.title, description = t.description, categoryId = t.categoryId, priority = t.priority,
                    tags = if (t.tags.isEmpty()) emptyList() else t.tags.split('\u001F'), estimatedMinutes = t.estimatedMinutes,
                    startDate = t.startDate?.let { LocalDate.ofEpochDay(it).toString() }, startMinute = t.startMinute, dueMinute = t.dueMinute,
                    reminderEnabled = r?.enabled ?: false, reminderOffsetMinutes = r?.offsetMinutes ?: 0, reminderRepeat = r?.repeatUntilComplete ?: true,
                    reminderIntervalMinutes = r?.intervalMinutes ?: 15, reminderMaxPerDay = r?.maxPerDay,
                    reminderContinueAfterDue = r?.continueAfterDue ?: true, reminderAllowOverdue = r?.allowOverdue ?: true,
                    reminderStopAfterMinutes = r?.stopAfterMinutes, reminderRespectQuiet = r?.respectQuietHours ?: true,
                    reminderQuietStart = r?.customQuietStart, reminderQuietEnd = r?.customQuietEnd,
                    recurrenceFrequency = rec?.frequency, recurrenceInterval = rec?.interval,
                    recurrenceWeekdays = rec?.let { parseInts(it.weekdays) } ?: emptyList(),
                    recurrenceMonthDays = rec?.let { parseInts(it.monthDays) } ?: emptyList(),
                    recurrenceStartDate = rec?.let { LocalDate.ofEpochDay(it.startDate).toString() },
                    recurrenceEndDate = rec?.endDate?.let { LocalDate.ofEpochDay(it).toString() },
                    recurrenceMissedPolicy = rec?.missedPolicy, createdAtMillis = t.createdAt, updatedAtMillis = t.updatedAt,
                )
            },
            occurrences = occs.map { o ->
                OccurrenceDto(
                    o.id, o.taskId, LocalDate.ofEpochDay(o.seriesDate).toString(), LocalDate.ofEpochDay(o.dueDate).toString(), o.dueMinute,
                    o.status, o.completedAt, o.snoozedUntil, o.lastReminderAt, o.remindersOnCountDate,
                    o.reminderCountDate?.let { LocalDate.ofEpochDay(it).toString() }, o.totalReminders, o.snoozeCount, o.rescheduleCount,
                    o.createdAt, o.updatedAt,
                )
            },
            habits = habitRows.map { h ->
                HabitDto(h.id, h.name, h.description, h.iconKey, h.colorIndex, h.type, h.target, h.unit, h.schedule, parseInts(h.weekdays),
                    h.timesPerWeek, h.preferredMinute, h.reminderEnabled, LocalDate.ofEpochDay(h.startDate).toString(), h.createdAt, h.archived)
            },
            habitLogs = logs.map { HabitLogDto(it.id, it.habitId, LocalDate.ofEpochDay(it.date).toString(), it.value) },
            reminderLogs = rlogs.map { ReminderLogDto(it.id, it.occurrenceId, it.taskId, it.type, it.at, it.detail) },
            dayPlans = plans.map { DayPlanDto(LocalDate.ofEpochDay(it.date).toString(), it.intention, parseLongs(it.focusOccurrenceIds)) },
        )
    }

    private suspend fun insertAll(data: BackupData) {
        val catIds = data.categories.map { it.id }.toSet()
        db.categoryDao().insertAll(data.categories.map { CategoryEntity(it.id, it.name.trim(), it.colorIndex, it.iconKey, it.isBuiltIn, it.sortOrder) })
        db.taskDao().insertAll(data.tasks.map { t ->
            TaskEntity(
                id = t.id, title = t.title.trim(), description = t.description, categoryId = t.categoryId?.takeIf { it in catIds },
                priority = t.priority, tags = t.tags.joinToString("\u001F"), estimatedMinutes = t.estimatedMinutes,
                startDate = t.startDate?.let { LocalDate.parse(it).toEpochDay() }, startMinute = t.startMinute, dueMinute = t.dueMinute,
                createdAt = t.createdAtMillis, updatedAt = t.updatedAtMillis,
            )
        })
        db.taskDao().insertReminders(data.tasks.map { t ->
            ReminderConfigEntity(t.id, t.reminderEnabled, t.reminderOffsetMinutes, t.reminderRepeat, t.reminderIntervalMinutes, t.reminderMaxPerDay,
                t.reminderContinueAfterDue, t.reminderAllowOverdue, t.reminderStopAfterMinutes, t.reminderRespectQuiet, t.reminderQuietStart, t.reminderQuietEnd)
        })
        db.taskDao().insertRecurrences(data.tasks.filter { it.recurrenceFrequency != null }.map { t ->
            RecurrenceRuleEntity(t.id, t.recurrenceFrequency!!, t.recurrenceInterval ?: 1, t.recurrenceWeekdays.sorted().joinToString(","),
                t.recurrenceMonthDays.sorted().joinToString(","), LocalDate.parse(t.recurrenceStartDate!!).toEpochDay(),
                t.recurrenceEndDate?.let { LocalDate.parse(it).toEpochDay() }, t.recurrenceMissedPolicy ?: "MARK_MISSED")
        })
        db.occurrenceDao().insertAll(data.occurrences.map { o ->
            OccurrenceEntity(
                id = o.id, taskId = o.taskId, seriesDate = LocalDate.parse(o.seriesDate).toEpochDay(), dueDate = LocalDate.parse(o.dueDate).toEpochDay(),
                dueMinute = o.dueMinute, status = o.status, completedAt = o.completedAtMillis, snoozedUntil = o.snoozedUntilMillis,
                lastReminderAt = o.lastReminderAtMillis, remindersOnCountDate = o.remindersOnCountDate,
                reminderCountDate = o.reminderCountDate?.let { LocalDate.parse(it).toEpochDay() }, totalReminders = o.totalReminders,
                snoozeCount = o.snoozeCount, rescheduleCount = o.rescheduleCount, nextReminderAt = null,
                createdAt = o.createdAtMillis, updatedAt = o.updatedAtMillis,
            )
        })
        val occIds = data.occurrences.map { it.id }.toSet()
        db.reminderLogDao().insertAll(data.reminderLogs.filter { it.occurrenceId in occIds }.map {
            ReminderLogEntity(it.id, it.occurrenceId, it.taskId, it.type, it.atMillis, it.detail)
        })
        db.habitDao().insertAll(data.habits.map { h ->
            HabitEntity(h.id, h.name.trim(), h.description, h.iconKey, h.colorIndex, h.type, h.target, h.unit, h.schedule,
                h.weekdays.sorted().joinToString(","), h.timesPerWeek, h.preferredMinute, h.reminderEnabled,
                LocalDate.parse(h.startDate).toEpochDay(), h.createdAtMillis, h.archived)
        })
        db.habitDao().insertLogs(data.habitLogs.map { HabitLogEntity(it.id, it.habitId, LocalDate.parse(it.date).toEpochDay(), it.value) })
        db.dayPlanDao().insertAll(data.dayPlans.map { DayPlanEntity(LocalDate.parse(it.date).toEpochDay(), it.intention, it.focusOccurrenceIds.joinToString(",")) })
    }
}
