package com.lifepilot.app.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifepilot.app.domain.model.HabitSchedule
import com.lifepilot.app.domain.model.HabitType
import com.lifepilot.app.domain.model.StreakUnit
import com.lifepilot.app.ui.common.AppIcons
import com.lifepilot.app.ui.common.Formats
import com.lifepilot.app.ui.common.formats
import com.lifepilot.app.ui.components.CompletionCheck
import com.lifepilot.app.ui.components.EmptyState
import com.lifepilot.app.ui.components.IconBadge
import com.lifepilot.app.ui.components.LpCard
import com.lifepilot.app.ui.components.SectionHeader
import com.lifepilot.app.ui.navigation.appContainer
import com.lifepilot.app.ui.theme.LpTheme
import com.lifepilot.app.ui.theme.Spacing

@Composable
fun HabitsScreen(contentPadding: PaddingValues, onOpenHabit: (Long) -> Unit, onNewHabit: () -> Unit) {
    val container = appContainer()
    val vm: HabitsViewModel = viewModel { HabitsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("habit_list"),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.md, bottom = contentPadding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                Column(Modifier.statusBarsPadding().padding(top = Spacing.sm, bottom = Spacing.sm)) {
                    Text("Habits", style = MaterialTheme.typography.headlineMedium)
                    if (state.active.isNotEmpty()) {
                        val due = state.active.filter { it.scheduledToday }
                        Text("${due.count { it.todayMet }} of ${due.size} done today", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (!state.loading && state.active.isEmpty() && state.archived.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.SelfImprovement, "Start a habit",
                        "Small daily actions add up. Track reading, exercise, journaling — anything you want to do regularly.",
                        action = { FilledTonalButton(onClick = onNewHabit) { Text("Create a habit") } },
                    )
                }
            }
            items(state.active, key = { it.habit.id }) { s -> HabitCard(s, onClick = { onOpenHabit(s.habit.id) }, onToggle = { vm.toggle(s.habit.id) }) }
            if (state.archived.isNotEmpty()) {
                item { SectionHeader("Archived") }
                items(state.archived, key = { "a${it.habit.id}" }) { s -> HabitCard(s, onClick = { onOpenHabit(s.habit.id) }, onToggle = null) }
            }
        }
        FloatingActionButton(
            onClick = onNewHabit,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = Spacing.screen, bottom = contentPadding.calculateBottomPadding() + Spacing.lg).testTag("fab_add_habit"),
        ) { Icon(Icons.Rounded.Add, contentDescription = "New habit") }
    }
}

@Composable
private fun HabitCard(s: HabitSummary, onClick: () -> Unit, onToggle: (() -> Unit)?) {
    val color = LpTheme.extended.category(s.habit.colorIndex)
    val f = formats
    LpCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(AppIcons.habit(s.habit.iconKey), color)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(s.habit.name, style = MaterialTheme.typography.titleMedium)
                val sub = when {
                    s.habit.schedule == HabitSchedule.WEEKLY_TARGET -> "${s.weekMet}/${s.habit.timesPerWeek} this week"
                    s.habit.type == HabitType.MEASURABLE -> "${Formats.number(s.todayValue)} / ${Formats.number(s.habit.target)} ${s.habit.unit}".trim()
                    !s.scheduledToday -> "Rest day"
                    s.todayMet -> "Done today"
                    else -> "Not yet today"
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (s.streak.current > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = "Current streak ${s.streak.current}" }) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, tint = LpTheme.extended.warning, modifier = Modifier.size(18.dp))
                    Text("${s.streak.current}${if (s.streak.unit == StreakUnit.WEEKS) "w" else ""}", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (onToggle != null && s.scheduledToday) CompletionCheck(s.todayMet, color, onToggle, "Mark ${s.habit.name} done today")
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            s.week.forEach { (date, fraction) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(f.weekday(date, short = true).take(2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    DayDot(fraction, color)
                }
            }
        }
    }
}

@Composable
internal fun DayDot(fraction: Float, color: Color, size: androidx.compose.ui.unit.Dp = 26.dp) {
    val bg = when {
        fraction < 0f -> Color.Transparent
        fraction >= 1f -> color
        fraction > 0f -> color.copy(alpha = 0.25f + 0.5f * fraction)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(bg)
            .then(if (fraction < 0f) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (fraction >= 1f) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(size * 0.6f))
    }
}
