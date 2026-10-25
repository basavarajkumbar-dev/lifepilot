package com.lifepilot.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lifepilot.app.LifePilotApp
import com.lifepilot.app.domain.service.ActionSource

/**
 * Handles Complete / Snooze / Mark-done taps on notifications. Works with the app closed: the
 * system starts the process, the action is applied through the same TaskService used by the UI
 * (serialized by its mutex and the conditional SQL update), and the result is persisted before
 * the receiver finishes.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id <= 0) return
        val container = (context.applicationContext as LifePilotApp).container
        when (intent.action) {
            ACTION_COMPLETE -> runAsync { container.taskService.completeOccurrence(id, ActionSource.NOTIFICATION) }
            ACTION_SNOOZE -> runAsync { container.taskService.snoozeOccurrence(id, null, ActionSource.NOTIFICATION) }
            ACTION_HABIT_DONE -> runAsync {
                container.habitService.toggle(id)
                container.notifier.cancelHabitReminder(id)
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.lifepilot.app.action.COMPLETE"
        const val ACTION_SNOOZE = "com.lifepilot.app.action.SNOOZE"
        const val ACTION_HABIT_DONE = "com.lifepilot.app.action.HABIT_DONE"
        const val EXTRA_ID = "id"
    }
}
