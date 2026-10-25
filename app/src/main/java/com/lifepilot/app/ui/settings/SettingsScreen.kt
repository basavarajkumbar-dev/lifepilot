@file:OptIn(ExperimentalMaterial3Api::class)

package com.lifepilot.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifepilot.app.BuildConfig
import com.lifepilot.app.domain.model.DateFormatStyle
import com.lifepilot.app.domain.model.MissedPolicy
import com.lifepilot.app.domain.model.Priority
import com.lifepilot.app.domain.model.ThemeMode
import com.lifepilot.app.ui.common.Formats
import com.lifepilot.app.ui.common.formats
import com.lifepilot.app.ui.components.ChoiceDialog
import com.lifepilot.app.ui.components.ConfirmDialog
import com.lifepilot.app.ui.components.LpCard
import com.lifepilot.app.ui.components.LpTimePickerDialog
import com.lifepilot.app.ui.components.NumberInputDialog
import com.lifepilot.app.ui.components.SectionHeader
import com.lifepilot.app.ui.components.SettingRow
import com.lifepilot.app.ui.navigation.appContainer
import com.lifepilot.app.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SettingsScreen(contentPadding: PaddingValues, onOpenReliability: () -> Unit, onOpenCategories: () -> Unit) {
    val container = appContainer()
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val preview by vm.importPreview.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resolver = context.contentResolver
    val f = formats
    var dialog by remember { mutableStateOf<String?>(null) }
    val stamp = LocalDate.now().toString()

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { vm.exportJson(resolver, it) } }
    val exportTasks = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { vm.exportTasksCsv(resolver, it) } }
    val exportHabits = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { vm.exportHabitsCsv(resolver, it) } }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.readBackup(resolver, it) } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = Spacing.screen, end = Spacing.screen, bottom = contentPadding.calculateBottomPadding() + Spacing.xl)
            .testTag("settings_list"),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.statusBarsPadding().padding(top = Spacing.md, bottom = Spacing.sm))

        SectionHeader("Appearance")
        LpCard(Modifier.fillMaxWidth()) {
            Text("Theme", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(Spacing.sm))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = s.themeMode == m, onClick = { vm.update { it.copy(themeMode = m) } },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        modifier = Modifier.testTag("theme_${m.name}"),
                    ) { Text(m.label) }
                }
            }
        }

        SectionHeader("Reminders")
        LpCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.sm)) {
            SettingRow("All reminders", "Master switch for task and habit reminders", icon = Icons.Rounded.NotificationsActive) {
                Switch(checked = s.remindersEnabled, onCheckedChange = { v -> vm.update { it.copy(remindersEnabled = v) } }, modifier = Modifier.testTag("master_switch"))
            }
            Divider()
            SettingRow("Reminder reliability", "Permissions, exact alarms and battery checks", icon = Icons.Rounded.Shield, onClick = onOpenReliability) { Chevron() }
            Divider()
            SettingRow("Quiet hours", if (s.quietHours.enabled) "${f.time(s.quietHours.startMinute)} – ${f.time(s.quietHours.endMinute)} · reminders are held, not lost" else "Off", icon = Icons.Rounded.Bedtime) {
                Switch(checked = s.quietHours.enabled, onCheckedChange = { v -> vm.update { it.copy(quietHours = it.quietHours.copy(enabled = v)) } })
            }
            if (s.quietHours.enabled) {
                SettingRow("Quiet from", f.time(s.quietHours.startMinute), onClick = { dialog = "quietStart" })
                SettingRow("Quiet until", f.time(s.quietHours.endMinute), onClick = { dialog = "quietEnd" })
            }
            Divider()
            SettingRow("Default repeat interval", Formats.interval(s.defaultIntervalMinutes), icon = Icons.Rounded.Timer, onClick = { dialog = "interval" })
            SettingRow("Default first reminder", if (s.defaultOffsetMinutes == 0) "At due time" else "${Formats.interval(s.defaultOffsetMinutes)} before", icon = Icons.Rounded.Schedule, onClick = { dialog = "offset" })
            SettingRow("Repeat until completed by default", null) {
                Switch(checked = s.defaultRepeatUntilComplete, onCheckedChange = { v -> vm.update { it.copy(defaultRepeatUntilComplete = v) } })
            }
            SettingRow("Remind after due time by default", null) {
                Switch(checked = s.defaultContinueAfterDue, onCheckedChange = { v -> vm.update { it.copy(defaultContinueAfterDue = v) } })
            }
            SettingRow("Overdue reminders by default", "Keep reminding on following days") {
                Switch(checked = s.defaultAllowOverdue, onCheckedChange = { v -> vm.update { it.copy(defaultAllowOverdue = v) } })
            }
            SettingRow("Snooze length", Formats.interval(s.defaultSnoozeMinutes), icon = Icons.Rounded.Snooze, onClick = { dialog = "snooze" })
            Divider()
            SettingRow("Sound", null, icon = Icons.AutoMirrored.Rounded.VolumeUp) {
                Switch(checked = s.notificationSound, onCheckedChange = { v -> vm.update { it.copy(notificationSound = v) } })
            }
            SettingRow("Vibration", null, icon = Icons.Rounded.Vibration) {
                Switch(checked = s.notificationVibration, onCheckedChange = { v -> vm.update { it.copy(notificationVibration = v) } })
            }
            Text(
                "Sound and vibration can also be fine-tuned per notification category in system settings.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
        }

        SectionHeader("Tasks & habits")
        LpCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.sm)) {
            SettingRow("Default priority", s.defaultPriority.label, icon = Icons.Rounded.Flag, onClick = { dialog = "priority" })
            SettingRow("Missed recurring tasks", s.defaultMissedPolicy.label, onClick = { dialog = "missed" })
            SettingRow("Categories", "Add, rename or remove categories", icon = Icons.Rounded.Category, onClick = onOpenCategories) { Chevron() }
            SettingRow("Habit reminders", "Nudges at each habit's preferred time") {
                Switch(checked = s.habitRemindersEnabled, onCheckedChange = { v -> vm.update { it.copy(habitRemindersEnabled = v) } })
            }
        }

        SectionHeader("Date & time")
        LpCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.sm)) {
            SettingRow("Week starts on", s.weekStart.getDisplayName(TextStyle.FULL, Locale.getDefault()), icon = Icons.Rounded.CalendarViewWeek, onClick = { dialog = "week" })
            SettingRow("24-hour time", null, icon = Icons.Rounded.Schedule) {
                Switch(checked = s.use24HourTime, onCheckedChange = { v -> vm.update { it.copy(use24HourTime = v) } })
            }
            SettingRow("Date format", s.dateFormat.label, icon = Icons.Rounded.DateRange, onClick = { dialog = "dateFormat" })
        }

        SectionHeader("Your data")
        LpCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.sm)) {
            SettingRow("Back up (JSON)", "Everything, restorable on any device", icon = Icons.Rounded.Backup, onClick = { exportJson.launch("lifepilot-backup-$stamp.json") })
            SettingRow("Restore from backup", "Validated before anything is replaced", icon = Icons.Rounded.Restore, onClick = { importJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
            SettingRow("Export tasks (CSV)", null, icon = Icons.Rounded.FileDownload, onClick = { exportTasks.launch("lifepilot-tasks-$stamp.csv") })
            SettingRow("Export habits (CSV)", null, icon = Icons.Rounded.FileDownload, onClick = { exportHabits.launch("lifepilot-habits-$stamp.csv") })
            Divider()
            SettingRow("Clear all data", "Delete every task, habit and history record", icon = Icons.Rounded.DeleteForever, onClick = { dialog = "clear" })
        }

        SectionHeader("About")
        LpCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.sm)) {
            SettingRow("Privacy", "Your data never leaves this device: no account, no cloud, no analytics or ads. Backups go only where you save them.", icon = Icons.Rounded.PrivacyTip)
            SettingRow("Version", "LifePilot ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", icon = Icons.Rounded.Info)
        }
        Spacer(Modifier.height(Spacing.xl))
    }

    when (dialog) {
        "quietStart" -> LpTimePickerDialog(s.quietHours.startMinute, s.use24HourTime, { m -> vm.update { it.copy(quietHours = it.quietHours.copy(startMinute = m)) } }, { dialog = null }, "Quiet hours start")
        "quietEnd" -> LpTimePickerDialog(s.quietHours.endMinute, s.use24HourTime, { m -> vm.update { it.copy(quietHours = it.quietHours.copy(endMinute = m)) } }, { dialog = null }, "Quiet hours end")
        "interval" -> ChoiceDialog("Default repeat interval", listOf(5, 10, 15, 30, 60, -1), s.defaultIntervalMinutes, { if (it < 0) "Custom…" else Formats.interval(it) },
            { v -> if (v < 0) dialog = "intervalCustom" else vm.update { it.copy(defaultIntervalMinutes = v) } }, { if (dialog == "interval") dialog = null })
        "intervalCustom" -> NumberInputDialog("Custom interval", "Minutes", s.defaultIntervalMinutes, 1..1440, { v -> vm.update { it.copy(defaultIntervalMinutes = v) } }, { dialog = null })
        "offset" -> ChoiceDialog("Default first reminder", listOf(0, 5, 10, 15, 30, 60), s.defaultOffsetMinutes, { if (it == 0) "At due time" else "${Formats.interval(it)} before" },
            { v -> vm.update { it.copy(defaultOffsetMinutes = v) } }, { dialog = null })
        "snooze" -> ChoiceDialog("Snooze length", listOf(5, 10, 15, 30, 60), s.defaultSnoozeMinutes, { Formats.interval(it) }, { v -> vm.update { it.copy(defaultSnoozeMinutes = v) } }, { dialog = null })
        "priority" -> ChoiceDialog("Default priority", Priority.entries, s.defaultPriority, { it.label }, { v -> vm.update { it.copy(defaultPriority = v) } }, { dialog = null })
        "missed" -> ChoiceDialog("Missed recurring tasks", MissedPolicy.entries, s.defaultMissedPolicy, { it.label }, { v -> vm.update { it.copy(defaultMissedPolicy = v) } }, { dialog = null })
        "week" -> ChoiceDialog("Week starts on", listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, DayOfWeek.SATURDAY), s.weekStart, { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) }, { v -> vm.update { it.copy(weekStart = v) } }, { dialog = null })
        "dateFormat" -> ChoiceDialog("Date format", DateFormatStyle.entries, s.dateFormat, { it.label }, { v -> vm.update { it.copy(dateFormat = v) } }, { dialog = null })
        "clear" -> ConfirmDialog(
            "Clear all data?", "Every task, habit, and history record will be permanently deleted. Settings are kept. Consider making a backup first.",
            "Delete everything", onConfirm = vm::clearAll, onDismiss = { dialog = null },
        )
    }

    preview?.let { p ->
        if (!p.validation.isValid) {
            AlertDialog(
                onDismissRequest = vm::cancelImport,
                title = { Text("Can't restore this file") },
                text = { Text("Your current data was not changed.\n\n" + p.validation.errors.joinToString("\n") { "• $it" }) },
                confirmButton = { TextButton(onClick = vm::cancelImport) { Text("OK") } },
            )
        } else {
            AlertDialog(
                onDismissRequest = vm::cancelImport,
                title = { Text("Replace all data?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("This backup contains ${p.summary}.")
                        Text("Restoring replaces everything currently in LifePilot. A safety copy of your current data is saved on this device first.")
                        if (p.validation.warnings.isNotEmpty()) Text(p.validation.warnings.joinToString("\n") { "• $it" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = { TextButton(onClick = vm::confirmImport) { Text("Replace and restore", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = vm::cancelImport) { Text("Cancel") } },
            )
        }
    }

    message?.let { msg ->
        AlertDialog(onDismissRequest = vm::consumeMessage, text = { Text(msg) }, confirmButton = { TextButton(onClick = vm::consumeMessage) { Text("OK") } })
    }
}

@Composable private fun Divider() = HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

@Composable private fun Chevron() = Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)

