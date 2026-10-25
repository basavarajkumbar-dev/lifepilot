package com.lifepilot.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lifepilot.app.domain.model.AppSettings
import com.lifepilot.app.domain.model.DateFormatStyle
import com.lifepilot.app.domain.model.MissedPolicy
import com.lifepilot.app.domain.model.Priority
import com.lifepilot.app.domain.model.QuietHours
import com.lifepilot.app.domain.model.ThemeMode
import com.lifepilot.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.DayOfWeek

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(context: Context) : SettingsRepository {

    private val store = context.applicationContext.dataStore

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val defaultPriority = stringPreferencesKey("default_priority")
        val interval = intPreferencesKey("default_interval")
        val offset = intPreferencesKey("default_offset")
        val repeat = booleanPreferencesKey("default_repeat")
        val continueAfterDue = booleanPreferencesKey("default_continue_after_due")
        val allowOverdue = booleanPreferencesKey("default_allow_overdue")
        val snooze = intPreferencesKey("default_snooze")
        val missedPolicy = stringPreferencesKey("default_missed_policy")
        val quietEnabled = booleanPreferencesKey("quiet_enabled")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val reminders = booleanPreferencesKey("reminders_enabled")
        val sound = booleanPreferencesKey("notification_sound")
        val vibration = booleanPreferencesKey("notification_vibration")
        val weekStart = intPreferencesKey("week_start")
        val use24h = booleanPreferencesKey("use_24h")
        val dateFormat = stringPreferencesKey("date_format")
        val habitReminders = booleanPreferencesKey("habit_reminders")
        val showCompleted = booleanPreferencesKey("show_completed_home")
        val onboarding = booleanPreferencesKey("onboarding_completed")
    }

    override val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[Keys.theme] = s.themeMode.name
            prefs[Keys.defaultPriority] = s.defaultPriority.name
            prefs[Keys.interval] = s.defaultIntervalMinutes.coerceIn(1, 1440)
            prefs[Keys.offset] = s.defaultOffsetMinutes.coerceIn(0, 1440)
            prefs[Keys.repeat] = s.defaultRepeatUntilComplete
            prefs[Keys.continueAfterDue] = s.defaultContinueAfterDue
            prefs[Keys.allowOverdue] = s.defaultAllowOverdue
            prefs[Keys.snooze] = s.defaultSnoozeMinutes.coerceIn(1, 1440)
            prefs[Keys.missedPolicy] = s.defaultMissedPolicy.name
            prefs[Keys.quietEnabled] = s.quietHours.enabled
            prefs[Keys.quietStart] = s.quietHours.startMinute
            prefs[Keys.quietEnd] = s.quietHours.endMinute
            prefs[Keys.reminders] = s.remindersEnabled
            prefs[Keys.sound] = s.notificationSound
            prefs[Keys.vibration] = s.notificationVibration
            prefs[Keys.weekStart] = s.weekStart.value
            prefs[Keys.use24h] = s.use24HourTime
            prefs[Keys.dateFormat] = s.dateFormat.name
            prefs[Keys.habitReminders] = s.habitRemindersEnabled
            prefs[Keys.showCompleted] = s.showCompletedOnHome
            prefs[Keys.onboarding] = s.onboardingCompleted
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeMode = ThemeMode.entries.firstOrNull { it.name == this[Keys.theme] } ?: d.themeMode,
            defaultPriority = Priority.fromName(this[Keys.defaultPriority]) ?: d.defaultPriority,
            defaultIntervalMinutes = this[Keys.interval] ?: d.defaultIntervalMinutes,
            defaultOffsetMinutes = this[Keys.offset] ?: d.defaultOffsetMinutes,
            defaultRepeatUntilComplete = this[Keys.repeat] ?: d.defaultRepeatUntilComplete,
            defaultContinueAfterDue = this[Keys.continueAfterDue] ?: d.defaultContinueAfterDue,
            defaultAllowOverdue = this[Keys.allowOverdue] ?: d.defaultAllowOverdue,
            defaultSnoozeMinutes = this[Keys.snooze] ?: d.defaultSnoozeMinutes,
            defaultMissedPolicy = MissedPolicy.entries.firstOrNull { it.name == this[Keys.missedPolicy] } ?: d.defaultMissedPolicy,
            quietHours = QuietHours(
                enabled = this[Keys.quietEnabled] ?: d.quietHours.enabled,
                startMinute = this[Keys.quietStart] ?: d.quietHours.startMinute,
                endMinute = this[Keys.quietEnd] ?: d.quietHours.endMinute,
            ),
            remindersEnabled = this[Keys.reminders] ?: d.remindersEnabled,
            notificationSound = this[Keys.sound] ?: d.notificationSound,
            notificationVibration = this[Keys.vibration] ?: d.notificationVibration,
            weekStart = this[Keys.weekStart]?.takeIf { it in 1..7 }?.let { DayOfWeek.of(it) } ?: d.weekStart,
            use24HourTime = this[Keys.use24h] ?: d.use24HourTime,
            dateFormat = DateFormatStyle.entries.firstOrNull { it.name == this[Keys.dateFormat] } ?: d.dateFormat,
            habitRemindersEnabled = this[Keys.habitReminders] ?: d.habitRemindersEnabled,
            showCompletedOnHome = this[Keys.showCompleted] ?: d.showCompletedOnHome,
            onboardingCompleted = this[Keys.onboarding] ?: d.onboardingCompleted,
        )
    }
}
