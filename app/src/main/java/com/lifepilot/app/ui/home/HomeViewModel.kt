package com.lifepilot.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepilot.app.AppContainer
import com.lifepilot.app.domain.analytics.InsightsCalculator
import com.lifepilot.app.domain.habit.HabitStats
import com.lifepilot.app.domain.model.Habit
import com.lifepilot.app.domain.model.HabitType
import com.lifepilot.app.domain.model.OccurrenceItem
import com.lifepilot.app.domain.model.OccurrenceStatus
import com.lifepilot.app.domain.model.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class HomeFilter(val label: String) { ALL("All"), PENDING("Pending"), COMPLETED("Completed"), OVERDUE("Overdue") }

data class NextReminder(val item: OccurrenceItem, val at: Instant)
data class HabitToday(val habit: Habit, val value: Double, val met: Boolean)

data class HomeUiState(
    val now: Instant = Instant.now(),
    val today: LocalDate = LocalDate.now(),
    val greeting: String = "Hello",
    val filter: HomeFilter = HomeFilter.ALL,
    val visibleItems: List<OccurrenceItem> = emptyList(),
    val totalToday: Int = 0,
    val completedToday: Int = 0,
    val remainingToday: Int = 0,
    val overdueCount: Int = 0,
    val nextReminder: NextReminder? = null,
    val highPriority: List<OccurrenceItem> = emptyList(),
    val habits: List<HabitToday> = emptyList(),
    val remindersAtRisk: String? = null,
) {
    val habitsDone: Int get() = habits.count { it.met }
    val heroMessage: String get() = when {
        totalToday == 0 -> "Nothing scheduled today"
        completedToday == totalToday -> "Everything done. Nice work."
        completedToday == 0 -> "$remainingToday ${if (remainingToday == 1) "task" else "tasks"} lined up"
        else -> "${totalToday - completedToday} to go"
    }
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val filter = MutableStateFlow(HomeFilter.ALL)
    private val risk = MutableStateFlow<String?>(null)

    /** Ticks every 30 s so "overdue" and the greeting stay current while the screen is open. */
    private val clock = flow {
        while (true) {
            emit(Instant.now())
            delay(30_000)
        }
    }

    private val base = combine(
        container.tasks.observeItems(),
        container.habits.observeHabits(),
        container.habits.observeLogs(),
        clock,
    ) { items, habits, logs, now -> Triple(items, habits to logs, now) }

    val state: StateFlow<HomeUiState> = combine(base, filter, risk) { (items, habitData, now), f, atRisk ->
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        val (habits, logs) = habitData
        val counted = items.filter { it.occurrence.status != OccurrenceStatus.SKIPPED }
        val todayItems = counted.filter { it.occurrence.dueDate == today && it.occurrence.status != OccurrenceStatus.MISSED }
        val overdue = items.filter { InsightsCalculator.isOverdue(it, now, zone) }
        val completedToday = todayItems.count { it.occurrence.status == OccurrenceStatus.COMPLETED }
        val sorter = compareBy<OccurrenceItem>({ it.occurrence.status == OccurrenceStatus.COMPLETED }, { it.occurrence.dueMinute ?: Int.MAX_VALUE }, { -it.task.priority.level })
        val visible = when (f) {
            HomeFilter.ALL -> todayItems.sortedWith(sorter)
            HomeFilter.PENDING -> todayItems.filter { it.occurrence.status == OccurrenceStatus.PENDING }.sortedWith(sorter)
            HomeFilter.COMPLETED -> todayItems.filter { it.occurrence.status == OccurrenceStatus.COMPLETED }.sortedByDescending { it.occurrence.completedAt }
            HomeFilter.OVERDUE -> overdue.sortedWith(compareBy({ it.occurrence.dueDate }, { it.occurrence.dueMinute ?: 0 }))
        }
        val visibleIds = visible.map { it.occurrence.id }.toSet()
        val next = items.filter { it.occurrence.status == OccurrenceStatus.PENDING && it.occurrence.nextReminderAt != null }
            .minByOrNull { it.occurrence.nextReminderAt!! }
            ?.let { NextReminder(it, it.occurrence.nextReminderAt!!) }
        val high = items.filter {
            it.occurrence.status == OccurrenceStatus.PENDING && it.task.priority >= Priority.HIGH &&
                !it.occurrence.dueDate.isAfter(today.plusDays(3)) && it.occurrence.id !in visibleIds
        }.sortedWith(compareBy({ -it.task.priority.level }, { it.occurrence.dueDate })).take(5)
        val habitsToday = habits.filter { !it.archived && HabitStats.isScheduled(it, today) }.map { h ->
            val v = logs.firstOrNull { it.habitId == h.id && it.date == today }?.value ?: 0.0
            HabitToday(h, v, HabitStats.isMet(h, v))
        }
        val hour = now.atZone(zone).hour
        HomeUiState(
            now = now,
            today = today,
            greeting = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Good night" },
            filter = f,
            visibleItems = visible,
            totalToday = todayItems.size,
            completedToday = completedToday,
            remainingToday = todayItems.count { it.occurrence.status == OccurrenceStatus.PENDING },
            overdueCount = overdue.size,
            nextReminder = next,
            highPriority = high,
            habits = habitsToday,
            remindersAtRisk = atRisk,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setFilter(f: HomeFilter) { filter.value = f }

    fun refresh() {
        viewModelScope.launch {
            val s = container.settings.current()
            risk.value = when {
                !s.remindersEnabled -> "Reminders are switched off in Settings."
                !container.reliability.notificationPermissionGranted() -> "Notifications are not allowed. Tap to fix."
                else -> null
            }
        }
    }

    fun toggle(item: OccurrenceItem) {
        viewModelScope.launch {
            if (item.occurrence.status == OccurrenceStatus.PENDING) container.taskService.completeOccurrence(item.occurrence.id)
            else container.taskService.reopenOccurrence(item.occurrence.id)
        }
    }

    fun toggleHabit(id: Long) {
        viewModelScope.launch {
            val habit = container.habits.getHabit(id) ?: return@launch
            if (habit.type == HabitType.CHECK) container.habitService.toggle(id)
            else {
                // Measurable: a tap adds one unit of progress (capped at target); details screen allows exact entry.
                val today = container.time.today()
                val current = container.habits.getLog(id, today)?.value ?: 0.0
                val next = if (current >= habit.target) 0.0 else minOf(habit.target, current + maxOf(1.0, habit.target / 4))
                container.habitService.setProgress(id, today, next)
            }
        }
    }
}
