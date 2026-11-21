package com.niranjan.ninety.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DayImage(
    val id: String,
    val kind: String,
    val holiday: Boolean,
    val copiedWeekday: Int?,
    val revision: Long,
    /** Present on deleted-day images so the row can be restored fully. */
    val semesterId: String? = null,
    val day: Long? = null,
)

@Serializable
data class SessionImage(
    val id: String,
    val dayId: String,
    /** Denormalized owner kept so deleted rows restore fully. */
    val semesterId: String,
    val courseId: String,
    val slot: Int?,
    val origin: String,
    val counting: Boolean,
    val startMinute: Int,
    val endMinute: Int,
    val cancelled: Boolean,
    val pinned: Boolean,
    val suppressed: Boolean,
    val revision: Long,
    val hadOutcome: Boolean,
)

@Serializable
data class OutcomeImage(
    val sessionId: String,
    val exists: Boolean,
    val kind: String? = null,
    val revision: Long = 0,
)

@Serializable
data class BalanceImage(
    val courseId: String,
    val loggingStartDay: Long,
    val conducted: Long,
    val present: Long,
    val dutyLeave: Long,
    val revision: Long,
)

@Serializable
data class VersionImage(val id: String, val semesterId: String, val effectiveDay: Long,
    val createdAt: Long, val slots: List<VersionSlotImage>)

@Serializable
data class VersionSlotImage(val weekday: Int, val slot: Int, val courseId: String)

@Serializable
data class ResumedCourseImage(val id: String, val existingScheduleIds: Set<String>)

/**
 * Everything needed to reverse one mutation: before-images of touched rows plus
 * identities of rows the change created. `expectedRevisions` maps each touched row
 * ("s:sessionId", "d:dayId", "o:sessionId", "b:courseId") to the revision it held
 * right after the change was written — undo is stale unless every row still matches.
 * A value of -1 means the row must be absent.
 */
@Serializable
data class UndoPayload(
    val label: String = "",
    val days: List<DayImage> = emptyList(),
    val sessions: List<SessionImage> = emptyList(),
    val outcomes: List<OutcomeImage> = emptyList(),
    val balances: List<BalanceImage> = emptyList(),
    val createdDays: List<String> = emptyList(),
    val createdSessions: List<String> = emptyList(),
    val createdVersions: List<String> = emptyList(),
    val deletedDays: List<DayImage> = emptyList(),
    val deletedSessions: List<SessionImage> = emptyList(),
    val expectedRevisions: Map<String, Long> = emptyMap(),
    val deletedVersions: List<VersionImage> = emptyList(),
    val resumedCourse: ResumedCourseImage? = null,
)

val undoJson: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
