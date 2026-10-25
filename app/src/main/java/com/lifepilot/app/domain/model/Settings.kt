package com.lifepilot.app.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class DateFormatStyle(val label: String, val pattern: String) {
    MEDIUM("Oct 25, 2026", "MMM d, yyyy"),
    DAY_MONTH("25 Oct 2026", "d MMM yyyy"),
    ISO("2026-10-25", "yyyy-MM-dd"),
    NUMERIC_DMY("25/10/2026", "dd/MM/yyyy"),
    NUMERIC_MDY("10/25/2026", "MM/dd/yyyy"),
}

/**
 * A daily window (minutes of day) during which reminders are held back. Windows may wrap past
 * midnight (e.g. 22:00 to 07:00). start == end means "no quiet window".
 */
data class QuietHours(val enabled: Boolean, val startMinute: Int, val endMinute: Int) {

    val isActiveWindow: Boolean get() = enabled && startMinute != endMinute

    fun contains(time: LocalTime): Boolean {
        if (!isActiveWindow) return false
        val m = time.hour * 60 + time.minute
        return if (startMinute < endMinute) m in startMinute until endMinute
        else m >= startMinute || m < endMinute
    }

    /**
     * Given an instant inside the window, returns the instant the window ends. DST gaps are
     * resolved by [ZonedDateTime.of], which shifts a non-existent local time forward.
     */
    fun windowEndAfter(instant: Instant, zone: ZoneId): Instant {
        val local = instant.atZone(zone)
        val m = local.hour * 60 + local.minute
        val endTime = LocalTime.of(endMinute / 60, endMinute % 60)
        val endDate: LocalDate =
            if (startMinute > endMinute && m >= startMinute) local.toLocalDate().plusDays(1)
            else local.toLocalDate()
        return ZonedDateTime.of(endDate, endTime, zone).toInstant()
    }

    companion object {
        val DEFAULT = QuietHours(enabled = true, startMinute = 22 * 60, endMinute = 7 * 60)
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val defaultPriority: Priority = Priority.MEDIUM,
    val defaultIntervalMinutes: Int = 15,
    val defaultOffsetMinutes: Int = 0,
    val defaultRepeatUntilComplete: Boolean = true,
    val defaultContinueAfterDue: Boolean = true,
    val defaultAllowOverdue: Boolean = true,
    val defaultSnoozeMinutes: Int = 10,
    val defaultMissedPolicy: MissedPolicy = MissedPolicy.MARK_MISSED,
    val quietHours: QuietHours = QuietHours.DEFAULT,
    val remindersEnabled: Boolean = true,
    val notificationSound: Boolean = true,
    val notificationVibration: Boolean = true,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val use24HourTime: Boolean = false,
    val dateFormat: DateFormatStyle = DateFormatStyle.MEDIUM,
    val habitRemindersEnabled: Boolean = true,
    val showCompletedOnHome: Boolean = true,
    val onboardingCompleted: Boolean = false,
) {
    fun defaultReminderConfig() = ReminderConfig(
        enabled = true,
        offsetMinutes = defaultOffsetMinutes,
        repeatUntilComplete = defaultRepeatUntilComplete,
        intervalMinutes = defaultIntervalMinutes,
        continueAfterDue = defaultContinueAfterDue,
        allowOverdue = defaultAllowOverdue,
    )
}
