package com.niranjan.ninety.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.niranjan.ninety.MainActivity
import com.niranjan.ninety.R
import com.niranjan.ninety.data.NinetyRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
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

    /** Check today's obligations without recording attendance. */
    suspend fun runCheck(context: Context, repository: NinetyRepository, clock: Clock) {
        refresh(context, repository, clock)
    }

    internal suspend fun markAllPresent(
        context: Context,
        repository: NinetyRepository,
        clock: Clock,
        semesterId: UUID,
        date: LocalDate,
    ) {
        val marked = repository.markPresentFromReminder(semesterId, date)
        refresh(context, repository, clock, if (marked > 0) Confirmation(semesterId, date, marked) else null)
    }

    private data class Confirmation(val semesterId: UUID, val date: LocalDate, val count: Int)

    private suspend fun refresh(
        context: Context,
        repository: NinetyRepository,
        clock: Clock,
        confirmation: Confirmation? = null,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val state = repository.observeAppState().first()
        val semester = state.activeSemester
        if (!state.settings.reminderEnabled || semester == null ||
            (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED)) {
            manager.cancel(NOTIFICATION_ID)
            return
        }

        val semesterId = UUID.fromString(semester.entity.id)
        val today = LocalDate.now(clock.withZone(ZoneId.of(semester.entity.collegeZone)))
        repository.ensureDay(semesterId, today)
        val day = repository.observeDay(semesterId, today).first()
        val marked = confirmation?.takeIf { it.semesterId == semesterId && it.date == today }?.count
        val remaining = day.unrecordedEligible
        if (day.holiday || day.weekend || (remaining == 0 && marked == null)) {
            manager.cancel(NOTIFICATION_ID)
            return
        }

        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel),
            NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.reminder_channel_description)
        })
        val openApp = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ninety)
            .setContentTitle(context.getString(R.string.record_today_reminder))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setOnlyAlertOnce(confirmation != null)
        if (remaining > 0) {
            val action = Intent(context, ReminderActionReceiver::class.java).apply {
                // The date and semester also identify the PendingIntent, so a later
                // reminder cannot replace the target of an older notification.
                data = "ninety://attendance/$semesterId/$today".toUri()
            }
            val allPresent = PendingIntent.getBroadcast(context, 0, action, PendingIntent.FLAG_IMMUTABLE)
            notification
                .setContentText(context.resources.getQuantityString(R.plurals.reminder_periods_not_marked,
                    remaining, remaining))
                .addAction(R.drawable.ic_check, context.getString(R.string.reminder_all_present), allPresent)
        } else if (marked != null) {
            notification
                .setContentText(context.resources.getQuantityString(R.plurals.reminder_marked_present, marked, marked))
                .setTimeoutAfter(8_000)
        }
        manager.notify(NOTIFICATION_ID, notification.build())
    }
}
