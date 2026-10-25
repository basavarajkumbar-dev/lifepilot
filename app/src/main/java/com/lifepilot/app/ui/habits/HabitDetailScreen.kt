@file:OptIn(ExperimentalMaterial3Api::class)

package com.lifepilot.app.ui.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifepilot.app.domain.habit.HabitStats
import com.lifepilot.app.domain.model.HabitSchedule
import com.lifepilot.app.domain.model.HabitType
import com.lifepilot.app.domain.model.StreakUnit
import com.lifepilot.app.ui.common.AppIcons
import com.lifepilot.app.ui.common.Formats
import com.lifepilot.app.ui.common.formats
import com.lifepilot.app.ui.components.BackTopBarTitle
import com.lifepilot.app.ui.components.ConfirmDialog
import com.lifepilot.app.ui.components.EmptyState
import com.lifepilot.app.ui.components.IconBadge
import com.lifepilot.app.ui.components.LpCard
import com.lifepilot.app.ui.components.StatTile
import com.lifepilot.app.ui.navigation.appContainer
import com.lifepilot.app.ui.theme.LpTheme
import com.lifepilot.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HabitDetailScreen(habitId: Long, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val container = appContainer()
    val vm: HabitDetailViewModel = viewModel(key = "habit_$habitId") { HabitDetailViewModel(container, habitId) }
    val state by vm.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var progressDate by remember { mutableStateOf<LocalDate?>(null) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    val f = formats

    val s = state.summary
    if (!state.loading && s == null) {
        Column(Modifier.fillMaxSize()) {
            BackTopBarTitle("Habit", onBack, Modifier.statusBarsPadding())
            EmptyState(Icons.Rounded.Delete, "Habit not found", "This habit was deleted.")
        }
        return
    }
    if (s == null) return
    val habit = s.habit
    val color = LpTheme.extended.category(habit.colorIndex)
    val today = LocalDate.now()

    Column(Modifier.fillMaxSize()) {
        BackTopBarTitle("", onBack, Modifier.statusBarsPadding()) {
            IconButton(onClick = { onEdit(habit.id) }) { Icon(Icons.Rounded.Edit, contentDescription = "Edit habit") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (habit.archived) "Restore" else "Archive") },
                        leadingIcon = { Icon(if (habit.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, null) },
                        onClick = { menu = false; vm.archive(!habit.archived) },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; confirmDelete = true },
                    )
                }
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(AppIcons.habit(habit.iconKey), color, size = 52.dp)
                Spacer(Modifier.width(Spacing.md))
                Column {
                    Text(habit.name, style = MaterialTheme.typography.headlineSmall)
                    val schedule = when (habit.schedule) {
                        HabitSchedule.DAILY -> "Every day"
                        HabitSchedule.WEEKDAYS -> habit.weekdays.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
                        HabitSchedule.WEEKLY_TARGET -> "${habit.timesPerWeek} times a week"
                    }
                    val target = if (habit.type == HabitType.MEASURABLE) " · ${Formats.number(habit.target)} ${habit.unit}".trimEnd() else ""
                    Text(schedule + target + (habit.preferredMinute?.let { " · ${f.time(it)}" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (habit.description.isNotBlank()) Text(habit.description, style = MaterialTheme.typography.bodyMedium)

            if (s.scheduledToday || habit.schedule == HabitSchedule.WEEKLY_TARGET) {
                Button(
                    onClick = { if (habit.type == HabitType.CHECK) vm.toggle(today) else progressDate = today },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(
                        when {
                            habit.type == HabitType.MEASURABLE -> "Log progress for today (${Formats.number(s.todayValue)}/${Formats.number(habit.target)})"
                            s.todayMet -> "Done today — tap to undo"
                            else -> "Mark done for today"
                        }
                    )
                }
            }

            val unit = if (s.streak.unit == StreakUnit.WEEKS) "weeks" else "days"
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile("Current streak", "${s.streak.current} $unit", Modifier.weight(1f), accent = color)
                StatTile("Best streak", "${s.streak.best} $unit", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile("Last 30 days", s.rate30?.let { "${(it * 100).toInt()}%" } ?: "—", Modifier.weight(1f))
                StatTile("This week", "${s.weekMet} done", Modifier.weight(1f))
            }
            if (s.streak.current == 0 && s.streak.best > 0) {
                Text("Every day is a fresh start — pick up where you left off.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            LpCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month") }
                    Text(
                        month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year,
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.now()) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month") }
                }
                val weekStart = state.settings.weekStart
                val days = (0L until 7L).map { weekStart.plus(it) }
                Row(Modifier.fillMaxWidth()) {
                    days.forEach { Text(it.getDisplayName(TextStyle.NARROW, Locale.getDefault()), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                val first = month.atDay(1)
                val lead = ((first.dayOfWeek.value - weekStart.value) + 7) % 7
                val cells = lead + month.lengthOfMonth()
                val rows = (cells + 6) / 7
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        for (c in 0 until 7) {
                            val index = r * 7 + c - lead
                            Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                if (index in 0 until month.lengthOfMonth()) {
                                    val date = month.atDay(index + 1)
                                    val scheduled = HabitStats.isScheduled(habit, date)
                                    val fraction = if (!scheduled || date.isAfter(today)) -1f else HabitStats.progressFraction(habit, state.logs[date])
                                    val editable = !date.isAfter(today) && !date.isBefore(habit.startDate)
                                    Box(
                                        Modifier.then(if (editable) Modifier.clickable { if (habit.type == HabitType.CHECK) vm.toggle(date) else progressDate = date } else Modifier),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        DayDot(fraction, color, size = 30.dp)
                                        if (fraction < 1f) Text("${date.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                Text("Tap a past day to correct it.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            "Delete \"${habit.name}\"?", "The habit and its entire history will be permanently removed. Archive it instead to keep the history.",
            "Delete", onConfirm = { vm.delete(onBack) }, onDismiss = { confirmDelete = false },
        )
    }
    progressDate?.let { date ->
        var text by remember(date) { mutableStateOf(state.logs[date]?.let { Formats.number(it) } ?: "") }
        AlertDialog(
            onDismissRequest = { progressDate = null },
            title = { Text("Progress · ${f.relativeDate(date)}") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' }.take(8) },
                    label = { Text("Amount ${habit.unit}".trim()) }, singleLine = true,
                    supportingText = { Text("Target: ${Formats.number(habit.target)} ${habit.unit}".trim()) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = text.replace(',', '.').toDoubleOrNull()
                    if (value != null) vm.setProgress(date, value) else if (text.isEmpty()) vm.setProgress(date, 0.0)
                    progressDate = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { progressDate = null }) { Text("Cancel") } },
        )
    }
}
