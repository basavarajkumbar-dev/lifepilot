@file:OptIn(ExperimentalMaterial3Api::class)

package com.lifepilot.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifepilot.app.AppContainer
import com.lifepilot.app.domain.analytics.InsightsCalculator
import com.lifepilot.app.domain.model.OccurrenceItem
import com.lifepilot.app.domain.model.OccurrenceStatus
import com.lifepilot.app.domain.model.Task
import com.lifepilot.app.domain.recurrence.RecurrenceEngine
import com.lifepilot.app.ui.common.formats
import com.lifepilot.app.ui.components.EmptyState
import com.lifepilot.app.ui.components.LpCard
import com.lifepilot.app.ui.components.Pill
import com.lifepilot.app.ui.components.SectionHeader
import com.lifepilot.app.ui.components.TaskRow
import com.lifepilot.app.ui.navigation.appContainer
import com.lifepilot.app.ui.theme.LpTheme
import com.lifepilot.app.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class CalendarMode(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month") }

/** A future occurrence of a recurring task that has not been generated yet (display only). */
data class ProjectedOccurrence(val task: Task, val date: LocalDate)

data class DayCounts(val pending: Int, val done: Int, val overdue: Int, val projected: Int) {
    val total get() = pending + done + projected
}

data class CalendarUiState(
    val mode: CalendarMode = CalendarMode.MONTH,
    val selected: LocalDate = LocalDate.now(),
    val month: YearMonth = YearMonth.now(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val counts: Map<LocalDate, DayCounts> = emptyMap(),
    val agenda: List<OccurrenceItem> = emptyList(),
    val projected: List<ProjectedOccurrence> = emptyList(),
)

private data class CalControls(val mode: CalendarMode, val selected: LocalDate)

class CalendarViewModel(private val container: AppContainer) : ViewModel() {
    private val controls = MutableStateFlow(CalControls(CalendarMode.MONTH, LocalDate.now()))

    val state: StateFlow<CalendarUiState> = combine(container.tasks.observeItems(), container.settings.settings, controls) { items, s, c ->
        val today = LocalDate.now()
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val month = YearMonth.from(c.selected)
        // Range covering everything that may be visible (month grid plus week overflow).
        val rangeStart = month.atDay(1).minusDays(7)
        val rangeEnd = month.atEndOfMonth().plusDays(7)
        val visible = items.filter { it.occurrence.status != OccurrenceStatus.SKIPPED && !it.occurrence.dueDate.isBefore(rangeStart) && !it.occurrence.dueDate.isAfter(rangeEnd) }
        val materialized = items.groupBy { it.task.id }.mapValues { (_, l) -> l.map { it.occurrence.seriesDate }.toSet() }
        val recurring = items.map { it.task }.distinctBy { it.id }.filter { it.recurrence != null }
        val projected = recurring.flatMap { t ->
            val from = maxOf(rangeStart, today.plusDays(1))
            if (from.isAfter(rangeEnd)) emptyList()
            else RecurrenceEngine.occurrencesBetween(t.recurrence!!, from, rangeEnd, limit = 62)
                .filter { it !in (materialized[t.id] ?: emptySet()) }
                .map { ProjectedOccurrence(t, it) }
        }
        val counts = HashMap<LocalDate, DayCounts>()
        visible.forEach { item ->
            val d = item.occurrence.dueDate
            val cur = counts[d] ?: DayCounts(0, 0, 0, 0)
            counts[d] = when {
                item.occurrence.status == OccurrenceStatus.COMPLETED -> cur.copy(done = cur.done + 1)
                InsightsCalculator.isOverdue(item, now, zone) -> cur.copy(pending = cur.pending + 1, overdue = cur.overdue + 1)
                item.occurrence.status == OccurrenceStatus.PENDING -> cur.copy(pending = cur.pending + 1)
                else -> cur
            }
        }
        projected.forEach { p -> val cur = counts[p.date] ?: DayCounts(0, 0, 0, 0); counts[p.date] = cur.copy(projected = cur.projected + 1) }
        CalendarUiState(
            mode = c.mode, selected = c.selected, month = month, weekStart = s.weekStart, counts = counts,
            agenda = visible.filter { it.occurrence.dueDate == c.selected }
                .sortedWith(compareBy({ it.occurrence.status == OccurrenceStatus.COMPLETED }, { it.occurrence.dueMinute ?: Int.MAX_VALUE })),
            projected = projected.filter { it.date == c.selected }.sortedBy { it.task.dueMinute ?: Int.MAX_VALUE },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun select(date: LocalDate) { controls.value = controls.value.copy(selected = date) }
    fun setMode(mode: CalendarMode) { controls.value = controls.value.copy(mode = mode) }
    fun shift(direction: Int) {
        val c = controls.value
        val d = when (c.mode) {
            CalendarMode.DAY -> c.selected.plusDays(direction.toLong())
            CalendarMode.WEEK -> c.selected.plusWeeks(direction.toLong())
            CalendarMode.MONTH -> c.selected.plusMonths(direction.toLong())
        }
        select(d)
    }

    fun toggle(item: OccurrenceItem) = viewModelScope.launch {
        if (item.occurrence.status == OccurrenceStatus.PENDING) container.taskService.completeOccurrence(item.occurrence.id)
        else container.taskService.reopenOccurrence(item.occurrence.id)
    }
}

@Composable
fun CalendarScreen(onBack: () -> Unit, onOpenOccurrence: (Long) -> Unit, onNewTask: (LocalDate) -> Unit) {
    val container = appContainer()
    val vm: CalendarViewModel = viewModel { CalendarViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val f = formats
    val today = LocalDate.now()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 120.dp)) {
            item {
                com.lifepilot.app.ui.components.BackTopBarTitle("Calendar", onBack, Modifier.statusBarsPadding()) {
                    TextButton(onClick = { vm.select(today) }) { Text("Today") }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    CalendarMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(selected = state.mode == m, onClick = { vm.setMode(m) }, shape = SegmentedButtonDefaults.itemShape(i, CalendarMode.entries.size)) { Text(m.label) }
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous") }
                    val title = when (state.mode) {
                        CalendarMode.MONTH -> state.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + state.month.year
                        CalendarMode.WEEK -> {
                            val ws = state.selected.with(TemporalAdjusters.previousOrSame(state.weekStart))
                            "${f.date(ws)} – ${f.date(ws.plusDays(6))}"
                        }
                        CalendarMode.DAY -> f.weekday(state.selected) + ", " + f.date(state.selected)
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    IconButton(onClick = { vm.shift(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next") }
                }
            }
            item {
                when (state.mode) {
                    CalendarMode.MONTH -> MonthGrid(state, today, vm::select)
                    CalendarMode.WEEK -> WeekStrip(state, today, vm::select)
                    CalendarMode.DAY -> {}
                }
            }
            item { SectionHeader(if (state.selected == today) "Today's agenda" else "Agenda · ${f.relativeDate(state.selected)}") }
            if (state.agenda.isEmpty() && state.projected.isEmpty()) {
                item {
                    EmptyState(Icons.Rounded.EventAvailable, "Nothing scheduled", "Tap + to plan something for ${f.relativeDate(state.selected).lowercase()}.")
                }
            }
            items(state.agenda, key = { it.occurrence.id }) { item ->
                TaskRow(item, onToggle = { vm.toggle(item) }, onClick = { onOpenOccurrence(item.occurrence.id) }, modifier = Modifier.padding(vertical = 4.dp), showDate = false)
            }
            items(state.projected, key = { "p_${it.task.id}_${it.date}" }) { p ->
                LpCard(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Repeat, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(p.task.title, style = MaterialTheme.typography.bodyLarge)
                            Text(p.task.dueMinute?.let { f.time(it) } ?: "All day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Pill("Upcoming", MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
        FloatingActionButton(
            onClick = { onNewTask(state.selected) },
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(Spacing.screen),
        ) { Icon(Icons.Rounded.Add, contentDescription = "Add task on this day") }
    }
}

@Composable
private fun MonthGrid(state: CalendarUiState, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    val days = (0L until 7L).map { state.weekStart.plus(it) }
    LpCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.sm)) {
        Row(Modifier.fillMaxWidth()) {
            days.forEach { Text(it.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        val first = state.month.atDay(1)
        val lead = ((first.dayOfWeek.value - state.weekStart.value) + 7) % 7
        val rows = (lead + state.month.lengthOfMonth() + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val date = first.plusDays((r * 7 + c - lead).toLong())
                    Box(Modifier.weight(1f).aspectRatio(0.9f), contentAlignment = Alignment.Center) {
                        DayCell(date, state.counts[date], selected = date == state.selected, today = date == today, inMonth = YearMonth.from(date) == state.month, onClick = { onSelect(date) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(state: CalendarUiState, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    val start = state.selected.with(TemporalAdjusters.previousOrSame(state.weekStart))
    LpCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.sm)) {
        Row(Modifier.fillMaxWidth()) {
            (0L until 7L).map { start.plusDays(it) }.forEach { date ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.aspectRatio(0.8f), contentAlignment = Alignment.Center) {
                        DayCell(date, state.counts[date], date == state.selected, date == today, true) { onSelect(date) }
                    }
                    val n = state.counts[date]?.total ?: 0
                    Text(if (n == 0) "" else "$n", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, counts: DayCounts?, selected: Boolean, today: Boolean, inMonth: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val bg = when { selected -> primary; today -> primary.copy(alpha = 0.16f); else -> Color.Transparent }
    val fg = when { selected -> MaterialTheme.colorScheme.onPrimary; !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f); else -> MaterialTheme.colorScheme.onSurface }
    val description = buildString {
        append(date.toString())
        counts?.let { append(", ${it.pending} pending, ${it.done} done"); if (it.overdue > 0) append(", ${it.overdue} overdue") }
    }
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp).semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Text("${date.dayOfMonth}", style = MaterialTheme.typography.bodyMedium, color = fg, fontWeight = if (today) FontWeight.SemiBold else FontWeight.Normal)
        }
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(5.dp)) {
            if (counts != null) {
                if (counts.overdue > 0) Dot(MaterialTheme.colorScheme.error)
                if (counts.pending - counts.overdue > 0) Dot(primary)
                if (counts.projected > 0) Dot(primary.copy(alpha = 0.4f))
                if (counts.done > 0) Dot(LpTheme.extended.success)
            }
        }
    }
}

@Composable
private fun Dot(color: Color) = Box(Modifier.size(5.dp).clip(CircleShape).background(color))
