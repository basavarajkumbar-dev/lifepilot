@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.lifepilot.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifepilot.app.domain.analytics.InsightsCalculator
import com.lifepilot.app.domain.model.OccurrenceItem
import com.lifepilot.app.domain.model.OccurrenceStatus
import com.lifepilot.app.domain.model.Priority
import com.lifepilot.app.ui.common.formats
import com.lifepilot.app.ui.theme.LpTheme
import com.lifepilot.app.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId

/** Round completion checkbox with a 48dp touch target. */
@Composable
fun CompletionCheck(checked: Boolean, color: Color, onToggle: () -> Unit, label: String, modifier: Modifier = Modifier) {
    val fill by animateColorAsState(if (checked) color else Color.Transparent, label = "check")
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(onClick = onToggle)
            .semantics {
                role = Role.Checkbox
                contentDescription = label
                stateDescription = if (checked) "Completed" else "Not completed"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(fill).border(2.dp, if (checked) color else color.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun TaskRow(
    item: OccurrenceItem,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    showDate: Boolean = true,
    now: Instant = Instant.now(),
) {
    val occ = item.occurrence
    val task = item.task
    val ext = LpTheme.extended
    val done = occ.status == OccurrenceStatus.COMPLETED
    val inactive = occ.status == OccurrenceStatus.SKIPPED || occ.status == OccurrenceStatus.MISSED
    val overdue = InsightsCalculator.isOverdue(item, now, ZoneId.systemDefault())
    val f = formats
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = container,
    ) {
        Row(
            Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(start = 4.dp, end = Spacing.lg, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompletionCheck(done, ext.priority(task.priority), onToggle, "Complete ${task.title}")
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done || inactive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val dueText = if (showDate) f.due(occ.dueDate, occ.dueMinute) else occ.dueMinute?.let { f.time(it) }
                    if (dueText != null) {
                        Pill(
                            if (overdue) "Overdue · $dueText" else dueText,
                            if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            icon = Icons.Rounded.Schedule,
                        )
                    }
                    when (occ.status) {
                        OccurrenceStatus.MISSED -> Pill("Missed", MaterialTheme.colorScheme.onSurfaceVariant)
                        OccurrenceStatus.SKIPPED -> Pill("Skipped", MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> {}
                    }
                    item.category?.let { Pill(it.name, ext.category(it.colorIndex)) }
                    if (task.priority >= Priority.HIGH && !done) PriorityBadge(task.priority, showLabel = task.priority == Priority.CRITICAL)
                    if (task.isRecurring) Pill("Repeats", MaterialTheme.colorScheme.primary, icon = Icons.Rounded.Repeat)
                }
            }
            if (occ.status == OccurrenceStatus.PENDING && occ.dueMinute != null) {
                Spacer(Modifier.width(Spacing.sm))
                Icon(
                    if (task.reminder.enabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                    contentDescription = if (task.reminder.enabled) "Reminders on" else "Reminders off",
                    tint = if (task.reminder.enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
