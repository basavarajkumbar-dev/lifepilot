package com.lifepilot.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): CategoryEntity?

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustom(id: Long): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM categories")
    suspend fun maxSortOrder(): Int

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

@Dao
interface TaskDao {
    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    @Upsert
    suspend fun upsertReminder(config: ReminderConfigEntity)

    @Upsert
    suspend fun upsertRecurrence(rule: RecurrenceRuleEntity)

    @Insert
    suspend fun insertReminders(configs: List<ReminderConfigEntity>)

    @Insert
    suspend fun insertRecurrences(rules: List<RecurrenceRuleEntity>)

    @Query("DELETE FROM recurrence_rules WHERE taskId = :taskId")
    suspend fun deleteRecurrence(taskId: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskWithDetails?

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: Long): Flow<TaskWithDetails?>

    @Transaction
    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskWithDetails>

    @Transaction
    @Query("SELECT tasks.* FROM tasks INNER JOIN recurrence_rules ON recurrence_rules.taskId = tasks.id")
    suspend fun getRecurring(): List<TaskWithDetails>

    @Query("UPDATE tasks SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveCategory(from: Long, to: Long?)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}

@Dao
interface OccurrenceDao {
    /** Returns -1 when (taskId, seriesDate) already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(occurrence: OccurrenceEntity): Long

    @Insert
    suspend fun insertAll(occurrences: List<OccurrenceEntity>)

    @Update
    suspend fun update(occurrence: OccurrenceEntity)

    @Query("DELETE FROM occurrences WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("SELECT * FROM occurrences WHERE id = :id")
    suspend fun get(id: Long): OccurrenceEntity?

    @Query("SELECT * FROM occurrences WHERE taskId = :taskId ORDER BY seriesDate")
    suspend fun forTask(taskId: Long): List<OccurrenceEntity>

    @Query("SELECT * FROM occurrences WHERE status = 'PENDING'")
    suspend fun pending(): List<OccurrenceEntity>

    @Query("SELECT * FROM occurrences WHERE nextReminderAt IS NOT NULL")
    suspend fun withScheduledReminder(): List<OccurrenceEntity>

    /**
     * Conditional update: only one of several concurrent completion attempts can succeed, because
     * the WHERE clause re-checks the status inside SQLite's write lock.
     */
    @Query("UPDATE occurrences SET status = 'COMPLETED', completedAt = :at, nextReminderAt = NULL, snoozedUntil = NULL, updatedAt = :at WHERE id = :id AND status = 'PENDING'")
    suspend fun completeIfPending(id: Long, at: Long): Int

    @Transaction
    @Query("SELECT * FROM occurrences ORDER BY dueDate, dueMinute")
    fun observeAllWithTask(): Flow<List<OccurrenceWithTask>>

    @Transaction
    @Query("SELECT * FROM occurrences WHERE id = :id")
    fun observeWithTask(id: Long): Flow<OccurrenceWithTask?>

    @Query("SELECT * FROM occurrences")
    suspend fun getAll(): List<OccurrenceEntity>

    @Query("DELETE FROM occurrences")
    suspend fun deleteAll()
}

@Dao
interface ReminderLogDao {
    @Insert
    suspend fun insert(entry: ReminderLogEntity)

    @Insert
    suspend fun insertAll(entries: List<ReminderLogEntity>)

    @Query("SELECT * FROM reminder_log WHERE taskId = :taskId ORDER BY at DESC LIMIT 200")
    fun observeForTask(taskId: Long): Flow<List<ReminderLogEntity>>

    @Query("SELECT * FROM reminder_log WHERE at >= :since ORDER BY at DESC")
    fun observeSince(since: Long): Flow<List<ReminderLogEntity>>

    @Query("SELECT * FROM reminder_log")
    suspend fun getAll(): List<ReminderLogEntity>

    @Query("DELETE FROM reminder_log")
    suspend fun deleteAll()
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY archived, createdAt")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    fun observe(id: Long): Flow<HabitEntity?>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun get(id: Long): HabitEntity?

    @Query("SELECT * FROM habits")
    suspend fun getAll(): List<HabitEntity>

    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Insert
    suspend fun insertAll(habits: List<HabitEntity>)

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM habit_logs ORDER BY date")
    fun observeLogs(): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY date")
    fun observeLogsFor(habitId: Long): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY date")
    suspend fun logsFor(habitId: Long): List<HabitLogEntity>

    @Query("SELECT * FROM habit_logs")
    suspend fun getAllLogs(): List<HabitLogEntity>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND date = :date")
    suspend fun getLog(habitId: Long, date: Long): HabitLogEntity?

    @Insert
    suspend fun insertLog(log: HabitLogEntity): Long

    /** Used with [insertLog] inside a transaction (SQLite UPSERT syntax needs API 30; minSdk is 26). */
    @Query("UPDATE habit_logs SET value = :value WHERE habitId = :habitId AND date = :date")
    suspend fun updateLogValue(habitId: Long, date: Long, value: Double): Int

    @Insert
    suspend fun insertLogs(logs: List<HabitLogEntity>)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND date = :date")
    suspend fun deleteLog(habitId: Long, date: Long)

    @Query("DELETE FROM habits")
    suspend fun deleteAll()
}

@Dao
interface DayPlanDao {
    @Query("SELECT * FROM day_plans WHERE date = :date")
    fun observe(date: Long): Flow<DayPlanEntity?>

    @Upsert
    suspend fun upsert(plan: DayPlanEntity)

    @Query("SELECT * FROM day_plans")
    suspend fun getAll(): List<DayPlanEntity>

    @Insert
    suspend fun insertAll(plans: List<DayPlanEntity>)

    @Query("DELETE FROM day_plans")
    suspend fun deleteAll()
}
