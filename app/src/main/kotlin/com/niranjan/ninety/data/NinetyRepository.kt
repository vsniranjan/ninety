package com.niranjan.ninety.data

import com.niranjan.ninety.domain.AttendanceOutcome
import com.niranjan.ninety.domain.AttendanceSummary
import com.niranjan.ninety.domain.CourseType
import com.niranjan.ninety.domain.DayKind
import com.niranjan.ninety.domain.OpeningBalance
import com.niranjan.ninety.domain.Semester
import com.niranjan.ninety.domain.SessionOrigin
import com.niranjan.ninety.domain.WeekTemplate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class CourseInput(val name: String, val code: String, val type: CourseType, val colorArgb: Long? = null)

/** One course plus its optional mid-semester opening balance during setup or later edits. */
data class CourseSetup(
    val courseId: UUID = UUID.randomUUID(),
    val input: CourseInput,
    val balance: OpeningBalance? = null,
)

data class SemesterSetup(
    val name: String,
    val startDate: LocalDate,
    val zoneId: String,
    val courses: List<CourseSetup>,
    val timetable: WeekTemplate,
)

data class CourseStats(
    val course: CourseEntity,
    val balance: OpeningBalance,
    val summary: AttendanceSummary,
)

enum class DlStatus { PENDING, APPROVED, REJECTED }

data class SessionView(
    val id: UUID,
    val courseId: UUID,
    val courseCode: String,
    val courseName: String,
    val courseType: CourseType,
    val colorArgb: Long?,
    val slot: Int?,
    val origin: SessionOrigin,
    val counting: Boolean,
    val startMinute: Int,
    val endMinute: Int,
    val cancelled: Boolean,
    val pinned: Boolean,
    val suppressed: Boolean,
    val outcome: AttendanceOutcome,
    val dlStatus: DlStatus?,
    val dlEventId: UUID?,
    val revision: Long,
) {
    val recorded: Boolean get() = outcome != AttendanceOutcome.UNRECORDED
    val loggable: Boolean get() = !cancelled && !suppressed
}

/** One standard slot cell in the day view; [session] is null for free/unassigned slots. */
data class SlotCell(
    val slot: Int,
    val startMinute: Int,
    val endMinute: Int,
    val session: SessionView?,
    /** Projected course for future dates that are not materialized yet. */
    val projectedCourseId: UUID? = null,
)

/** Consecutive slots sharing one course are grouped (lab cards); free slots stand alone. */
data class SlotGroup(val courseId: UUID?, val cells: List<SlotCell>) {
    val grouped: Boolean get() = courseId != null && cells.size > 1
}

data class DayView(
    val semesterId: UUID,
    val date: LocalDate,
    val kind: DayKind,
    val holiday: Boolean,
    val copiedWeekday: DayOfWeek?,
    val weekend: Boolean,
    val future: Boolean,
    val writable: Boolean,
    val groups: List<SlotGroup>,
    val extras: List<SessionView>,
) {
    val total: Int get() = groups.sumOf { g -> g.cells.count { it.session?.counting == true && it.session.loggable } } +
        extras.count { it.counting && it.loggable }
    val recorded: Int get() = groups.sumOf { g -> g.cells.count { it.session?.recorded == true && it.session.loggable && it.session.counting } } +
        extras.count { it.recorded && it.counting && it.loggable }
    val unrecordedEligible: Int get() = total - recorded
}

data class DaySummary(
    val date: LocalDate,
    val kind: DayKind,
    val holiday: Boolean,
    val copiedWeekday: DayOfWeek?,
    val total: Int,
    val recorded: Int,
    val hasSessions: Boolean,
) {
    val complete: Boolean get() = total > 0 && recorded >= total
    val incomplete: Boolean get() = recorded < total
}

data class ConflictInfo(
    val sessionId: UUID,
    val date: LocalDate,
    val slot: Int?,
    val courseCode: String,
    val expectedCourseId: UUID,
    val reason: String,
)

data class CopyResult(val created: Int, val conflicts: List<ConflictInfo>, val kind: DayKind)

data class DlApplyResult(val eventId: UUID, val applied: Int, val conflicts: List<UUID>)

enum class DlEventStatus { PENDING, APPROVED, REJECTED, MIXED, EMPTY }

data class DlEventView(
    val id: UUID,
    val title: String?,
    val createdAt: Long,
    val status: DlEventStatus,
    val periods: Int,
    val pending: Int,
    val approved: Int,
    val rejected: Int,
    val dates: Int,
    val dateLabels: List<LocalDate>,
    val courseCodes: List<String>,
)

data class DlStats(
    val distinctDates: Int,
    val events: Int,
    val pending: Int,
    val approved: Int,
    val rejected: Int,
    val openingPending: Long,
    val openingApproved: Long,
)

data class SemesterInfo(val entity: SemesterEntity, val courseCount: Int) {
    val semester: Semester
        get() = Semester(UUID.fromString(entity.id), entity.name,
            LocalDate.ofEpochDay(entity.startDay), java.time.ZoneId.of(entity.collegeZone), entity.archived)
}

data class AppSettings(
    val theme: String = "SYSTEM",
    val haptics: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderMinute: Int = 1140,
)

data class AppState(
    val loaded: Boolean = false,
    val semesters: List<SemesterInfo> = emptyList(),
    val activeSemesterId: UUID? = null,
    val settings: AppSettings = AppSettings(),
) {
    val activeSemester: SemesterInfo? get() = semesters.firstOrNull { it.entity.id == activeSemesterId?.toString() }
    val needsSetup: Boolean get() = loaded && semesters.isEmpty()
}

data class UndoableAction(val batchId: UUID, val kind: String, val label: String, val createdAt: Long)

enum class UndoResult { DONE, STALE, NOT_FOUND }

data class BackupPreview(
    val formatVersion: Int,
    val createdAt: String?,
    val semesters: Int,
    val courses: Int,
    val sessions: Int,
    val outcomes: Int,
    val dlEvents: Int,
)

interface NinetyRepository {
    fun observeAppState(): Flow<AppState>
    fun observeCourses(semesterId: UUID): Flow<List<CourseEntity>>
    fun observeCourseStats(semesterId: UUID, asOf: LocalDate): Flow<List<CourseStats>>
    fun observeDay(semesterId: UUID, date: LocalDate): Flow<DayView>
    fun observeMonth(semesterId: UUID, month: YearMonth): Flow<List<DaySummary>>
    fun observeIncompleteDates(semesterId: UUID, through: LocalDate): Flow<List<LocalDate>>
    fun observeDlEvents(semesterId: UUID): Flow<List<DlEventView>>
    fun observeDlEventSessions(eventId: UUID): Flow<List<SessionView>>
    fun observeDlStats(semesterId: UUID): Flow<DlStats>
    fun observeUndoable(): Flow<UndoableAction?>
    fun observeTemplates(semesterId: UUID): Flow<List<com.niranjan.ninety.domain.ScheduleVersion>>
    fun currentTemplate(semesterId: UUID): Flow<WeekTemplate>

    suspend fun ensureMaterialized(semesterId: UUID)
    suspend fun ensureDay(semesterId: UUID, date: LocalDate)

    suspend fun createSemester(setup: SemesterSetup): UUID
    suspend fun updateSemester(semesterId: UUID, name: String, startDate: LocalDate, zoneId: String)
    suspend fun setSemesterCompleted(semesterId: UUID, completed: Boolean)
    suspend fun setActiveSemester(semesterId: UUID)
    suspend fun addCourse(semesterId: UUID, setup: CourseSetup)
    suspend fun updateCourse(courseId: UUID, input: CourseInput)
    suspend fun retireCourse(courseId: UUID)
    suspend fun correctOpeningBalance(balance: OpeningBalance)
    suspend fun resolveOpeningPendingDl(courseId: UUID, approve: Boolean)
    suspend fun updatePreferences(theme: String, haptics: Boolean, reminderEnabled: Boolean, reminderMinute: Int)

    suspend fun saveTemplate(semesterId: UUID, template: WeekTemplate, effectiveFrom: LocalDate): List<ConflictInfo>
    suspend fun previewTemplateConflicts(semesterId: UUID, template: WeekTemplate, effectiveFrom: LocalDate): List<ConflictInfo>

    suspend fun recordPeriod(sessionId: UUID, outcome: AttendanceOutcome?)
    suspend fun recordAll(sessionIds: List<UUID>, outcome: AttendanceOutcome)
    suspend fun markAllPresent(semesterId: UUID, date: LocalDate): Int
    suspend fun setCancelled(sessionId: UUID, cancelled: Boolean)
    suspend fun setHoliday(semesterId: UUID, date: LocalDate, holiday: Boolean)
    suspend fun copyWeekday(semesterId: UUID, date: LocalDate, sourceWeekday: DayOfWeek): CopyResult
    suspend fun addExtra(semesterId: UUID, date: LocalDate, courseId: UUID, startMinute: Int, endMinute: Int, counting: Boolean): UUID
    suspend fun setExtraCounting(sessionId: UUID, counting: Boolean)
    suspend fun deleteExtra(sessionId: UUID)
    suspend fun substituteCourse(sessionId: UUID, courseId: UUID)

    suspend fun applyDutyLeave(semesterId: UUID, sessionIds: List<UUID>, title: String?, replace: Boolean = false): DlApplyResult
    suspend fun updateDlStatus(eventId: UUID, status: DlStatus, sessionIds: Set<UUID>? = null)
    suspend fun undo(batchId: UUID): UndoResult

    suspend fun exportBackup(): ByteArray
    suspend fun validateBackup(bytes: ByteArray): BackupPreview
    suspend fun restoreBackup(bytes: ByteArray)
}
