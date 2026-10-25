package com.lifepilot.app.data.repository

import androidx.room.withTransaction
import com.lifepilot.app.data.database.HabitLogEntity
import com.lifepilot.app.data.database.LifePilotDatabase
import com.lifepilot.app.data.database.OccurrenceWithTask
import com.lifepilot.app.data.database.toDomain
import com.lifepilot.app.data.database.toEntity
import com.lifepilot.app.domain.model.Category
import com.lifepilot.app.domain.model.DayPlan
import com.lifepilot.app.domain.model.Habit
import com.lifepilot.app.domain.model.HabitLog
import com.lifepilot.app.domain.model.Occurrence
import com.lifepilot.app.domain.model.OccurrenceItem
import com.lifepilot.app.domain.model.ReminderLogEntry
import com.lifepilot.app.domain.model.Task
import com.lifepilot.app.domain.repository.CategoryRepository
import com.lifepilot.app.domain.repository.DayPlanRepository
import com.lifepilot.app.domain.repository.HabitRepository
import com.lifepilot.app.domain.repository.TaskRepository
import com.lifepilot.app.data.database.CategoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

class RoomTaskRepository(private val db: LifePilotDatabase) : TaskRepository {
    private val taskDao = db.taskDao()
    private val occDao = db.occurrenceDao()
    private val logDao = db.reminderLogDao()
    private val catDao = db.categoryDao()

    override suspend fun <R> inTransaction(block: suspend () -> R): R = db.withTransaction { block() }

    override suspend fun insertTask(task: Task): Long = db.withTransaction {
        val id = taskDao.insert(task.toEntity().copy(id = 0))
        taskDao.upsertReminder(task.reminder.toEntity(id))
        task.recurrence?.let { taskDao.upsertRecurrence(it.toEntity(id)) }
        id
    }

    override suspend fun updateTask(task: Task) = db.withTransaction {
        taskDao.update(task.toEntity())
        taskDao.upsertReminder(task.reminder.toEntity(task.id))
        val rule = task.recurrence
        if (rule != null) taskDao.upsertRecurrence(rule.toEntity(task.id)) else taskDao.deleteRecurrence(task.id)
    }

    override suspend fun deleteTask(taskId: Long) = taskDao.delete(taskId) // cascades to occurrences, config, logs

    override suspend fun getTask(taskId: Long): Task? = taskDao.get(taskId)?.toDomain()
    override suspend fun getAllTasks(): List<Task> = taskDao.getAll().map { it.toDomain() }
    override suspend fun getRecurringTasks(): List<Task> = taskDao.getRecurring().map { it.toDomain() }

    override suspend fun insertOccurrence(occurrence: Occurrence): Long? =
        occDao.insert(occurrence.toEntity().copy(id = 0)).takeIf { it != -1L }

    override suspend fun updateOccurrence(occurrence: Occurrence) = occDao.update(occurrence.toEntity())
    override suspend fun deleteOccurrences(ids: List<Long>) { if (ids.isNotEmpty()) occDao.delete(ids) }
    override suspend fun getOccurrence(id: Long): Occurrence? = occDao.get(id)?.toDomain()
    override suspend fun occurrencesForTask(taskId: Long): List<Occurrence> = occDao.forTask(taskId).map { it.toDomain() }
    override suspend fun pendingOccurrences(): List<Occurrence> = occDao.pending().map { it.toDomain() }
    override suspend fun occurrencesWithScheduledReminder(): List<Occurrence> = occDao.withScheduledReminder().map { it.toDomain() }
    override suspend fun completeIfPending(id: Long, at: Instant): Boolean = occDao.completeIfPending(id, at.toEpochMilli()) == 1

    override suspend fun addLog(entry: ReminderLogEntry) {
        // Skip history rows for occurrences deleted in the meantime (FK would reject them).
        if (occDao.get(entry.occurrenceId) != null) logDao.insert(entry.toEntity().copy(id = 0))
    }

    private fun OccurrenceWithTask.toItem(categories: Map<Long, Category>): OccurrenceItem? {
        val t = task ?: return null
        val domainTask = t.toDomain()
        return OccurrenceItem(occurrence.toDomain(), domainTask, domainTask.categoryId?.let { categories[it] })
    }

    override fun observeItems(): Flow<List<OccurrenceItem>> =
        combine(occDao.observeAllWithTask(), catDao.observeAll()) { rows, cats ->
            val byId = cats.associate { it.id to it.toDomain() }
            rows.mapNotNull { it.toItem(byId) }
        }

    override fun observeItem(occurrenceId: Long): Flow<OccurrenceItem?> =
        combine(occDao.observeWithTask(occurrenceId), catDao.observeAll()) { row, cats ->
            row?.toItem(cats.associate { it.id to it.toDomain() })
        }.distinctUntilChanged()

    override fun observeTask(taskId: Long): Flow<Task?> = taskDao.observe(taskId).map { it?.toDomain() }
    override fun observeLogsForTask(taskId: Long): Flow<List<ReminderLogEntry>> = logDao.observeForTask(taskId).map { l -> l.map { it.toDomain() } }
    override fun observeLogsSince(since: Instant): Flow<List<ReminderLogEntry>> = logDao.observeSince(since.toEpochMilli()).map { l -> l.map { it.toDomain() } }
}

class RoomCategoryRepository(private val db: LifePilotDatabase) : CategoryRepository {
    private val dao = db.categoryDao()
    override fun observeCategories(): Flow<List<Category>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    override suspend fun getCategories(): List<Category> = dao.getAll().map { it.toDomain() }

    override suspend fun addCategory(name: String, colorIndex: Int, iconKey: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 60) { "Invalid category name" }
        return dao.insert(CategoryEntity(name = trimmed, colorIndex = colorIndex, iconKey = iconKey, isBuiltIn = false, sortOrder = dao.maxSortOrder() + 1))
    }

    override suspend fun updateCategory(category: Category) {
        require(category.name.isNotBlank()) { "Invalid category name" }
        dao.update(category.copy(name = category.name.trim()).toEntity())
    }

    override suspend fun deleteCategory(categoryId: Long) = db.withTransaction {
        val cat = dao.get(categoryId) ?: return@withTransaction
        if (cat.isBuiltIn) return@withTransaction
        val fallback = dao.get(LifePilotDatabase.OTHER_CATEGORY_ID)?.id
        db.taskDao().moveCategory(categoryId, fallback)
        dao.deleteCustom(categoryId)
        Unit
    }
}

class RoomHabitRepository(private val db: LifePilotDatabase) : HabitRepository {
    private val dao = db.habitDao()
    override suspend fun <R> inTransaction(block: suspend () -> R): R = db.withTransaction { block() }
    override fun observeHabits(): Flow<List<Habit>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    override fun observeHabit(id: Long): Flow<Habit?> = dao.observe(id).map { it?.toDomain() }
    override fun observeLogs(): Flow<List<HabitLog>> = dao.observeLogs().map { l -> l.map { it.toDomain() } }
    override fun observeLogsForHabit(habitId: Long): Flow<List<HabitLog>> = dao.observeLogsFor(habitId).map { l -> l.map { it.toDomain() } }
    override suspend fun getHabit(id: Long): Habit? = dao.get(id)?.toDomain()
    override suspend fun getHabits(): List<Habit> = dao.getAll().map { it.toDomain() }
    override suspend fun upsertHabit(habit: Habit): Long =
        if (habit.id == 0L) dao.insert(habit.toEntity()) else { dao.update(habit.toEntity()); habit.id }
    override suspend fun deleteHabit(id: Long) = dao.delete(id)
    override suspend fun getLog(habitId: Long, date: LocalDate): HabitLog? = dao.getLog(habitId, date.toEpochDay())?.toDomain()
    override suspend fun setLogValue(habitId: Long, date: LocalDate, value: Double) = db.withTransaction {
        if (dao.updateLogValue(habitId, date.toEpochDay(), value) == 0) {
            dao.insertLog(HabitLogEntity(habitId = habitId, date = date.toEpochDay(), value = value))
        }
        Unit
    }
    override suspend fun deleteLog(habitId: Long, date: LocalDate) = dao.deleteLog(habitId, date.toEpochDay())
    override suspend fun logsForHabit(habitId: Long): List<HabitLog> = dao.logsFor(habitId).map { it.toDomain() }
}

class RoomDayPlanRepository(db: LifePilotDatabase) : DayPlanRepository {
    private val dao = db.dayPlanDao()
    override fun observePlan(date: LocalDate): Flow<DayPlan?> = dao.observe(date.toEpochDay()).map { it?.toDomain() }
    override suspend fun savePlan(plan: DayPlan) = dao.upsert(plan.toEntity())
}
