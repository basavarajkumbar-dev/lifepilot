package com.lifepilot.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepilot.app.domain.model.Category
import com.lifepilot.app.ui.common.AppIcons
import com.lifepilot.app.ui.components.BackTopBarTitle
import com.lifepilot.app.ui.components.ConfirmDialog
import com.lifepilot.app.ui.components.IconBadge
import com.lifepilot.app.ui.components.LpCard
import com.lifepilot.app.ui.navigation.appContainer
import com.lifepilot.app.ui.tasks.NewCategoryDialog
import com.lifepilot.app.ui.theme.LpTheme
import com.lifepilot.app.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val container = appContainer()
    val categories by container.categories.observeCategories().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BackTopBarTitle("Categories", onBack, Modifier.statusBarsPadding())
            LazyColumn(
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(categories, key = { it.id }) { c ->
                    LpCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(AppIcons.category(c.iconKey), LpTheme.extended.category(c.colorIndex))
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.bodyLarge)
                                if (c.isBuiltIn) Text("Built-in", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { renaming = c }) { Icon(Icons.Rounded.Edit, contentDescription = "Rename ${c.name}") }
                            if (!c.isBuiltIn) IconButton(onClick = { deleting = c }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete ${c.name}") }
                        }
                    }
                }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
        FloatingActionButton(
            onClick = { adding = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(Spacing.screen),
        ) { Icon(Icons.Rounded.Add, contentDescription = "Add category") }
    }

    if (adding) NewCategoryDialog(onCreate = { name, color -> scope.launch { runCatching { container.categories.addCategory(name, color, "star") } } }, onDismiss = { adding = false })
    renaming?.let { c ->
        var name by remember(c.id) { mutableStateOf(c.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename category") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true, label = { Text("Name") }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch { runCatching { container.categories.updateCategory(c.copy(name = name.trim())) } }
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { c ->
        ConfirmDialog(
            "Delete \"${c.name}\"?", "Tasks in this category will move to Other. No tasks are deleted.", "Delete",
            onConfirm = { scope.launch { container.categories.deleteCategory(c.id) } }, onDismiss = { deleting = null },
        )
    }
}

