package com.lifepilot.app.domain.repository

import com.lifepilot.app.domain.model.AppSettings
import com.lifepilot.app.domain.model.Category
import com.lifepilot.app.domain.model.DayPlan
import com.lifepilot.app.domain.model.Habit
import com.lifepilot.app.domain.model.HabitLog
import com.lifepilot.app.domain.model.Occurrence
import com.lifepilot.app.domain.model.OccurrenceItem
import com.lifepilot.app.domain.model.ReminderLogEntry
import com.lifepilot.app.domain.model.Task
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * Persistence boundary for tasks, occurrences and reminder history. Implemented by Room in
 * production and by an in-memory fake in unit tests. The UI and the reminder engine never touch
 * the database directly.
 */
interface TaskRepository {
    /** Runs [block] atomically (Room: withTransaction). */
    suspend fun <R> inTransaction(block: suspend () -> R): R

    suspend fun insertTask(task: Task): Long
    suspend fun updateTask(task: Task)
    suspend fun deleteTask(taskId: Long)
    suspend fun getTask(taskId: Long): Task?
    suspend fun getAllTasks(): List<Task>
    suspend fun getRecurringTasks(): List<Task>

    /** Inserts unless (taskId, seriesDate) already exists; returns the new id or null on conflict. */
    suspend fun insertOccurrence(occurrence: Occurrence): Long?
    suspend fun updateOccurrence(occurrence: Occurrence)
    suspend fun deleteOccurrences(ids: List<Long>)
    suspend fun getOccurrence(id: Long): Occurrence?
    suspend fun occurrencesForTask(taskId: Long): List<Occurrence>
    suspend fun pendingOccurrences(): List<Occurrence>
    suspend fun occurrencesWithScheduledReminder(): List<Occurrence>

    /**
     * Atomically flips PENDING -> COMPLETED. Returns false when the occurrence was not pending,
     * which is how concurrent taps (notification + UI) are made safe: only one wins.
     */
    suspend fun completeIfPending(id: Long, at: Instant): Boolean

    suspend fun addLog(entry: ReminderLogEntry)

    fun observeItems(): Flow<List<OccurrenceItem>>
    fun observeItem(occurrenceId: Long): Flow<OccurrenceItem?>
    fun observeTask(taskId: Long): Flow<Task?>
    fun observeLogsForTask(taskId: Long): Flow<List<ReminderLogEntry>>
    fun observeLogsSince(since: Instant): Flow<List<ReminderLogEntry>>
}

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    suspend fun getCategories(): List<Category>
    suspend fun addCategory(name: String, colorIndex: Int, iconKey: String): Long
    suspend fun updateCategory(category: Category)
    /** Deletes a custom category, moving its tasks to the built-in "Other" category. */
    suspend fun deleteCategory(categoryId: Long)
}

interface HabitRepository {
    suspend fun <R> inTransaction(block: suspend () -> R): R
    fun observeHabits(): Flow<List<Habit>>
    fun observeHabit(id: Long): Flow<Habit?>
    fun observeLogs(): Flow<List<HabitLog>>
    fun observeLogsForHabit(habitId: Long): Flow<List<HabitLog>>
    suspend fun getHabit(id: Long): Habit?
    suspend fun getHabits(): List<Habit>
    suspend fun upsertHabit(habit: Habit): Long
    suspend fun deleteHabit(id: Long)
    suspend fun getLog(habitId: Long, date: LocalDate): HabitLog?
    suspend fun setLogValue(habitId: Long, date: LocalDate, value: Double)
    suspend fun deleteLog(habitId: Long, date: LocalDate)
    suspend fun logsForHabit(habitId: Long): List<HabitLog>
}

interface DayPlanRepository {
    fun observePlan(date: LocalDate): Flow<DayPlan?>
    suspend fun savePlan(plan: DayPlan)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
