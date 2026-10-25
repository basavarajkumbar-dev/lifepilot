package com.lifepilot.app.scheduling

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lifepilot.app.LifePilotApp
import java.util.concurrent.TimeUnit

/**
 * Deferrable safety net (WorkManager). Exact timing is handled by AlarmManager; this worker only
 * (1) generates the next occurrences of recurring tasks once per day even if the app is never
 * opened, and (2) re-plans alarms in case one was lost (e.g. force-stop on some devices).
 * It is not a polling loop: it runs a few times a day with no constraints.
 */
class ReconcileWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as LifePilotApp).container.reconcileAll()
        Result.success()
    } catch (e: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    companion object {
        private const val PERIODIC = "reconcile_periodic"
        private const val ONE_SHOT = "reconcile_now"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReconcileWorker>(8, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun enqueueNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReconcileWorker>().build())
        }
    }
}
