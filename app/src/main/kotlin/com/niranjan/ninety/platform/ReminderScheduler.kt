package com.niranjan.ninety.platform

import android.content.Context
import com.niranjan.ninety.data.NinetyRepository
import java.util.UUID

/**
 * Schedules the optional end-of-day reminder. WorkManager timing is approximate:
 * it never marks attendance and must operate fully offline.
 */
class ReminderScheduler(private val context: Context, private val repository: NinetyRepository) {
    /** Reconcile scheduled work with stored settings; safe to call after any change. */
    suspend fun reconcile() {
        ReminderWorker.schedule(context, repository)
    }

    suspend fun cancel() = ReminderWorker.cancel(context)
}
