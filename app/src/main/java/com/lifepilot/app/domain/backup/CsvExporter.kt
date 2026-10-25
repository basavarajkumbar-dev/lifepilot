package com.lifepilot.app.domain.backup

import com.lifepilot.app.domain.model.Habit
import com.lifepilot.app.domain.model.HabitLog
import com.lifepilot.app.domain.model.OccurrenceItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** RFC 4180 CSV export of task occurrences and habit records. */
object CsvExporter {

    fun escape(value: String?): String {
        val v = value ?: ""
        // Guard against spreadsheet formula injection for cells starting with = + - @.
        val safe = if (v.isNotEmpty() && v[0] in "=+-@") "'$v" else v
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private fun row(vararg cells: String?) = cells.joinToString(",") { escape(it) }

    private fun fmt(i: Instant?, zone: ZoneId): String = i?.atZone(zone)?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) ?: ""
    private fun hm(m: Int?) = m?.let { "%02d:%02d".format(it / 60, it % 60) } ?: ""

    fun tasksCsv(items: List<OccurrenceItem>, zone: ZoneId): String = buildString {
        appendLine(row("task_id", "occurrence_id", "title", "description", "category", "priority", "tags", "due_date", "due_time",
            "status", "completed_at", "recurring", "reminders_sent", "snoozes", "reschedules", "created_at"))
        items.sortedWith(compareBy({ it.occurrence.dueDate }, { it.occurrence.dueMinute ?: -1 })).forEach { i ->
            val o = i.occurrence
            appendLine(row(
                i.task.id.toString(), o.id.toString(), i.task.title, i.task.description, i.category?.name, i.task.priority.label,
                i.task.tags.joinToString(";"), o.dueDate.toString(), hm(o.dueMinute), o.status.name, fmt(o.completedAt, zone),
                if (i.task.recurrence != null) "yes" else "no", o.totalReminders.toString(), o.snoozeCount.toString(),
                o.rescheduleCount.toString(), fmt(i.task.createdAt, zone),
            ))
        }
    }

    fun habitsCsv(habits: List<Habit>, logs: List<HabitLog>): String = buildString {
        appendLine(row("habit_id", "habit", "date", "value", "target", "unit", "met"))
        val byId = habits.associateBy { it.id }
        logs.sortedWith(compareBy({ it.habitId }, { it.date })).forEach { l ->
            val h = byId[l.habitId] ?: return@forEach
            appendLine(row(h.id.toString(), h.name, l.date.toString(), trim(l.value), trim(h.target), h.unit, if (l.value >= h.target) "yes" else "no"))
        }
    }

    private fun trim(d: Double): String = if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()
}
