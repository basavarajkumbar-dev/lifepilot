package com.lifepilot.app.domain.backup

import com.lifepilot.app.domain.model.HabitSchedule
import com.lifepilot.app.domain.model.HabitType
import com.lifepilot.app.domain.model.MissedPolicy
import com.lifepilot.app.domain.model.OccurrenceStatus
import com.lifepilot.app.domain.model.Priority
import com.lifepilot.app.domain.model.RecurrenceFrequency
import com.lifepilot.app.domain.model.ReminderEventType
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class BackupValidation(
    val errors: List<String>,
    val warnings: List<String>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Validates a decoded backup before it may replace local data. Checks schema version, value
 * ranges, enum names, date formats, unique ids and referential integrity. The import is refused
 * if any error is found, so a corrupt or foreign file can never partially overwrite user data.
 */
object BackupValidator {

    const val MAX_RECORDS = 200_000
    private const val MAX_TEXT = 10_000
    private const val MAX_ERRORS = 25

    fun validate(data: BackupData): BackupValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        fun err(msg: String) { if (errors.size < MAX_ERRORS) errors += msg }

        if (data.schemaVersion < 1 || data.schemaVersion > BackupData.CURRENT_SCHEMA_VERSION) {
            err("Unsupported backup version ${data.schemaVersion} (this app supports up to ${BackupData.CURRENT_SCHEMA_VERSION})")
            return BackupValidation(errors, warnings)
        }
        val total = data.categories.size + data.tasks.size + data.occurrences.size + data.habits.size +
            data.habitLogs.size + data.reminderLogs.size + data.dayPlans.size
        if (total > MAX_RECORDS) {
            err("Backup is too large ($total records)")
            return BackupValidation(errors, warnings)
        }

        fun checkUnique(name: String, ids: List<Long>) {
            val dupes = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            if (dupes.isNotEmpty()) err("Duplicate $name ids: ${dupes.take(5).joinToString()}")
            if (ids.any { it <= 0 }) err("$name ids must be positive")
        }
        fun validDate(s: String?): Boolean = s != null && try {
            val d = LocalDate.parse(s); d.year in 1970..2200
        } catch (_: DateTimeParseException) { false }
        fun validMinute(m: Int?) = m == null || m in 0 until 1440

        checkUnique("category", data.categories.map { it.id })
        checkUnique("task", data.tasks.map { it.id })
        checkUnique("occurrence", data.occurrences.map { it.id })
        checkUnique("habit", data.habits.map { it.id })
        checkUnique("habit log", data.habitLogs.map { it.id })

        val categoryIds = data.categories.map { it.id }.toSet()
        data.categories.forEach {
            if (it.name.isBlank() || it.name.length > 60) err("Category ${it.id} has an invalid name")
        }

        val taskIds = data.tasks.map { it.id }.toSet()
        data.tasks.forEach { t ->
            val label = "Task ${t.id}"
            if (t.title.isBlank()) err("$label has an empty title")
            if (t.title.length > 500 || t.description.length > MAX_TEXT) err("$label has text that is too long")
            if (Priority.fromName(t.priority) == null) err("$label has unknown priority '${t.priority}'")
            if (t.categoryId != null && t.categoryId !in categoryIds) warnings += "$label refers to a missing category; it will be uncategorized"
            if (!validMinute(t.dueMinute) || !validMinute(t.startMinute)) err("$label has an invalid time")
            if (t.startDate != null && !validDate(t.startDate)) err("$label has an invalid start date")
            if (t.reminderIntervalMinutes !in 1..1440) err("$label has an invalid reminder interval")
            if (t.reminderOffsetMinutes !in 0..(7 * 1440)) err("$label has an invalid reminder offset")
            if (t.reminderMaxPerDay != null && t.reminderMaxPerDay !in 1..1440) err("$label has an invalid reminder limit")
            if (!validMinute(t.reminderQuietStart) || !validMinute(t.reminderQuietEnd)) err("$label has invalid quiet hours")
            if (t.estimatedMinutes != null && t.estimatedMinutes !in 0..(30 * 1440)) err("$label has an invalid duration")
            if (t.recurrenceFrequency != null) {
                if (RecurrenceFrequency.entries.none { it.name == t.recurrenceFrequency }) err("$label has unknown repeat type")
                if ((t.recurrenceInterval ?: 0) !in 1..1000) err("$label has an invalid repeat interval")
                if (!validDate(t.recurrenceStartDate)) err("$label has an invalid repeat start date")
                if (t.recurrenceEndDate != null && !validDate(t.recurrenceEndDate)) err("$label has an invalid repeat end date")
                if (t.recurrenceWeekdays.any { it !in 1..7 }) err("$label has invalid weekdays")
                if (t.recurrenceMonthDays.any { it != -1 && it !in 1..31 }) err("$label has invalid month days")
                if (t.recurrenceMissedPolicy != null && MissedPolicy.entries.none { it.name == t.recurrenceMissedPolicy }) err("$label has unknown missed policy")
            }
        }

        val occurrenceIds = data.occurrences.map { it.id }.toSet()
        val seriesKeys = HashSet<Pair<Long, String>>()
        data.occurrences.forEach { o ->
            val label = "Occurrence ${o.id}"
            if (o.taskId !in taskIds) err("$label refers to missing task ${o.taskId}")
            if (OccurrenceStatus.entries.none { it.name == o.status }) err("$label has unknown status '${o.status}'")
            if (!validDate(o.seriesDate) || !validDate(o.dueDate)) err("$label has an invalid date")
            if (o.reminderCountDate != null && !validDate(o.reminderCountDate)) err("$label has an invalid reminder date")
            if (!validMinute(o.dueMinute)) err("$label has an invalid time")
            if (o.remindersOnCountDate < 0 || o.totalReminders < 0 || o.snoozeCount < 0 || o.rescheduleCount < 0) err("$label has negative counters")
            if (o.status == OccurrenceStatus.COMPLETED.name && o.completedAtMillis == null) warnings += "$label is completed without a completion time"
            if (!seriesKeys.add(o.taskId to o.seriesDate)) err("$label duplicates another occurrence of task ${o.taskId} on ${o.seriesDate}")
        }

        val habitIds = data.habits.map { it.id }.toSet()
        data.habits.forEach { h ->
            val label = "Habit ${h.id}"
            if (h.name.isBlank() || h.name.length > 200) err("$label has an invalid name")
            if (HabitType.entries.none { it.name == h.type }) err("$label has unknown type")
            if (HabitSchedule.entries.none { it.name == h.schedule }) err("$label has unknown schedule")
            if (!(h.target > 0 && h.target.isFinite() && h.target <= 1_000_000)) err("$label has an invalid target")
            if (h.timesPerWeek !in 1..7) err("$label has an invalid weekly target")
            if (h.weekdays.any { it !in 1..7 }) err("$label has invalid weekdays")
            if (!validMinute(h.preferredMinute)) err("$label has an invalid time")
            if (!validDate(h.startDate)) err("$label has an invalid start date")
        }
        val logKeys = HashSet<Pair<Long, String>>()
        data.habitLogs.forEach { l ->
            if (l.habitId !in habitIds) err("Habit log ${l.id} refers to missing habit ${l.habitId}")
            if (!validDate(l.date)) err("Habit log ${l.id} has an invalid date")
            if (!(l.value >= 0 && l.value.isFinite())) err("Habit log ${l.id} has an invalid value")
            if (!logKeys.add(l.habitId to l.date)) err("Habit log ${l.id} duplicates another entry for ${l.date}")
        }
        data.reminderLogs.forEach { r ->
            if (ReminderEventType.entries.none { it.name == r.type }) err("Reminder history ${r.id} has unknown type")
            if (r.occurrenceId !in occurrenceIds) warnings += "Some reminder history refers to removed occurrences and will be skipped"
        }
        data.dayPlans.forEach { p ->
            if (!validDate(p.date)) err("Day plan has an invalid date '${p.date}'")
            if (p.intention.length > MAX_TEXT) err("Day plan ${p.date} text is too long")
        }
        return BackupValidation(errors, warnings.distinct())
    }
}
