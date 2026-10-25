@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.lifepilot.app.ui.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepilot.app.domain.model.AppSettings
import com.lifepilot.app.domain.model.Priority
import com.lifepilot.app.domain.quickadd.QuickAddParser
import com.lifepilot.app.domain.service.TaskDraft
import com.lifepilot.app.ui.common.formats
import com.lifepilot.app.ui.components.LpDatePickerDialog
import com.lifepilot.app.ui.components.LpTimePickerDialog
import com.lifepilot.app.ui.navigation.appContainer
import com.lifepilot.app.ui.theme.LpTheme
import com.lifepilot.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Fast capture: type a title, optionally with phrases like "tomorrow at 6pm !high". Recognised
 * phrases are shown as chips so the interpretation is always visible; explicit choices made with
 * the chips below override anything parsed from the text.
 */
@Composable
fun QuickAddSheet(onDismiss: () -> Unit, onOpenFullEditor: (LocalDate?) -> Unit) {
    val container = appContainer()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val f = formats

    var text by remember { mutableStateOf("") }
    var manualDate by remember { mutableStateOf<LocalDate?>(null) }
    var manualMinute by remember { mutableStateOf<Int?>(null) }
    var clearedTime by remember { mutableStateOf(false) }
    var manualPriority by remember { mutableStateOf<Priority?>(null) }
    var remind by remember { mutableStateOf(true) }
    var picker by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val parsed = remember(text) { QuickAddParser.parse(text, LocalDateTime.now()) }
    val today = LocalDate.now()
    val date = manualDate ?: parsed.date ?: today
    val minute = if (clearedTime) null else manualMinute ?: parsed.minute
    val priority = manualPriority ?: parsed.priority ?: settings.defaultPriority

    fun save() {
        val title = parsed.title.trim()
        if (title.isEmpty()) { error = "Type a title first"; return }
        val draft = TaskDraft(
            title = title, priority = priority, dueDate = date, dueMinute = minute,
            reminder = settings.defaultReminderConfig().copy(enabled = remind && minute != null),
        )
        scope.launch {
            runCatching { container.taskService.createTask(draft) }
                .onSuccess { sheetState.hide(); onDismiss() }
                .onFailure { error = it.message }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = Spacing.screen).imePadding().navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text("Quick add", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(500); error = null },
                placeholder = { Text("e.g. Call mom tomorrow at 6pm") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("quickadd_input"),
            )
            if (parsed.recognized.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Understood: ${parsed.recognized.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(selected = date == today, onClick = { manualDate = today }, label = { Text("Today") })
                FilterChip(selected = date == today.plusDays(1), onClick = { manualDate = today.plusDays(1) }, label = { Text("Tomorrow") })
                AssistChip(onClick = { picker = "date" }, label = { Text(if (date != today && date != today.plusDays(1)) f.relativeDate(date) else "Pick date") }, leadingIcon = { Icon(Icons.Rounded.Event, null, Modifier.size(18.dp)) })
                AssistChip(
                    onClick = { picker = "time" },
                    label = { Text(minute?.let { f.time(it) } ?: "Add time") },
                    leadingIcon = { Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("quickadd_time"),
                )
                if (minute != null) TextButton(onClick = { clearedTime = true; manualMinute = null }) { Text("No time") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Priority.entries.forEach { p ->
                    val c = LpTheme.extended.priority(p)
                    FilterChip(
                        selected = priority == p, onClick = { manualPriority = p },
                        label = { Text(p.label) },
                        leadingIcon = { Icon(Icons.Rounded.Flag, null, tint = c, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.copy(alpha = 0.18f)),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Remind me until done", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (minute == null) "Add a time to get reminders"
                        else "At ${f.time(minute)}, then every ${settings.defaultIntervalMinutes} min until completed",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = remind && minute != null, onCheckedChange = { remind = it }, enabled = minute != null)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onOpenFullEditor(date) }) { Text("More options") }
                Spacer(Modifier.weight(1f))
                Button(onClick = ::save, enabled = parsed.title.isNotBlank(), modifier = Modifier.height(48.dp).testTag("quickadd_save")) { Text("Add task") }
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }

    when (picker) {
        "date" -> LpDatePickerDialog(date, { manualDate = it }, { picker = null })
        "time" -> LpTimePickerDialog(minute ?: ((java.time.LocalTime.now().hour + 1) % 24) * 60, settings.use24HourTime, { manualMinute = it; clearedTime = false }, { picker = null })
    }
}
