package com.lifepilot.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.lifepilot.app.domain.scheduling.AlarmKey
import com.lifepilot.app.domain.scheduling.AlarmScheduler
import java.time.Instant

/**
 * AlarmManager-backed scheduler.
 *
 * Identity: every [AlarmKey] maps to an explicit broadcast Intent with a unique data URI
 * (lifepilot://alarm/task/42). PendingIntents are compared with Intent.filterEquals, which
 * includes the data URI, so re-scheduling a key with FLAG_UPDATE_CURRENT *replaces* the existing
 * alarm. Duplicates are impossible by construction, and cancel() targets exactly one alarm.
 *
 * Timing: when the app may use exact alarms (always before Android 12; on 12+ when the user has
 * granted "Alarms & reminders"), setExactAndAllowWhileIdle is used so reminders fire on time even
 * in Doze. Otherwise setAndAllowWhileIdle is used: the system may delay delivery (typically by
 * minutes), which the Reminder Reliability screen explains. Alarms do not survive reboot; the boot
 * receiver re-plans them from the database.
 */
class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager?.canScheduleExactAlarms() == true

    override fun schedule(key: AlarmKey, at: Instant) {
        val am = alarmManager ?: return
        val pi = pendingIntent(key, create = true) ?: return
        val triggerAt = at.toEpochMilli()
        try {
            if (canScheduleExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (_: SecurityException) {
            // Exact-alarm access revoked between the check and the call: fall back to inexact.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    override fun cancel(key: AlarmKey) {
        val pi = pendingIntent(key, create = false) ?: return
        alarmManager?.cancel(pi)
        pi.cancel()
    }

    private fun pendingIntent(key: AlarmKey, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ReminderAlarmReceiver.ACTION_ALARM)
            .setData(uriFor(key))
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    companion object {
        fun uriFor(key: AlarmKey): Uri = when (key) {
            is AlarmKey.OccurrenceReminder -> Uri.parse("lifepilot://alarm/task/${key.occurrenceId}")
            is AlarmKey.HabitReminder -> Uri.parse("lifepilot://alarm/habit/${key.habitId}")
            AlarmKey.TestReminder -> Uri.parse("lifepilot://alarm/test/0")
        }

        fun keyFor(uri: Uri?): AlarmKey? {
            val segments = uri?.pathSegments ?: return null
            if (uri.host != "alarm" || segments.size != 2) return null
            val id = segments[1].toLongOrNull() ?: return null
            return when (segments[0]) {
                "task" -> AlarmKey.OccurrenceReminder(id)
                "habit" -> AlarmKey.HabitReminder(id)
                "test" -> AlarmKey.TestReminder
                else -> null
            }
        }
    }
}
