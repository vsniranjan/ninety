package com.niranjan.ninety.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.niranjan.ninety.NinetyApplication
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val path = intent.data?.pathSegments ?: return
        if (path.size != 2) return
        val semesterId = runCatching { UUID.fromString(path[0]) }.getOrNull() ?: return
        val date = runCatching { LocalDate.parse(path[1]) }.getOrNull() ?: return
        val app = context.applicationContext as NinetyApplication
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderNotifier.markAllPresent(app, app.container.repository, app.container.clock, semesterId, date)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("ReminderActionReceiver", "Could not mark attendance from reminder", error)
            } finally {
                pending.finish()
            }
        }
    }
}
