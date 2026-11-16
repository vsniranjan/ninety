package com.niranjan.ninety.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.work.ListenableWorker
import com.niranjan.ninety.data.NinetyRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first

object ReminderNotifier {
    private const val CHANNEL_ID = "attendance_reminders"
    private const val NOTIFICATION_ID = 90

    /** Minutes until the next approximate reminder time. */
    fun initialDelayMinutes(reminderMinute: Int, now: LocalDateTime = LocalDateTime.now()): Long {
        var target = now.toLocalDate().atTime(reminderMinute / 60, reminderMinute % 60)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return ChronoUnit.MINUTES.between(now, target).coerceAtLeast(1)
    }

    /**
     * Posts a reminder when the active semester still has unrecorded counting periods
     * today; silent on holidays, weekends, completed days, and when permission is denied.
     */
    suspend fun runCheck(context: Context, repository: NinetyRepository): ListenableWorker.Result {
        val app = context.applicationContext as com.niranjan.ninety.NinetyApplication
        val state = repository.observeAppState().first()
        val settings = state.settings
        val semester = state.activeSemester ?: return ListenableWorker.Result.success()
        val zone = java.time.ZoneId.of(semester.entity.collegeZone)
        val today = LocalDate.now(zone)
        repository.ensureDay(java.util.UUID.fromString(semester.entity.id), today)
        val day = repository.observeDay(java.util.UUID.fromString(semester.entity.id), today).first()
        if (day.unrecordedEligible <= 0 || day.holiday || day.weekend) return ListenableWorker.Result.success()
        if (androidx.core.content.ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return ListenableWorker.Result.success()
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Attendance reminders",
            NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Approximate end-of-day reminders to record attendance."
        })
        val intent = android.app.PendingIntent.getActivity(context, 0,
            android.content.Intent(context, com.niranjan.ninety.MainActivity::class.java)
                .setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val notification = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.niranjan.ninety.R.drawable.ic_ninety)
            .setContentTitle("Record today's attendance")
            .setContentText("${day.unrecordedEligible} ${if (day.unrecordedEligible == 1) "period" else "periods"} still unrecorded.")
            .setContentIntent(intent).setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return ListenableWorker.Result.success()
    }
}
