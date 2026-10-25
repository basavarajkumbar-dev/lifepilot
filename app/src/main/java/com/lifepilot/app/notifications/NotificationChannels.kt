package com.lifepilot.app.notifications

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager

/**
 * Notification channels. On Android 8+ sound and vibration belong to the channel and cannot be
 * changed by the app after creation, so the in-app sound/vibration toggles select one of four
 * task-reminder channels. Users can still fine-tune each channel in system settings.
 */
object NotificationChannels {
    const val GROUP_REMINDERS = "reminders"
    const val HABITS = "habit_reminders"

    fun taskChannelId(sound: Boolean, vibration: Boolean) = "task_reminders_s${if (sound) 1 else 0}_v${if (vibration) 1 else 0}"

    val ALL_TASK_CHANNELS = listOf(true to true, true to false, false to true, false to false)

    fun ensureCreated(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannelGroup(NotificationChannelGroup(GROUP_REMINDERS, "Reminders"))
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ALL_TASK_CHANNELS.forEach { (sound, vibration) ->
            val name = buildString {
                append("Task reminders")
                when {
                    sound && vibration -> {}
                    sound -> append(" (no vibration)")
                    vibration -> append(" (vibrate only)")
                    else -> append(" (silent)")
                }
            }
            val ch = NotificationChannel(taskChannelId(sound, vibration), name, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders for tasks that repeat until you complete them"
                group = GROUP_REMINDERS
                enableVibration(vibration)
                if (vibration) vibrationPattern = longArrayOf(0, 250, 150, 250)
                if (sound) setSound(defaultSound, attrs) else setSound(null, null)
                setShowBadge(true)
            }
            nm.createNotificationChannel(ch)
        }
        nm.createNotificationChannel(
            NotificationChannel(HABITS, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Gentle nudges at your preferred habit times"
                group = GROUP_REMINDERS
            }
        )
    }
}
