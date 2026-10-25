package com.lifepilot.app.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifepilot.app.AppContainer
import com.lifepilot.app.domain.analytics.InsightsCalculator
import com.lifepilot.app.domain.habit.HabitStats
import com.lifepilot.app.domain.model.DayPlan
import com.lifepilot.app.domain.model.Habit
import com.lifepilot.app.domain.model.OccurrenceItem
import com.lifepilot.app.domain.model.OccurrenceStatus
import com.lifepilot.app.domain.model.ReminderEventType
import com.lifepilot.app.ui.common.formats
import com.lifepilot.app.ui.components.BackTopBarTitle
import com.lifepilot.app.ui.components.ConfirmDialog
import com.lifepilot.app.ui.components.EmptyState
import com.lifepilot.app.ui.components.LpCard
import com.lifepilot.app.ui.components.SectionHeader
import com.lifepilot.app.ui.components.TaskRow
import com.lifepilot.app.ui.navigation.appContainer
import com.lifepilot.app.ui.theme.LpTheme
import com.lifepilot.app.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ReviewUiState(
    val loading: Boolean = true,
    val today: List<OccurrenceItem> = emptyList(),
    val overdue: List<OccurrenceItem> = emptyList(),
    val completedToday: List<OccurrenceItem> = emptyList(),
    val remainingToday: List<OccurrenceItem> = emptyList(),
    val missedReminders: List<OccurrenceItem> = emptyList(),
    val habitsDone: List<Habit> = emptyList(),
    val habitsOpen: List<Habit> = emptyList(),
    val plan: DayPlan? = null,
)

class ReviewViewModel(private val container: AppContainer) : ViewModel() {
    private val date = LocalDate.now()
    private val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant()

    val state: StateFlow<ReviewUiState> = combine(
        container.tasks.observeItems(),
        container.tasks.observeLogsSince(dayStart),
        combine(container.habits.observeHabits(), container.habits.observeLogs()) { h, l -> h to l },
        container.dayPlans.observePlan(date),
    ) { items, logs, (habits, habitLogs), plan ->
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val todayItems = items.filter { it.occurrence.dueDate == date && it.occurrence.status != OccurrenceStatus.SKIPPED }
        val overdue = items.filter { InsightsCalculator.isOverdue(it, now, zone) && it.occurrence.dueDate.isBefore(date) }
        val firedIds = logs.filter { it.type == ReminderEventType.FIRED }.map { it.occurrenceId }.toSet()
        val active = habits.filter { !it.archived && HabitStats.isScheduled(it, date) }
        val doneIds = habitLogs.filter { it.date == date }.filter { l -> active.firstOrNull { it.id == l.habitId }?.let { HabitStats.isMet(it, l.value) } == true }.map { it.habitId }.toSet()
        ReviewUiState(
            loading = false,
            today = todayItems.sortedWith(compareBy({ it.occurrence.status != OccurrenceStatus.PENDING }, { it.occurrence.dueMinute ?: Int.MAX_VALUE })),
            overdue = overdue.sortedBy { it.occurrence.dueDate },
            completedToday = items.filter { it.occurrence.completedAt?.atZone(zone)?.toLocalDate() == date },
            remainingToday = todayItems.filter { it.occurrence.status == OccurrenceStatus.PENDING },
            missedReminders = items.filter { it.occurrence.id in firedIds && it.occurrence.status == OccurrenceStatus.PENDING },
            habitsDone = active.filter { it.id in doneIds },
            habitsOpen = active.filter { it.id !in doneIds },
            plan = plan,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    fun savePlan(intention: String, focus: List<Long>) = viewModelScope.launch {
        container.dayPlans.savePlan(DayPlan(date, intention.trim().take(2000), focus))
    }

    fun toggle(item: OccurrenceItem) = viewModelScope.launch {
        if (item.occurrence.status == OccurrenceStatus.PENDING) container.taskService.completeOccurrence(item.occurrence.id)
        else container.taskService.reopenOccurrence(item.occurrence.id)
    }

    /** Moves the given occurrences to [target] keeping their time. Only ever called after the user chooses it. */
    fun moveTo(items: List<OccurrenceItem>, target: LocalDate) = viewModelScope.launch {
        items.forEach { container.taskService.rescheduleOccurrence(it.occurrence.id, target, it.occurrence.dueMinute) }
    }
}

@Composable
fun DailyPlanScreen(onBack: () -> Unit, onOpenOccurrence: (Long) -> Unit) {
    val container = appContainer()
    val vm: ReviewViewModel = viewModel(key = "plan") { ReviewViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    var intention by remember { mutableStateOf("") }
    var focus by remember { mutableStateOf(listOf<Long>()) }
    var initialized by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading, state.plan) {
        if (!state.loading && !initialized) {
            intention = state.plan?.intention ?: ""
            focus = state.plan?.focusOccurrenceIds ?: emptyList()
            initialized = true
        }
    }
    val f = formats
    val today = LocalDate.now()

    Column(Modifier.fillMaxSize()) {
        BackTopBarTitle("Plan my day", onBack, Modifier.statusBarsPadding())
        LazyColumn(contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            item {
                Text("${f.weekday(today)}, ${f.date(today)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("What matters most today?", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = intention, onValueChange = { intention = it.take(2000); saved = false },
                    label = { Text("Today's goal or intention") }, placeholder = { Text("e.g. Finish the report draft, walk 30 minutes") },
                    minLines = 2, modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.overdue.isNotEmpty()) {
                item {
                    SectionHeader("Overdue · ${state.overdue.size}")
                    Text("Decide what to do with each — nothing is moved unless you choose to.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(state.overdue, key = { "o${it.occurrence.id}" }) { item ->
                    Column {
                        TaskRow(item, onToggle = { vm.toggle(item) }, onClick = { onOpenOccurrence(item.occurrence.id) })
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { vm.moveTo(listOf(item), today) }) { Text("Do today") }
                            TextButton(onClick = { vm.moveTo(listOf(item), today.plusDays(1)) }) { Text("Tomorrow") }
                        }
                    }
                }
            }
            item {
                SectionHeader("Today · ${state.today.size}")
                Text("Star up to 3 priorities to focus on.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.today.isEmpty()) item { EmptyState(Icons.Rounded.StarBorder, "Nothing scheduled", "Add tasks for today from the home screen.") }
            items(state.today, key = { "t${it.occurrence.id}" }) { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TaskRow(item, onToggle = { vm.toggle(item) }, onClick = { onOpenOccurrence(item.occurrence.id) }, modifier = Modifier.weight(1f), showDate = false)
                    val starred = item.occurrence.id in focus
                    IconButton(onClick = {
                        focus = if (starred) focus - item.occurrence.id else if (focus.size < 3) focus + item.occurrence.id else focus
                        saved = false
                    }) {
                        Icon(if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, contentDescription = if (starred) "Remove from focus" else "Add to focus",
                            tint = if (starred) LpTheme.extended.warning else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Spacer(Modifier.height(Spacing.md))
                Button(onClick = { vm.savePlan(intention, focus); saved = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (saved) "Plan saved" else "Save plan") }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
fun EndOfDayReviewScreen(onBack: () -> Unit, onOpenOccurrence: (Long) -> Unit) {
    val container = appContainer()
    val vm: ReviewViewModel = viewModel(key = "review") { ReviewViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmMoveAll by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    Column(Modifier.fillMaxSize()) {
        BackTopBarTitle("End-of-day review", onBack, Modifier.statusBarsPadding())
        LazyColumn(contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            item {
                LpCard(Modifier.fillMaxWidth()) {
                    Text("${state.completedToday.size} completed today", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${state.remainingToday.size} still open · ${state.habitsDone.size}/${state.habitsDone.size + state.habitsOpen.size} habits done",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.plan?.intention?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(Spacing.sm))
                        Text("Today's intention: $it", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (state.completedToday.isNotEmpty()) {
                item { SectionHeader("Completed") }
                items(state.completedToday, key = { "c${it.occurrence.id}" }) { item -> TaskRow(item, onToggle = { vm.toggle(item) }, onClick = { onOpenOccurrence(item.occurrence.id) }, showDate = false) }
            }
            if (state.missedReminders.isNotEmpty()) {
                item {
                    SectionHeader("Reminded but not done · ${state.missedReminders.size}")
                }
                items(state.missedReminders, key = { "m${it.occurrence.id}" }) { item -> TaskRow(item, onToggle = { vm.toggle(item) }, onClick = { onOpenOccurrence(item.occurrence.id) }) }
            }
            item { SectionHeader("Still open today · ${state.remainingToday.size}") }
            if (state.remainingToday.isEmpty()) {
                item { EmptyState(Icons.Rounded.Star, "Nothing left over", "Everything planned for today is done.") }
            } else {
                items(state.remainingToday, key = { "r${it.occurrence.id}" }) { item ->
                    Column {
                        TaskRow(item, onToggle = { vm.toggle(item) }, onClick = { onOpenOccurrence(item.occurrence.id) }, showDate = false)
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { vm.moveTo(listOf(item), today.plusDays(1)) }) { Text("Move to tomorrow") }
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = { confirmMoveAll = true }, modifier = Modifier.fillMaxWidth()) { Text("Move all open tasks to tomorrow…") }
                }
            }
            if (state.habitsDone.isNotEmpty() || state.habitsOpen.isNotEmpty()) {
                item { SectionHeader("Habits") }
                items(state.habitsDone + state.habitsOpen, key = { "h${it.id}" }) { h ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Checkbox(checked = h in state.habitsDone, onCheckedChange = null)
                        Text(h.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
    if (confirmMoveAll) {
        ConfirmDialog(
            "Move ${state.remainingToday.size} tasks to tomorrow?", "Their due date changes to tomorrow at the same time, and their reminders are rescheduled.",
            "Move", destructive = false, onConfirm = { vm.moveTo(state.remainingToday, today.plusDays(1)) }, onDismiss = { confirmMoveAll = false },
        )
    }
}
