package com.lifepilot.app

import android.app.Application
import com.lifepilot.app.notifications.NotificationChannels
import com.lifepilot.app.scheduling.ReconcileWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LifePilotApp : Application() {

    lateinit var container: AppContainer
        private set

    /** Process-wide scope for work that must outlive a screen (e.g. reconciling alarms). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.ensureCreated(this)
        ReconcileWorker.schedulePeriodic(this)
        // Reconcile on every process start: covers app updates, force-stops (which clear alarms)
        // and anything missed while the process was dead. Idempotent, so it never duplicates.
        appScope.launch { runCatching { container.reconcileAll() } }
    }
}
