package com.lifepilot.app.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepilot.app.domain.model.AppSettings
import com.lifepilot.app.domain.scheduling.AlarmKey
import com.lifepilot.app.reminder.CheckState
import com.lifepilot.app.ui.components.BackTopBarTitle
import com.lifepilot.app.ui.components.LpCard
import com.lifepilot.app.ui.navigation.appContainer
import com.lifepilot.app.ui.theme.LpTheme
import com.lifepilot.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Explains, in plain language, everything that can stop or delay reminders on this device and
 * offers a direct path to fix each item. It never claims reminders are guaranteed.
 */
@Composable
fun ReliabilityScreen(onBack: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    var refresh by remember { mutableIntStateOf(0) }
    var testScheduled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refresh++
        // Exact-alarm access may have just changed: re-plan so alarms use the best delivery mode.
        scope.launch { runCatching { container.reconcileAll() } }
    }
    val items = remember(settings, refresh) { container.reliability.check(settings) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    fun open(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    val problems = items.count { it.state == CheckState.PROBLEM }
    val warnings = items.count { it.state == CheckState.WARNING }

    Column(Modifier.fillMaxSize()) {
        BackTopBarTitle("Reminder reliability", onBack, Modifier.statusBarsPadding())
        LazyColumn(
            Modifier.fillMaxSize().testTag("reliability_list"),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                LpCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                    Text(
                        when {
                            problems > 0 -> "Reminders are blocked"
                            warnings > 0 -> "Reminders work, with some limits"
                            else -> "Everything looks good"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "LifePilot schedules reminders with Android's alarm service, so they work offline and with the app closed. " +
                            "Android and your settings decide whether they can appear on time — the checks below show what is currently allowed.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (Build.VERSION.SDK_INT >= 33 && !container.reliability.notificationPermissionGranted()) {
                        Spacer(Modifier.height(Spacing.sm))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.testTag("grant_notifications")) {
                            Text("Allow notifications")
                        }
                    }
                }
            }
            items(items, key = { it.id }) { item ->
                LpCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Top) {
                        val (icon, tint) = when (item.state) {
                            CheckState.OK -> Icons.Rounded.CheckCircle to LpTheme.extended.success
                            CheckState.WARNING -> Icons.Rounded.WarningAmber to LpTheme.extended.warning
                            CheckState.PROBLEM -> Icons.Rounded.Error to MaterialTheme.colorScheme.error
                        }
                        Icon(icon, contentDescription = item.state.name.lowercase(), tint = tint, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall)
                            Text(item.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val action = item.action
                            if (item.actionLabel != null && action != null) {
                                Spacer(Modifier.height(Spacing.sm))
                                FilledTonalButton(onClick = { open(action()) }) { Text(item.actionLabel) }
                            }
                        }
                    }
                }
            }
            item {
                LpCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text("Test a reminder", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (testScheduled) "Scheduled — lock your phone and wait about 15 seconds." else "Schedules a real alarm 15 seconds from now, through the same path as task reminders.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    FilledTonalButton(onClick = {
                        container.scheduler.schedule(AlarmKey.TestReminder, Instant.now().plusSeconds(15))
                        testScheduled = true
                    }, modifier = Modifier.testTag("test_reminder")) { Text("Send test reminder") }
                }
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}
