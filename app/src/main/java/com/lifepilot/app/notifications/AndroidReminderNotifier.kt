package com.lifepilot.app.notifications

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lifepilot.app.MainActivity
import com.lifepilot.app.R
import com.lifepilot.app.domain.model.Habit
import com.lifepilot.app.domain.model.Occurrence
import com.lifepilot.app.domain.model.Priority
import com.lifepilot.app.domain.model.Task
import com.lifepilot.app.domain.repository.SettingsRepository
import com.lifepilot.app.domain.scheduling.ReminderNotifier
import com.lifepilot.app.scheduling.NotificationActionReceiver
import kotlinx.coroutines.runBlocking
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Posts actionable notifications. Each task occurrence uses the tag "task" + its id, so posting
 * again replaces the existing notification rather than stacking duplicates.
 *
 * Dismissing a notification has no effect on the task (no delete intent is registered), which is
 * exactly the required behaviour: only the explicit Complete action completes it.
 */
class AndroidReminderNotifier(
    private val context: Context,
    private val settings: SettingsRepository,
) : ReminderNotifier {

    private val nm = NotificationManagerCompat.from(context)

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return nm.areNotificationsEnabled()
    }

    override fun showTaskReminder(task: Task, occurrence: Occurrence, categoryName: String?) {
        if (!canPost()) return
        // Settings are read from DataStore; this runs on a background dispatcher (receiver coroutine).
        val s = runBlocking { settings.current() }
        val channel = NotificationChannels.taskChannelId(s.notificationSound, s.notificationVibration)
        val id = notificationId(occurrence.id)
        val snoozeMinutes = s.defaultSnoozeMinutes

        val due = occurrence.dueMinute?.let {
            val t = LocalTime.of(it / 60, it % 60)
            "Due " + t.format(DateTimeFormatter.ofPattern(if (s.use24HourTime) "HH:mm" else "h:mm a"))
        }
        val detail = listOfNotNull(due, categoryName, task.priority.takeIf { it >= Priority.HIGH }?.let { "${it.label} priority" })
            .joinToString(" · ")
        val reminderNo = if (occurrence.totalReminders > 1) "Reminder ${occurrence.totalReminders} — still pending" else "Still pending until you complete it"

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_lifepilot)
            .setContentTitle(task.title)
            .setContentText(detail.ifEmpty { reminderNo })
            .setSubText(if (detail.isNotEmpty()) reminderNo else null)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFF7C6CF2.toInt())
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            // Lock screen shows a generic message; the task title stays private.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_stat_lifepilot)
                    .setContentTitle("Task reminder")
                    .setContentText("You have a pending task")
                    .build()
            )
            .setContentIntent(openIntent(occurrence.id))
            .addAction(R.drawable.ic_action_check, "Complete", actionIntent(NotificationActionReceiver.ACTION_COMPLETE, occurrence.id))
            .addAction(R.drawable.ic_action_snooze, "Snooze $snoozeMinutes min", actionIntent(NotificationActionReceiver.ACTION_SNOOZE, occurrence.id))
            .addAction(R.drawable.ic_action_open, "Open", openIntent(occurrence.id))
        if (task.description.isNotBlank()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(listOf(detail, task.description.take(300)).filter { it.isNotBlank() }.joinToString("\n")))
        }
        post(TAG_TASK, id, builder.build())
    }

    override fun cancelTaskReminder(occurrenceId: Long) = nm.cancel(TAG_TASK, notificationId(occurrenceId))

    override fun showHabitReminder(habit: Habit) {
        if (!canPost()) return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("lifepilot://habit/${habit.id}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, NotificationChannels.HABITS)
            .setSmallIcon(R.drawable.ic_stat_lifepilot)
            .setContentTitle(habit.name)
            .setContentText("A good moment for your habit")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(0xFF7C6CF2.toInt())
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .apply {
                if (habit.type == com.lifepilot.app.domain.model.HabitType.CHECK) {
                    addAction(R.drawable.ic_action_check, "Mark done", actionIntent(NotificationActionReceiver.ACTION_HABIT_DONE, habit.id))
                }
            }
            .build()
        post(TAG_HABIT, notificationId(habit.id), n)
    }

    override fun cancelHabitReminder(habitId: Long) = nm.cancel(TAG_HABIT, notificationId(habitId))

    fun showTestNotification() {
        if (!canPost()) return
        val s = runBlocking { settings.current() }
        val n = NotificationCompat.Builder(context, NotificationChannels.taskChannelId(s.notificationSound, s.notificationVibration))
            .setSmallIcon(R.drawable.ic_stat_lifepilot)
            .setContentTitle("Test reminder")
            .setContentText("Reminders are working on this device.")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFF7C6CF2.toInt())
            .setAutoCancel(true)
            .build()
        post(TAG_TEST, 1, n)
    }

    private fun post(tag: String, id: Int, notification: Notification) {
        try {
            nm.notify(tag, id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; the Reliability screen reports it.
        }
    }

    private fun openIntent(occurrenceId: Long): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("lifepilot://occurrence/$occurrenceId"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun actionIntent(action: String, id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        // The data URI makes each (action, id) pair a distinct PendingIntent.
        Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("lifepilot://action/$action/$id"))
            .putExtra(NotificationActionReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val TAG_TASK = "task"
        const val TAG_HABIT = "habit"
        const val TAG_TEST = "test"
        fun notificationId(id: Long): Int = (id xor (id ushr 32)).toInt()
    }
}
