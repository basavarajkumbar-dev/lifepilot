package com.lifepilot.app.reminder

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lifepilot.app.domain.model.AppSettings
import com.lifepilot.app.notifications.NotificationChannels

enum class CheckState { OK, WARNING, PROBLEM }

data class ReliabilityItem(
    val id: String,
    val title: String,
    val state: CheckState,
    val explanation: String,
    val actionLabel: String?,
    val action: (() -> Intent)?,
)

/**
 * Inspects every Android setting that can stop or delay reminders and explains it in plain
 * language. Nothing here claims delivery is guaranteed; it reports what the OS currently allows.
 */
class ReliabilityChecker(private val context: Context) {

    fun notificationPermissionGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun exactAlarmsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(android.app.AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    fun check(settings: AppSettings): List<ReliabilityItem> {
        val pkg = context.packageName
        val nm = context.getSystemService(NotificationManager::class.java)
        val items = mutableListOf<ReliabilityItem>()

        items += ReliabilityItem(
            "master", "Reminders in LifePilot",
            if (settings.remindersEnabled) CheckState.OK else CheckState.PROBLEM,
            if (settings.remindersEnabled) "The master reminder switch is on."
            else "The master switch in Settings is off, so no task or habit reminders will be shown.",
            null, null,
        )

        val permission = notificationPermissionGranted()
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        items += ReliabilityItem(
            "notifications", "Notification permission",
            if (permission && enabled) CheckState.OK else CheckState.PROBLEM,
            if (permission && enabled) "LifePilot is allowed to show notifications."
            else "Notifications are blocked for LifePilot. Reminders cannot appear until you allow them.",
            if (permission && enabled) null else "Allow notifications",
            if (permission && enabled) null else { { appNotificationSettings() } },
        )

        val channelId = NotificationChannels.taskChannelId(settings.notificationSound, settings.notificationVibration)
        val channel = nm?.getNotificationChannel(channelId)
        val channelOk = channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        val channelLow = channel != null && channel.importance in 1 until NotificationManager.IMPORTANCE_DEFAULT
        items += ReliabilityItem(
            "channel", "Task reminder channel",
            when { !channelOk -> CheckState.PROBLEM; channelLow -> CheckState.WARNING; else -> CheckState.OK },
            when {
                !channelOk -> "The \"Task reminders\" category is turned off in system settings."
                channelLow -> "Task reminders are set to a low importance, so they may arrive silently."
                else -> "Task reminders use high importance with heads-up display."
            },
            if (!channelOk || channelLow) "Open channel settings" else null,
            if (!channelOk || channelLow) { { channelSettings(channelId) } } else null,
        )

        val exact = exactAlarmsAllowed()
        items += ReliabilityItem(
            "exact", "Exact alarms (Alarms & reminders)",
            if (exact) CheckState.OK else CheckState.WARNING,
            if (exact) "Reminders are scheduled for the exact minute, even when the phone is idle."
            else "Exact alarm access is off. Reminders still arrive, but Android may delay them by several minutes. Allow \"Alarms & reminders\" for on-time reminders.",
            if (exact || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) null else "Allow exact alarms",
            if (exact || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) null else {
                { Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$pkg")) }
            },
        )

        val pm = context.getSystemService(PowerManager::class.java)
        val unrestricted = pm?.isIgnoringBatteryOptimizations(pkg) == true
        items += ReliabilityItem(
            "battery", "Battery optimization",
            if (unrestricted) CheckState.OK else CheckState.WARNING,
            if (unrestricted) "LifePilot is exempt from battery optimization."
            else "Battery optimization is on (the Android default). Exact alarms still fire in Doze, but very frequent repeats (every 5 minutes) may be spaced out by the system while the phone is idle. If reminders arrive late, set LifePilot to \"Unrestricted\".",
            if (unrestricted) null else "Battery settings",
            if (unrestricted) null else { { Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) } },
        )

        val am = context.getSystemService(ActivityManager::class.java)
        val restricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && am?.isBackgroundRestricted == true
        items += ReliabilityItem(
            "background", "Background activity",
            if (restricted) CheckState.PROBLEM else CheckState.OK,
            if (restricted) "Background activity is restricted for LifePilot. Android may block reminders until you remove the restriction."
            else "Background activity is not restricted.",
            if (restricted) "App settings" else null,
            if (restricted) { { appDetails() } } else null,
        )

        val filter = nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
        val dnd = filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        items += ReliabilityItem(
            "dnd", "Do Not Disturb",
            if (dnd) CheckState.WARNING else CheckState.OK,
            if (dnd) "Do Not Disturb is on. Reminders are still recorded and appear in the notification shade, but may not make a sound."
            else "Do Not Disturb is off.",
            null, null,
        )

        items += ReliabilityItem(
            "quiet", "Quiet hours",
            CheckState.OK,
            if (settings.quietHours.isActiveWindow) "Reminders that fall inside your quiet hours are held and delivered when quiet hours end — never dropped."
            else "Quiet hours are off.",
            null, null,
        )

        val maker = Build.MANUFACTURER.lowercase()
        if (maker in AGGRESSIVE_MANUFACTURERS) {
            items += ReliabilityItem(
                "oem", "Device-specific power saving (${Build.MANUFACTURER})",
                CheckState.WARNING,
                "Some ${Build.MANUFACTURER} phones stop apps in the background beyond standard Android rules. If reminders go missing, allow LifePilot to \"Auto-start\" / \"Run in background\" and lock it in recent apps. See dontkillmyapp.com for model-specific steps.",
                "App settings", { appDetails() },
            )
        }
        return items
    }

    private fun appNotificationSettings() = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    private fun channelSettings(id: String) = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, id)
    private fun appDetails() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    companion object {
        private val AGGRESSIVE_MANUFACTURERS = setOf("xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "realme", "vivo", "oneplus", "samsung", "meizu", "asus", "tecno", "infinix")
    }
}
