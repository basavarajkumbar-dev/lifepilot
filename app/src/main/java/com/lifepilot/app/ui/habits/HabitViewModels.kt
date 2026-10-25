package com.lifepilot.app.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepilot.app.AppContainer
import com.lifepilot.app.domain.habit.HabitStats
import com.lifepilot.app.domain.model.AppSettings
import com.lifepilot.app.domain.model.Habit
import com.lifepilot.app.domain.model.HabitLog
import com.lifepilot.app.domain.model.HabitSchedule
import com.lifepilot.app.domain.model.HabitType
import com.lifepilot.app.domain.model.StreakInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

data class HabitSummary(
    val habit: Habit,
    val todayValue: Double,
    val todayMet: Boolean,
    val scheduledToday: Boolean,
    val streak: StreakInfo,
    val week: List<Pair<LocalDate, Float>>, // date -> progress fraction (0..1), -1 when not scheduled
    val rate30: Double?,
    val weekMet: Int,
)

data class HabitsUiState(val loading: Boolean = true, val active: List<HabitSummary> = emptyList(), val archived: List<HabitSummary> = emptyList(), val today: LocalDate = LocalDate.now())

private fun summarize(h: Habit, logs: List<HabitLog>, today: LocalDate, weekStart: DayOfWeek): HabitSummary {
    val mine = logs.filter { it.habitId == h.id }
    val byDate = mine.associate { it.date to it.value }
    val start = HabitStats.weekStartOf(today, weekStart)
    val week = (0L until 7L).map { start.plusDays(it) }.map { d ->
        d to if (!HabitStats.isScheduled(h, d) || d.isAfter(today)) -1f else HabitStats.progressFraction(h, byDate[d])
    }
    return HabitSummary(
        habit = h, todayValue = byDate[today] ?: 0.0, todayMet = HabitStats.isMet(h, byDate[today]), scheduledToday = HabitStats.isScheduled(h, today),
        streak = HabitStats.streak(h, mine, today, weekStart), week = week,
        rate30 = HabitStats.completionRate(h, mine, today.minusDays(29), today, weekStart),
        weekMet = HabitStats.metDaysInWeek(h, byDate, today, weekStart),
    )
}

class HabitsViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<HabitsUiState> = combine(container.habits.observeHabits(), container.habits.observeLogs(), container.settings.settings) { habits, logs, s ->
        val today = container.time.today()
        val all = habits.map { summarize(it, logs, today, s.weekStart) }
        HabitsUiState(false, all.filter { !it.habit.archived }, all.filter { it.habit.archived }, today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState())

    fun toggle(id: Long) = viewModelScope.launch { container.habitService.toggle(id) }
}

data class HabitDetailUiState(
    val loading: Boolean = true,
    val summary: HabitSummary? = null,
    val logs: Map<LocalDate, Double> = emptyMap(),
    val settings: AppSettings = AppSettings(),
    val deleted: Boolean = false,
)

class HabitDetailViewModel(private val container: AppContainer, private val habitId: Long) : ViewModel() {
    val state: StateFlow<HabitDetailUiState> = combine(container.habits.observeHabit(habitId), container.habits.observeLogsForHabit(habitId), container.settings.settings) { h, logs, s ->
        if (h == null) HabitDetailUiState(loading = false)
        else HabitDetailUiState(false, summarize(h, logs, container.time.today(), s.weekStart), logs.associate { it.date to it.value }, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitDetailUiState())

    fun toggle(date: LocalDate) = viewModelScope.launch { container.habitService.toggle(habitId, date) }
    fun setProgress(date: LocalDate, value: Double) = viewModelScope.launch { runCatching { container.habitService.setProgress(habitId, date, value) } }
    fun archive(archived: Boolean) = viewModelScope.launch { container.habitService.setArchived(habitId, archived) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { container.habitService.deleteHabit(habitId); onDone() }
}

data class HabitForm(
    val name: String = "",
    val description: String = "",
    val iconKey: String = "book",
    val colorIndex: Int = 0,
    val type: HabitType = HabitType.CHECK,
    val targetText: String = "1",
    val unit: String = "",
    val schedule: HabitSchedule = HabitSchedule.DAILY,
    val weekdays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
    val timesPerWeek: Int = 3,
    val preferredMinute: Int? = null,
    val reminderEnabled: Boolean = false,
)

data class HabitEditorUiState(val loaded: Boolean = false, val isNew: Boolean = true, val form: HabitForm = HabitForm(), val error: String? = null, val done: Boolean = false, val use24h: Boolean = false)

class HabitEditorViewModel(private val container: AppContainer, private val habitId: Long?) : ViewModel() {
    private val _state = MutableStateFlow(HabitEditorUiState())
    val state: StateFlow<HabitEditorUiState> = _state.asStateFlow()
    private var existing: Habit? = null

    init {
        viewModelScope.launch {
            val s = container.settings.current()
            val h = habitId?.let { container.habits.getHabit(it) }
            existing = h
            val form = h?.let {
                HabitForm(
                    it.name, it.description, it.iconKey, it.colorIndex, it.type, com.lifepilot.app.ui.common.Formats.number(it.target), it.unit,
                    it.schedule, it.weekdays.ifEmpty { setOf(DayOfWeek.MONDAY) }, it.timesPerWeek, it.preferredMinute, it.reminderEnabled,
                )
            } ?: HabitForm()
            _state.value = HabitEditorUiState(loaded = true, isNew = h == null, form = form, use24h = s.use24HourTime)
        }
    }

    fun update(t: (HabitForm) -> HabitForm) = _state.update { it.copy(form = t(it.form), error = null) }

    fun save() {
        val f = _state.value.form
        val target = if (f.type == HabitType.CHECK) 1.0 else f.targetText.replace(',', '.').toDoubleOrNull()
        val error = when {
            f.name.isBlank() -> "Give your habit a name"
            target == null || target <= 0 || target > 1_000_000 -> "Enter a target greater than 0"
            f.schedule == HabitSchedule.WEEKDAYS && f.weekdays.isEmpty() -> "Pick at least one day"
            f.reminderEnabled && f.preferredMinute == null -> "Pick a reminder time"
            else -> null
        }
        if (error != null) { _state.update { it.copy(error = error) }; return }
        val now = Instant.now()
        val base = existing
        val habit = Habit(
            id = base?.id ?: 0, name = f.name.trim(), description = f.description.trim(), iconKey = f.iconKey, colorIndex = f.colorIndex,
            type = f.type, target = target!!, unit = f.unit.trim(), schedule = f.schedule, weekdays = f.weekdays, timesPerWeek = f.timesPerWeek,
            preferredMinute = f.preferredMinute, reminderEnabled = f.reminderEnabled && f.preferredMinute != null,
            startDate = base?.startDate ?: container.time.today(), createdAt = base?.createdAt ?: now, archived = base?.archived ?: false,
        )
        viewModelScope.launch {
            runCatching { container.habitService.saveHabit(habit) }
                .onSuccess { _state.update { it.copy(done = true) } }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }
}
