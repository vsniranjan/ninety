package com.niranjan.ninety.data

import android.annotation.SuppressLint
import android.content.Context
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Records exports made on this device. Portable backups never replace this preference. */
class BackupExportSettings(context: Context, private val clock: Clock) {
    private val preferences = context.getSharedPreferences("backup_exports", Context.MODE_PRIVATE)
    private val timestamp = MutableStateFlow(
        preferences.getLong(LAST_EXPORT, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(Instant::ofEpochMilli)
    )
    val lastExport = timestamp.asStateFlow()

    // The KTX edit extension discards the result of the synchronous disk write.
    @SuppressLint("UseKtx")
    fun recordSuccessfulExport() {
        val now = clock.millis()
        if (!preferences.edit().putLong(LAST_EXPORT, now).commit()) {
            throw IOException("Could not record the backup export time.")
        }
        timestamp.value = Instant.ofEpochMilli(now)
    }

    fun isExportOverdue(lastExport: Instant?): Boolean =
        lastExport != null && Duration.between(lastExport, clock.instant()) >= Duration.ofDays(30)

    private companion object {
        const val LAST_EXPORT = "last_export_epoch_millis"
    }
}
