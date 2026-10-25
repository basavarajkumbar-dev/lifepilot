package com.lifepilot.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepilot.app.AppContainer
import com.lifepilot.app.domain.model.Category
import com.lifepilot.app.domain.model.MissedPolicy
import com.lifepilot.app.domain.model.OccurrenceStatus
import com.lifepilot.app.domain.model.Priority
import com.lifepilot.app.domain.model.RecurrenceFrequency
import com.lifepilot.app.domain.model.RecurrenceRule
import com.lifepilot.app.domain.model.ReminderConfig
import com.lifepilot.app.domain.service.TaskDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

enum class RepeatPreset(val label: String) {
    NONE("Does not repeat"), DAILY("Every day"), WEEKDAYS("Every weekday"), WEEKLY("Every week"),
    MONTHLY("Every month"), YEARLY("Every year"), CUSTOM("Custom…")
}

data class EditorForm(
    val title: String = "",
    val description: String = "",
    val categoryId: Long? = null,
    val priority: Priority = Priority.MEDIUM,
    val tagsText: String = "",
    val estimatedMinutes: Int? = null,
    val dueDate: LocalDate = LocalDate.now(),
    val dueMinute: Int? = null,
    val startDate: LocalDate? = null,
    val startMinute: Int? = null,
    val reminder: ReminderConfig = ReminderConfig(),
    val repeat: RepeatPreset = RepeatPreset.NONE,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.DAILY,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val monthDays: Set<Int> = emptySet(),
    val endDate: LocalDate? = null,
    val missedPolicy: MissedPolicy = MissedPolicy.MARK_MISSED,
) {
    fun recurrence(): RecurrenceRule? = when (repeat) {
        RepeatPreset.NONE -> null
        RepeatPreset.DAILY -> RecurrenceRule(RecurrenceFrequency.DAILY, startDate = dueDate, endDate = endDate, missedPolicy = missedPolicy)
        RepeatPreset.WEEKDAYS -> RecurrenceRule(
            RecurrenceFrequency.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            startDate = dueDate, endDate = endDate, missedPolicy = missedPolicy,
        )
        RepeatPreset.WEEKLY -> RecurrenceRule(RecurrenceFrequency.WEEKLY, weekdays = setOf(dueDate.dayOfWeek), startDate = dueDate, endDate = endDate, missedPolicy = missedPolicy)
        RepeatPreset.MONTHLY -> RecurrenceRule(RecurrenceFrequency.MONTHLY, monthDays = setOf(dueDate.dayOfMonth), startDate = dueDate, endDate = endDate, missedPolicy = missedPolicy)
        RepeatPreset.YEARLY -> RecurrenceRule(RecurrenceFrequency.YEARLY, startDate = dueDate, endDate = endDate, missedPolicy = missedPolicy)
        RepeatPreset.CUSTOM -> RecurrenceRule(
            frequency, interval.coerceAtLeast(1),
            weekdays = if (frequency == RecurrenceFrequency.WEEKLY) weekdays.ifEmpty { setOf(dueDate.dayOfWeek) } else emptySet(),
            monthDays = if (frequency == RecurrenceFrequency.MONTHLY) monthDays.ifEmpty { setOf(dueDate.dayOfMonth) } else emptySet(),
            startDate = dueDate, endDate = endDate, missedPolicy = missedPolicy,
        )
    }

    fun toDraft() = TaskDraft(
        title = title, description = description, categoryId = categoryId, priority = priority,
        tags = tagsText.split(',').map { it.trim() }.filter { it.isNotEmpty() }, estimatedMinutes = estimatedMinutes,
        dueDate = dueDate, dueMinute = dueMinute, startDate = startDate, startMinute = startMinute,
        reminder = reminder.copy(enabled = reminder.enabled && dueMinute != null),
        recurrence = recurrence(),
    )
}

data class EditorUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val form: EditorForm = EditorForm(),
    val errors: List<String> = emptyList(),
    val conflicts: List<String> = emptyList(),
    val saving: Boolean = false,
    val done: Boolean = false,
    val use24h: Boolean = false,
)

class TaskEditorViewModel(
    private val container: AppContainer,
    private val taskId: Long?,
    initialDate: LocalDate?,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    val categories: StateFlow<List<Category>> = container.categories.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val s = container.settings.current()
            val task = taskId?.let { container.tasks.getTask(it) }
            val form = if (task == null) {
                EditorForm(
                    dueDate = initialDate ?: container.time.today(),
                    priority = s.defaultPriority,
                    reminder = s.defaultReminderConfig(),
                    missedPolicy = s.defaultMissedPolicy,
                    categoryId = null,
                )
            } else {
                val occs = container.tasks.occurrencesForTask(task.id)
                val occ = occs.filter { it.status == OccurrenceStatus.PENDING }.minByOrNull { it.dueDate } ?: occs.maxByOrNull { it.seriesDate }
                val rule = task.recurrence
                EditorForm(
                    title = task.title, description = task.description, categoryId = task.categoryId, priority = task.priority,
                    tagsText = task.tags.joinToString(", "), estimatedMinutes = task.estimatedMinutes,
                    dueDate = if (rule != null) rule.startDate else occ?.dueDate ?: container.time.today(),
                    dueMinute = if (rule != null) task.dueMinute else occ?.dueMinute ?: task.dueMinute,
                    startDate = task.startDate, startMinute = task.startMinute, reminder = task.reminder,
                    repeat = rule?.let { presetFor(it) } ?: RepeatPreset.NONE,
                    frequency = rule?.frequency ?: RecurrenceFrequency.DAILY, interval = rule?.interval ?: 1,
                    weekdays = rule?.weekdays ?: emptySet(), monthDays = rule?.monthDays ?: emptySet(),
                    endDate = rule?.endDate, missedPolicy = rule?.missedPolicy ?: s.defaultMissedPolicy,
                )
            }
            _state.value = EditorUiState(loaded = true, isNew = task == null, form = form, use24h = s.use24HourTime)
            refreshConflicts()
        }
    }

    private fun presetFor(rule: RecurrenceRule): RepeatPreset {
        val start = rule.startDate
        if (rule.interval != 1) return RepeatPreset.CUSTOM
        return when (rule.frequency) {
            RecurrenceFrequency.DAILY -> RepeatPreset.DAILY
            RecurrenceFrequency.WEEKLY -> when (rule.weekdays) {
                setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) -> RepeatPreset.WEEKDAYS
                setOf(start.dayOfWeek), emptySet<DayOfWeek>() -> RepeatPreset.WEEKLY
                else -> RepeatPreset.CUSTOM
            }
            RecurrenceFrequency.MONTHLY -> if (rule.monthDays.isEmpty() || rule.monthDays == setOf(start.dayOfMonth)) RepeatPreset.MONTHLY else RepeatPreset.CUSTOM
            RecurrenceFrequency.YEARLY -> RepeatPreset.YEARLY
        }
    }

    fun update(transform: (EditorForm) -> EditorForm) {
        _state.update { it.copy(form = transform(it.form), errors = emptyList()) }
        refreshConflicts()
    }

    fun updateReminder(transform: (ReminderConfig) -> ReminderConfig) = update { it.copy(reminder = transform(it.reminder)) }

    fun addCategory(name: String, colorIndex: Int) {
        viewModelScope.launch {
            runCatching { container.categories.addCategory(name, colorIndex, "star") }
                .onSuccess { id -> update { it.copy(categoryId = id) } }
                .onFailure { e -> _state.update { it.copy(errors = listOf(e.message ?: "Could not add category")) } }
        }
    }

    /** Warns (does not block) when another pending task overlaps the same time slot. */
    private fun refreshConflicts() {
        viewModelScope.launch {
            val f = _state.value.form
            val minute = f.dueMinute
            if (minute == null) { _state.update { it.copy(conflicts = emptyList()) }; return@launch }
            val duration = f.estimatedMinutes ?: 30
            val items = container.tasks.observeItems().first()
            val clashes = items.filter { item ->
                val o = item.occurrence
                val m = o.dueMinute ?: return@filter false
                item.task.id != taskId && o.status == OccurrenceStatus.PENDING && o.dueDate == f.dueDate &&
                    m < minute + duration && minute < m + (item.task.estimatedMinutes ?: 30)
            }.map { it.task.title }.distinct().take(3)
            _state.update { it.copy(conflicts = clashes) }
        }
    }

    fun save() {
        val current = _state.value
        val draft = current.form.toDraft()
        val errors = draft.validate()
        if (errors.isNotEmpty()) { _state.update { it.copy(errors = errors) }; return }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching {
                if (taskId == null) container.taskService.createTask(draft) else container.taskService.updateTask(taskId, draft)
            }.onSuccess { _state.update { it.copy(saving = false, done = true) } }
                .onFailure { e -> _state.update { it.copy(saving = false, errors = listOf(e.message ?: "Could not save task")) } }
        }
    }
}
