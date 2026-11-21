package com.niranjan.ninety.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale

@Entity(tableName = "semester")
data class SemesterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val startDay: Long,
    val collegeZone: String,
    val archived: Boolean = false,
    /** Local date on which the semester was marked done; freezes obligation generation. */
    val completedDay: Long? = null,
    /** Last local date reconciled against the weekly template; null before any generation. */
    val materializedDay: Long? = null,
)

@Entity(
    tableName = "course",
    foreignKeys = [ForeignKey(
        entity = SemesterEntity::class, parentColumns = ["id"], childColumns = ["semesterId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("semesterId"), Index(value = ["semesterId", "normalizedCode"], unique = true)],
)
data class CourseEntity(
    @PrimaryKey val id: String,
    val semesterId: String,
    val name: String,
    val type: String,
    val colorArgb: Long? = null,
    val retired: Boolean = false,
    // Schema 3 requires these columns. New records use the ID as a compatibility key.
    @ColumnInfo(name = "code") val legacyKey: String = id,
    @ColumnInfo(name = "normalizedCode") val normalizedLegacyKey: String = legacyKey.uppercase(Locale.ROOT),
)

@Entity(
    tableName = "opening_balance",
    foreignKeys = [ForeignKey(
        entity = CourseEntity::class, parentColumns = ["id"], childColumns = ["courseId"],
        onDelete = ForeignKey.RESTRICT,
    )],
)
data class OpeningBalanceEntity(
    @PrimaryKey val courseId: String,
    val loggingStartDay: Long,
    val conducted: Long,
    val present: Long,
    val dutyLeave: Long,
    val revision: Long = 0,
)

@Entity(
    tableName = "app_settings",
    foreignKeys = [ForeignKey(
        entity = SemesterEntity::class, parentColumns = ["id"], childColumns = ["activeSemesterId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("activeSemesterId")],
)
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val activeSemesterId: String?,
    val theme: String = "SYSTEM",
    @ColumnInfo(defaultValue = "1") val haptics: Boolean = true,
    @ColumnInfo(defaultValue = "0") val reminderEnabled: Boolean = false,
    /** Reminder time in minutes since local midnight; approximate, WorkManager-scheduled. */
    @ColumnInfo(defaultValue = "1140") val reminderMinute: Int = 1140,
    val revision: Long = 0,
)

@Entity(
    tableName = "schedule_version",
    foreignKeys = [ForeignKey(
        entity = SemesterEntity::class, parentColumns = ["id"], childColumns = ["semesterId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("semesterId"), Index(value = ["semesterId", "effectiveDay"], unique = true)],
)
data class ScheduleVersionEntity(
    @PrimaryKey val id: String,
    val semesterId: String,
    val effectiveDay: Long,
    val createdAt: Long,
)

@Entity(
    tableName = "template_slot",
    primaryKeys = ["versionId", "weekday", "slot"],
    foreignKeys = [
        ForeignKey(entity = ScheduleVersionEntity::class, parentColumns = ["id"],
            childColumns = ["versionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CourseEntity::class, parentColumns = ["id"],
            childColumns = ["courseId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("courseId")],
)
data class TemplateSlotEntity(
    val versionId: String,
    /** 1 = Monday … 5 = Friday. */
    val weekday: Int,
    /** Standard period index 1-6. */
    val slot: Int,
    val courseId: String,
)

@Entity(
    tableName = "day_instance",
    foreignKeys = [ForeignKey(
        entity = SemesterEntity::class, parentColumns = ["id"], childColumns = ["semesterId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index(value = ["semesterId", "day"], unique = true)],
)
data class DayInstanceEntity(
    @PrimaryKey val id: String,
    val semesterId: String,
    val day: Long,
    /** REGULAR | COPIED | CUSTOM — snapshot days are never reconciled against the template. */
    val kind: String,
    val holiday: Boolean = false,
    /** Source weekday (1-5) for COPIED/CUSTOM days, else null. */
    val copiedWeekday: Int? = null,
    val revision: Long = 0,
)

@Entity(
    tableName = "session",
    foreignKeys = [
        ForeignKey(entity = DayInstanceEntity::class, parentColumns = ["id"],
            childColumns = ["dayId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SemesterEntity::class, parentColumns = ["id"],
            childColumns = ["semesterId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CourseEntity::class, parentColumns = ["id"],
            childColumns = ["courseId"], onDelete = ForeignKey.RESTRICT),
    ],
    // Unique (dayId, slot): SQLite treats NULLs as distinct, so extras never collide
    // while a standard slot can never be materialized twice.
    indices = [Index("dayId"), Index("semesterId"), Index("courseId"),
        Index(value = ["dayId", "slot"], unique = true)],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val dayId: String,
    val semesterId: String,
    val courseId: String,
    /** Standard period 1-6; null for supplemental extras. */
    val slot: Int?,
    /** TEMPLATE | COPIED | EXTRA. */
    val origin: String,
    /** Whether this period is attendance-counting. Extras default to false. */
    val counting: Boolean,
    val startMinute: Int,
    val endMinute: Int,
    /** Explicitly marked not conducted; retains its outcome for reversal. */
    val cancelled: Boolean = false,
    /** Hand-edited date-specific substitution; the reconciler must not rewrite it. */
    val pinned: Boolean = false,
    /** Retained generated record whose slot left the effective template. */
    val suppressed: Boolean = false,
    val revision: Long = 0,
)

@Entity(
    tableName = "attendance_outcome",
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"],
            childColumns = ["sessionId"], onDelete = ForeignKey.RESTRICT),
    ],
)
data class AttendanceOutcomeEntity(
    /** One current outcome per dated session. */
    @PrimaryKey val sessionId: String,
    /** PRESENT | ABSENT | DUTY_LEAVE. */
    val kind: String,
    val revision: Long = 0,
)

@Entity(tableName = "change_batch")
data class ChangeBatchEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val createdAt: Long,
    val undoneAt: Long? = null,
    /** Serialized before-images and created ids used for guarded undo/history. */
    val payload: String,
)
