@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.lifepilot.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifepilot.app.domain.model.Priority
import com.lifepilot.app.ui.theme.EyebrowStyle
import com.lifepilot.app.ui.theme.LpTheme
import com.lifepilot.app.ui.theme.Spacing

/** Elevated content card with a hairline border; the basic surface of the design system. */
@Composable
fun LpCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val border = BorderStroke(1.dp, LpTheme.extended.cardBorder)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, border = border, tonalElevation = 0.dp) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = border, tonalElevation = 0.dp) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = EyebrowStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = Spacing.sm)) { Text(action) }
        }
    }
}

/** Circular progress ring with smooth animation. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    stroke: Dp = 10.dp,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    brush: Brush = Brush.sweepGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary)),
    content: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            val topLeft = Offset(sw / 2, sw / 2)
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(sw))
            if (animated > 0f) drawArc(brush, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun PriorityBadge(priority: Priority, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    val c = LpTheme.extended.priority(priority)
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(c.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics { contentDescription = "${priority.label} priority" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Flag, contentDescription = null, tint = c, modifier = Modifier.size(12.dp))
        if (showLabel) {
            Spacer(Modifier.width(4.dp))
            Text(priority.label, style = MaterialTheme.typography.labelSmall, color = c)
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun IconBadge(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(Spacing.lg))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.xs))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(Spacing.lg))
            action()
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary, onClick: (() -> Unit)? = null) {
    LpCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.md)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = accent, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun BackTopBarTitle(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Simple vertical bar chart drawn on Canvas (no chart library). */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    secondaryValues: List<Float>? = null,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    highlightLast: Boolean = false,
    height: Dp = 140.dp,
    contentDescription: String,
) {
    val max = (values + (secondaryValues ?: emptyList())).maxOrNull()?.takeIf { it > 0f } ?: 1f
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    Column(modifier.semantics { this.contentDescription = contentDescription }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val groups = if (secondaryValues != null) 2 else 1
            val barW = (slot * 0.56f / groups).coerceAtMost(28.dp.toPx())
            val radius = androidx.compose.ui.geometry.CornerRadius(barW / 2, barW / 2)
            values.forEachIndexed { i, v ->
                val groupW = barW * groups + (groups - 1) * 3.dp.toPx()
                var x = slot * i + (slot - groupW) / 2
                drawRoundRect(track, Offset(x, 0f), Size(groupW, size.height), radius)
                val h = (v / max) * size.height
                val c = if (highlightLast && i == values.lastIndex) barColor else barColor.copy(alpha = if (highlightLast) 0.55f else 1f)
                if (h > 0f) drawRoundRect(c, Offset(x, size.height - h), Size(barW, h), radius)
                if (secondaryValues != null) {
                    x += barW + 3.dp.toPx()
                    val h2 = (secondaryValues.getOrElse(i) { 0f } / max) * size.height
                    if (h2 > 0f) drawRoundRect(secondaryColor, Offset(x, size.height - h2), Size(barW, h2), radius)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = labelColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1) }
        }
    }
}

/** Horizontal labelled progress bar. */
@Composable
fun LabeledBar(label: String, value: String, fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "bar")
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))) {
            Box(Modifier.fillMaxWidth(animated).height(8.dp).clip(RoundedCornerShape(50)).background(color))
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}
