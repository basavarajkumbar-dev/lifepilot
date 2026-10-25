package com.lifepilot.app.data.backup

import com.lifepilot.app.domain.backup.BackupData
import com.lifepilot.app.domain.backup.CategoryDto
import com.lifepilot.app.domain.backup.DayPlanDto
import com.lifepilot.app.domain.backup.HabitDto
import com.lifepilot.app.domain.backup.HabitLogDto
import com.lifepilot.app.domain.backup.OccurrenceDto
import com.lifepilot.app.domain.backup.ReminderLogDto
import com.lifepilot.app.domain.backup.TaskDto
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class BackupFormatException(message: String) : Exception(message)

/**
 * JSON encoding of [BackupData] using the platform's org.json (no extra dependency). Decoding is
 * strict about structure and types: missing required fields or wrong types raise
 * [BackupFormatException]; semantic checks are done afterwards by BackupValidator.
 */
object BackupJson {

    fun encode(data: BackupData): String = JSONObject().apply {
        put("format", BackupData.FORMAT_ID)
        put("schemaVersion", data.schemaVersion)
        put("exportedAt", data.exportedAtMillis)
        put("appVersion", data.appVersion)
        put("categories", JSONArray(data.categories.map { c ->
            JSONObject().put("id", c.id).put("name", c.name).put("colorIndex", c.colorIndex).put("iconKey", c.iconKey)
                .put("isBuiltIn", c.isBuiltIn).put("sortOrder", c.sortOrder)
        }))
        put("tasks", JSONArray(data.tasks.map { t ->
            JSONObject().put("id", t.id).put("title", t.title).put("description", t.description).putOpt2("categoryId", t.categoryId)
                .put("priority", t.priority).put("tags", JSONArray(t.tags)).putOpt2("estimatedMinutes", t.estimatedMinutes)
                .putOpt2("startDate", t.startDate).putOpt2("startMinute", t.startMinute).putOpt2("dueMinute", t.dueMinute)
                .put("reminder", JSONObject().put("enabled", t.reminderEnabled).put("offsetMinutes", t.reminderOffsetMinutes)
                    .put("repeat", t.reminderRepeat).put("intervalMinutes", t.reminderIntervalMinutes).putOpt2("maxPerDay", t.reminderMaxPerDay)
                    .put("continueAfterDue", t.reminderContinueAfterDue).put("allowOverdue", t.reminderAllowOverdue)
                    .putOpt2("stopAfterMinutes", t.reminderStopAfterMinutes).put("respectQuietHours", t.reminderRespectQuiet)
                    .putOpt2("quietStart", t.reminderQuietStart).putOpt2("quietEnd", t.reminderQuietEnd))
                .apply {
                    if (t.recurrenceFrequency != null) put("recurrence", JSONObject().put("frequency", t.recurrenceFrequency)
                        .put("interval", t.recurrenceInterval ?: 1).put("weekdays", JSONArray(t.recurrenceWeekdays))
                        .put("monthDays", JSONArray(t.recurrenceMonthDays)).putOpt2("startDate", t.recurrenceStartDate)
                        .putOpt2("endDate", t.recurrenceEndDate).putOpt2("missedPolicy", t.recurrenceMissedPolicy))
                }
                .put("createdAt", t.createdAtMillis).put("updatedAt", t.updatedAtMillis)
        }))
        put("occurrences", JSONArray(data.occurrences.map { o ->
            JSONObject().put("id", o.id).put("taskId", o.taskId).put("seriesDate", o.seriesDate).put("dueDate", o.dueDate)
                .putOpt2("dueMinute", o.dueMinute).put("status", o.status).putOpt2("completedAt", o.completedAtMillis)
                .putOpt2("snoozedUntil", o.snoozedUntilMillis).putOpt2("lastReminderAt", o.lastReminderAtMillis)
                .put("remindersOnCountDate", o.remindersOnCountDate).putOpt2("reminderCountDate", o.reminderCountDate)
                .put("totalReminders", o.totalReminders).put("snoozeCount", o.snoozeCount).put("rescheduleCount", o.rescheduleCount)
                .put("createdAt", o.createdAtMillis).put("updatedAt", o.updatedAtMillis)
        }))
        put("habits", JSONArray(data.habits.map { h ->
            JSONObject().put("id", h.id).put("name", h.name).put("description", h.description).put("iconKey", h.iconKey)
                .put("colorIndex", h.colorIndex).put("type", h.type).put("target", h.target).put("unit", h.unit)
                .put("schedule", h.schedule).put("weekdays", JSONArray(h.weekdays)).put("timesPerWeek", h.timesPerWeek)
                .putOpt2("preferredMinute", h.preferredMinute).put("reminderEnabled", h.reminderEnabled)
                .put("startDate", h.startDate).put("createdAt", h.createdAtMillis).put("archived", h.archived)
        }))
        put("habitLogs", JSONArray(data.habitLogs.map { l ->
            JSONObject().put("id", l.id).put("habitId", l.habitId).put("date", l.date).put("value", l.value)
        }))
        put("reminderLogs", JSONArray(data.reminderLogs.map { r ->
            JSONObject().put("id", r.id).put("occurrenceId", r.occurrenceId).put("taskId", r.taskId).put("type", r.type)
                .put("at", r.atMillis).putOpt2("detail", r.detail)
        }))
        put("dayPlans", JSONArray(data.dayPlans.map { p ->
            JSONObject().put("date", p.date).put("intention", p.intention).put("focus", JSONArray(p.focusOccurrenceIds))
        }))
    }.toString(2)

    fun decode(text: String): BackupData {
        try {
            val root = JSONObject(text)
            if (root.optString("format") != BackupData.FORMAT_ID) throw BackupFormatException("This file is not a LifePilot backup")
            return BackupData(
                schemaVersion = root.getInt("schemaVersion"),
                exportedAtMillis = root.optLong("exportedAt", 0),
                appVersion = root.optString("appVersion", "unknown"),
                categories = root.arr("categories").objects { c ->
                    CategoryDto(c.getLong("id"), c.getString("name"), c.getInt("colorIndex"), c.getString("iconKey"), c.getBoolean("isBuiltIn"), c.getInt("sortOrder"))
                },
                tasks = root.arr("tasks").objects { t ->
                    val r = t.getJSONObject("reminder")
                    val rec = t.optJSONObject("recurrence")
                    TaskDto(
                        id = t.getLong("id"), title = t.getString("title"), description = t.optString("description", ""),
                        categoryId = t.longOrNull("categoryId"), priority = t.getString("priority"),
                        tags = t.optJSONArray("tags")?.strings() ?: emptyList(), estimatedMinutes = t.intOrNull("estimatedMinutes"),
                        startDate = t.stringOrNull("startDate"), startMinute = t.intOrNull("startMinute"), dueMinute = t.intOrNull("dueMinute"),
                        reminderEnabled = r.getBoolean("enabled"), reminderOffsetMinutes = r.getInt("offsetMinutes"),
                        reminderRepeat = r.getBoolean("repeat"), reminderIntervalMinutes = r.getInt("intervalMinutes"),
                        reminderMaxPerDay = r.intOrNull("maxPerDay"), reminderContinueAfterDue = r.getBoolean("continueAfterDue"),
                        reminderAllowOverdue = r.getBoolean("allowOverdue"), reminderStopAfterMinutes = r.intOrNull("stopAfterMinutes"),
                        reminderRespectQuiet = r.getBoolean("respectQuietHours"), reminderQuietStart = r.intOrNull("quietStart"),
                        reminderQuietEnd = r.intOrNull("quietEnd"),
                        recurrenceFrequency = rec?.getString("frequency"), recurrenceInterval = rec?.getInt("interval"),
                        recurrenceWeekdays = rec?.optJSONArray("weekdays")?.ints() ?: emptyList(),
                        recurrenceMonthDays = rec?.optJSONArray("monthDays")?.ints() ?: emptyList(),
                        recurrenceStartDate = rec?.stringOrNull("startDate"), recurrenceEndDate = rec?.stringOrNull("endDate"),
                        recurrenceMissedPolicy = rec?.stringOrNull("missedPolicy"),
                        createdAtMillis = t.getLong("createdAt"), updatedAtMillis = t.getLong("updatedAt"),
                    )
                },
                occurrences = root.arr("occurrences").objects { o ->
                    OccurrenceDto(
                        o.getLong("id"), o.getLong("taskId"), o.getString("seriesDate"), o.getString("dueDate"), o.intOrNull("dueMinute"),
                        o.getString("status"), o.longOrNull("completedAt"), o.longOrNull("snoozedUntil"), o.longOrNull("lastReminderAt"),
                        o.optInt("remindersOnCountDate", 0), o.stringOrNull("reminderCountDate"), o.optInt("totalReminders", 0),
                        o.optInt("snoozeCount", 0), o.optInt("rescheduleCount", 0), o.getLong("createdAt"), o.getLong("updatedAt"),
                    )
                },
                habits = root.arr("habits").objects { h ->
                    HabitDto(
                        h.getLong("id"), h.getString("name"), h.optString("description", ""), h.getString("iconKey"), h.getInt("colorIndex"),
                        h.getString("type"), h.getDouble("target"), h.optString("unit", ""), h.getString("schedule"),
                        h.optJSONArray("weekdays")?.ints() ?: emptyList(), h.getInt("timesPerWeek"), h.intOrNull("preferredMinute"),
                        h.getBoolean("reminderEnabled"), h.getString("startDate"), h.getLong("createdAt"), h.getBoolean("archived"),
                    )
                },
                habitLogs = root.arr("habitLogs").objects { l -> HabitLogDto(l.getLong("id"), l.getLong("habitId"), l.getString("date"), l.getDouble("value")) },
                reminderLogs = root.arr("reminderLogs").objects { r ->
                    ReminderLogDto(r.getLong("id"), r.getLong("occurrenceId"), r.getLong("taskId"), r.getString("type"), r.getLong("at"), r.stringOrNull("detail"))
                },
                dayPlans = root.arr("dayPlans").objects { p -> DayPlanDto(p.getString("date"), p.optString("intention", ""), p.optJSONArray("focus")?.longs() ?: emptyList()) },
            )
        } catch (e: JSONException) {
            throw BackupFormatException("The backup file is damaged or incomplete (${e.message?.take(120)})")
        }
    }

    // ---- helpers

    private fun JSONObject.putOpt2(key: String, value: Any?): JSONObject = if (value == null) put(key, JSONObject.NULL) else put(key, value)
    private fun JSONObject.arr(key: String): JSONArray = optJSONArray(key) ?: JSONArray()
    private fun JSONObject.longOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)
    private fun JSONObject.intOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)
    private fun JSONObject.stringOrNull(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)
    private fun <T> JSONArray.objects(map: (JSONObject) -> T): List<T> = (0 until length()).map { map(getJSONObject(it)) }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }
    private fun JSONArray.longs(): List<Long> = (0 until length()).map { getLong(it) }
}
