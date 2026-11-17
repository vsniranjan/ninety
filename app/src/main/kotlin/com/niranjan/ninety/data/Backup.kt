package com.niranjan.ninety.data

import androidx.room.withTransaction
import com.niranjan.ninety.domain.MAX_PERIOD_COUNT
import java.time.Clock
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Versioned portable backup. Field order mirrors the relational model. */
@Serializable
data class BackupFile(
    val format: Int,
    val app: String = "ninety",
    val createdAt: String,
    val settings: BackupSettings,
    val semesters: List<BackupSemester> = emptyList(),
    val courses: List<BackupCourse> = emptyList(),
    val balances: List<BackupBalance> = emptyList(),
    val versions: List<BackupVersion> = emptyList(),
    val templateSlots: List<BackupSlot> = emptyList(),
    val days: List<BackupDay> = emptyList(),
    val sessions: List<BackupSession> = emptyList(),
    val outcomes: List<BackupOutcome> = emptyList(),
    val dlEvents: List<BackupEvent> = emptyList(),
    val changeBatches: List<BackupBatch> = emptyList(),
)

@Serializable
data class BackupSettings(
    val activeSemesterId: String?,
    val theme: String = "SYSTEM",
    val haptics: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderMinute: Int = 1140,
    val revision: Long = 0,
)

@Serializable
data class BackupSemester(
    val id: String, val name: String, val startDay: Long, val collegeZone: String,
    val archived: Boolean = false, val completedDay: Long? = null, val materializedDay: Long? = null,
)

@Serializable
data class BackupCourse(
    val id: String, val semesterId: String, val name: String,
    val type: String, val colorArgb: Long? = null, val retired: Boolean = false,
)

@Serializable
data class BackupBalance(
    val courseId: String, val loggingStartDay: Long, val conducted: Long, val present: Long,
    val approvedDl: Long, val pendingDl: Long, val revision: Long = 0,
)

@Serializable
data class BackupVersion(val id: String, val semesterId: String, val effectiveDay: Long, val createdAt: Long)

@Serializable
data class BackupSlot(val versionId: String, val weekday: Int, val slot: Int, val courseId: String)

@Serializable
data class BackupDay(
    val id: String, val semesterId: String, val day: Long, val kind: String,
    val holiday: Boolean = false, val copiedWeekday: Int? = null, val revision: Long = 0,
)

@Serializable
data class BackupSession(
    val id: String, val dayId: String, val semesterId: String, val courseId: String,
    val slot: Int? = null, val origin: String, val counting: Boolean,
    val startMinute: Int, val endMinute: Int, val cancelled: Boolean = false,
    val pinned: Boolean = false, val suppressed: Boolean = false, val revision: Long = 0,
)

@Serializable
data class BackupOutcome(
    val sessionId: String, val kind: String, val dlStatus: String? = null,
    val dlEventId: String? = null, val revision: Long = 0,
)

@Serializable
data class BackupEvent(val id: String, val semesterId: String, val title: String? = null, val createdAt: Long)

@Serializable
data class BackupBatch(val id: String, val kind: String, val createdAt: Long, val undoneAt: Long? = null, val payload: String)

class BackupException(message: String) : Exception(message)

object BackupCodec {
    const val FORMAT_VERSION = 2
    private const val LEGACY_FORMAT_VERSION = 1
    const val MAX_BYTES = 64 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }

    suspend fun export(db: NinetyDatabase, clock: Clock): ByteArray {
        val data = db.withTransaction {
            BackupFile(
                format = FORMAT_VERSION,
                createdAt = Instant.ofEpochMilli(clock.millis()).toString(),
                settings = db.setupDao().settings()?.let {
                    BackupSettings(it.activeSemesterId, it.theme, it.haptics, it.reminderEnabled, it.reminderMinute, it.revision)
                } ?: BackupSettings(null),
                semesters = db.setupDao().semesters().map {
                    BackupSemester(it.id, it.name, it.startDay, it.collegeZone, it.archived, it.completedDay, it.materializedDay) },
                courses = db.setupDao().semesters().flatMap { db.setupDao().courses(it.id) }.map {
                    BackupCourse(it.id, it.semesterId, it.name, it.type, it.colorArgb, it.retired) },
                balances = db.setupDao().balances().map {
                    BackupBalance(it.courseId, it.loggingStartDay, it.conducted, it.present, it.approvedDl, it.pendingDl, it.revision) },
                versions = db.setupDao().semesters().flatMap { db.scheduleDao().versions(it.id) }.map {
                    BackupVersion(it.id, it.semesterId, it.effectiveDay, it.createdAt) },
                templateSlots = db.setupDao().semesters().flatMap { s -> db.scheduleDao().versions(s.id).flatMap { db.scheduleDao().slots(it.id) } }.map {
                    BackupSlot(it.versionId, it.weekday, it.slot, it.courseId) },
                days = db.setupDao().semesters().flatMap { db.journalDao().allDays(it.id) }.map {
                    BackupDay(it.id, it.semesterId, it.day, it.kind, it.holiday, it.copiedWeekday, it.revision) },
                sessions = db.setupDao().semesters().flatMap { db.journalDao().allSessions(it.id) }.map {
                    BackupSession(it.id, it.dayId, it.semesterId, it.courseId, it.slot, it.origin, it.counting,
                        it.startMinute, it.endMinute, it.cancelled, it.pinned, it.suppressed, it.revision) },
                outcomes = db.journalDao().allOutcomes().map {
                    BackupOutcome(it.sessionId, it.kind, it.dlStatus, it.dlEventId, it.revision) },
                dlEvents = db.setupDao().semesters().flatMap { db.journalDao().allEvents(it.id) }.map {
                    BackupEvent(it.id, it.semesterId, it.title, it.createdAt) },
                changeBatches = db.journalDao().allBatches().map {
                    BackupBatch(it.id, it.kind, it.createdAt, it.undoneAt, it.payload) },
            )
        }
        return json.encodeToString(BackupFile.serializer(), data).toByteArray(Charsets.UTF_8)
    }

    /** Bounds file-picker reads before allocating or parsing an entire untrusted document. */
    fun read(input: java.io.InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size().toLong() + count > MAX_BYTES) throw BackupException("This backup is too large to read safely.")
            output.write(buffer, 0, count)
        }
    }

    private fun parse(bytes: ByteArray): BackupFile {
        if (bytes.size > MAX_BYTES) throw BackupException("This backup is too large to read safely.")
        val text = try {
            bytes.toString(Charsets.UTF_8)
        } catch (e: Exception) { throw BackupException("This file is not a readable backup.") }
        return try {
            val root = json.parseToJsonElement(text) as JsonObject
            val format = (root["format"] as? JsonPrimitive)?.intOrNull
                ?: throw BackupException("This file is not a valid Ninety backup.")
            val compatible = when (format) {
                FORMAT_VERSION -> root
                LEGACY_FORMAT_VERSION -> {
                    // Existing records already reference stable course IDs, so the labels can be dropped.
                    val courses = root["courses"] as? JsonArray
                    if (courses == null) root else JsonObject(root + ("courses" to JsonArray(courses.map {
                        if (it is JsonObject) JsonObject(it - "code" - "normalizedCode") else it
                    })))
                }
                else -> throw BackupException("This backup uses an unsupported version ($format).")
            }
            json.decodeFromJsonElement(BackupFile.serializer(), compatible)
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) { throw BackupException("This file is not a valid Ninety backup.") }
    }

    fun validate(bytes: ByteArray): BackupPreview {
        val backup = parse(bytes)
        checkIntegrity(backup)
        return BackupPreview(backup.format, backup.createdAt, backup.semesters.size, backup.courses.size,
            backup.sessions.size, backup.outcomes.size, backup.dlEvents.size)
    }

    private fun checkIntegrity(b: BackupFile) {
        val semesters = b.semesters.associateBy { it.id }
        val courses = b.courses.associateBy { it.id }
        val versions = b.versions.associateBy { it.id }
        val days = b.days.associateBy { it.id }
        val sessions = b.sessions.associateBy { it.id }
        val events = b.dlEvents.associateBy { it.id }
        fun idsUnique(items: List<String>, what: String) {
            if (items.distinct().size != items.size) throw BackupException("Duplicate $what in this backup.") }
        idsUnique(b.semesters.map { it.id }, "semester IDs")
        idsUnique(b.courses.map { it.id }, "course IDs")
        idsUnique(b.versions.map { it.id }, "schedule version IDs")
        idsUnique(b.days.map { it.id }, "day IDs")
        idsUnique(b.sessions.map { it.id }, "class period IDs")
        idsUnique(b.dlEvents.map { it.id }, "duty leave event IDs")
        idsUnique(b.balances.map { it.courseId }, "opening balances")
        idsUnique(b.versions.map { "${it.semesterId}:${it.effectiveDay}" }, "schedule effective dates")
        if (b.app != "ninety") throw BackupException("This file is not a Ninety backup.")
        fun validId(id: String) {
            if (runCatching { java.util.UUID.fromString(id).toString() == id }.getOrDefault(false).not())
                throw BackupException("A record has an invalid identity.")
        }
        (semesters.keys + courses.keys + versions.keys + days.keys + sessions.keys + events.keys +
            b.changeBatches.map { it.id }).forEach(::validId)
        fun validDay(day: Long) {
            if (runCatching { java.time.LocalDate.ofEpochDay(day) }.isFailure)
                throw BackupException("A record has an invalid date.")
        }
        if (b.balances.map { it.courseId }.toSet() != courses.keys)
            throw BackupException("Every course must have exactly one opening balance.")
        if (b.settings.revision < 0) throw BackupException("Corrupt settings revision.")
        idsUnique(b.outcomes.map { it.sessionId }, "attendance outcomes")
        idsUnique(b.changeBatches.map { it.id }, "change records")
        b.semesters.forEach { s ->
            validDay(s.startDay)
            s.completedDay?.let(::validDay)
            s.materializedDay?.let(::validDay)
            if (s.name.isBlank()) throw BackupException("A semester has no name.")
            runCatching { java.time.ZoneId.of(s.collegeZone) }.getOrElse { throw BackupException("A semester has an invalid timezone.") }
        }
        if (b.settings.theme !in setOf("SYSTEM", "LIGHT", "DARK")) throw BackupException("Invalid theme in backup settings.")
        if (b.settings.reminderMinute !in 0..1439) throw BackupException("Invalid reminder time in backup settings.")
        b.settings.activeSemesterId?.let {
            val semester = semesters[it] ?: throw BackupException("Settings point to a missing semester.")
            if (semester.archived) throw BackupException("The active semester in this backup is archived.")
        }
        b.courses.forEach { c ->
            if (c.semesterId !in semesters) throw BackupException("A course belongs to a missing semester.")
            if (c.type !in setOf("THEORY", "LAB", "OTHER") || c.name.isBlank())
                throw BackupException("A course is invalid in this backup.")
        }
        b.balances.forEach { bal ->
            val course = courses[bal.courseId] ?: throw BackupException("An opening balance belongs to a missing course.")
            val semester = semesters[course.semesterId]!!
            validDay(bal.loggingStartDay)
            if (bal.conducted !in 0..MAX_PERIOD_COUNT || bal.present !in 0..bal.conducted ||
                bal.approvedDl !in 0..(bal.conducted - bal.present) ||
                bal.pendingDl !in 0..(bal.conducted - bal.present - bal.approvedDl))
                throw BackupException("An opening balance has impossible counts.")
            if (bal.loggingStartDay < semester.startDay) throw BackupException("A logging start precedes the semester start.")
            if (bal.revision < 0) throw BackupException("Corrupt revision data in this backup.")
        }
        b.versions.forEach { v ->
            validDay(v.effectiveDay)
            if (v.semesterId !in semesters) throw BackupException("A schedule belongs to a missing semester.")
        }
        val slotKeys = b.templateSlots.map { Triple(it.versionId, it.weekday, it.slot) }
        if (slotKeys.distinct().size != slotKeys.size) throw BackupException("Duplicate timetable slots in this backup.")
        b.templateSlots.forEach { s ->
            val version = versions[s.versionId] ?: throw BackupException("A timetable slot belongs to a missing schedule.")
            val course = courses[s.courseId] ?: throw BackupException("A timetable slot references a missing course.")
            if (course.semesterId != version.semesterId) throw BackupException("A timetable references a course from another semester.")
            if (s.weekday !in 1..5 || s.slot !in 1..6) throw BackupException("A timetable slot is out of range.")
        }
        val dayKeys = b.days.map { it.semesterId to it.day }
        if (dayKeys.distinct().size != dayKeys.size) throw BackupException("Duplicate days in this backup.")
        b.days.forEach { d ->
            validDay(d.day)
            if (d.semesterId !in semesters) throw BackupException("A day belongs to a missing semester.")
            if (d.kind !in setOf("REGULAR", "COPIED", "CUSTOM")) throw BackupException("A day has an invalid kind.")
            if (d.copiedWeekday != null && d.copiedWeekday !in 1..5) throw BackupException("A copied day has an invalid source weekday.")
            if (d.revision < 0) throw BackupException("Corrupt revision data in this backup.")
        }
        val sessionSlotKeys = b.sessions.filter { it.slot != null }.map { it.dayId to it.slot }
        if (sessionSlotKeys.distinct().size != sessionSlotKeys.size) throw BackupException("Duplicate class periods in this backup.")
        b.sessions.forEach { s ->
            val day = days[s.dayId] ?: throw BackupException("A class period belongs to a missing day.")
            val course = courses[s.courseId] ?: throw BackupException("A class period references a missing course.")
            if (s.semesterId != day.semesterId || course.semesterId != day.semesterId)
                throw BackupException("A class period crosses semester boundaries.")
            if (s.origin !in setOf("TEMPLATE", "COPIED", "EXTRA")) throw BackupException("A class period has an invalid origin.")
            if ((s.origin == "EXTRA") != (s.slot == null)) throw BackupException("Class period slots are inconsistent.")
            if (s.slot != null && s.slot !in 1..6) throw BackupException("A class period slot is out of range.")
            if (s.startMinute < 0 || s.endMinute > 1440 || s.startMinute >= s.endMinute)
                throw BackupException("A class period has impossible times.")
            if (s.revision < 0) throw BackupException("Corrupt revision data in this backup.")
        }
        b.outcomes.forEach { o ->
            if (o.sessionId !in sessions) throw BackupException("An attendance record belongs to a missing class period.")
            if (o.kind !in setOf("PRESENT", "ABSENT", "DL")) throw BackupException("An attendance record has an invalid kind.")
            if ((o.kind == "DL") != (o.dlStatus != null && o.dlEventId != null))
                throw BackupException("Duty leave records are inconsistent in this backup.")
            if (o.dlStatus != null && o.dlStatus !in setOf("PENDING", "APPROVED", "REJECTED"))
                throw BackupException("A duty leave status is invalid.")
            if (o.dlEventId != null) {
                val event = events[o.dlEventId] ?: throw BackupException("An attendance record references a missing duty leave event.")
                if (sessions.getValue(o.sessionId).semesterId != event.semesterId)
                    throw BackupException("A duty leave record crosses semester boundaries.")
            }
            if (o.kind != "DL" && (o.dlStatus != null || o.dlEventId != null))
                throw BackupException("An attendance record has unexpected duty leave data.")
            if (o.revision < 0) throw BackupException("Corrupt revision data in this backup.")
        }
        b.dlEvents.forEach { e -> if (e.semesterId !in semesters) throw BackupException("A duty leave event belongs to a missing semester.") }
    }

    suspend fun restore(db: NinetyDatabase, bytes: ByteArray, clock: Clock) {
        val backup = parse(bytes)
        checkIntegrity(backup)
        db.withTransaction {
            val setup = db.setupDao(); val schedule = db.scheduleDao(); val journal = db.journalDao()
            val sql = db.openHelper.writableDatabase
            listOf("change_batch", "attendance_outcome", "session", "dl_event", "day_instance",
                "template_slot", "schedule_version", "opening_balance", "course", "app_settings", "semester")
                .forEach { sql.execSQL("DELETE FROM $it") }
            backup.semesters.forEach { setup.insertSemester(SemesterEntity(it.id, it.name, it.startDay, it.collegeZone, it.archived, it.completedDay, it.materializedDay)) }
            backup.courses.forEach { setup.insertCourse(CourseEntity(it.id, it.semesterId, it.name, it.type, it.colorArgb, it.retired)) }
            backup.balances.forEach { setup.insertBalance(OpeningBalanceEntity(it.courseId, it.loggingStartDay, it.conducted, it.present, it.approvedDl, it.pendingDl, it.revision)) }
            setup.insertSettings(AppSettingsEntity(1, backup.settings.activeSemesterId, backup.settings.theme,
                backup.settings.haptics, backup.settings.reminderEnabled, backup.settings.reminderMinute, backup.settings.revision))
            backup.versions.forEach { schedule.insertVersion(ScheduleVersionEntity(it.id, it.semesterId, it.effectiveDay, it.createdAt)) }
            backup.templateSlots.chunked(200).forEach { chunk ->
                schedule.insertSlots(chunk.map { TemplateSlotEntity(it.versionId, it.weekday, it.slot, it.courseId) }) }
            backup.days.forEach { journal.insertDay(DayInstanceEntity(it.id, it.semesterId, it.day, it.kind, it.holiday, it.copiedWeekday, it.revision)) }
            backup.dlEvents.forEach { journal.insertEvent(DlEventEntity(it.id, it.semesterId, it.title, it.createdAt)) }
            backup.sessions.forEach { journal.insertSession(SessionEntity(it.id, it.dayId, it.semesterId, it.courseId, it.slot, it.origin, it.counting, it.startMinute, it.endMinute, it.cancelled, it.pinned, it.suppressed, it.revision)) }
            backup.outcomes.forEach { journal.insertOutcome(AttendanceOutcomeEntity(it.sessionId, it.kind, it.dlStatus, it.dlEventId, it.revision)) }
            backup.changeBatches.forEach { journal.insertBatch(ChangeBatchEntity(it.id, it.kind, it.createdAt, it.undoneAt, it.payload)) }
        }
    }
}
