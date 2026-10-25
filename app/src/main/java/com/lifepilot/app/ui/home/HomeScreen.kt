@file:OptIn(ExperimentalMaterial3Api::class)

package com.lifepilot.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifepilot.app.ui.common.AppIcons
import com.lifepilot.app.ui.common.Formats
import com.lifepilot.app.ui.common.formats
import com.lifepilot.app.ui.components.EmptyState
import com.lifepilot.app.ui.components.LpCard
import com.lifepilot.app.ui.components.ProgressRing
import com.lifepilot.app.ui.components.SectionHeader
import com.lifepilot.app.ui.components.TaskRow
import com.lifepilot.app.ui.navigation.appContainer
import com.lifepilot.app.ui.tasks.TaskFilter
import com.lifepilot.app.ui.theme.LpTheme
import com.lifepilot.app.ui.theme.Spacing

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onQuickAdd: () -> Unit,
    onOpenOccurrence: (Long) -> Unit,
    onOpenTasks: (TaskFilter) -> Unit,
    onOpenHabits: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenReliability: () -> Unit,
) {
    val container = appContainer()
    val vm: HomeViewModel = viewModel { HomeViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val f = formats

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("home_list"),
            contentPadding = PaddingValues(
                start = Spacing.screen, end = Spacing.screen,
                top = Spacing.md, bottom = contentPadding.calculateBottomPadding() + 96.dp,
            ),
        ) {
            item {
                Row(Modifier.statusBarsPadding().fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.weekday(state.today) + ", " + f.date(state.today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(state.greeting, style = MaterialTheme.typography.headlineMedium)
                    }
                    IconButton(onClick = onOpenCalendar) { Icon(Icons.Rounded.CalendarMonth, contentDescription = "Calendar") }
                }
                Spacer(Modifier.height(Spacing.lg))
            }

            item { HeroCard(state, onOpenTasks) }

            if (state.remindersAtRisk != null) {
                item {
                    Spacer(Modifier.height(Spacing.md))
                    LpCard(onClick = onOpenReliability, color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text("Reminders may not reach you", style = MaterialTheme.typography.titleSmall)
                                Text(state.remindersAtRisk!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            state.nextReminder?.let { next ->
                item {
                    Spacer(Modifier.height(Spacing.md))
                    LpCard(onClick = { onOpenOccurrence(next.item.occurrence.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Alarm, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text("Next reminder · ${f.dateTime(next.at, state.today)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(next.item.task.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(Spacing.md))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    item { QuickAction(Icons.Rounded.WbSunny, "Plan my day", onOpenPlan) }
                    item { QuickAction(Icons.Rounded.PendingActions, "Review pending") { onOpenTasks(TaskFilter.PENDING) } }
                    item { QuickAction(Icons.Rounded.NightsStay, "End-of-day review", onOpenReview) }
                    item { QuickAction(Icons.Rounded.CalendarMonth, "Calendar", onOpenCalendar) }
                }
            }

            item {
                SectionHeader("Today", action = "All tasks", onAction = { onOpenTasks(TaskFilter.TODAY) })
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    HomeFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.filter == filter,
                            onClick = { vm.setFilter(filter) },
                            label = { Text(filter.label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                            modifier = Modifier.testTag("home_filter_${filter.name}"),
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
            }

            if (state.visibleItems.isEmpty()) {
                item {
                    val (title, message) = when {
                        state.totalToday == 0 && state.filter == HomeFilter.ALL -> "A clear day" to "Nothing is scheduled for today. Add a task or plan ahead."
                        state.filter == HomeFilter.COMPLETED -> "Nothing completed yet" to "Completed tasks will appear here."
                        state.filter == HomeFilter.OVERDUE -> "No overdue tasks" to "You're on top of things."
                        else -> "All done for today" to "Every task for today is complete."
                    }
                    EmptyState(Icons.Rounded.EventAvailable, title, message)
                }
            } else {
                items(state.visibleItems, key = { it.occurrence.id }) { item ->
                    TaskRow(
                        item = item,
                        onToggle = { vm.toggle(item) },
                        onClick = { onOpenOccurrence(item.occurrence.id) },
                        modifier = Modifier.padding(vertical = 4.dp).animateItem(),
                        showDate = state.filter == HomeFilter.OVERDUE,
                        now = state.now,
                    )
                }
            }

            if (state.highPriority.isNotEmpty()) {
                item { SectionHeader("High priority", action = "See all", onAction = { onOpenTasks(TaskFilter.HIGH_PRIORITY) }) }
                items(state.highPriority, key = { "hp_" + it.occurrence.id }) { item ->
                    TaskRow(item, onToggle = { vm.toggle(item) }, onClick = { onOpenOccurrence(item.occurrence.id) }, modifier = Modifier.padding(vertical = 4.dp), now = state.now)
                }
            }

            item {
                SectionHeader("Habits today", action = if (state.habits.isEmpty()) "Add" else "Open", onAction = onOpenHabits)
                if (state.habits.isEmpty()) {
                    LpCard(onClick = onOpenHabits) {
                        Text("Build a routine", style = MaterialTheme.typography.titleSmall)
                        Text("Track reading, exercise, journaling or anything you want to do regularly.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("${state.habitsDone} of ${state.habits.size} done", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.sm))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(state.habits, key = { it.habit.id }) { h ->
                            HabitChip(h, onToggle = { vm.toggleHabit(h.habit.id) })
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onQuickAdd,
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Add task") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Spacing.screen, bottom = contentPadding.calculateBottomPadding() + Spacing.lg)
                .testTag("fab_add"),
        )
    }
}

@Composable
private fun HeroCard(state: HomeUiState, onOpenTasks: (TaskFilter) -> Unit) {
    val ext = LpTheme.extended
    Surface(shape = MaterialTheme.shapes.extraLarge, color = Color.Transparent, onClick = { onOpenTasks(TaskFilter.TODAY) }) {
        Box(Modifier.background(Brush.linearGradient(ext.heroGradient)).padding(Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    progress = if (state.totalToday == 0) 0f else state.completedToday.toFloat() / state.totalToday,
                    size = 104.dp,
                    trackColor = Color.White.copy(alpha = 0.14f),
                    brush = Brush.sweepGradient(listOf(Color(0xFFB9AEFF), Color(0xFF7FE3D0), Color(0xFFB9AEFF))),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.completedToday}/${state.totalToday}", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("done", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                    }
                }
                Spacer(Modifier.width(Spacing.xl))
                Column(Modifier.weight(1f)) {
                    Text(state.heroMessage, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(Modifier.height(Spacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        HeroStat("Left", state.remainingToday.toString(), Modifier.weight(1f)) { onOpenTasks(TaskFilter.TODAY) }
                        HeroStat("Overdue", state.overdueCount.toString(), Modifier.weight(1f), alert = state.overdueCount > 0) { onOpenTasks(TaskFilter.OVERDUE) }
                        HeroStat("Done", state.completedToday.toString(), Modifier.weight(1f)) { onOpenTasks(TaskFilter.COMPLETED) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier, alert: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.10f), modifier = modifier) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = if (alert) Color(0xFFFFB4A8) else Color.White)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun HabitChip(h: HabitToday, onToggle: () -> Unit) {
    val color = LpTheme.extended.category(h.habit.colorIndex)
    Surface(onClick = onToggle, shape = RoundedCornerShape(16.dp), color = if (h.met) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = if (h.met) 1f else 0.16f)), contentAlignment = Alignment.Center) {
                Icon(if (h.met) Icons.Rounded.Check else AppIcons.habit(h.habit.iconKey), contentDescription = null, tint = if (h.met) MaterialTheme.colorScheme.surface else color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(h.habit.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(
                    if (h.habit.type == com.lifepilot.app.domain.model.HabitType.MEASURABLE) "${Formats.number(h.value)}/${Formats.number(h.habit.target)} ${h.habit.unit}".trim()
                    else if (h.met) "Done" else "Tap to mark done",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
