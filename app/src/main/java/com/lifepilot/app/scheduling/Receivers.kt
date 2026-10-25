package com.lifepilot.app.scheduling

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lifepilot.app.LifePilotApp
import com.lifepilot.app.domain.scheduling.AlarmKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs suspend work from a BroadcastReceiver. goAsync() keeps the process alive until finish()
 * is called; the timeout keeps us inside the system's receiver budget.
 */
internal fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            withTimeoutOrNull(9_000) { block() }
        } finally {
            pending.finish()
        }
    }
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Delivered by AlarmManager for task, habit and test reminders. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM) return
        val key = AndroidAlarmScheduler.keyFor(intent.data) ?: return
        val container = (context.applicationContext as LifePilotApp).container
        runAsync {
            when (key) {
                is AlarmKey.OccurrenceReminder -> container.taskService.onReminderAlarm(key.occurrenceId)
                is AlarmKey.HabitReminder -> container.habitService.onHabitAlarm(key.habitId)
                AlarmKey.TestReminder -> container.notifier.showTestNotification()
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "com.lifepilot.app.action.REMINDER_ALARM"
    }
}

/**
 * Re-plans every alarm after events that invalidate them: reboot (alarms are cleared), app update,
 * manual clock change, timezone change (local due times map to new instants) and exact-alarm
 * permission changes (switch between exact and inexact delivery).
 */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                val container = (context.applicationContext as LifePilotApp).container
                runAsync {
                    try {
                        container.reconcileAll()
                    } catch (e: Exception) {
                        // Fall back to WorkManager, which retries with backoff.
                        ReconcileWorker.enqueueNow(context)
                    }
                }
            }
        }
    }
}
