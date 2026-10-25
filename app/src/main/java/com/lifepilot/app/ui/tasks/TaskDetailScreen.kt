@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.lifepilot.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifepilot.app.AppContainer
import com.lifepilot.app.domain.analytics.InsightsCalculator
import com.lifepilot.app.domain.model.AppSettings
import com.lifepilot.app.domain.model.Occurrence
import com.lifepilot.app.domain.model.OccurrenceItem
import com.lifepilot.app.domain.model.OccurrenceStatus
import com.lifepilot.app.domain.model.ReminderEventType
import com.lifepilot.app.domain.model.ReminderLogEntry
import com.lifepilot.app.domain.recurrence.RecurrenceEngine
import com.lifepilot.app.domain.reminder.ReminderDecision
import com.lifepilot.app.domain.reminder.ReminderPolicy
import com.lifepilot.app.domain.reminder.StopReason
import com.lifepilot.app.ui.common.Formats
import com.lifepilot.app.ui.common.formats
import com.lifepilot.app.ui.components.BackTopBarTitle
import com.lifepilot.app.ui.components.ConfirmDialog
import com.lifepilot.app.ui.components.EmptyState
import com.lifepilot.app.ui.components.LpCard
import com.lifepilot.app.ui.components.LpDatePickerDialog
import com.lifepilot.app.ui.components.LpTimePickerDialog
import com.lifepilot.app.ui.components.Pill
import com.lifepilot.app.ui.components.PriorityBadge
import com.lifepilot.app.ui.components.SectionHeader
import com.lifepilot.app.ui.navigation.appContainer
import com.lifepilot.app.ui.theme.LpTheme
import com.lifepilot.app.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DetailUiState(
    val loading: Boolean = true,
    val item: OccurrenceItem? = null,
    val siblings: List<Occurrence> = emptyList(),
    val logs: List<ReminderLogEntry> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val deleted: Boolean = false,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TaskDetailViewModel(private val container: AppContainer, private val occurrenceId: Long) : ViewModel() {

    val state: StateFlow<DetailUiState> = container.tasks.observeItem(occurrenceId).flatMapLatest { item ->
        if (item == null) flowOf(DetailUiState(loading = false, item = null))
        else combine(
            container.tasks.observeLogsForTask(item.task.id),
            container.tasks.observeItems(),
            container.settings.settings,
        ) { logs, all, s ->
            DetailUiState(
                loading = false, item = item, logs = logs, settings = s,
                siblings = all.filter { it.task.id == item.task.id && it.occurrence.id != occurrenceId }.map { it.occurrence }.sortedByDescending { it.seriesDate },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    private fun act(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    fun complete() = act { container.taskService.completeOccurrence(occurrenceId) }
    fun reopen() = act { container.taskService.reopenOccurrence(occurrenceId) }
    fun snooze(minutes: Int) = act { container.taskService.snoozeOccurrence(occurrenceId, minutes) }
    fun reschedule(date: LocalDate, minute: Int?) = act { container.taskService.rescheduleOccurrence(occurrenceId, date, minute) }
    fun skip() = act { container.taskService.skipOccurrence(occurrenceId) }
    fun setReminders(taskId: Long, enabled: Boolean) = act { container.taskService.setTaskRemindersEnabled(taskId, enabled) }
    fun duplicate(taskId: Long, onCreated: (Long) -> Unit) = act {
        val newTaskId = container.taskService.duplicateTask(taskId) ?: return@act
        container.tasks.occurrencesForTask(newTaskId).firstOrNull()?.let { onCreated(it.id) }
    }
    fun delete(taskId: Long, onDone: () -> Unit) = act { container.taskService.deleteTask(taskId); onDone() }
}

@Composable
fun TaskDetailScreen(occurrenceId: Long, onBack: () -> Unit, onEdit: (Long) -> Unit, onOpenOccurrence: (Long) -> Unit) {
    val container = appContainer()
    val vm: TaskDetailViewModel = viewModel(key = "detail_$occurrenceId") { TaskDetailViewModel(container, occurrenceId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val f = formats
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    val item = state.item
    if (!state.loading && item == null) {
        Column(Modifier.fillMaxSize()) {
            BackTopBarTitle("Task", onBack, Modifier.statusBarsPadding())
            EmptyState(Icons.Rounded.Delete, "Task not found", "This task was deleted or is no longer available.")
        }
        return
    }
    if (item == null) return
    val occ = item.occurrence
    val task = item.task
    val pending = occ.status == OccurrenceStatus.PENDING
    val now = Instant.now()
    val overdue = InsightsCalculator.isOverdue(item, now, ZoneId.systemDefault())

    Column(Modifier.fillMaxSize()) {
        BackTopBarTitle("", onBack, Modifier.statusBarsPadding()) {
            IconButton(onClick = { onEdit(task.id) }, modifier = Modifier.testTag("detail_edit")) { Icon(Icons.Rounded.Edit, contentDescription = "Edit") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = {
                        menu = false; vm.duplicate(task.id) { onOpenOccurrence(it) }
                    })
                    if (task.isRecurring && pending) {
                        DropdownMenuItem(text = { Text("Skip this occurrence") }, leadingIcon = { Icon(Icons.Rounded.SkipNext, null) }, onClick = { menu = false; vm.skip() })
                    }
                    DropdownMenuItem(
                        text = { Text(if (task.isRecurring) "Delete series" else "Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; dialog = "delete" },
                        modifier = Modifier.testTag("detail_delete"),
                    )
                }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.category?.let { Pill(it.name, LpTheme.extended.category(it.colorIndex)) }
                    PriorityBadge(task.priority)
                    if (task.isRecurring) Pill("Repeats", MaterialTheme.colorScheme.primary, icon = Icons.Rounded.Repeat)
                    when (occ.status) {
                        OccurrenceStatus.COMPLETED -> Pill("Completed", LpTheme.extended.success, icon = Icons.Rounded.Check)
                        OccurrenceStatus.SKIPPED -> Pill("Skipped", MaterialTheme.colorScheme.onSurfaceVariant)
                        OccurrenceStatus.MISSED -> Pill("Missed", MaterialTheme.colorScheme.onSurfaceVariant)
                        OccurrenceStatus.PENDING -> if (overdue) Pill("Overdue", MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(task.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("detail_title"))
                Spacer(Modifier.height(4.dp))
                Text(
                    "Due ${f.due(occ.dueDate, occ.dueMinute)}" + (occ.completedAt?.let { " · completed ${f.dateTime(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                task.recurrence?.let { Text(RecurrenceEngine.describe(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }

            item {
                if (pending) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Button(onClick = vm::complete, modifier = Modifier.weight(1f).height(48.dp).testTag("detail_complete")) {
                            Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(6.dp)); Text("Complete")
                        }
                        FilledTonalButton(onClick = { dialog = "snooze" }, modifier = Modifier.weight(1f).height(48.dp).testTag("detail_snooze")) {
                            Icon(Icons.Rounded.Snooze, null); Spacer(Modifier.width(6.dp)); Text("Snooze")
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedButton(onClick = { dialog = "reschedule" }, modifier = Modifier.fillMaxWidth().testTag("detail_reschedule")) {
                        Icon(Icons.Rounded.EventRepeat, null); Spacer(Modifier.width(6.dp)); Text("Reschedule")
                    }
                } else {
                    OutlinedButton(onClick = vm::reopen, modifier = Modifier.fillMaxWidth().testTag("detail_reopen")) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, null); Spacer(Modifier.width(6.dp)); Text("Mark as not done")
                    }
                }
            }

            item { ReminderCard(item, state.settings, now, onToggle = { vm.setReminders(task.id, it) }) }

            if (task.description.isNotBlank() || task.tags.isNotEmpty() || task.estimatedMinutes != null || task.startDate != null) {
                item {
                    LpCard(Modifier.fillMaxWidth()) {
                        if (task.description.isNotBlank()) {
                            Text(task.description, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(Spacing.sm))
                        }
                        task.estimatedMinutes?.let { Text("Estimated: ${Formats.duration(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        task.startDate?.let { Text("Starts ${f.due(it, task.startMinute)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (task.tags.isNotEmpty()) {
                            Spacer(Modifier.height(Spacing.sm))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { task.tags.forEach { Pill("#$it", MaterialTheme.colorScheme.onSurfaceVariant) } }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MiniStat("Reminders", occ.totalReminders.toString(), Modifier.weight(1f))
                    MiniStat("Snoozes", occ.snoozeCount.toString(), Modifier.weight(1f))
                    MiniStat("Reschedules", occ.rescheduleCount.toString(), Modifier.weight(1f))
                }
            }

            if (state.siblings.isNotEmpty()) {
                item { SectionHeader("Other occurrences") }
                items(state.siblings.take(10), key = { "s${it.id}" }) { s ->
                    LpCard(Modifier.fillMaxWidth(), onClick = { onOpenOccurrence(s.id) }, contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.due(s.dueDate, s.dueMinute), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(s.status.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (state.logs.isNotEmpty()) {
                item { SectionHeader("History") }
                items(state.logs.take(30), key = { "l${it.id}" }) { log ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(logLabel(log), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(f.dateTime(log.at), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }

    when (dialog) {
        "delete" -> ConfirmDialog(
            title = if (task.isRecurring) "Delete this recurring task?" else "Delete this task?",
            message = if (task.isRecurring) "All occurrences, reminders and history of \"${task.title}\" will be permanently removed." else "\"${task.title}\" and its reminders will be permanently removed.",
            confirmLabel = "Delete",
            onConfirm = { vm.delete(task.id, onBack) },
            onDismiss = { dialog = null },
        )
        "snooze" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Snooze reminders") },
            text = {
                Column {
                    Text("The task stays pending. You'll be reminded again after:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Spacing.sm))
                    listOf(5, 10, 15, 30, 60, 120).forEach { m ->
                        TextButton(onClick = { vm.snooze(m); dialog = null }, modifier = Modifier.fillMaxWidth().testTag("snooze_$m")) { Text(Formats.interval(m), modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        "reschedule" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Reschedule") },
            text = {
                Column {
                    val today = LocalDate.now()
                    val nextHour = ((java.time.LocalTime.now().hour + 1) % 24) * 60
                    TextButton(onClick = { vm.reschedule(today, nextHour); dialog = null }, modifier = Modifier.fillMaxWidth()) { Text("Later today (${f.time(nextHour)})", modifier = Modifier.fillMaxWidth()) }
                    TextButton(onClick = { vm.reschedule(today.plusDays(1), occ.dueMinute); dialog = null }, modifier = Modifier.fillMaxWidth()) { Text("Tomorrow" + (occ.dueMinute?.let { " (${f.time(it)})" } ?: ""), modifier = Modifier.fillMaxWidth()) }
                    TextButton(onClick = { vm.reschedule(today.plusWeeks(1), occ.dueMinute); dialog = null }, modifier = Modifier.fillMaxWidth()) { Text("Next week", modifier = Modifier.fillMaxWidth()) }
                    TextButton(onClick = { dialog = "pickDate" }, modifier = Modifier.fillMaxWidth()) { Text("Pick date and time…", modifier = Modifier.fillMaxWidth()) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        "pickDate" -> LpDatePickerDialog(occ.dueDate, { pickedDate = it; dialog = "pickTime" }, { if (dialog == "pickDate") dialog = null })
        "pickTime" -> LpTimePickerDialog(occ.dueMinute ?: 9 * 60, state.settings.use24HourTime, { m -> pickedDate?.let { vm.reschedule(it, m) } }, { dialog = null }, "New time")
    }
    LaunchedEffect(dialog) { if (dialog == null) pickedDate = null }
}

@Composable
private fun ReminderCard(item: OccurrenceItem, settings: AppSettings, now: Instant, onToggle: (Boolean) -> Unit) {
    val f = formats
    val task = item.task
    val occ = item.occurrence
    val decision = ReminderPolicy.next(task.reminder, occ, settings.quietHours, settings.remindersEnabled, now, ZoneId.systemDefault())
    LpCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (task.reminder.enabled) Icons.Rounded.Alarm else Icons.Rounded.NotificationsOff, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("Reminders", style = MaterialTheme.typography.titleSmall)
                val status = when (decision) {
                    is ReminderDecision.Schedule -> buildString {
                        append("Next: ${f.dateTime(decision.at)}")
                        if (decision.deferredByQuietHours) append(" (after quiet hours)")
                        if (decision.deferredByDailyLimit) append(" (daily limit reached)")
                    }
                    is ReminderDecision.None -> when (decision.reason) {
                        StopReason.MASTER_SWITCH_OFF -> "All reminders are switched off in Settings"
                        StopReason.TASK_REMINDERS_OFF -> "Off for this task"
                        StopReason.NOT_PENDING -> "Stopped — this task is done"
                        StopReason.NO_DUE_TIME -> "Add a due time to get reminders"
                        StopReason.SINGLE_REMINDER_SENT -> "Reminder sent (repeating is off)"
                        StopReason.DUE_TIME_PASSED -> "Stopped at the due time (per your settings)"
                        StopReason.STOP_TIME_REACHED -> "Stopped — the stop time was reached"
                        StopReason.OVERDUE_NOT_ALLOWED -> "Stopped — overdue reminders are off"
                    }
                }
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("detail_reminder_status"))
                if (task.reminder.enabled && occ.dueMinute != null && task.reminder.repeatUntilComplete) {
                    Text("Repeats every ${Formats.interval(task.reminder.intervalMinutes)} until completed" + (task.reminder.maxPerDay?.let { ", max $it/day" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (occ.dueMinute != null) Switch(checked = task.reminder.enabled, onCheckedChange = onToggle, modifier = Modifier.testTag("detail_reminder_switch"))
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    LpCard(modifier, contentPadding = PaddingValues(Spacing.md)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun logLabel(log: ReminderLogEntry): String = when (log.type) {
    ReminderEventType.FIRED -> "Reminder sent"
    ReminderEventType.SNOOZED -> "Snoozed" + (log.detail?.let { " · ${it.substringBefore(" via")}" } ?: "")
    ReminderEventType.COMPLETED -> if (log.detail == "NOTIFICATION") "Completed from notification" else "Completed"
    ReminderEventType.REOPENED -> "Marked as not done"
    ReminderEventType.RESCHEDULED -> "Rescheduled" + (log.detail?.let { " · $it" } ?: "")
    ReminderEventType.SKIPPED -> "Skipped"
    ReminderEventType.MISSED -> "Missed"
}

