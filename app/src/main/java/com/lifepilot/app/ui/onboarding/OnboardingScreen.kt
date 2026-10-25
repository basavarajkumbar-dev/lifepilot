package com.lifepilot.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lifepilot.app.ui.components.LpCard
import com.lifepilot.app.ui.navigation.appContainer
import com.lifepilot.app.ui.theme.LpTheme
import com.lifepilot.app.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen() {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableIntStateOf(0) }
    val finish: () -> Unit = {
        scope.launch {
            container.settings.update { it.copy(onboardingCompleted = true) }
            runCatching { container.reconcileAll() }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(Spacing.xl)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                repeat(3) { i ->
                    Box(Modifier.height(6.dp).width(if (i == page) 24.dp else 6.dp).clip(RoundedCornerShape(50))
                        .background(if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)))
                }
            }
            if (page < 2) TextButton(onClick = finish, modifier = Modifier.testTag("onboarding_skip")) { Text("Skip") }
        }
        AnimatedContent(targetState = page, transitionSpec = { fadeIn() togetherWith fadeOut() }, modifier = Modifier.weight(1f), label = "onboarding") { p ->
            when (p) {
                0 -> IntroPage(Icons.Rounded.TaskAlt, "Your day, organised", "Tasks, routines and habits in one calm place. Everything stays on this device — no account, no cloud.")
                1 -> IntroPage(Icons.Rounded.Repeat, "Reminders that don't give up", "Give a task a time and LifePilot reminds you — then keeps reminding you at the interval you choose until you tap Complete. Snoozing or dismissing never marks it done.")
                else -> PermissionsPage()
            }
        }
        Button(
            onClick = { if (page < 2) page++ else finish() },
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag(if (page < 2) "onboarding_next" else "onboarding_start"),
        ) { Text(if (page < 2) "Continue" else "Get started") }
    }
}

@Composable
private fun IntroPage(icon: ImageVector, title: String, body: String) {
    val ext = LpTheme.extended
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(120.dp).clip(CircleShape).background(Brush.linearGradient(ext.heroGradient)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.95f), modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(Spacing.xxl))
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.md))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PermissionsPage() {
    val container = appContainer()
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val notificationsOk = remember(refresh) { container.reliability.notificationPermissionGranted() }
    val exactOk = remember(refresh) { container.reliability.exactAlarmsAllowed() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("Make reminders reliable", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Spacing.sm))
        Text("LifePilot asks only for what reminders need. You can change these any time in Settings → Reminder reliability.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xl))
        PermissionCard(
            Icons.Rounded.NotificationsActive, "Notifications", "Required to show reminders.", notificationsOk,
            if (Build.VERSION.SDK_INT >= 33) "Allow" else null, tag = "onboarding_allow_notifications",
        ) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        Spacer(Modifier.height(Spacing.md))
        PermissionCard(
            Icons.Rounded.Alarm, "Alarms & reminders", "Delivers reminders at the exact minute. Without it, Android may deliver them a few minutes late.", exactOk,
            if (Build.VERSION.SDK_INT >= 31) "Allow" else null,
        ) {
            if (Build.VERSION.SDK_INT >= 31) {
                runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("No internet permission. Your data stays on this phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PermissionCard(icon: ImageVector, title: String, body: String, granted: Boolean, actionLabel: String?, tag: String? = null, onAction: () -> Unit) {
    LpCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(Spacing.sm))
            if (granted) Icon(Icons.Rounded.CheckCircle, contentDescription = "Granted", tint = LpTheme.extended.success)
            else if (actionLabel != null) FilledTonalButton(onClick = onAction, modifier = if (tag != null) Modifier.testTag(tag) else Modifier) { Text(actionLabel) }
        }
    }
}
