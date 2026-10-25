@file:OptIn(ExperimentalMaterial3Api::class)

package com.lifepilot.app.ui.tasks

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifepilot.app.ui.components.ConfirmDialog
import com.lifepilot.app.ui.components.EmptyState
import com.lifepilot.app.ui.components.SectionHeader
import com.lifepilot.app.ui.components.TaskRow
import com.lifepilot.app.ui.navigation.appContainer
import com.lifepilot.app.ui.theme.LpTheme
import com.lifepilot.app.ui.theme.Spacing
import java.time.Instant

@Composable
fun TasksScreen(
    initialFilter: TaskFilter,
    contentPadding: PaddingValues,
    onQuickAdd: () -> Unit,
    onNewTask: () -> Unit,
    onOpenOccurrence: (Long) -> Unit,
    onOpenCalendar: () -> Unit,
) {
    val container = appContainer()
    val vm: TasksViewModel = viewModel(key = "tasks_${initialFilter.name}") { TasksViewModel(container, initialFilter) }
    val state by vm.state.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val now = remember(state) { Instant.now() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Header / selection bar
            Row(Modifier.statusBarsPadding().fillMaxWidth().padding(start = Spacing.screen, end = 8.dp, top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                if (state.selectionMode) {
                    IconButton(onClick = vm::clearSelection) { Icon(Icons.Rounded.Close, contentDescription = "Cancel selection") }
                    Text("${state.selected.size} selected", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = vm::completeSelected) { Icon(Icons.Rounded.DoneAll, contentDescription = "Complete selected") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete selected") }
                } else {
                    Text("Tasks", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onOpenCalendar) { Icon(Icons.Rounded.CalendarMonth, contentDescription = "Calendar") }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            TaskSort.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.label, color = if (s == state.sort) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                    onClick = { vm.setSort(s); sortMenu = false },
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Search tasks, tags, categories") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search") } },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedBorderColor = LpTheme.extended.cardBorder,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm).testTag("task_search"),
            )

            LazyRow(contentPadding = PaddingValues(horizontal = Spacing.screen), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(TaskFilter.entries) { f ->
                    FilterChip(
                        selected = state.filter == f, onClick = { vm.setFilter(f) }, label = { Text(f.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    )
                }
            }
            if (state.categories.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = Spacing.screen), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    item { FilterChip(selected = state.categoryId == null, onClick = { vm.setCategory(null) }, label = { Text("All categories") }) }
                    items(state.categories, key = { it.id }) { c ->
                        val color = LpTheme.extended.category(c.colorIndex)
                        FilterChip(
                            selected = state.categoryId == c.id,
                            onClick = { vm.setCategory(if (state.categoryId == c.id) null else c.id) },
                            label = { Text(c.name) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.2f)),
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("task_list"),
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = contentPadding.calculateBottomPadding() + 96.dp),
            ) {
                if (!state.loading && state.isEmpty) {
                    item {
                        when {
                            state.totalCount == 0 -> EmptyState(
                                Icons.Rounded.TaskAlt, "No tasks yet", "Capture what's on your mind. Tasks with a time can remind you until they're done.",
                                action = { FilledTonalButton(onClick = onNewTask) { Text("Create a task") } },
                            )
                            state.query.isNotEmpty() -> EmptyState(Icons.Rounded.SearchOff, "No matches", "Nothing matches \"${state.query}\". Try a different word or filter.")
                            else -> EmptyState(Icons.Rounded.TaskAlt, "Nothing here", "No tasks match the ${state.filter.label.lowercase()} filter.")
                        }
                    }
                }
                state.sections.forEach { section ->
                    item(key = "h_${section.title}") { SectionHeader("${section.title} · ${section.items.size}") }
                    items(section.items, key = { "${section.title}_${it.occurrence.id}" }) { item ->
                        TaskRow(
                            item = item,
                            onToggle = { vm.toggle(item) },
                            onClick = { if (state.selectionMode) vm.toggleSelection(item.occurrence.id) else onOpenOccurrence(item.occurrence.id) },
                            onLongClick = { vm.toggleSelection(item.occurrence.id) },
                            selected = item.occurrence.id in state.selected,
                            modifier = Modifier.padding(vertical = 4.dp).animateItem(),
                            now = now,
                        )
                    }
                }
                item { Spacer(Modifier.height(Spacing.lg)) }
            }
        }

        if (!state.selectionMode) {
            FloatingActionButton(
                onClick = onQuickAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = Spacing.screen, bottom = contentPadding.calculateBottomPadding() + Spacing.lg)
                    .testTag("fab_add_tasks"),
            ) { Icon(Icons.Rounded.Add, contentDescription = "Add task") }
        }
    }

    if (confirmDelete) {
        val n = state.selected.size
        ConfirmDialog(
            title = "Delete $n ${if (n == 1) "task" else "tasks"}?",
            message = "The selected tasks, their reminders and history will be permanently removed. Recurring tasks are deleted with their whole series.",
            confirmLabel = "Delete",
            onConfirm = vm::deleteSelected,
            onDismiss = { confirmDelete = false },
        )
    }
}
