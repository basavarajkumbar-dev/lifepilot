@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.lifepilot.app.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifepilot.app.domain.model.HabitSchedule
import com.lifepilot.app.domain.model.HabitType
import com.lifepilot.app.ui.common.AppIcons
import com.lifepilot.app.ui.common.formats
import com.lifepilot.app.ui.components.BackTopBarTitle
import com.lifepilot.app.ui.components.LpCard
import com.lifepilot.app.ui.components.LpTimePickerDialog
import com.lifepilot.app.ui.navigation.appContainer
import com.lifepilot.app.ui.tasks.SwitchRow
import com.lifepilot.app.ui.theme.LpTheme
import com.lifepilot.app.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HabitEditorScreen(habitId: Long?, onDone: () -> Unit) {
    val container = appContainer()
    val vm: HabitEditorViewModel = viewModel(key = "habitEditor_$habitId") { HabitEditorViewModel(container, habitId) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    if (!state.loaded) return
    val form = state.form
    val ext = LpTheme.extended
    val f = formats
    var timePicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().imePadding()) {
        BackTopBarTitle(if (state.isNew) "New habit" else "Edit habit", onDone, Modifier.statusBarsPadding()) {
            TextButton(onClick = vm::save) { Text("Save") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            OutlinedTextField(
                value = form.name, onValueChange = { v -> vm.update { it.copy(name = v.take(100)) } },
                label = { Text("Name") }, placeholder = { Text("e.g. Read 20 pages") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().testTag("habit_name"),
            )
            OutlinedTextField(
                value = form.description, onValueChange = { v -> vm.update { it.copy(description = v.take(500)) } },
                label = { Text("Why it matters (optional)") }, maxLines = 3, modifier = Modifier.fillMaxWidth(),
            )
            LpCard(Modifier.fillMaxWidth()) {
                Text("Look", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.sm))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppIcons.habitIcons.forEach { (key, icon) ->
                        FilterChip(selected = form.iconKey == key, onClick = { vm.update { it.copy(iconKey = key) } }, label = { Icon(icon, contentDescription = key, modifier = Modifier.size(18.dp)) })
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ext.categories.indices.forEach { i ->
                        FilterChip(
                            selected = form.colorIndex == i, onClick = { vm.update { it.copy(colorIndex = i) } }, label = { Text(" ") },
                            colors = FilterChipDefaults.filterChipColors(containerColor = ext.category(i).copy(alpha = 0.45f), selectedContainerColor = ext.category(i)),
                        )
                    }
                }
            }
            LpCard(Modifier.fillMaxWidth()) {
                Text("Goal", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.sm))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    HabitType.entries.forEachIndexed { i, t ->
                        SegmentedButton(selected = form.type == t, onClick = { vm.update { it.copy(type = t) } }, shape = SegmentedButtonDefaults.itemShape(i, HabitType.entries.size)) { Text(t.label) }
                    }
                }
                if (form.type == HabitType.MEASURABLE) {
                    Spacer(Modifier.height(Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedTextField(
                            value = form.targetText, onValueChange = { v -> vm.update { it.copy(targetText = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(8)) } },
                            label = { Text("Daily target") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = form.unit, onValueChange = { v -> vm.update { it.copy(unit = v.take(20)) } },
                            label = { Text("Unit") }, placeholder = { Text("minutes") }, singleLine = true, modifier = Modifier.weight(1f),
                        )
                    }
                    Text("Partial progress counts toward the target and shows on your calendar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LpCard(Modifier.fillMaxWidth()) {
                Text("Schedule", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.sm))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    HabitSchedule.entries.forEach { sc -> FilterChip(selected = form.schedule == sc, onClick = { vm.update { it.copy(schedule = sc) } }, label = { Text(sc.label) }) }
                }
                when (form.schedule) {
                    HabitSchedule.WEEKDAYS -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { d ->
                            FilterChip(
                                selected = d in form.weekdays,
                                onClick = { vm.update { it.copy(weekdays = if (d in it.weekdays) it.weekdays - d else it.weekdays + d) } },
                                label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                            )
                        }
                    }
                    HabitSchedule.WEEKLY_TARGET -> Row {
                        TextButton(onClick = { vm.update { it.copy(timesPerWeek = (it.timesPerWeek - 1).coerceAtLeast(1)) } }) { Text("−") }
                        Text("${form.timesPerWeek} times per week", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                        TextButton(onClick = { vm.update { it.copy(timesPerWeek = (it.timesPerWeek + 1).coerceAtMost(7)) } }) { Text("+") }
                    }
                    HabitSchedule.DAILY -> {}
                }
            }
            LpCard(Modifier.fillMaxWidth()) {
                Text("Reminder", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.sm))
                SwitchRow("Remind me", "A gentle nudge at your preferred time if not done yet", form.reminderEnabled) { v ->
                    vm.update { it.copy(reminderEnabled = v, preferredMinute = it.preferredMinute ?: if (v) 8 * 60 else null) }
                }
                AssistChip(onClick = { timePicker = true }, label = { Text(form.preferredMinute?.let { "Preferred time: ${f.time(it)}" } ?: "Set preferred time") })
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("habit_save")) { Text(if (state.isNew) "Create habit" else "Save changes") }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
    if (timePicker) {
        LpTimePickerDialog(form.preferredMinute ?: 8 * 60, state.use24h, { m -> vm.update { it.copy(preferredMinute = m) } }, { timePicker = false }, "Preferred time")
    }
}
