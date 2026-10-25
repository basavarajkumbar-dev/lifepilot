package com.lifepilot.app.ui.insights

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifepilot.app.AppContainer
import com.lifepilot.app.domain.analytics.Insights
import com.lifepilot.app.domain.analytics.InsightsCalculator
import com.lifepilot.app.domain.model.Category
import com.lifepilot.app.domain.model.Habit
import com.lifepilot.app.domain.model.HabitLog
import com.lifepilot.app.domain.model.OccurrenceItem
import com.lifepilot.app.domain.model.ReminderEventType
import com.lifepilot.app.ui.components.BarChart
import com.lifepilot.app.ui.components.EmptyState
import com.lifepilot.app.ui.components.LabeledBar
import com.lifepilot.app.ui.components.LpCard
import com.lifepilot.app.ui.components.ProgressRing
import com.lifepilot.app.ui.components.SectionHeader
import com.lifepilot.app.ui.components.StatTile
import com.lifepilot.app.ui.navigation.appContainer
import com.lifepilot.app.ui.theme.LpTheme
import com.lifepilot.app.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

class InsightsViewModel(container: AppContainer) : ViewModel() {
    private val since = LocalDate.now().minusDays(40).atStartOfDay(ZoneId.systemDefault()).toInstant()

    private data class Inputs(
        val items: List<OccurrenceItem>,
        val categories: List<Category>,
        val habits: List<Habit>,
        val logs: List<HabitLog>,
    )

    private val inputs = combine(
        container.tasks.observeItems(),
        container.categories.observeCategories(),
        container.habits.observeHabits(),
        container.habits.observeLogs(),
    ) { items, cats, habits, logs -> Inputs(items, cats, habits, logs) }

    val state: StateFlow<Insights?> = combine(
        inputs,
        container.tasks.observeLogsSince(since).map { l -> l.filter { it.type == ReminderEventType.SNOOZED }.map { it.at } },
        container.settings.settings,
    ) { i, snoozes, s ->
        InsightsCalculator.compute(
            items = i.items,
            categories = i.categories,
            habits = i.habits,
            habitLogs = i.logs,
            snoozeEventTimes = snoozes,
            now = Instant.now(), zone = ZoneId.systemDefault(), weekStart = s.weekStart,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun InsightsScreen(contentPadding: PaddingValues) {
    val container = appContainer()
    val vm: InsightsViewModel = viewModel { InsightsViewModel(container) }
    val insights by vm.state.collectAsStateWithLifecycle()
    val ext = LpTheme.extended

    LazyColumn(
        Modifier.fillMaxSize().testTag("insights_list"),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.md, bottom = contentPadding.calculateBottomPadding() + Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = Spacing.sm)) {
                Text("Insights", style = MaterialTheme.typography.headlineMedium)
                Text("Calculated from your own records, on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val i = insights ?: return@LazyColumn
        if (i.isEmpty) {
            item { EmptyState(Icons.Rounded.Insights, "No data yet", "Complete a few tasks and habits — your trends and statistics will appear here.") }
            return@LazyColumn
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                RateCard("Today", i.todayRate, "${i.todayCompleted}/${i.todayTotal} done", Modifier.weight(1f))
                RateCard("This week", i.weekRate, "${i.weekCompleted}/${i.weekTotal} done", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile("Overdue", i.overdueCount.toString(), Modifier.weight(1f), accent = if (i.overdueCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                StatTile("Missed (30d)", i.missedLast30.toString(), Modifier.weight(1f), accent = MaterialTheme.colorScheme.onSurface)
                StatTile("Rescheduled", i.rescheduledLast30.toString(), Modifier.weight(1f), accent = ext.warning)
            }
        }
        item {
            LpCard(Modifier.fillMaxWidth()) {
                Text("Completed vs created", style = MaterialTheme.typography.titleSmall)
                Text("Last 7 days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.md))
                val days = i.createdVsCompleted
                BarChart(
                    values = days.map { it.completed.toFloat() },
                    secondaryValues = days.map { it.created.toFloat() },
                    labels = days.map { it.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2) },
                    contentDescription = "Completed versus created tasks for the last 7 days: " + days.joinToString { "${it.date}: ${it.completed} completed, ${it.created} created" },
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Legend(MaterialTheme.colorScheme.primary, "Completed ${days.sumOf { it.completed }}")
                    Spacer(Modifier.width(Spacing.lg))
                    Legend(MaterialTheme.colorScheme.secondary, "Created ${days.sumOf { it.created }}")
                }
            }
        }
        item {
            LpCard(Modifier.fillMaxWidth()) {
                Text("Completion trend", style = MaterialTheme.typography.titleSmall)
                Text("Tasks completed per day, last 14 days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.md))
                val t = i.completionTrend
                BarChart(
                    values = t.map { it.completed.toFloat() },
                    labels = t.mapIndexed { idx, d -> if (idx % 2 == t.lastIndex % 2) d.date.dayOfMonth.toString() else "" },
                    highlightLast = true, height = 110.dp,
                    contentDescription = "Tasks completed per day: " + t.joinToString { "${it.date}: ${it.completed}" },
                )
            }
        }
        item {
            LpCard(Modifier.fillMaxWidth()) {
                Text("Most productive days", style = MaterialTheme.typography.titleSmall)
                Text(
                    i.mostProductiveDay?.let { "You complete the most on ${it.getDisplayName(TextStyle.FULL, Locale.getDefault())}s" } ?: "Not enough completions yet",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.md))
                BarChart(
                    values = i.weekdays.map { it.completed.toFloat() },
                    labels = i.weekdays.map { it.day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2) },
                    barColor = ext.category(5), height = 100.dp,
                    contentDescription = "Completions by weekday: " + i.weekdays.joinToString { "${it.day}: ${it.completed}" },
                )
            }
        }
        if (i.categories.isNotEmpty()) {
            item {
                LpCard(Modifier.fillMaxWidth()) {
                    Text("By category", style = MaterialTheme.typography.titleSmall)
                    Text("Completion in the last 30 days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.sm))
                    i.categories.forEach { c ->
                        LabeledBar(c.name, "${c.completed}/${c.total}", c.rate.toFloat(), ext.category(c.colorIndex))
                    }
                }
            }
        }
        item { SectionHeader("Habits") }
        item {
            LpCard(Modifier.fillMaxWidth()) {
                if (i.habitConsistency.isEmpty()) {
                    Text("No habit data yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing((i.averageHabitConsistency ?: 0.0).toFloat(), size = 64.dp, stroke = 7.dp) {
                            Text("${((i.averageHabitConsistency ?: 0.0) * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.width(Spacing.lg))
                        Text("Average habit consistency over the last 30 days", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    i.habitConsistency.forEachIndexed { idx, h -> LabeledBar(h.name, "${(h.rate * 100).toInt()}%", h.rate.toFloat(), ext.category(idx)) }
                }
            }
        }
        item {
            Text(
                "${i.totalCompletedAllTime} tasks completed in total · ${i.snoozesLast30} snoozes in the last 30 days",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RateCard(title: String, rate: Double?, detail: String, modifier: Modifier) {
    LpCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing((rate ?: 0.0).toFloat(), size = 56.dp, stroke = 6.dp) {
                Text(rate?.let { "${(it * 100).toInt()}%" } ?: "—", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
