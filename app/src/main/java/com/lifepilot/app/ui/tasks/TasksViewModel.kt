package com.lifepilot.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepilot.app.AppContainer
import com.lifepilot.app.domain.analytics.InsightsCalculator
import com.lifepilot.app.domain.model.Category
import com.lifepilot.app.domain.model.OccurrenceItem
import com.lifepilot.app.domain.model.OccurrenceStatus
import com.lifepilot.app.domain.model.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class TaskFilter(val label: String) {
    ALL("All"), TODAY("Today"), PENDING("Pending"), UPCOMING("Upcoming"), OVERDUE("Overdue"), HIGH_PRIORITY("High priority"), COMPLETED("Completed")
}

enum class TaskSort(val label: String) { DUE("Due date"), PRIORITY("Priority"), CREATED("Recently created"), TITLE("Title") }

data class TaskSection(val title: String, val items: List<OccurrenceItem>)

data class TasksUiState(
    val query: String = "",
    val filter: TaskFilter = TaskFilter.ALL,
    val sort: TaskSort = TaskSort.DUE,
    val categoryId: Long? = null,
    val categories: List<Category> = emptyList(),
    val sections: List<TaskSection> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val loading: Boolean = true,
    val totalCount: Int = 0,
) {
    val selectionMode: Boolean get() = selected.isNotEmpty()
    val isEmpty: Boolean get() = sections.all { it.items.isEmpty() }
}

private data class Controls(val query: String, val filter: TaskFilter, val sort: TaskSort, val categoryId: Long?, val selected: Set<Long>)

class TasksViewModel(private val container: AppContainer, initialFilter: TaskFilter) : ViewModel() {

    private val controls = MutableStateFlow(Controls("", initialFilter, TaskSort.DUE, null, emptySet()))

    val state: StateFlow<TasksUiState> = combine(container.tasks.observeItems(), container.categories.observeCategories(), controls) { items, cats, c ->
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        val q = c.query.trim().lowercase()
        val filtered = items.asSequence()
            .filter { c.categoryId == null || it.task.categoryId == c.categoryId }
            .filter {
                q.isEmpty() || it.task.title.lowercase().contains(q) || it.task.description.lowercase().contains(q) ||
                    it.task.tags.any { t -> t.lowercase().contains(q) } || (it.category?.name?.lowercase()?.contains(q) == true)
            }
            .filter { item ->
                val o = item.occurrence
                val pending = o.status == OccurrenceStatus.PENDING
                when (c.filter) {
                    TaskFilter.ALL -> true
                    TaskFilter.TODAY -> o.dueDate == today && o.status != OccurrenceStatus.SKIPPED
                    TaskFilter.PENDING -> pending
                    TaskFilter.UPCOMING -> pending && o.dueDate.isAfter(today)
                    TaskFilter.OVERDUE -> InsightsCalculator.isOverdue(item, now, zone)
                    TaskFilter.HIGH_PRIORITY -> pending && item.task.priority >= Priority.HIGH
                    TaskFilter.COMPLETED -> o.status == OccurrenceStatus.COMPLETED
                }
            }
            .toList()

        val sections = if (c.sort != TaskSort.DUE || c.filter == TaskFilter.COMPLETED) {
            val sorted = when (c.sort) {
                TaskSort.PRIORITY -> filtered.sortedWith(compareBy({ -it.task.priority.level }, { it.occurrence.dueDate }, { it.occurrence.dueMinute ?: Int.MAX_VALUE }))
                TaskSort.CREATED -> filtered.sortedByDescending { it.task.createdAt }
                TaskSort.TITLE -> filtered.sortedBy { it.task.title.lowercase() }
                TaskSort.DUE -> filtered.sortedByDescending { it.occurrence.completedAt ?: it.occurrence.updatedAt }
            }
            listOf(TaskSection(if (c.filter == TaskFilter.COMPLETED) "Completed" else c.sort.label, sorted))
        } else groupByDue(filtered, today, now, zone)

        TasksUiState(
            query = c.query, filter = c.filter, sort = c.sort, categoryId = c.categoryId, categories = cats,
            sections = sections.filter { it.items.isNotEmpty() },
            selected = c.selected.filter { id -> items.any { it.occurrence.id == id } }.toSet(),
            loading = false, totalCount = items.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState(filter = initialFilter))

    private fun groupByDue(items: List<OccurrenceItem>, today: LocalDate, now: Instant, zone: ZoneId): List<TaskSection> {
        val byTime = compareBy<OccurrenceItem>({ it.occurrence.dueDate }, { it.occurrence.dueMinute ?: Int.MAX_VALUE }, { -it.task.priority.level })
        val pending = items.filter { it.occurrence.status == OccurrenceStatus.PENDING }
        val done = items.filter { it.occurrence.status != OccurrenceStatus.PENDING }
        val overdue = pending.filter { InsightsCalculator.isOverdue(it, now, zone) }.sortedWith(byTime)
        val rest = pending - overdue.toSet()
        return listOf(
            TaskSection("Overdue", overdue),
            TaskSection("Today", rest.filter { it.occurrence.dueDate == today }.sortedWith(byTime)),
            TaskSection("Tomorrow", rest.filter { it.occurrence.dueDate == today.plusDays(1) }.sortedWith(byTime)),
            TaskSection("Next 7 days", rest.filter { it.occurrence.dueDate.isAfter(today.plusDays(1)) && !it.occurrence.dueDate.isAfter(today.plusDays(7)) }.sortedWith(byTime)),
            TaskSection("Later", rest.filter { it.occurrence.dueDate.isAfter(today.plusDays(7)) }.sortedWith(byTime)),
            TaskSection("Earlier", rest.filter { it.occurrence.dueDate.isBefore(today) }.sortedWith(byTime)),
            TaskSection("Done, skipped & missed", done.sortedByDescending { it.occurrence.completedAt ?: it.occurrence.updatedAt }.take(100)),
        )
    }

    fun setQuery(q: String) = controls.update { it.copy(query = q) }
    fun setFilter(f: TaskFilter) = controls.update { it.copy(filter = f) }
    fun setSort(s: TaskSort) = controls.update { it.copy(sort = s) }
    fun setCategory(id: Long?) = controls.update { it.copy(categoryId = id) }
    fun toggleSelection(id: Long) = controls.update { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id) }
    fun clearSelection() = controls.update { it.copy(selected = emptySet()) }

    fun toggle(item: OccurrenceItem) {
        viewModelScope.launch {
            if (item.occurrence.status == OccurrenceStatus.PENDING) container.taskService.completeOccurrence(item.occurrence.id)
            else container.taskService.reopenOccurrence(item.occurrence.id)
        }
    }

    fun completeSelected() {
        val ids = state.value.selected
        clearSelection()
        viewModelScope.launch { container.taskService.completeOccurrences(ids) }
    }

    /** Deletes the tasks behind the selected entries (including whole recurring series). */
    fun deleteSelected() {
        val ids = state.value.selected
        val taskIds = state.value.sections.flatMap { it.items }.filter { it.occurrence.id in ids }.map { it.task.id }.toSet()
        clearSelection()
        viewModelScope.launch { container.taskService.deleteTasks(taskIds) }
    }

    private inline fun MutableStateFlow<Controls>.update(f: (Controls) -> Controls) { value = f(value) }
}
