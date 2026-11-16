package com.niranjan.ninety.data

import androidx.room.withTransaction
import com.niranjan.ninety.domain.AttendanceEngine
import com.niranjan.ninety.domain.AttendanceOutcome
import com.niranjan.ninety.domain.CourseType
import com.niranjan.ninety.domain.DayKind
import com.niranjan.ninety.domain.OpeningBalance
import com.niranjan.ninety.domain.PeriodFact
import com.niranjan.ninety.domain.PeriodTimes
import com.niranjan.ninety.domain.ScheduleResolver
import com.niranjan.ninety.domain.ScheduleVersion
import com.niranjan.ninety.domain.SessionOrigin
import com.niranjan.ninety.domain.ExpectedSlot
import com.niranjan.ninety.domain.ReconcileOp
import com.niranjan.ninety.domain.TrackedSession
import com.niranjan.ninety.domain.WeekTemplate
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Deterministic, timezone-safe identities for generated rows. */
private fun stableUuid(vararg parts: Any?): UUID =
    UUID.nameUUIDFromBytes(parts.joinToString("|") { it.toString() }.toByteArray(Charsets.UTF_8))

private fun dayUuid(semesterId: String, day: Long): UUID = stableUuid("day", semesterId, day)
private fun sessionUuid(semesterId: String, day: Long, slot: Int): UUID = stableUuid("session", semesterId, day, slot)

private fun mapOutcome(kind: String?, dlStatus: String?): AttendanceOutcome = when {
    kind == null -> AttendanceOutcome.UNRECORDED
    kind == "PRESENT" -> AttendanceOutcome.PRESENT
    kind == "ABSENT" -> AttendanceOutcome.ABSENT
    kind == "DL" && dlStatus == "APPROVED" -> AttendanceOutcome.DL_APPROVED
    kind == "DL" && dlStatus == "REJECTED" -> AttendanceOutcome.DL_REJECTED
    else -> AttendanceOutcome.DL_PENDING
}

private fun SessionEntity.image(hadOutcome: Boolean) = SessionImage(
    id, dayId, semesterId, courseId, slot, origin, counting, startMinute, endMinute,
    cancelled, pinned, suppressed, revision, hadOutcome)

private fun SessionImage.toEntity() = SessionEntity(id, dayId, semesterId, courseId, slot, origin, counting,
    startMinute, endMinute, cancelled, pinned, suppressed, revision)

private fun DayInstanceEntity.image() = DayImage(id, kind, holiday, copiedWeekday, revision)
private fun DayInstanceEntity.deletedImage() = DayImage(id, kind, holiday, copiedWeekday, revision, semesterId, day)
private fun AttendanceOutcomeEntity.image() = OutcomeImage(sessionId, true, kind, dlStatus, dlEventId, revision)
private fun OpeningBalanceEntity.image() = BalanceImage(courseId, loggingStartDay, conducted, present, approvedDl, pendingDl, revision)
private fun missingOutcomeImage(sessionId: String) = OutcomeImage(sessionId, exists = false)

private fun SessionRow.toFact(): PeriodFact = PeriodFact(
    UUID.fromString(id), UUID.fromString(courseId), LocalDate.ofEpochDay(day),
    mapOutcome(outcomeKind, outcomeDlStatus), counting, cancelled || suppressed, holiday)

private fun SessionRow.toView(course: CourseEntity?): SessionView = SessionView(
    UUID.fromString(id), UUID.fromString(courseId), course?.code ?: "?",
    course?.name ?: "Course",
    course?.let { runCatching { CourseType.valueOf(it.type) }.getOrDefault(CourseType.OTHER) } ?: CourseType.OTHER,
    course?.colorArgb, slot, SessionOrigin.valueOf(origin), counting, startMinute, endMinute,
    cancelled, pinned, suppressed, mapOutcome(outcomeKind, outcomeDlStatus),
    outcomeDlStatus?.let { DlStatus.valueOf(it) },
    outcomeDlEventId?.let { UUID.fromString(it) }, revision)

private fun String.toUuid(): UUID = UUID.fromString(this)

private class BatchBuilder(var label: String) {
    val days = mutableListOf<DayImage>()
    val sessions = mutableListOf<SessionImage>()
    val outcomes = mutableListOf<OutcomeImage>()
    val balances = mutableListOf<BalanceImage>()
    val createdDays = mutableListOf<String>()
    val createdSessions = mutableListOf<String>()
    val createdVersions = mutableListOf<String>()
    val createdEvents = mutableListOf<String>()
    val deletedDays = mutableListOf<DayImage>()
    val deletedSessions = mutableListOf<SessionImage>()
    val expected = mutableMapOf<String, Long>()
    fun payload() = undoJson.encodeToString(UndoPayload.serializer(),
        UndoPayload(label, days, sessions, outcomes, balances, createdDays, createdSessions,
            createdVersions, createdEvents, deletedDays, deletedSessions, expected))
}

class RoomNinetyRepository(
    private val database: NinetyDatabase,
    private val clock: Clock = Clock.systemUTC(),
) : NinetyRepository {
    private val setup = database.setupDao()
    private val schedule = database.scheduleDao()
    private val journal = database.journalDao()

    private fun today(zone: ZoneId): LocalDate = LocalDate.now(clock.withZone(zone))
    private fun now(): Long = clock.millis()
    private fun zoneOf(semester: SemesterEntity): ZoneId = ZoneId.of(semester.collegeZone)

    /** Latest date a semester generates obligations for; freezes once marked done. */
    private fun ceiling(semester: SemesterEntity, today: LocalDate): LocalDate {
        val completed = semester.completedDay?.let { LocalDate.ofEpochDay(it) }
        return if (semester.archived && completed != null && completed.isBefore(today)) completed else today
    }

    // ---------------------------------------------------------------- observation

    override fun observeAppState(): Flow<AppState> =
        combine(setup.observeSemesters(), setup.observeSettings()) { semesters, settings ->
            AppState(loaded = true,
                semesters = semesters.map { SemesterInfo(it, 0) },
                activeSemesterId = settings?.activeSemesterId?.let { runCatching { it.toUuid() }.getOrNull() },
                settings = settings?.let { AppSettings(it.theme, it.haptics, it.reminderEnabled, it.reminderMinute) }
                    ?: AppSettings())
        }

    override fun observeCourses(semesterId: UUID): Flow<List<CourseEntity>> =
        setup.observeCourses(semesterId.toString())

    private fun observeBalances(semesterId: UUID): Flow<Map<String, OpeningBalanceEntity>> =
        setup.observeCourses(semesterId.toString()).map { courses ->
            courses.mapNotNull { setup.balance(it.id) }.associateBy { it.courseId }
        }

    override fun observeCourseStats(semesterId: UUID, asOf: LocalDate): Flow<List<CourseStats>> =
        combine(setup.observeSemester(semesterId.toString()),
            setup.observeCourses(semesterId.toString()),
            journal.observeSessionRows(semesterId.toString()),
            observeBalances(semesterId)) { semester, courses, rows, balances ->
            val startDay = semester?.startDay ?: 0L
            val byCourse = rows.groupBy { it.courseId }
            courses.map { course ->
                val balance = balances[course.id]?.let {
                    OpeningBalance(course.id.toUuid(), LocalDate.ofEpochDay(it.loggingStartDay),
                        it.conducted, it.present, it.approvedDl, it.pendingDl)
                } ?: OpeningBalance(course.id.toUuid(), LocalDate.ofEpochDay(startDay))
                CourseStats(course, balance,
                    AttendanceEngine.calculate(balance, byCourse[course.id].orEmpty().map { it.toFact() }, asOf))
            }
        }

    override fun observeDay(semesterId: UUID, date: LocalDate): Flow<DayView> {
        val sid = semesterId.toString()
        val dayId = dayUuid(sid, date.toEpochDay()).toString()
        return combine(
            setup.observeSemester(sid),
            journal.observeDay(sid, date.toEpochDay()),
            journal.observeSessionsForDay(dayId),
            schedule.observeTemplates(sid),
            setup.observeCourses(sid),
        ) { semester, day, sessions, templates, courses ->
            val zone = semester?.let { zoneOf(it) } ?: ZoneId.systemDefault()
            buildDayView(semesterId, date, day, sessions, templates, courses, today(zone))
        }
    }

    private fun buildDayView(
        semesterId: UUID, date: LocalDate,
        day: DayInstanceEntity?, sessions: List<SessionRow>,
        templates: List<TemplateRow>, courses: List<CourseEntity>, today: LocalDate,
    ): DayView {
        val weekday = date.dayOfWeek
        val weekend = !PeriodTimes.isCollegeWeekday(weekday)
        val courseById = courses.associateBy { it.id }
        val visible = sessions.filter { !it.suppressed }
        val extras = visible.filter { it.origin == SessionOrigin.EXTRA.name }.map { it.toView(courseById[it.courseId]) }
        val bySlot = visible.filter { it.slot != null }.associateBy { it.slot!! }
        // Project the effective template for unmaterialized future dates.
        val projected: Map<Int, UUID> = if (day == null && !weekend) {
            val version = ScheduleResolver.effectiveVersion(groupVersions(semesterId, templates), date)
            version?.let { ScheduleResolver.expectedSlots(it.template, weekday) }
                ?.associate { it.slot to it.courseId } ?: emptyMap()
        } else emptyMap()
        val displayWeekday = if (day != null && day.kind != DayKind.REGULAR.name)
            day.copiedWeekday?.let { DayOfWeek.of(it) } ?: weekday else weekday
        val times = PeriodTimes.slotTimes(displayWeekday)
        val cells = (1..PeriodTimes.SLOT_COUNT).map { slot ->
            val session = bySlot[slot]?.let { it.toView(courseById[it.courseId]) }
            val (start, end) = session?.let { it.startMinute to it.endMinute }
                ?: times.getOrNull(slot - 1) ?: (0 to 0)
            SlotCell(slot, start, end, session,
                projectedCourseId = if (session == null) projected[slot] else null)
        }
        val groups = mutableListOf<SlotGroup>()
        cells.forEach { cell ->
            val key = cell.session?.courseId ?: cell.projectedCourseId
            val last = groups.lastOrNull()
            if (last != null && last.courseId != null && last.courseId == key && cell.session != null) {
                groups[groups.lastIndex] = last.copy(cells = last.cells + cell)
            } else {
                groups += SlotGroup(key, listOf(cell))
            }
        }
        return DayView(semesterId, date,
            kind = day?.let { DayKind.valueOf(it.kind) } ?: DayKind.REGULAR,
            holiday = day?.holiday == true,
            copiedWeekday = day?.copiedWeekday?.let { DayOfWeek.of(it) },
            weekend = weekend,
            future = date.isAfter(today),
            writable = !date.isAfter(today),
            groups = groups, extras = extras)
    }

    private fun groupVersions(semesterId: UUID, rows: List<TemplateRow>): List<ScheduleVersion> {
        return rows.groupBy { it.effectiveDay }.entries.sortedBy { it.key }.map { (effectiveDay, slots) ->
            val template = WeekTemplate(slots.groupBy({ DayOfWeek.of(it.weekday) },
                { it.slot to it.courseId.toUuid() }).mapValues { (_, pairs) -> pairs.toMap() })
            ScheduleVersion(dayUuid(semesterId.toString(), effectiveDay), semesterId,
                LocalDate.ofEpochDay(effectiveDay), template)
        }
    }

    private suspend fun loadVersions(semesterId: String): List<ScheduleVersion> =
        schedule.versions(semesterId).map { v ->
            val slots = schedule.slots(v.id)
            val template = WeekTemplate(slots.groupBy({ DayOfWeek.of(it.weekday) },
                { it.slot to it.courseId.toUuid() }).mapValues { (_, p) -> p.toMap() })
            ScheduleVersion(v.id.toUuid(), semesterId.toUuid(), LocalDate.ofEpochDay(v.effectiveDay), template)
        }

    override fun observeMonth(semesterId: UUID, month: YearMonth): Flow<List<DaySummary>> {
        val from = month.atDay(1).toEpochDay()
        val to = month.atEndOfMonth().toEpochDay()
        return journal.observeDaySummaries(semesterId.toString(), from, to).map { rows ->
            rows.map {
                DaySummary(LocalDate.ofEpochDay(it.day), DayKind.valueOf(it.kind), it.holiday,
                    it.copiedWeekday?.let(DayOfWeek::of), it.total, it.recorded, it.sessionCount > 0)
            }
        }
    }

    override fun observeIncompleteDates(semesterId: UUID, through: LocalDate): Flow<List<LocalDate>> =
        combine(journal.observeSessionRows(semesterId.toString()), observeBalances(semesterId)) { rows, balances ->
            rows.filter { row ->
                row.outcomeKind == null && row.counting && !row.cancelled && !row.suppressed &&
                    !row.holiday && row.day < through.toEpochDay() &&
                    row.day >= (balances[row.courseId]?.loggingStartDay ?: Long.MIN_VALUE)
            }.map { LocalDate.ofEpochDay(it.day) }.distinct().sorted()
        }

    override fun observeDlEvents(semesterId: UUID): Flow<List<DlEventView>> =
        combine(journal.observeEvents(semesterId.toString()),
            journal.observeSessionRows(semesterId.toString()),
            setup.observeCourses(semesterId.toString())) { rows, sessions, courses ->
            val byEvent = sessions.filter { it.outcomeDlEventId != null }.groupBy { it.outcomeDlEventId!! }
            rows.map { row ->
                val status = when {
                    row.periods == 0 -> DlEventStatus.EMPTY
                    row.pending > 0 && row.approved == 0 && row.rejected == 0 -> DlEventStatus.PENDING
                    row.approved > 0 && row.pending == 0 && row.rejected == 0 -> DlEventStatus.APPROVED
                    row.rejected > 0 && row.pending == 0 && row.approved == 0 -> DlEventStatus.REJECTED
                    else -> DlEventStatus.MIXED
                }
                val attached = byEvent[row.id].orEmpty()
                DlEventView(row.id.toUuid(), row.title, row.createdAt, status,
                    row.periods, row.pending, row.approved, row.rejected, row.dates,
                    attached.map { LocalDate.ofEpochDay(it.day) }.distinct().sorted(),
                    attached.mapNotNull { courses.firstOrNull { c -> c.id == it.courseId }?.code }.distinct().sorted())
            }
        }

    override fun observeDlEventSessions(eventId: UUID): Flow<List<SessionView>> =
        combine(journal.observeSessionsForEvent(eventId.toString()),
            setup.observeSemesters()) { rows, semesters ->
            val courses = semesters.flatMap { setup.activeCourses(it.id) }.associateBy { it.id }
            rows.map { it.toView(courses[it.courseId]) }
        }

    override fun observeDlStats(semesterId: UUID): Flow<DlStats> =
        combine(journal.observeDlDates(semesterId.toString()),
            journal.observeDlStatusCounts(semesterId.toString()),
            journal.observeEvents(semesterId.toString()),
            observeBalances(semesterId)) { dates, counts, events, balances ->
            fun count(status: String) = counts.firstOrNull { it.dlStatus == status }?.total ?: 0
            DlStats(dates, events.size, count("PENDING"), count("APPROVED"), count("REJECTED"),
                balances.values.sumOf { it.pendingDl }, balances.values.sumOf { it.approvedDl })
        }

    override fun observeUndoable(): Flow<UndoableAction?> = journal.observeUndoable().map { batch ->
        batch?.let {
            val label = runCatching {
                undoJson.decodeFromString(UndoPayload.serializer(), it.payload).label
            }.getOrDefault(it.kind)
            UndoableAction(it.id.toUuid(), it.kind, label, it.createdAt)
        }
    }

    override fun observeTemplates(semesterId: UUID): Flow<List<ScheduleVersion>> =
        schedule.observeTemplates(semesterId.toString()).map { groupVersions(semesterId, it) }

    override fun currentTemplate(semesterId: UUID): Flow<WeekTemplate> =
        schedule.observeTemplates(semesterId.toString()).map { rows ->
            groupVersions(semesterId, rows).lastOrNull()?.template ?: WeekTemplate()
        }

    // ---------------------------------------------------------------- generation

    override suspend fun ensureMaterialized(semesterId: UUID) {
        val semester = setup.semester(semesterId.toString()) ?: return
        val today = today(zoneOf(semester))
        val ceiling = ceiling(semester, today)
        val versions = loadVersions(semester.id)
        if (versions.isEmpty()) return
        val from = maxOf(semester.startDay, (semester.materializedDay ?: (semester.startDay - 1)) + 1)
        if (from > ceiling.toEpochDay()) return
        database.withTransaction {
            var day = LocalDate.ofEpochDay(from)
            while (!day.isAfter(ceiling)) {
                reconcileDay(semester, day, versions, null, null)
                day = day.plusDays(1)
            }
            setup.setMaterializedDay(semester.id, ceiling.toEpochDay())
        }
    }

    override suspend fun ensureDay(semesterId: UUID, date: LocalDate) {
        val semester = setup.semester(semesterId.toString()) ?: return
        if (date.isAfter(ceiling(semester, today(zoneOf(semester))))) return
        val versions = loadVersions(semester.id)
        database.withTransaction { reconcileDay(semester, date, versions, null, null) }
    }

    /** Apply template reconciliation to one date, recording batch images and conflicts if asked. */
    private suspend fun reconcileDay(
        semester: SemesterEntity, date: LocalDate, versions: List<ScheduleVersion>,
        batch: BatchBuilder?, conflicts: MutableList<ConflictInfo>?,
    ) {
        val weekday = date.dayOfWeek
        if (!PeriodTimes.isCollegeWeekday(weekday)) return
        val version = ScheduleResolver.effectiveVersion(versions, date) ?: return
        val expected = ScheduleResolver.expectedSlots(version.template, weekday)
        val sid = semester.id
        val epochDay = date.toEpochDay()
        if (epochDay < semester.startDay) return
        var day = journal.day(sid, epochDay)
        if (day == null) {
            if (expected.isEmpty()) return
            val entity = DayInstanceEntity(dayUuid(sid, epochDay).toString(), sid, epochDay, DayKind.REGULAR.name)
            journal.insertDay(entity)
            batch?.createdDays?.add(entity.id)
            day = entity
        }
        if (day.kind != DayKind.REGULAR.name) return
        val existing = journal.sessionsForDay(day.id)
        val ops = ScheduleResolver.reconcile(expected, existing.map {
            TrackedSession(it.id.toUuid(), it.slot, it.courseId.toUuid(),
                SessionOrigin.valueOf(it.origin), it.cancelled, it.pinned, it.suppressed,
                journal.outcome(it.id) != null)
        })
        ops.forEach { op ->
            when (op) {
                is ReconcileOp.Create -> {
                    val id = sessionUuid(sid, epochDay, op.slot.slot)
                    journal.insertSession(SessionEntity(id.toString(), day.id, sid,
                        op.slot.courseId.toString(), op.slot.slot, SessionOrigin.TEMPLATE.name,
                        counting = true, op.slot.startMinute, op.slot.endMinute))
                    batch?.createdSessions?.add(id.toString())
                }
                is ReconcileOp.Update -> applyUpdate(op.sessionId.toString(), op.slot, batch)
                is ReconcileOp.Restore -> applyUpdate(op.sessionId.toString(), op.slot, batch)
                is ReconcileOp.Suppress -> {
                    val s = journal.session(op.sessionId.toString()) ?: return@forEach
                    batch?.sessions?.add(s.image(journal.outcome(s.id) != null))
                    journal.setSuppressed(s.id, true)
                }
                is ReconcileOp.Delete -> {
                    val s = journal.session(op.sessionId.toString()) ?: return@forEach
                    batch?.deletedSessions?.add(s.image(journal.outcome(s.id) != null))
                    journal.deleteSession(s.id)
                }
                is ReconcileOp.Conflict -> {
                    val s = journal.session(op.sessionId.toString()) ?: return@forEach
                    if (conflicts != null) {
                        conflicts += ConflictInfo(s.id.toUuid(), date, s.slot,
                            setup.course(s.courseId)?.code ?: "?", op.expected.courseId,
                            if (s.cancelled) "Marked not conducted" else "Already recorded")
                    }
                }
            }
        }
        // Remove an emptied generated day entirely; holiday and extras keep it alive.
        if (expected.isEmpty() && !day.holiday && journal.sessionsForDay(day.id).isEmpty()) {
            batch?.deletedDays?.add(day.deletedImage())
            journal.deleteDay(day.id)
        }
    }

    private suspend fun applyUpdate(sessionId: String, slot: ExpectedSlot, batch: BatchBuilder?) {
        val s = journal.session(sessionId) ?: return
        if (s.courseId == slot.courseId.toString() && s.startMinute == slot.startMinute &&
            s.endMinute == slot.endMinute && !s.suppressed) return
        batch?.sessions?.add(s.image(journal.outcome(s.id) != null))
        journal.reconcileSession(s.id, slot.courseId.toString(), slot.startMinute, slot.endMinute)
    }

    override suspend fun createSemester(setupData: SemesterSetup): UUID {
        require(setupData.name.isNotBlank()) { "Enter a semester name." }
        require(setupData.courses.isNotEmpty()) { "Add at least one course." }
        val zone = ZoneId.of(setupData.zoneId)
        val today = today(zone)
        val codes = setupData.courses.map { it.input.code.trim().uppercase(Locale.ROOT) }
        require(codes.distinct().size == codes.size) { "Course codes must be unique." }
        val start = setupData.startDate
        setupData.courses.forEach { cs ->
            require(cs.input.name.isNotBlank() && cs.input.code.isNotBlank()) { "Each course needs a name and code." }
            val b = cs.balance
            if (b != null) {
                require(b.courseId == cs.courseId) { "Invalid course ownership." }
                require(!b.loggingStartDate.isBefore(start)) { "First logging date cannot precede the semester start." }
                require(b.conducted == 0L || b.loggingStartDate.isAfter(start)) {
                    "Opening attendance needs historical dates before the first logging date." }
                require(b.conducted == 0L || !b.loggingStartDate.isAfter(today)) {
                    "Historical attendance cannot start in the future." }
            }
        }
        val courseIds = setupData.courses.map { it.courseId.toString() }.toSet()
        require(setupData.timetable.assignments.values.flatMap { it.values }.all { it.toString() in courseIds }) {
            "The timetable can only contain courses in this semester." }
        val semesterId = UUID.randomUUID()
        database.withTransaction {
            setup.insertSemester(SemesterEntity(semesterId.toString(), setupData.name.trim(),
                start.toEpochDay(), zone.id))
            setupData.courses.forEach { cs ->
                setup.insertCourse(CourseEntity(cs.courseId.toString(), semesterId.toString(),
                    cs.input.name.trim(), cs.input.code.trim(), cs.input.code.trim().uppercase(Locale.ROOT),
                    cs.input.type.name, cs.input.colorArgb))
                val b = cs.balance ?: OpeningBalance(cs.courseId, start)
                setup.insertBalance(OpeningBalanceEntity(cs.courseId.toString(),
                    b.loggingStartDate.toEpochDay(), b.conducted, b.present, b.approvedDl, b.pendingDl))
            }
            if (!setupData.timetable.isEmpty()) {
                val versionId = UUID.randomUUID().toString()
                schedule.insertVersion(ScheduleVersionEntity(versionId, semesterId.toString(),
                    start.toEpochDay(), now()))
                schedule.insertSlots(setupData.timetable.assignments.flatMap { (weekday, slots) ->
                    slots.map { (slot, course) -> TemplateSlotEntity(versionId, weekday.value, slot, course.toString()) }
                })
            }
            if (setup.settings() == null) {
                setup.insertSettings(AppSettingsEntity(activeSemesterId = semesterId.toString()))
            } else {
                setup.switchActiveSemester(semesterId.toString())
            }
        }
        return semesterId
    }

    override suspend fun updateSemester(semesterId: UUID, name: String, startDate: LocalDate, zoneId: String) {
        require(name.isNotBlank()) { "Enter a semester name." }
        ZoneId.of(zoneId)
        database.withTransaction {
            val current = setup.semester(semesterId.toString()) ?: throw IllegalArgumentException("Unknown semester.")
            // Start changes may never exclude opening-balance coverage or recorded days.
            val earliestCutoff = setup.activeCourses(semesterId.toString())
                .mapNotNull { setup.balance(it.id)?.loggingStartDay }.minOrNull()
            val earliestSession = journal.earliestSessionDay(semesterId.toString())
            listOfNotNull(earliestCutoff, earliestSession).forEach { bound ->
                require(startDate.toEpochDay() <= bound) { "Semester start cannot move past recorded history." }
            }
            setup.updateSemester(semesterId.toString(), name.trim(), startDate.toEpochDay(), zoneId)
            if (current.startDay != startDate.toEpochDay()) setup.setMaterializedDay(semesterId.toString(), null)
        }
    }

    override suspend fun setSemesterCompleted(semesterId: UUID, completed: Boolean) {
        database.withTransaction {
            val semester = setup.semester(semesterId.toString()) ?: throw IllegalArgumentException("Unknown semester.")
            val active = setup.activeSemesterId()
            if (completed) {
                check(active == semesterId.toString() || active == null) { "Only the active semester can be marked done." }
                if (active == semesterId.toString()) check(setup.clearActiveSemester(semesterId.toString()) == 1)
                check(setup.markSemesterCompleted(semesterId.toString(), true,
                    today(zoneOf(semester)).toEpochDay()) == 1)
            } else {
                check(active == null || active == semesterId.toString()) { "Another semester is already active." }
                check(setup.markSemesterCompleted(semesterId.toString(), false, null) == 1)
                check(setup.switchActiveSemester(semesterId.toString()) == 1)
            }
        }
    }

    override suspend fun setActiveSemester(semesterId: UUID) {
        val semester = setup.semester(semesterId.toString()) ?: throw IllegalArgumentException("Unknown semester.")
        require(!semester.archived) { "Reopen the semester before making it active." }
        check(setup.switchActiveSemester(semesterId.toString()) == 1) { "Settings unavailable." }
    }

    override suspend fun addCourse(semesterId: UUID, courseSetup: CourseSetup) {
        val semester = setup.semester(semesterId.toString()) ?: throw IllegalArgumentException("Unknown semester.")
        val start = LocalDate.ofEpochDay(semester.startDay)
        require(courseSetup.input.name.isNotBlank() && courseSetup.input.code.isNotBlank()) {
            "Each course needs a name and code." }
        courseSetup.balance?.let { b ->
            require(!b.loggingStartDate.isBefore(start)) { "First logging date cannot precede the semester start." }
            require(b.conducted == 0L || b.loggingStartDate.isAfter(start)) {
                "Opening attendance needs historical dates before the first logging date." }
        }
        database.withTransaction {
            setup.insertCourse(CourseEntity(courseSetup.courseId.toString(), semesterId.toString(),
                courseSetup.input.name.trim(), courseSetup.input.code.trim(),
                courseSetup.input.code.trim().uppercase(Locale.ROOT), courseSetup.input.type.name,
                courseSetup.input.colorArgb))
            val b = courseSetup.balance ?: OpeningBalance(courseSetup.courseId, start)
            setup.insertBalance(OpeningBalanceEntity(courseSetup.courseId.toString(),
                b.loggingStartDate.toEpochDay(), b.conducted, b.present, b.approvedDl, b.pendingDl))
        }
    }

    override suspend fun updateCourse(courseId: UUID, input: CourseInput) {
        require(input.name.isNotBlank() && input.code.isNotBlank()) { "Each course needs a name and code." }
        check(setup.updateCourse(courseId.toString(), input.name.trim(), input.code.trim(),
            input.code.trim().uppercase(Locale.ROOT), input.type.name, input.colorArgb) == 1) { "Unknown course." }
    }

    override suspend fun retireCourse(courseId: UUID) {
        check(setup.retireCourse(courseId.toString()) == 1) { "Unknown course." }
    }

    override suspend fun correctOpeningBalance(balance: OpeningBalance) {
        val course = setup.course(balance.courseId.toString()) ?: throw IllegalArgumentException("Unknown course.")
        val semester = setup.semester(course.semesterId) ?: throw IllegalArgumentException("Unknown semester.")
        val start = LocalDate.ofEpochDay(semester.startDay)
        require(!balance.loggingStartDate.isBefore(start)) { "First logging date cannot precede the semester start." }
        require(balance.conducted == 0L || balance.loggingStartDate.isAfter(start)) {
            "Opening attendance needs historical dates before the first logging date." }
        database.withTransaction {
            val existing = setup.balance(balance.courseId.toString())
                ?: throw IllegalArgumentException("Unknown opening balance.")
            if (existing.loggingStartDay != balance.loggingStartDate.toEpochDay()) {
                val hasRecorded = journal.sessionRowsForCourse(semester.id, course.id)
                    .any { it.outcomeKind != null }
                check(!hasRecorded) { "The first logging date is locked once attendance is recorded." }
            }
            val batch = BatchBuilder("Opening balance updated")
            batch.balances += existing.image()
            check(setup.updateBalance(course.id, balance.loggingStartDate.toEpochDay(), balance.conducted,
                balance.present, balance.approvedDl, balance.pendingDl) == 1)
            recordBatch("opening_balance", batch)
        }
    }

    override suspend fun resolveOpeningPendingDl(courseId: UUID, approve: Boolean) {
        database.withTransaction {
            val existing = setup.balance(courseId.toString())
                ?: throw IllegalArgumentException("Unknown opening balance.")
            check(existing.pendingDl > 0) { "No pending opening duty leave to resolve." }
            val batch = BatchBuilder(if (approve) "Opening duty leave approved" else "Opening duty leave rejected")
            batch.balances += existing.image()
            check(setup.updateBalance(courseId.toString(), existing.loggingStartDay, existing.conducted,
                existing.present, if (approve) existing.approvedDl + existing.pendingDl else existing.approvedDl,
                0) == 1)
            recordBatch("opening_dl", batch)
        }
    }

    override suspend fun updatePreferences(theme: String, haptics: Boolean, reminderEnabled: Boolean, reminderMinute: Int) {
        require(theme in setOf("SYSTEM", "LIGHT", "DARK"))
        require(reminderMinute in 0..1439) { "Choose a valid reminder time." }
        check(setup.updatePreferences(theme, haptics, reminderEnabled, reminderMinute) == 1) { "Settings unavailable." }
    }

    // ---------------------------------------------------------------- schedule

    private suspend fun validateTemplate(semesterId: String, template: WeekTemplate) {
        template.assignments.values.flatMap { it.values }.map { it.toString() }.toSet().forEach { id ->
            val course = setup.course(id) ?: throw IllegalArgumentException("Unknown course in timetable.")
            require(course.semesterId == semesterId) { "Timetable courses must belong to the semester." }
            require(!course.retired) { "Retired courses cannot stay on the timetable." }
        }
    }

    override suspend fun saveTemplate(semesterId: UUID, template: WeekTemplate, effectiveFrom: LocalDate): List<ConflictInfo> {
        val semester = setup.semester(semesterId.toString()) ?: throw IllegalArgumentException("Unknown semester.")
        require(!effectiveFrom.isBefore(today(zoneOf(semester)))) {
            "Timetable changes apply from today or a future date. Edit past days directly." }
        validateTemplate(semester.id, template)
        val conflicts = mutableListOf<ConflictInfo>()
        database.withTransaction {
            val batch = BatchBuilder("Timetable updated")
            schedule.versionAt(semester.id, effectiveFrom.toEpochDay())?.let { schedule.deleteVersion(it.id) }
            val versionId = UUID.randomUUID().toString()
            schedule.insertVersion(ScheduleVersionEntity(versionId, semester.id, effectiveFrom.toEpochDay(), now()))
            schedule.insertSlots(template.assignments.flatMap { (weekday, slots) ->
                slots.map { (slot, course) -> TemplateSlotEntity(versionId, weekday.value, slot, course.toString()) }
            })
            batch.createdVersions += versionId
            val through = semester.materializedDay
            if (through != null && through >= effectiveFrom.toEpochDay()) {
                val versions = loadVersions(semester.id)
                var day = effectiveFrom.toEpochDay()
                while (day <= through) {
                    reconcileDay(semester, LocalDate.ofEpochDay(day), versions, batch, conflicts)
                    day += 1
                }
            }
            recordBatch("schedule", batch)
        }
        return conflicts
    }

    override suspend fun previewTemplateConflicts(semesterId: UUID, template: WeekTemplate, effectiveFrom: LocalDate): List<ConflictInfo> {
        val semester = setup.semester(semesterId.toString()) ?: throw IllegalArgumentException("Unknown semester.")
        validateTemplate(semester.id, template)
        val versions = loadVersions(semester.id).filter { it.effectiveFrom != effectiveFrom } +
            ScheduleVersion(UUID.randomUUID(), semesterId, effectiveFrom, template)
        val conflicts = mutableListOf<ConflictInfo>()
        val through = semester.materializedDay ?: return emptyList()
        var day = effectiveFrom.toEpochDay()
        while (day <= through) {
            val date = LocalDate.ofEpochDay(day)
            val entity = journal.day(semester.id, day)
            if (entity != null && entity.kind == DayKind.REGULAR.name && PeriodTimes.isCollegeWeekday(date.dayOfWeek)) {
                val version = ScheduleResolver.effectiveVersion(versions, date) ?: run { day += 1; continue }
                val expected = ScheduleResolver.expectedSlots(version.template, date.dayOfWeek)
                val existing = journal.sessionsForDay(entity.id)
                ScheduleResolver.reconcile(expected, existing.map {
                    TrackedSession(it.id.toUuid(), it.slot, it.courseId.toUuid(),
                        SessionOrigin.valueOf(it.origin), it.cancelled, it.pinned, it.suppressed,
                        journal.outcome(it.id) != null)
                }).forEach { op ->
                    if (op is ReconcileOp.Conflict) {
                        val s = existing.first { it.id == op.sessionId.toString() }
                        conflicts += ConflictInfo(op.sessionId, date, s.slot,
                            setup.course(s.courseId)?.code ?: "?", op.expected.courseId,
                            if (s.cancelled) "Marked not conducted" else "Already recorded")
                    }
                }
            }
            day += 1
        }
        return conflicts
    }

    // ---------------------------------------------------------------- day operations

    private suspend fun requireWritableSession(sessionId: UUID, today: LocalDate): SessionEntity {
        val session = journal.session(sessionId.toString()) ?: throw IllegalArgumentException("Unknown class period.")
        val day = journal.dayById(session.dayId) ?: throw IllegalArgumentException("Unknown day.")
        require(!LocalDate.ofEpochDay(day.day).isAfter(today)) { "Future classes cannot be recorded yet." }
        require(!day.holiday) { "This day is marked as a holiday." }
        require(!session.suppressed) { "This period is no longer on the schedule." }
        require(!session.cancelled) { "This period is marked not conducted." }
        return session
    }

    private suspend fun recordBatch(kind: String, batch: BatchBuilder) {
        // Freeze each touched row's post-write revision so undo can tell "still
        // exactly as this change left it" apart from "modified or deleted since".
        batch.sessions.forEach { batch.expected["s:${it.id}"] = journal.session(it.id)?.revision ?: -1 }
        batch.days.forEach { batch.expected["d:${it.id}"] = journal.dayById(it.id)?.revision ?: -1 }
        batch.outcomes.forEach { batch.expected["o:${it.sessionId}"] = journal.outcome(it.sessionId)?.revision ?: -1 }
        batch.balances.forEach { batch.expected["b:${it.courseId}"] = setup.balance(it.courseId)?.revision ?: -1 }
        batch.createdSessions.forEach { batch.expected["s:$it"] = journal.session(it)?.revision ?: -1 }
        batch.createdDays.forEach { batch.expected["d:$it"] = journal.dayById(it)?.revision ?: -1 }
        batch.deletedSessions.forEach { batch.expected["s:${it.id}"] = -1 }
        batch.deletedDays.forEach { batch.expected["d:${it.id}"] = -1 }
        journal.insertBatch(ChangeBatchEntity(UUID.randomUUID().toString(), kind, now(), payload = batch.payload()))
        journal.pruneBatches(30)
    }

    private suspend fun batchOutcomeImage(batch: BatchBuilder, sessionId: String) {
        batch.outcomes += journal.outcome(sessionId)?.image() ?: missingOutcomeImage(sessionId)
    }

    override suspend fun recordPeriod(sessionId: UUID, outcome: AttendanceOutcome?) {
        require(outcome == null || outcome == AttendanceOutcome.PRESENT || outcome == AttendanceOutcome.ABSENT) {
            "Use duty leave actions for duty leave outcomes." }
        val session = journal.session(sessionId.toString()) ?: throw IllegalArgumentException("Unknown class period.")
        val semester = setup.semester(session.semesterId) ?: throw IllegalArgumentException("Unknown semester.")
        val today = today(zoneOf(semester))
        database.withTransaction {
            requireWritableSession(sessionId, today)
            val batch = BatchBuilder("Attendance updated")
            batchOutcomeImage(batch, sessionId.toString())
            if (outcome == null) {
                journal.deleteOutcome(sessionId.toString())
            } else if (journal.outcome(sessionId.toString()) == null) {
                journal.insertOutcome(AttendanceOutcomeEntity(sessionId.toString(), outcome.name))
            } else {
                journal.updateOutcome(sessionId.toString(), outcome.name, null, null)
            }
            recordBatch("record", batch)
        }
    }

    override suspend fun recordAll(sessionIds: List<UUID>, outcome: AttendanceOutcome) {
        require(outcome == AttendanceOutcome.PRESENT || outcome == AttendanceOutcome.ABSENT) {
            "Use duty leave actions for duty leave outcomes." }
        if (sessionIds.isEmpty()) return
        val semesterId = journal.session(sessionIds.first().toString())?.semesterId
            ?: throw IllegalArgumentException("Unknown class period.")
        val semester = setup.semester(semesterId) ?: throw IllegalArgumentException("Unknown semester.")
        val today = today(zoneOf(semester))
        database.withTransaction {
            val batch = BatchBuilder(if (outcome == AttendanceOutcome.PRESENT) "Marked present" else "Marked absent")
            sessionIds.distinct().forEach { id ->
                requireWritableSession(id, today)
                batchOutcomeImage(batch, id.toString())
                if (journal.outcome(id.toString()) == null) {
                    journal.insertOutcome(AttendanceOutcomeEntity(id.toString(), outcome.name))
                } else {
                    journal.updateOutcome(id.toString(), outcome.name, null, null)
                }
            }
            recordBatch("record", batch)
        }
    }

    override suspend fun markAllPresent(semesterId: UUID, date: LocalDate): Int {
        val semester = setup.semester(semesterId.toString()) ?: throw IllegalArgumentException("Unknown semester.")
        require(!date.isAfter(today(zoneOf(semester)))) { "Future classes cannot be recorded yet." }
        return database.withTransaction {
            val batch = BatchBuilder("Marked all present")
            reconcileDay(semester, date, loadVersions(semester.id), batch, null)
            val day = journal.day(semester.id, date.toEpochDay())
                ?: throw IllegalArgumentException("No classes scheduled for this date.")
            require(!day.holiday) { "This day is marked as a holiday." }
            val eligible = journal.sessionsForDay(day.id).filter { s ->
                s.origin != SessionOrigin.EXTRA.name && s.counting && !s.cancelled && !s.suppressed &&
                    journal.outcome(s.id) == null
            }
            batch.label = "Marked ${eligible.size} periods present"
            eligible.forEach { s ->
                batch.outcomes += missingOutcomeImage(s.id)
                journal.insertOutcome(AttendanceOutcomeEntity(s.id, "PRESENT"))
            }
            if (eligible.isNotEmpty()) recordBatch("mark_all", batch)
            eligible.size
        }
    }

    override suspend fun setCancelled(sessionId: UUID, cancelled: Boolean) {
        val session = journal.session(sessionId.toString()) ?: throw IllegalArgumentException("Unknown class period.")
        val semester = setup.semester(session.semesterId) ?: throw IllegalArgumentException("Unknown semester.")
        val day = journal.dayById(session.dayId) ?: throw IllegalArgumentException("Unknown day.")
        require(!LocalDate.ofEpochDay(day.day).isAfter(today(zoneOf(semester)))) {
            "Future classes cannot be changed yet." }
        database.withTransaction {
            val batch = BatchBuilder(if (cancelled) "Period marked not conducted" else "Period restored")
            batch.sessions += session.image(journal.outcome(session.id) != null)
            check(journal.setCancelled(session.id, cancelled) == 1)
            recordBatch("cancel", batch)
        }
    }

    override suspend fun setHoliday(semesterId: UUID, date: LocalDate, holiday: Boolean) {
        val semester = setup.semester(semesterId.toString()) ?: throw IllegalArgumentException("Unknown semester.")
        require(!date.isAfter(today(zoneOf(semester)))) { "Future days cannot be marked as holidays yet." }
        database.withTransaction {
            val batch = BatchBuilder(if (holiday) "Marked as holiday" else "Holiday reversed")
            val existing = journal.day(semester.id, date.toEpochDay())
            when {
                existing == null && !holiday -> return@withTransaction
                existing == null -> batch.createdDays += dayUuid(semester.id, date.toEpochDay()).toString()
                else -> batch.days += existing.image()
            }
            val day = existing ?: requireDay(semesterId, date)
            check(journal.setHoliday(day.id, holiday) == 1)
            recordBatch("holiday", batch)
        }
    }

    private suspend fun requireDay(semesterId: UUID, date: LocalDate): DayInstanceEntity {
        journal.day(semesterId.toString(), date.toEpochDay())?.let { return it }
        val entity = DayInstanceEntity(dayUuid(semesterId.toString(), date.toEpochDay()).toString(),
            semesterId.toString(), date.toEpochDay(), DayKind.REGULAR.name)
        journal.insertDay(entity)
        return entity
    }

    override suspend fun copyWeekday(semesterId: UUID, date: LocalDate, sourceWeekday: DayOfWeek): CopyResult {
        require(PeriodTimes.isCollegeWeekday(sourceWeekday)) { "Copy a weekday timetable, Monday to Friday." }
        val semester = setup.semester(semesterId.toString()) ?: throw IllegalArgumentException("Unknown semester.")
        require(!date.isAfter(today(zoneOf(semester)))) { "Future days cannot be scheduled yet." }
        val versions = loadVersions(semester.id)
        val version = ScheduleResolver.effectiveVersion(versions, date)
            ?: throw IllegalArgumentException("No weekly timetable exists for that date.")
        val expected = ScheduleResolver.expectedSlots(version.template, sourceWeekday)
        require(expected.isNotEmpty()) { "The chosen weekday has no classes to copy." }
        return database.withTransaction {
            val batch = BatchBuilder("Copied timetable")
            val hadDay = journal.day(semester.id, date.toEpochDay())
            if (hadDay == null) batch.createdDays += dayUuid(semester.id, date.toEpochDay()).toString()
            else batch.days += hadDay.image()
            val day = hadDay ?: requireDay(semesterId, date)
            val existing = journal.sessionsForDay(day.id)
            val bySlot = existing.filter { it.slot != null }.associateBy { it.slot!! }
            val conflicts = mutableListOf<ConflictInfo>()
            var created = 0
            expected.forEach { slot ->
                val current = bySlot[slot.slot]
                when {
                    current == null -> {
                        val id = stableUuid("copied", semester.id, date.toEpochDay(), slot.slot).toString()
                        journal.insertSession(SessionEntity(id, day.id, semester.id, slot.courseId.toString(),
                            slot.slot, SessionOrigin.COPIED.name, counting = true,
                            slot.startMinute, slot.endMinute))
                        batch.createdSessions += id
                        created++
                    }
                    current.cancelled || current.pinned || journal.outcome(current.id) != null ->
                        conflicts += ConflictInfo(current.id.toUuid(), date, current.slot,
                            setup.course(current.courseId)?.code ?: "?", slot.courseId, "Already recorded")
                    current.courseId != slot.courseId.toString() || current.origin != SessionOrigin.COPIED.name ||
                        current.startMinute != slot.startMinute || current.endMinute != slot.endMinute || !current.counting -> {
                        batch.sessions += current.image(journal.outcome(current.id) != null)
                        journal.adoptSession(current.id, SessionOrigin.COPIED.name, slot.courseId.toString(),
                            slot.startMinute, slot.endMinute, true)
                        created++
                    }
                    else -> Unit // identical copy: idempotent no-op
                }
            }
            // An exact copy replaces the whole day; a merge around recorded periods is custom.
            val exact = conflicts.isEmpty() && bySlot.values.all { it.suppressed }
            val kind = if (exact) DayKind.COPIED else DayKind.CUSTOM
            if (day.kind != kind.name || day.copiedWeekday != sourceWeekday.value) {
                journal.updateDayKind(day.id, kind.name, sourceWeekday.value)
            }
            if (created > 0 || hadDay == null) recordBatch("copy", batch)
            CopyResult(created, conflicts, kind)
        }
    }

    override suspend fun addExtra(semesterId: UUID, date: LocalDate, courseId: UUID,
        startMinute: Int, endMinute: Int, counting: Boolean): UUID {
        val semester = setup.semester(semesterId.toString()) ?: throw IllegalArgumentException("Unknown semester.")
        val course = setup.course(courseId.toString()) ?: throw IllegalArgumentException("Unknown course.")
        require(course.semesterId == semester.id) { "Extra classes must belong to the semester." }
        require(startMinute in 0..1439 && endMinute in 1..1440 && startMinute < endMinute) { "Check the extra class times." }
        require(!date.isAfter(today(zoneOf(semester)))) { "Future days cannot be scheduled yet." }
        return database.withTransaction {
            val batch = BatchBuilder("Extra class added")
            val hadDay = journal.day(semester.id, date.toEpochDay())
            if (hadDay == null) batch.createdDays += dayUuid(semester.id, date.toEpochDay()).toString()
            val day = hadDay ?: requireDay(semesterId, date)
            val id = UUID.randomUUID().toString()
            journal.insertSession(SessionEntity(id, day.id, semester.id, course.id, null,
                SessionOrigin.EXTRA.name, counting, startMinute, endMinute))
            batch.createdSessions += id
            recordBatch("extra", batch)
            id.toUuid()
        }
    }

    override suspend fun setExtraCounting(sessionId: UUID, counting: Boolean) {
        val session = journal.session(sessionId.toString()) ?: throw IllegalArgumentException("Unknown class period.")
        require(session.origin == SessionOrigin.EXTRA.name) { "Only extra classes change attendance counting." }
        database.withTransaction {
            val batch = BatchBuilder(if (counting) "Extra class now counts" else "Extra class no longer counts")
            batch.sessions += session.image(journal.outcome(session.id) != null)
            check(journal.setCounting(session.id, counting) == 1)
            recordBatch("extra_counting", batch)
        }
    }

    override suspend fun deleteExtra(sessionId: UUID) {
        val session = journal.session(sessionId.toString()) ?: throw IllegalArgumentException("Unknown class period.")
        require(session.origin == SessionOrigin.EXTRA.name) { "Only extra classes can be removed." }
        database.withTransaction {
            val batch = BatchBuilder("Extra class removed")
            journal.outcome(session.id)?.image()?.let { batch.outcomes += it }
            batch.deletedSessions += session.image(journal.outcome(session.id) != null)
            journal.deleteOutcome(session.id)
            journal.deleteSession(session.id)
            recordBatch("extra_remove", batch)
        }
    }

    override suspend fun substituteCourse(sessionId: UUID, courseId: UUID) {
        val session = journal.session(sessionId.toString()) ?: throw IllegalArgumentException("Unknown class period.")
        val course = setup.course(courseId.toString()) ?: throw IllegalArgumentException("Unknown course.")
        require(course.semesterId == session.semesterId) { "Substitute courses must belong to the semester." }
        database.withTransaction {
            val batch = BatchBuilder("Class substituted")
            batch.sessions += session.image(journal.outcome(session.id) != null)
            check(journal.substituteCourse(session.id, course.id) == 1)
            recordBatch("substitute", batch)
        }
    }

    // ---------------------------------------------------------------- duty leave

    override suspend fun applyDutyLeave(semesterId: UUID, sessionIds: List<UUID>, title: String?, replace: Boolean): DlApplyResult {
        require(sessionIds.isNotEmpty()) { "Choose at least one period." }
        val semester = setup.semester(semesterId.toString()) ?: throw IllegalArgumentException("Unknown semester.")
        val today = today(zoneOf(semester))
        return database.withTransaction {
            val conflicts = mutableListOf<UUID>()
            val sessions = sessionIds.distinct().map { id ->
                val s = requireWritableSession(id, today)
                require(s.semesterId == semester.id) { "Duty leave periods must belong to the semester." }
                require(s.counting) { "Only attendance-counting periods can receive duty leave." }
                if (journal.outcome(id.toString()) != null) conflicts += id
                s
            }
            if (conflicts.isNotEmpty() && !replace) {
                return@withTransaction DlApplyResult(UUID(0, 0), 0, conflicts)
            }
            val eventId = UUID.randomUUID().toString()
            journal.insertEvent(DlEventEntity(eventId, semester.id, title?.trim()?.ifBlank { null }, now()))
            val batch = BatchBuilder("Duty leave applied")
            batch.createdEvents += eventId
            sessions.forEach { s ->
                batchOutcomeImage(batch, s.id)
                if (journal.outcome(s.id) == null) {
                    journal.insertOutcome(AttendanceOutcomeEntity(s.id, "DL", "PENDING", eventId))
                } else {
                    journal.updateOutcome(s.id, "DL", "PENDING", eventId)
                }
            }
            recordBatch("dl_apply", batch)
            DlApplyResult(eventId.toUuid(), sessions.size, emptyList())
        }
    }

    override suspend fun updateDlStatus(eventId: UUID, status: DlStatus, sessionIds: Set<UUID>?) {
        database.withTransaction {
            val outcomes = journal.outcomesForEvent(eventId.toString())
                .filter { it.kind == "DL" && (sessionIds == null || it.sessionId.toUuid() in sessionIds) }
            if (outcomes.isEmpty()) return@withTransaction
            val batch = BatchBuilder("Duty leave ${status.name.lowercase(Locale.ROOT)}")
            outcomes.forEach { o ->
                batch.outcomes += o.image()
                check(journal.setOutcomeDlStatus(o.sessionId, eventId.toString(), status.name) == 1)
            }
            recordBatch("dl_status", batch)
        }
    }

    // ---------------------------------------------------------------- undo

    override suspend fun undo(batchId: UUID): UndoResult {
        val batch = journal.batch(batchId.toString()) ?: return UndoResult.NOT_FOUND
        if (batch.undoneAt != null) return UndoResult.NOT_FOUND
        val payload = runCatching { undoJson.decodeFromString(UndoPayload.serializer(), batch.payload) }
            .getOrNull() ?: return UndoResult.NOT_FOUND
        return database.withTransaction {
            // Staleness: every row the change touched must still match the revision
            // it held right after the write (-1 = must still be absent).
            payload.expectedRevisions.forEach { (key, expected) ->
                val current = when (key.substringBefore(':')) {
                    "s" -> journal.session(key.drop(2))?.revision
                    "d" -> journal.dayById(key.drop(2))?.revision
                    "o" -> journal.outcome(key.drop(2))?.revision
                    "b" -> setup.balance(key.drop(2))?.revision
                    else -> expected
                }
                if ((current ?: -1L) != expected) return@withTransaction UndoResult.STALE
            }
            payload.createdVersions.forEach { id ->
                if (schedule.version(id) == null) return@withTransaction UndoResult.STALE
            }
            payload.createdSessions.forEach { id ->
                if (journal.outcome(id) != null) return@withTransaction UndoResult.STALE
            }
            payload.createdEvents.forEach { id ->
                journal.event(id) ?: return@withTransaction UndoResult.STALE
                val attached = journal.outcomesForEvent(id)
                if (!attached.all { o -> payload.outcomes.any { it.sessionId == o.sessionId } }) {
                    return@withTransaction UndoResult.STALE
                }
            }
            // Reverse in dependency order: remove created outcomes, restore deleted days,
            // delete/restore sessions, then restore outcomes, containers, and balances.
            payload.outcomes.filter { !it.exists }.forEach { journal.deleteOutcome(it.sessionId) }
            payload.days.forEach { img ->
                check(journal.restoreDay(img.id, img.kind, img.holiday, img.copiedWeekday, img.revision) == 1)
            }
            payload.deletedDays.forEach { img ->
                journal.insertDay(DayInstanceEntity(img.id, img.semesterId!!, img.day!!,
                    img.kind, img.holiday, img.copiedWeekday, img.revision))
            }
            payload.createdSessions.forEach { journal.deleteSession(it) }
            payload.deletedSessions.forEach { img -> journal.insertSession(img.toEntity()) }
            payload.sessions.forEach { img ->
                check(journal.restoreSession(img.id, img.courseId, img.origin, img.counting,
                    img.startMinute, img.endMinute, img.cancelled, img.pinned, img.suppressed,
                    img.revision) == 1)
            }
            payload.outcomes.filter { it.exists }.forEach { img ->
                if (journal.restoreOutcome(img.sessionId, img.kind!!, img.dlStatus, img.dlEventId,
                        img.revision) == 0) {
                    journal.insertOutcome(AttendanceOutcomeEntity(img.sessionId, img.kind, img.dlStatus,
                        img.dlEventId, img.revision))
                }
            }
            payload.createdDays.forEach { id ->
                if (journal.sessionsForDay(id).isEmpty()) journal.deleteDay(id)
                else check(journal.setHoliday(id, false) == 1)
            }
            payload.createdVersions.forEach { schedule.deleteVersion(it) }
            payload.createdEvents.forEach { journal.deleteEvent(it) }
            payload.balances.forEach { img ->
                check(setup.restoreBalance(img.courseId, img.loggingStartDay, img.conducted, img.present,
                    img.approvedDl, img.pendingDl, img.revision) == 1)
            }
            journal.markUndone(batch.id, now())
            UndoResult.DONE
        }
    }

    // ---------------------------------------------------------------- backup

    override suspend fun exportBackup(): ByteArray = BackupCodec.export(database, clock)
    override suspend fun validateBackup(bytes: ByteArray): BackupPreview = BackupCodec.validate(bytes)
    override suspend fun restoreBackup(bytes: ByteArray) = BackupCodec.restore(database, bytes, clock)
}
