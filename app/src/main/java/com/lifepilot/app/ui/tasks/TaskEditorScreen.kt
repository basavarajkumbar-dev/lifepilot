@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.lifepilot.app.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifepilot.app.domain.model.MissedPolicy
import com.lifepilot.app.domain.model.Priority
import com.lifepilot.app.domain.model.RecurrenceFrequency
import com.lifepilot.app.domain.model.ReminderConfig
import com.lifepilot.app.domain.recurrence.RecurrenceEngine
import com.lifepilot.app.ui.common.Formats
import com.lifepilot.app.ui.common.formats
import com.lifepilot.app.ui.components.BackTopBarTitle
import com.lifepilot.app.ui.components.LpCard
import com.lifepilot.app.ui.components.LpDatePickerDialog
import com.lifepilot.app.ui.components.LpTimePickerDialog
import com.lifepilot.app.ui.components.NumberInputDialog
import com.lifepilot.app.ui.navigation.appContainer
import com.lifepilot.app.ui.theme.LpTheme
import com.lifepilot.app.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TaskEditorScreen(taskId: Long?, initialDate: LocalDate?, onDone: () -> Unit) {
    val container = appContainer()
    val vm: TaskEditorViewModel = viewModel(key = "editor_${taskId}_$initialDate") { TaskEditorViewModel(container, taskId, initialDate) }
    val state by vm.state.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    if (!state.loaded) return
    val form = state.form
    val f = formats
    val today = LocalDate.now()

    var dialog by remember { mutableStateOf<EditorDialog?>(null) }
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (state.isNew) runCatching { titleFocus.requestFocus() } }

    Column(Modifier.fillMaxSize().imePadding()) {
        BackTopBarTitle(if (state.isNew) "New task" else "Edit task", onBack = onDone, modifier = Modifier.statusBarsPadding()) {
            TextButton(onClick = vm::save, enabled = !state.saving, modifier = Modifier.testTag("editor_save_top")) { Text("Save") }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedTextField(
                value = form.title,
                onValueChange = { v -> vm.update { it.copy(title = v.take(500)) } },
                label = { Text("Title") },
                placeholder = { Text("What needs to be done?") },
                singleLine = false, maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = state.errors.any { it.contains("Title") },
                modifier = Modifier.fillMaxWidth().focusRequester(titleFocus).testTag("editor_title"),
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> vm.update { it.copy(description = v.take(10_000)) } },
                label = { Text("Notes (optional)") },
                minLines = 2, maxLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            // ---------------------------------------------------------------- When
            EditorSection("When") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(selected = form.dueDate == today, onClick = { vm.update { it.copy(dueDate = today) } }, label = { Text("Today") })
                    FilterChip(selected = form.dueDate == today.plusDays(1), onClick = { vm.update { it.copy(dueDate = today.plusDays(1)) } }, label = { Text("Tomorrow") })
                    AssistChip(onClick = { dialog = EditorDialog.DueDate }, label = { Text(f.relativeDate(form.dueDate)) }, leadingIcon = { Icon(Icons.Rounded.Event, null, Modifier.size(18.dp)) })
                    if (form.dueMinute == null) {
                        AssistChip(onClick = { dialog = EditorDialog.DueTime }, label = { Text("Add time") }, leadingIcon = { Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp)) }, modifier = Modifier.testTag("editor_add_time"))
                    } else {
                        InputChip(
                            selected = true, onClick = { dialog = EditorDialog.DueTime },
                            label = { Text(f.time(form.dueMinute)) },
                            leadingIcon = { Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp)) },
                        )
                    }
                }
                if (form.dueMinute != null) {
                    TextButton(onClick = { vm.update { it.copy(dueMinute = null) } }) { Text("Make it an all-day task") }
                }
                Spacer(Modifier.height(Spacing.sm))
                Text("Repeat", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    RepeatPreset.entries.forEach { p ->
                        FilterChip(selected = form.repeat == p, onClick = { vm.update { it.copy(repeat = p) } }, label = { Text(p.label) })
                    }
                }
                AnimatedVisibility(form.repeat == RepeatPreset.CUSTOM) { CustomRepeat(form, vm) }
                if (form.repeat != RepeatPreset.NONE) {
                    form.recurrence()?.let { Text(RecurrenceEngine.describe(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        AssistChip(onClick = { dialog = EditorDialog.EndDate }, label = { Text(form.endDate?.let { "Ends ${f.date(it)}" } ?: "No end date") })
                        if (form.endDate != null) TextButton(onClick = { vm.update { it.copy(endDate = null) } }) { Text("Remove end") }
                    }
                    Text("If an occurrence is not done before the next one", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        MissedPolicy.entries.forEach { p ->
                            FilterChip(selected = form.missedPolicy == p, onClick = { vm.update { it.copy(missedPolicy = p) } }, label = { Text(p.label) })
                        }
                    }
                }
                if (state.conflicts.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
                        Icon(Icons.Rounded.WarningAmber, null, tint = LpTheme.extended.warning, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Overlaps with: ${state.conflicts.joinToString()}", style = MaterialTheme.typography.bodySmall, color = LpTheme.extended.warning)
                    }
                }
            }

            // ---------------------------------------------------------------- Priority & category
            EditorSection("Priority") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Priority.entries.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = form.priority == p,
                            onClick = { vm.update { it.copy(priority = p) } },
                            shape = SegmentedButtonDefaults.itemShape(i, Priority.entries.size),
                            colors = SegmentedButtonDefaults.colors(activeContainerColor = LpTheme.extended.priority(p).copy(alpha = 0.2f)),
                        ) { Text(p.label, maxLines = 1) }
                    }
                }
            }
            EditorSection("Category") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(selected = form.categoryId == null, onClick = { vm.update { it.copy(categoryId = null) } }, label = { Text("None") })
                    categories.forEach { c ->
                        val color = LpTheme.extended.category(c.colorIndex)
                        FilterChip(
                            selected = form.categoryId == c.id, onClick = { vm.update { it.copy(categoryId = c.id) } }, label = { Text(c.name) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.22f)),
                        )
                    }
                    AssistChip(onClick = { dialog = EditorDialog.NewCategory }, label = { Text("New") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) })
                }
            }

            // ---------------------------------------------------------------- Reminders
            ReminderSection(form, state.use24h, vm, onCustomInterval = { dialog = EditorDialog.Interval }, onCustomMax = { dialog = EditorDialog.MaxPerDay },
                onCustomStop = { dialog = EditorDialog.StopAfter }, onQuietStart = { dialog = EditorDialog.QuietStart }, onQuietEnd = { dialog = EditorDialog.QuietEnd })

            // ---------------------------------------------------------------- Details
            EditorSection("Details") {
                Text("Estimated duration", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf<Int?>(null, 15, 30, 45, 60, 90, 120).forEach { m ->
                        FilterChip(selected = form.estimatedMinutes == m, onClick = { vm.update { it.copy(estimatedMinutes = m) } }, label = { Text(m?.let { Formats.duration(it) } ?: "None") })
                    }
                    if (form.estimatedMinutes != null && form.estimatedMinutes !in listOf(15, 30, 45, 60, 90, 120)) {
                        FilterChip(selected = true, onClick = { dialog = EditorDialog.Duration }, label = { Text(Formats.duration(form.estimatedMinutes)) })
                    }
                    AssistChip(onClick = { dialog = EditorDialog.Duration }, label = { Text("Custom") })
                }
                Text("Start (optional)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AssistChip(onClick = { dialog = EditorDialog.StartDate }, label = { Text(form.startDate?.let { f.relativeDate(it) } ?: "Start date") })
                    AssistChip(onClick = { dialog = EditorDialog.StartTime }, label = { Text(form.startMinute?.let { f.time(it) } ?: "Start time") })
                    if (form.startDate != null || form.startMinute != null) TextButton(onClick = { vm.update { it.copy(startDate = null, startMinute = null) } }) { Text("Clear") }
                }
                OutlinedTextField(
                    value = form.tagsText,
                    onValueChange = { v -> vm.update { it.copy(tagsText = v.take(300)) } },
                    label = { Text("Tags") }, placeholder = { Text("e.g. errands, deep work") },
                    supportingText = { Text("Separate with commas") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.errors.isNotEmpty()) {
                Text(state.errors.joinToString("\n"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("editor_errors"))
            }
            Button(onClick = vm::save, enabled = !state.saving, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("editor_save")) {
                Text(if (state.isNew) "Create task" else "Save changes")
            }
            Spacer(Modifier.navigationBarsPadding().height(Spacing.xl))
        }
    }

    when (dialog) {
        EditorDialog.DueDate -> LpDatePickerDialog(form.dueDate, { date -> vm.update { it.copy(dueDate = date) } }, { dialog = null })
        EditorDialog.EndDate -> LpDatePickerDialog(form.endDate ?: form.dueDate.plusMonths(1), { date -> vm.update { it.copy(endDate = date) } }, { dialog = null })
        EditorDialog.StartDate -> LpDatePickerDialog(form.startDate ?: form.dueDate, { date -> vm.update { it.copy(startDate = date) } }, { dialog = null })
        EditorDialog.DueTime -> LpTimePickerDialog(form.dueMinute ?: defaultTime(), state.use24h, { m -> vm.update { it.copy(dueMinute = m, reminder = it.reminder.copy(enabled = if (it.dueMinute == null) true else it.reminder.enabled)) } }, { dialog = null }, "Due time")
        EditorDialog.StartTime -> LpTimePickerDialog(form.startMinute ?: form.dueMinute ?: defaultTime(), state.use24h, { m -> vm.update { it.copy(startMinute = m, startDate = it.startDate ?: it.dueDate) } }, { dialog = null }, "Start time")
        EditorDialog.QuietStart -> LpTimePickerDialog(form.reminder.customQuietStart ?: 22 * 60, state.use24h, { m -> vm.updateReminder { it.copy(customQuietStart = m, customQuietEnd = it.customQuietEnd ?: 7 * 60) } }, { dialog = null }, "Quiet from")
        EditorDialog.QuietEnd -> LpTimePickerDialog(form.reminder.customQuietEnd ?: 7 * 60, state.use24h, { m -> vm.updateReminder { it.copy(customQuietEnd = m, customQuietStart = it.customQuietStart ?: 22 * 60) } }, { dialog = null }, "Quiet until")
        EditorDialog.Interval -> NumberInputDialog("Custom interval", "Minutes between reminders", form.reminder.intervalMinutes, 1..1440, { v -> vm.updateReminder { it.copy(intervalMinutes = v) } }, { dialog = null })
        EditorDialog.MaxPerDay -> NumberInputDialog("Maximum reminders per day", "Reminders", form.reminder.maxPerDay, 1..200, { v -> vm.updateReminder { it.copy(maxPerDay = v) } }, { dialog = null })
        EditorDialog.StopAfter -> NumberInputDialog("Stop reminding", "Minutes after due time", form.reminder.stopAfterMinutes, 1..10_080, { v -> vm.updateReminder { it.copy(stopAfterMinutes = v) } }, { dialog = null })
        EditorDialog.Duration -> NumberInputDialog("Estimated duration", "Minutes", form.estimatedMinutes, 1..1440, { v -> vm.update { it.copy(estimatedMinutes = v) } }, { dialog = null })
        EditorDialog.NewCategory -> NewCategoryDialog(onCreate = { name, color -> vm.addCategory(name, color) }, onDismiss = { dialog = null })
        null -> {}
    }
}

private enum class EditorDialog { DueDate, DueTime, EndDate, StartDate, StartTime, QuietStart, QuietEnd, Interval, MaxPerDay, StopAfter, Duration, NewCategory }

private fun defaultTime(): Int {
    // Next full hour, a sensible default that is always in the future today.
    val now = java.time.LocalTime.now()
    return ((now.hour + 1) % 24) * 60
}

@Composable
private fun EditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    LpCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), content = content)
    }
}

@Composable
private fun CustomRepeat(form: EditorForm, vm: TaskEditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            RecurrenceFrequency.entries.forEachIndexed { i, fr ->
                SegmentedButton(selected = form.frequency == fr, onClick = { vm.update { it.copy(frequency = fr) } }, shape = SegmentedButtonDefaults.itemShape(i, RecurrenceFrequency.entries.size)) {
                    Text(fr.label, maxLines = 1)
                }
            }
        }
        val unit = when (form.frequency) { RecurrenceFrequency.DAILY -> "days"; RecurrenceFrequency.WEEKLY -> "weeks"; RecurrenceFrequency.MONTHLY -> "months"; RecurrenceFrequency.YEARLY -> "years" }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Every", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.size(8.dp))
            TextButton(onClick = { vm.update { it.copy(interval = (it.interval - 1).coerceAtLeast(1)) } }) { Text("−") }
            Text("${form.interval}", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { vm.update { it.copy(interval = (it.interval + 1).coerceAtMost(365)) } }) { Text("+") }
            Text(unit, style = MaterialTheme.typography.bodyMedium)
        }
        if (form.frequency == RecurrenceFrequency.WEEKLY) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { d ->
                    val selected = d in form.weekdays.ifEmpty { setOf(form.dueDate.dayOfWeek) }
                    FilterChip(
                        selected = selected,
                        onClick = {
                            vm.update {
                                val current = it.weekdays.ifEmpty { setOf(it.dueDate.dayOfWeek) }
                                val next = if (d in current) current - d else current + d
                                it.copy(weekdays = next.ifEmpty { current })
                            }
                        },
                        label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                    )
                }
            }
        }
        if (form.frequency == RecurrenceFrequency.MONTHLY) {
            Text("Days of the month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ((1..31).toList() + listOf(-1)).forEach { day ->
                    val selected = day in form.monthDays.ifEmpty { setOf(form.dueDate.dayOfMonth) }
                    FilterChip(
                        selected = selected,
                        onClick = {
                            vm.update {
                                val current = it.monthDays.ifEmpty { setOf(it.dueDate.dayOfMonth) }
                                val next = if (day in current) current - day else current + day
                                it.copy(monthDays = next.ifEmpty { current })
                            }
                        },
                        label = { Text(if (day == -1) "Last" else day.toString()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderSection(
    form: EditorForm,
    use24h: Boolean,
    vm: TaskEditorViewModel,
    onCustomInterval: () -> Unit,
    onCustomMax: () -> Unit,
    onCustomStop: () -> Unit,
    onQuietStart: () -> Unit,
    onQuietEnd: () -> Unit,
) {
    val r = form.reminder
    val f = formats
    val hasTime = form.dueMinute != null
    EditorSection("Reminders") {
        SwitchRow(
            "Remind me",
            if (hasTime) "Notify at the scheduled time" else "Add a due time to enable reminders",
            r.enabled && hasTime, enabled = hasTime, tag = "editor_remind_switch",
        ) { v -> vm.updateReminder { it.copy(enabled = v) } }
        AnimatedVisibility(r.enabled && hasTime) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("First reminder", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf(0, 5, 10, 15, 30, 60).forEach { m ->
                        FilterChip(selected = r.offsetMinutes == m, onClick = { vm.updateReminder { it.copy(offsetMinutes = m) } }, label = { Text(if (m == 0) "At due time" else "${Formats.interval(m)} before") })
                    }
                }
                SwitchRow("Repeat until completed", "Keep reminding until you tap Complete", r.repeatUntilComplete, tag = "editor_repeat_switch") { v -> vm.updateReminder { it.copy(repeatUntilComplete = v) } }
                AnimatedVisibility(r.repeatUntilComplete) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("Repeat every", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            ReminderConfig.INTERVAL_PRESETS.forEach { m ->
                                FilterChip(selected = r.intervalMinutes == m, onClick = { vm.updateReminder { it.copy(intervalMinutes = m) } }, label = { Text(Formats.interval(m)) })
                            }
                            val custom = r.intervalMinutes !in ReminderConfig.INTERVAL_PRESETS
                            FilterChip(selected = custom, onClick = onCustomInterval, label = { Text(if (custom) Formats.interval(r.intervalMinutes) else "Custom") })
                        }
                        Text("Maximum per day", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            listOf<Int?>(null, 3, 5, 10).forEach { m ->
                                FilterChip(selected = r.maxPerDay == m, onClick = { vm.updateReminder { it.copy(maxPerDay = m) } }, label = { Text(m?.toString() ?: "No limit") })
                            }
                            val custom = r.maxPerDay != null && r.maxPerDay !in listOf(3, 5, 10)
                            FilterChip(selected = custom, onClick = onCustomMax, label = { Text(if (custom) r.maxPerDay.toString() else "Custom") })
                        }
                        SwitchRow("Continue after due time", "Keep repeating once the due time has passed", r.continueAfterDue) { v -> vm.updateReminder { it.copy(continueAfterDue = v) } }
                        SwitchRow("Overdue reminders", "Keep reminding on the following days", r.allowOverdue, enabled = r.continueAfterDue) { v -> vm.updateReminder { it.copy(allowOverdue = v) } }
                        Text("Stop reminding", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            listOf<Int?>(null, 60, 180).forEach { m ->
                                FilterChip(selected = r.stopAfterMinutes == m, onClick = { vm.updateReminder { it.copy(stopAfterMinutes = m) } }, label = { Text(m?.let { "${Formats.interval(it)} after due" } ?: "Never") })
                            }
                            val custom = r.stopAfterMinutes != null && r.stopAfterMinutes !in listOf(60, 180)
                            FilterChip(selected = custom, onClick = onCustomStop, label = { Text(if (custom) "${Formats.interval(r.stopAfterMinutes!!)} after" else "Custom") })
                        }
                    }
                }
                SwitchRow("Respect quiet hours", "Hold reminders during quiet hours and deliver them afterwards", r.respectQuietHours) { v -> vm.updateReminder { it.copy(respectQuietHours = v) } }
                if (r.respectQuietHours) {
                    val custom = r.customQuietStart != null && r.customQuietEnd != null
                    SwitchRow("Custom quiet hours for this task", if (custom) "${f.time(r.customQuietStart!!)} – ${f.time(r.customQuietEnd!!)}" else "Uses your quiet hours from Settings", custom) { v ->
                        vm.updateReminder { if (v) it.copy(customQuietStart = 22 * 60, customQuietEnd = 7 * 60) else it.copy(customQuietStart = null, customQuietEnd = null) }
                    }
                    if (custom) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            AssistChip(onClick = onQuietStart, label = { Text("From ${f.time(r.customQuietStart!!)}") })
                            AssistChip(onClick = onQuietEnd, label = { Text("Until ${f.time(r.customQuietEnd!!)}") })
                        }
                    }
                }
                val summary = buildString {
                    append("You'll be reminded ")
                    append(if (r.offsetMinutes == 0) "at ${f.time(form.dueMinute!!)}" else "${Formats.interval(r.offsetMinutes)} before ${f.time(form.dueMinute!!)}")
                    if (r.repeatUntilComplete) append(", then every ${Formats.interval(r.intervalMinutes)} until you complete it")
                    r.maxPerDay?.let { append(" (max $it a day)") }
                    append(".")
                }
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("editor_reminder_summary"))
            }
        }
    }
}

@Composable
internal fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, tag: String? = null, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, modifier = if (tag != null) Modifier.testTag(tag) else Modifier)
    }
}

@Composable
internal fun NewCategoryDialog(onCreate: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(0) }
    val ext = LpTheme.extended
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Name") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ext.categories.indices.forEach { i ->
                        FilterChip(
                            selected = color == i, onClick = { color = i }, label = { Text(" ") },
                            colors = FilterChipDefaults.filterChipColors(containerColor = ext.category(i).copy(alpha = 0.5f), selectedContainerColor = ext.category(i)),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name.trim(), color); onDismiss() }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
