package com.lifepilot.app

import android.content.Context
import com.lifepilot.app.data.backup.BackupManager
import com.lifepilot.app.data.database.LifePilotDatabase
import com.lifepilot.app.data.repository.RoomCategoryRepository
import com.lifepilot.app.data.repository.RoomDayPlanRepository
import com.lifepilot.app.data.repository.RoomHabitRepository
import com.lifepilot.app.data.repository.RoomTaskRepository
import com.lifepilot.app.data.settings.DataStoreSettingsRepository
import com.lifepilot.app.domain.repository.CategoryRepository
import com.lifepilot.app.domain.repository.DayPlanRepository
import com.lifepilot.app.domain.repository.HabitRepository
import com.lifepilot.app.domain.repository.SettingsRepository
import com.lifepilot.app.domain.repository.TaskRepository
import com.lifepilot.app.domain.scheduling.AlarmScheduler
import com.lifepilot.app.domain.service.HabitService
import com.lifepilot.app.domain.service.TaskService
import com.lifepilot.app.domain.time.SystemTimeProvider
import com.lifepilot.app.domain.time.TimeProvider
import com.lifepilot.app.notifications.AndroidReminderNotifier
import com.lifepilot.app.reminder.ReliabilityChecker
import com.lifepilot.app.scheduling.AndroidAlarmScheduler

/**
 * Manual dependency injection: one place that wires the object graph. Kept deliberately simple
 * (no DI framework) — the graph is small and this keeps startup fast.
 */
class AppContainer(context: Context, databaseName: String? = LifePilotDatabase.NAME) {
    private val appContext = context.applicationContext

    val time: TimeProvider = SystemTimeProvider
    val database: LifePilotDatabase = LifePilotDatabase.build(appContext, databaseName)
    val settings: SettingsRepository = DataStoreSettingsRepository(appContext)
    val tasks: TaskRepository = RoomTaskRepository(database)
    val categories: CategoryRepository = RoomCategoryRepository(database)
    val habits: HabitRepository = RoomHabitRepository(database)
    val dayPlans: DayPlanRepository = RoomDayPlanRepository(database)
    val scheduler: AlarmScheduler = AndroidAlarmScheduler(appContext)
    val notifier = AndroidReminderNotifier(appContext, settings)
    val reliability = ReliabilityChecker(appContext)

    val taskService = TaskService(tasks, categories, settings, scheduler, notifier, time)
    val habitService = HabitService(habits, settings, scheduler, notifier, time)
    val backupManager = BackupManager(appContext, database, tasks, habits, scheduler, time)

    /** Rebuilds all occurrence and habit alarms from the database (idempotent). */
    suspend fun reconcileAll() {
        taskService.reconcileAll()
        habitService.reconcileAll()
    }
}
