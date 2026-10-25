package com.lifepilot.app.testing

import com.lifepilot.app.domain.model.AppSettings
import com.lifepilot.app.domain.model.Category
import com.lifepilot.app.domain.model.Habit
import com.lifepilot.app.domain.model.HabitLog
import com.lifepilot.app.domain.model.Occurrence
import com.lifepilot.app.domain.model.OccurrenceItem
import com.lifepilot.app.domain.model.OccurrenceStatus
import com.lifepilot.app.domain.model.ReminderLogEntry
import com.lifepilot.app.domain.model.Task
import com.lifepilot.app.domain.repository.CategoryRepository
import com.lifepilot.app.domain.repository.HabitRepository
import com.lifepilot.app.domain.repository.SettingsRepository
import com.lifepilot.app.domain.repository.TaskRepository
import com.lifepilot.app.domain.scheduling.AlarmKey
import com.lifepilot.app.domain.scheduling.AlarmScheduler
import com.lifepilot.app.domain.scheduling.ReminderNotifier
import com.lifepilot.app.domain.time.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class FakeTime(var current: Instant, var zoneId: ZoneId = ZoneId.of("Asia/Kolkata")) : TimeProvider {
    override fun now(): Instant = current
    override fun zone(): ZoneId = zoneId
    fun advance(d: Duration) { current = current.plus(d) }
    fun advanceMinutes(m: Long) = advance(Duration.ofMinutes(m))
    fun set(local: LocalDateTime) { current = local.atZone(zoneId).toInstant() }

    companion object {
        fun at(local: LocalDateTime, zone: ZoneId = ZoneId.of("Asia/Kolkata")) = FakeTime(local.atZone(zone).toInstant(), zone)
    }
}

/** Records alarms by key exactly like AlarmManager does with unique PendingIntents. */
class FakeAlarmScheduler : AlarmScheduler {
    val alarms = linkedMapOf<AlarmKey, Instant>()
    var scheduleCalls = 0
    var exact = true
    override fun schedule(key: AlarmKey, at: Instant) { scheduleCalls++; alarms[key] = at }
    override fun cancel(key: AlarmKey) { alarms.remove(key) }
    override fun canScheduleExact() = exact
    fun at(occurrenceId: Long): Instant? = alarms[AlarmKey.OccurrenceReminder(occurrenceId)]
}

class FakeNotifier : ReminderNotifier {
    val shown = mutableListOf<Long>()          // occurrence ids, in order
    val active = linkedSetOf<Long>()
    val habitShown = mutableListOf<Long>()
    override fun showTaskReminder(task: Task, occurrence: Occurrence, categoryName: String?) { shown += occurrence.id; active += occurrence.id }
    override fun cancelTaskReminder(occurrenceId: Long) { active -= occurrenceId }
    override fun showHabitReminder(habit: Habit) { habitShown += habit.id }
    override fun cancelHabitReminder(habitId: Long) {}
}

class FakeSettings(initial: AppSettings = AppSettings(quietHours = AppSettings().quietHours.copy(enabled = false))) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun current() = state.value
    override suspend fun update(transform: (AppSettings) -> AppSettings) { state.value = transform(state.value) }
}

class FakeCategories : CategoryRepository {
    private val state = MutableStateFlow(listOf(Category(1, "Work", 0, "work", true, 0), Category(7, "Other", 6, "other", true, 6)))
    override fun observeCategories() = state
    override suspend fun getCategories() = state.value
    override suspend fun addCategory(name: String, colorIndex: Int, iconKey: String): Long {
        val id = (state.value.maxOfOrNull { it.id } ?: 0) + 1
        state.value = state.value + Category(id, name, colorIndex, iconKey, false, id.toInt()); return id
    }
    override suspend fun updateCategory(category: Category) { state.value = state.value.map { if (it.id == category.id) category else it } }
    override suspend fun deleteCategory(categoryId: Long) { state.value = state.value.filterNot { it.id == categoryId && !it.isBuiltIn } }
}

/** In-memory TaskRepository with the same uniqueness and conditional-update semantics as Room. */
class FakeTaskRepository : TaskRepository {
    private val tasks = MutableStateFlow<Map<Long, Task>>(emptyMap())
    private val occs = MutableStateFlow<Map<Long, Occurrence>>(emptyMap())
    val logs = mutableListOf<ReminderLogEntry>()
    private var nextTaskId = 1L
    private var nextOccId = 1L

    override suspend fun <R> inTransaction(block: suspend () -> R): R = block()
    override suspend fun insertTask(task: Task): Long { val id = nextTaskId++; tasks.value = tasks.value + (id to task.copy(id = id)); return id }
    override suspend fun updateTask(task: Task) { tasks.value = tasks.value + (task.id to task) }
    override suspend fun deleteTask(taskId: Long) {
        tasks.value = tasks.value - taskId
        occs.value = occs.value.filterValues { it.taskId != taskId }
    }
    override suspend fun getTask(taskId: Long) = tasks.value[taskId]
    override suspend fun getAllTasks() = tasks.value.values.toList()
    override suspend fun getRecurringTasks() = tasks.value.values.filter { it.recurrence != null }
    override suspend fun insertOccurrence(occurrence: Occurrence): Long? {
        if (occs.value.values.any { it.taskId == occurrence.taskId && it.seriesDate == occurrence.seriesDate }) return null
        val id = nextOccId++
        occs.value = occs.value + (id to occurrence.copy(id = id)); return id
    }
    override suspend fun updateOccurrence(occurrence: Occurrence) { if (occurrence.id in occs.value) occs.value = occs.value + (occurrence.id to occurrence) }
    override suspend fun deleteOccurrences(ids: List<Long>) { occs.value = occs.value - ids.toSet() }
    override suspend fun getOccurrence(id: Long) = occs.value[id]
    override suspend fun occurrencesForTask(taskId: Long) = occs.value.values.filter { it.taskId == taskId }.sortedBy { it.seriesDate }
    override suspend fun pendingOccurrences() = occs.value.values.filter { it.status == OccurrenceStatus.PENDING }
    override suspend fun occurrencesWithScheduledReminder() = occs.value.values.filter { it.nextReminderAt != null }
    override suspend fun completeIfPending(id: Long, at: Instant): Boolean {
        val o = occs.value[id] ?: return false
        if (o.status != OccurrenceStatus.PENDING) return false
        occs.value = occs.value + (id to o.copy(status = OccurrenceStatus.COMPLETED, completedAt = at, nextReminderAt = null, snoozedUntil = null, updatedAt = at))
        return true
    }
    override suspend fun addLog(entry: ReminderLogEntry) { logs += entry.copy(id = logs.size + 1L) }
    override fun observeItems(): Flow<List<OccurrenceItem>> = occs.map { m -> m.values.mapNotNull { o -> tasks.value[o.taskId]?.let { OccurrenceItem(o, it, null) } } }
    override fun observeItem(occurrenceId: Long) = occs.map { m -> m[occurrenceId]?.let { o -> tasks.value[o.taskId]?.let { OccurrenceItem(o, it, null) } } }
    override fun observeTask(taskId: Long) = tasks.map { it[taskId] }
    override fun observeLogsForTask(taskId: Long) = MutableStateFlow(logs.filter { it.taskId == taskId })
    override fun observeLogsSince(since: Instant) = MutableStateFlow(logs.filter { !it.at.isBefore(since) })

    fun allOccurrences() = occs.value.values.sortedBy { it.id }
}

class FakeHabitRepository : HabitRepository {
    private val habits = MutableStateFlow<Map<Long, Habit>>(emptyMap())
    private val logs = MutableStateFlow<List<HabitLog>>(emptyList())
    private var nextId = 1L
    override suspend fun <R> inTransaction(block: suspend () -> R): R = block()
    override fun observeHabits() = habits.map { it.values.toList() }
    override fun observeHabit(id: Long) = habits.map { it[id] }
    override fun observeLogs() = logs
    override fun observeLogsForHabit(habitId: Long) = logs.map { l -> l.filter { it.habitId == habitId } }
    override suspend fun getHabit(id: Long) = habits.value[id]
    override suspend fun getHabits() = habits.value.values.toList()
    override suspend fun upsertHabit(habit: Habit): Long {
        val id = if (habit.id == 0L) nextId++ else habit.id
        habits.value = habits.value + (id to habit.copy(id = id)); return id
    }
    override suspend fun deleteHabit(id: Long) { habits.value = habits.value - id; logs.value = logs.value.filter { it.habitId != id } }
    override suspend fun getLog(habitId: Long, date: LocalDate) = logs.value.firstOrNull { it.habitId == habitId && it.date == date }
    override suspend fun setLogValue(habitId: Long, date: LocalDate, value: Double) {
        logs.value = logs.value.filterNot { it.habitId == habitId && it.date == date } + HabitLog(logs.value.size + 1L, habitId, date, value)
    }
    override suspend fun deleteLog(habitId: Long, date: LocalDate) { logs.value = logs.value.filterNot { it.habitId == habitId && it.date == date } }
    override suspend fun logsForHabit(habitId: Long) = logs.value.filter { it.habitId == habitId }
}
