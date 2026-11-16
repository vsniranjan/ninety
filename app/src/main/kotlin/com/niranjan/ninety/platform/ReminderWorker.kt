package com.niranjan.ninety.platform

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.niranjan.ninety.data.NinetyRepository
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

private const val WORK_NAME = "ninety.evening_reminder"

/**
 * Approximate daily check: if today still has unrecorded attendance-counting periods,
 * post a local notification. Runs fully offline; never writes attendance.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as com.niranjan.ninety.NinetyApplication
        val repository = app.container.repository
        return ReminderNotifier.runCheck(applicationContext, repository)
    }

    companion object {
        suspend fun schedule(context: Context, repository: NinetyRepository) {
            val app = context.applicationContext as com.niranjan.ninety.NinetyApplication
            val settings = repository.observeAppState().first().settings
            val work = WorkManager.getInstance(context)
            if (!settings.reminderEnabled) {
                work.cancelUniqueWork(WORK_NAME)
                return
            }
            work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(ReminderNotifier.initialDelayMinutes(settings.reminderMinute), TimeUnit.MINUTES)
                    .build())
        }

        suspend fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
