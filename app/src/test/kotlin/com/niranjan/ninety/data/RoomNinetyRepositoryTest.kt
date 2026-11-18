package com.niranjan.ninety.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.niranjan.ninety.domain.AttendanceOutcome
import com.niranjan.ninety.domain.CourseType
import com.niranjan.ninety.domain.DayKind
import com.niranjan.ninety.domain.OpeningBalance
import com.niranjan.ninety.domain.WeekTemplate
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Wednesday 2025-01-08 12:00 UTC = 17:30 IST; the semester began Monday 2025-01-06.
 * Unrecorded periods on the current day are neither conducted nor missing — only
 * strictly past unrecorded periods count as missing data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomNinetyRepositoryTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2025-01-08T12:00:00Z"), zone)
    private val today = LocalDate.of(2025, 1, 8)
    private val monday = LocalDate.of(2025, 1, 6)

    private lateinit var db: NinetyDatabase
    private lateinit var repository: RoomNinetyRepository

    private val mathId = UUID.randomUUID()
    private val physId = UUID.randomUUID()
    private val labId = UUID.randomUUID()
    private var semesterId: UUID = UUID(0, 0)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), NinetyDatabase::class.java)
            .addCallback(NinetyDatabase.integrityCallback)
            .allowMainThreadQueries()
            .build()
        repository = RoomNinetyRepository(db, clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedSemester(
        template: WeekTemplate = defaultTemplate(),
        start: LocalDate = monday,
    ) {
        semesterId = repository.createSemester(SemesterSetup("Spring", start, zone.id,
            listOf(
                CourseSetup(mathId, CourseInput("Mathematics", CourseType.THEORY),
                    OpeningBalance(mathId, monday)),
                CourseSetup(physId, CourseInput("Physics", CourseType.THEORY),
                    OpeningBalance(physId, monday)),
                CourseSetup(labId, CourseInput("Physics Lab", CourseType.LAB),
                    OpeningBalance(labId, monday)),
            ), template))
        repository.ensureMaterialized(semesterId)
    }

    private fun defaultTemplate() = WeekTemplate(mapOf(
        DayOfWeek.MONDAY to mapOf(1 to mathId, 2 to physId),
        DayOfWeek.WEDNESDAY to mapOf(1 to mathId, 2 to physId, 3 to labId, 4 to labId, 5 to labId),
    ))

    private suspend fun stats(courseId: UUID) =
        repository.observeCourseStats(semesterId, today).first()
            .single { it.course.id == courseId.toString() }

    private suspend fun dayView(date: LocalDate) =
        repository.observeDay(semesterId, date).first()

    private suspend fun sessionsOf(date: LocalDate): List<SessionView> {
        val view = dayView(date)
        return view.groups.flatMap { it.cells.mapNotNull { c -> c.session } } + view.extras
    }

    private suspend fun undoLatest(): UndoResult {
        val action = repository.observeUndoable().first() ?: error("nothing to undo")
        return repository.undo(action.batchId)
    }

    // ---------------------------------------------------------------- basics

    @Test
    fun `materialization generates sessions for scheduled weekdays only`() = runTest {
        seedSemester()
        assertEquals(5, sessionsOf(today).size) // MA, PH, 3× PHL
        assertEquals(2, sessionsOf(monday).size)
        assertTrue(sessionsOf(LocalDate.of(2025, 1, 7)).isEmpty()) // Tuesday has no classes
    }

    @Test
    fun `friday template uses 45 minute periods with midday break`() = runTest {
        seedSemester(WeekTemplate(mapOf(
            DayOfWeek.FRIDAY to mapOf(1 to mathId, 4 to physId, 5 to labId))),
            start = LocalDate.of(2025, 1, 1))
        val friday = LocalDate.of(2025, 1, 3) // already past: materialized by ensureMaterialized
        val sessions = sessionsOf(friday).sortedBy { it.slot }
        assertEquals(listOf(540 to 585, 675 to 720, 840 to 900),
            sessions.map { it.startMinute to it.endMinute })
    }

    @Test
    fun `future days project the template without materializing`() = runTest {
        seedSemester()
        val future = dayView(LocalDate.of(2025, 1, 15)) // next Wednesday
        assertTrue(future.future)
        assertFalse(future.writable)
        val projected = future.groups.flatMap { it.cells.mapNotNull { c -> c.projectedCourseId } }
        assertEquals(5, projected.size)
        assertTrue(sessionsOf(LocalDate.of(2025, 1, 15)).all { it.outcome == AttendanceOutcome.UNRECORDED })
    }

    // ---------------------------------------------------------------- recording

    @Test
    fun `recording outcomes drives the exact formula`() = runTest {
        seedSemester()
        val wednesday = sessionsOf(today)
        repository.recordPeriod(wednesday.single { it.courseId == mathId }.id, AttendanceOutcome.PRESENT)
        repository.recordPeriod(wednesday.single { it.courseId == physId }.id, AttendanceOutcome.ABSENT)
        stats(mathId).let {
            assertEquals(1, it.summary.conducted)      // Wed P1 recorded; Mon P1 unrecorded
            assertEquals(1, it.summary.credited)
            assertEquals(1, it.summary.missingPastPeriods)
            assertEquals(true, it.summary.meetsMinimum)
        }
        stats(physId).let {
            assertEquals(1, it.summary.conducted)
            assertEquals(0, it.summary.credited)
            assertEquals(false, it.summary.meetsMinimum)
        }
        stats(labId).let {
            assertEquals(0, it.summary.conducted) // today's sessions unrecorded: no data yet
            assertEquals(0, it.summary.missingPastPeriods)
        }
    }

    @Test
    fun `mark all present records only eligible unrecorded periods`() = runTest {
        seedSemester()
        val math = sessionsOf(today).single { it.courseId == mathId }
        repository.recordPeriod(math.id, AttendanceOutcome.ABSENT)
        val marked = repository.markAllPresent(semesterId, today)
        assertEquals(4, marked)
        val after = sessionsOf(today)
        assertEquals(1, after.count { it.outcome == AttendanceOutcome.ABSENT })
        assertEquals(4, after.count { it.outcome == AttendanceOutcome.PRESENT })
        stats(labId).let {
            assertEquals(3, it.summary.conducted)
            assertEquals(3, it.summary.credited)
        }
    }

    @Test
    fun `mark all present rejects holidays and empty days`() = runTest {
        seedSemester()
        repository.setHoliday(semesterId, today, true)
        assertTrue(runCatching { repository.markAllPresent(semesterId, today) }.isFailure)
        assertTrue(runCatching {
            repository.markAllPresent(semesterId, LocalDate.of(2025, 1, 7)) }.isFailure)
    }

    @Test
    fun `recording a future day is rejected`() = runTest {
        seedSemester()
        assertTrue(runCatching {
            repository.markAllPresent(semesterId, today.plusDays(7)) }.isFailure)
    }

    // ---------------------------------------------------------------- exceptions

    @Test
    fun `holiday excludes periods and reverses cleanly`() = runTest {
        seedSemester()
        repository.markAllPresent(semesterId, monday)
        repository.markAllPresent(semesterId, today)
        repository.setHoliday(semesterId, today, true)
        stats(mathId).let {
            assertEquals(1, it.summary.conducted) // only Monday remains
            assertEquals(1, it.summary.credited)
        }
        repository.setHoliday(semesterId, today, false)
        stats(mathId).let {
            assertEquals(2, it.summary.conducted)
            assertEquals(2, it.summary.credited) // outcomes were preserved, not destroyed
        }
    }

    @Test
    fun `cancelled period leaves the conducted total`() = runTest {
        seedSemester()
        val lab = sessionsOf(today).filter { it.courseId == labId }
        repository.recordPeriod(lab[0].id, AttendanceOutcome.ABSENT)
        repository.recordUnrecorded(lab.map { it.id }, AttendanceOutcome.PRESENT)
        assertEquals(3, stats(labId).summary.conducted)
        assertEquals(2, stats(labId).summary.credited)
        repository.setCancelled(lab[0].id, true)
        stats(labId).let {
            assertEquals(2, it.summary.conducted)
            assertEquals(2, it.summary.credited) // the cancelled outcome is preserved but excluded
        }
        repository.setCancelled(lab[0].id, false)
        assertEquals(3, stats(labId).summary.conducted)
        assertEquals(2, stats(labId).summary.credited)
    }

    @Test
    fun `extras count only when marked counting`() = runTest {
        seedSemester()
        val extra = repository.addExtra(semesterId, today, mathId, 1020, 1080, counting = false)
        assertEquals(0, stats(mathId).summary.conducted)
        repository.setExtraCounting(extra, true)
        repository.recordPeriod(extra, AttendanceOutcome.PRESENT)
        stats(mathId).let {
            assertEquals(1, it.summary.conducted)
            assertEquals(1, it.summary.credited)
        }
        repository.deleteExtra(extra)
        assertEquals(0, stats(mathId).summary.conducted)
    }

    @Test
    fun `weekday copy materializes and is idempotent`() = runTest {
        seedSemester()
        val tuesday = LocalDate.of(2025, 1, 7)
        val first = repository.copyWeekday(semesterId, tuesday, DayOfWeek.WEDNESDAY)
        assertEquals(5, first.created)
        assertEquals(DayKind.COPIED, first.kind)
        assertEquals(5, sessionsOf(tuesday).size)
        val second = repository.copyWeekday(semesterId, tuesday, DayOfWeek.WEDNESDAY)
        assertEquals(0, second.created) // identical copy: no-op
        assertEquals(5, sessionsOf(tuesday).size)
    }

    @Test
    fun `copy preserves recorded periods as conflicts`() = runTest {
        seedSemester()
        val tuesday = LocalDate.of(2025, 1, 7)
        repository.copyWeekday(semesterId, tuesday, DayOfWeek.WEDNESDAY)
        val recorded = sessionsOf(tuesday).first { it.courseId == labId }
        repository.recordPeriod(recorded.id, AttendanceOutcome.PRESENT)
        val recopy = repository.copyWeekday(semesterId, tuesday, DayOfWeek.MONDAY)
        assertTrue(recopy.conflicts.isNotEmpty())
        assertEquals(DayKind.CUSTOM, recopy.kind)
        assertEquals(AttendanceOutcome.PRESENT,
            sessionsOf(tuesday).single { it.id == recorded.id }.outcome)
    }

    @Test
    fun `substitution pins the session against reconciliation`() = runTest {
        seedSemester()
        val target = sessionsOf(today).single { it.courseId == physId }
        repository.substituteCourse(target.id, labId)
        val newTemplate = defaultTemplate().with(DayOfWeek.WEDNESDAY, 2, mathId)
        val conflicts = repository.saveTemplate(semesterId, newTemplate, today)
        assertEquals(labId, sessionsOf(today).single { it.id == target.id }.courseId)
        assertTrue(conflicts.any { it.sessionId == target.id })
    }

    // ---------------------------------------------------------------- duty leave

    @Test
    fun `pending duty leave credits provisionally then resolves`() = runTest {
        seedSemester()
        val phys = sessionsOf(today).single { it.courseId == physId }
        val applied = repository.applyDutyLeave(semesterId, listOf(phys.id), "Conference")
        assertTrue(applied.conflicts.isEmpty())
        stats(physId).let {
            assertEquals(1, it.summary.pendingDl)
            assertTrue(it.summary.isProvisional)
            assertEquals(1, it.summary.credited) // provisional credit
        }
        repository.updateDlStatus(applied.eventId, DlStatus.APPROVED)
        stats(physId).let {
            assertEquals(0, it.summary.pendingDl)
            assertEquals(1, it.summary.approvedDl)
            assertFalse(it.summary.isProvisional)
        }
    }

    @Test
    fun `rejected duty leave removes the credit`() = runTest {
        seedSemester()
        val phys = sessionsOf(today).single { it.courseId == physId }
        val applied = repository.applyDutyLeave(semesterId, listOf(phys.id), null)
        repository.updateDlStatus(applied.eventId, DlStatus.REJECTED)
        stats(physId).let {
            assertEquals(0, it.summary.credited)
            assertEquals(1, it.summary.datedRejectedDl)
        }
    }

    @Test
    fun `applying duty leave over a recorded period needs replace`() = runTest {
        seedSemester()
        val phys = sessionsOf(today).single { it.courseId == physId }
        repository.recordPeriod(phys.id, AttendanceOutcome.ABSENT)
        val first = repository.applyDutyLeave(semesterId, listOf(phys.id), null)
        assertEquals(listOf(phys.id), first.conflicts)
        val second = repository.applyDutyLeave(semesterId, listOf(phys.id), null, replace = true)
        assertEquals(1, second.applied)
        assertEquals(AttendanceOutcome.DL_PENDING, sessionsOf(today).single { it.id == phys.id }.outcome)
    }

    // ---------------------------------------------------------------- undo

    @Test
    fun `undo restores recorded outcomes`() = runTest {
        seedSemester()
        val phys = sessionsOf(today).single { it.courseId == physId }
        repository.recordPeriod(phys.id, AttendanceOutcome.PRESENT)
        assertEquals(UndoResult.DONE, undoLatest())
        assertEquals(AttendanceOutcome.UNRECORDED, sessionsOf(today).single { it.id == phys.id }.outcome)
    }

    @Test
    fun `undo removes mark-all outcomes`() = runTest {
        seedSemester()
        repository.markAllPresent(semesterId, today)
        assertEquals(UndoResult.DONE, undoLatest())
        assertTrue(sessionsOf(today).all { it.outcome == AttendanceOutcome.UNRECORDED })
    }

    @Test
    fun `undo reports stale when the row changed again`() = runTest {
        seedSemester()
        val phys = sessionsOf(today).single { it.courseId == physId }
        repository.recordPeriod(phys.id, AttendanceOutcome.PRESENT)
        val first = repository.observeUndoable().first()!!
        repository.recordPeriod(phys.id, AttendanceOutcome.ABSENT) // bumps outcome revision
        assertEquals(UndoResult.STALE, repository.undo(first.batchId))
        // the newer batch still works
        assertEquals(UndoResult.DONE, undoLatest())
    }

    @Test
    fun `undo restores deleted extras`() = runTest {
        seedSemester()
        val extra = repository.addExtra(semesterId, today, physId, 1020, 1080, counting = true)
        repository.deleteExtra(extra)
        assertEquals(5, sessionsOf(today).size)
        assertEquals(UndoResult.DONE, undoLatest())
        assertEquals(6, sessionsOf(today).size)
    }

    @Test
    fun `undo reverts a holiday without touching outcomes`() = runTest {
        seedSemester()
        repository.markAllPresent(semesterId, today)
        repository.setHoliday(semesterId, today, true)
        assertEquals(UndoResult.DONE, undoLatest())
        stats(mathId).let { assertEquals(1, it.summary.credited) } // Wed present again; Mon still unrecorded
    }

    // ---------------------------------------------------------------- schedule revisions

    @Test
    fun `template revision updates unrecorded days and preserves recorded`() = runTest {
        seedSemester()
        val mondayPhys = sessionsOf(monday).single { it.courseId == physId }
        repository.recordPeriod(mondayPhys.id, AttendanceOutcome.PRESENT)
        val revised = defaultTemplate()
            .with(DayOfWeek.WEDNESDAY, 1, physId)   // swap Wed P1 course
        val conflicts = repository.saveTemplate(semesterId, revised, today)
        // Monday is in the past: a revision effective today must not touch it
        assertEquals(physId, sessionsOf(monday).single { it.id == mondayPhys.id }.courseId)
        val wednesday = sessionsOf(today)
        assertEquals(physId, wednesday.single { it.slot == 1 }.courseId)
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `removing a slot suppresses recorded sessions and deletes clean ones`() = runTest {
        seedSemester()
        val phys = sessionsOf(today).single { it.courseId == physId }
        repository.recordPeriod(phys.id, AttendanceOutcome.PRESENT)
        val revised = defaultTemplate().with(DayOfWeek.WEDNESDAY, 2, null)
        repository.saveTemplate(semesterId, revised, today)
        val after = sessionsOf(today)
        assertNull(after.firstOrNull { it.id == phys.id }) // suppressed: kept in storage, not shown
        assertEquals(0, stats(physId).summary.conducted)   // and it stops counting
    }

    // ---------------------------------------------------------------- opening balances & completeness

    @Test
    fun `opening balance covers history without double counting dated rows`() = runTest {
        seedSemester()
        repository.correctOpeningBalance(OpeningBalance(mathId, monday.plusDays(1), conducted = 10, present = 9))
        stats(mathId).let {
            // Monday's session precedes the logging start: excluded, not counted twice
            assertEquals(10, it.summary.conducted)
            assertEquals(9, it.summary.credited)
        }
    }

    @Test
    fun `incomplete dates list unrecorded past periods`() = runTest {
        seedSemester()
        val dates = repository.observeIncompleteDates(semesterId, today.plusDays(1)).first()
        assertEquals(setOf(monday, today), dates.toSet())
        repository.markAllPresent(semesterId, monday)
        val after = repository.observeIncompleteDates(semesterId, today.plusDays(1)).first()
        assertEquals(listOf(today), after)
    }

    // ---------------------------------------------------------------- backup

    @Test
    fun `backup round-trips every table atomically`() = runTest {
        seedSemester()
        repository.markAllPresent(semesterId, today)
        val phys = sessionsOf(today).single { it.courseId == physId }
        repository.applyDutyLeave(semesterId, listOf(phys.id), "Festival", replace = true)
        val before = repository.observeCourseStats(semesterId, today).first()
            .associate { it.course.id to it.summary }
        val bytes = repository.exportBackup()

        val db2 = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), NinetyDatabase::class.java)
            .addCallback(NinetyDatabase.integrityCallback).allowMainThreadQueries().build()
        try {
            val repo2 = RoomNinetyRepository(db2, clock)
            val preview = repo2.validateBackup(bytes)
            assertEquals(1, preview.semesters)
            assertEquals(3, preview.courses)
            repo2.restoreBackup(bytes)
            val after = repo2.observeCourseStats(semesterId, today).first()
                .associate { it.course.id to it.summary }
            before.forEach { (courseId, summary) ->
                val restored = after.getValue(courseId)
                assertEquals("$courseId conducted", summary.conducted, restored.conducted)
                assertEquals("$courseId credited", summary.credited, restored.credited)
                assertEquals("$courseId pending", summary.pendingDl, restored.pendingDl)
            }
            assertEquals(1, repo2.observeDlStats(semesterId).first().pending)
        } finally {
            db2.close()
        }
    }

    @Test
    fun `invalid backups are rejected without touching data`() = runTest {
        seedSemester()
        assertTrue(runCatching { repository.validateBackup("not json".toByteArray()) }.isFailure)
        val tampered = repository.exportBackup().decodeToString()
            .replace("\"conducted\":0", "\"conducted\":-1")
        assertTrue(runCatching { repository.validateBackup(tampered.toByteArray()) }.isFailure)
        assertTrue(repository.observeCourseStats(semesterId, today).first().isNotEmpty())
    }

    @Test
    fun `legacy course labels are ignored without losing the schedule or recorded attendance`() = runTest {
        seedSemester()
        val period = sessionsOf(today).first { it.courseId == labId }
        repository.recordPeriod(period.id, AttendanceOutcome.PRESENT)

        val fresh = Json.parseToJsonElement(repository.exportBackup().decodeToString()).jsonObject
        val courses = fresh.getValue("courses").jsonArray
        assertTrue(courses.all { "code" !in it.jsonObject && "normalizedCode" !in it.jsonObject })
        val legacyCourses = courses.mapIndexed { index, course ->
            JsonObject(course.jsonObject + mapOf(
                "code" to JsonPrimitive("Lab-$index"), "normalizedCode" to JsonPrimitive("LAB-$index"),
            ))
        }
        val legacy = JsonObject(fresh + mapOf("format" to JsonPrimitive(1), "courses" to JsonArray(legacyCourses)))
            .toString().encodeToByteArray()
        assertEquals(1, repository.validateBackup(legacy).formatVersion)
        repository.restoreBackup(legacy)
        repository.updateCourse(labId, CourseInput("Experimental Physics Laboratory", CourseType.LAB))

        val restored = sessionsOf(today).single { it.id == period.id }
        assertEquals(labId, restored.courseId)
        assertEquals("Experimental Physics Laboratory", restored.courseName)
        assertEquals(AttendanceOutcome.PRESENT, restored.outcome)
        assertTrue(repository.currentTemplate(semesterId).first().assignments.values.any { labId in it.values })
    }

    // ---------------------------------------------------------------- semester lifecycle

    @Test
    fun `completing a semester freezes materialization`() = runTest {
        seedSemester()
        repository.setSemesterCompleted(semesterId, true)
        repository.ensureMaterialized(semesterId)
        repository.ensureDay(semesterId, LocalDate.of(2025, 1, 15))
        assertTrue(sessionsOf(LocalDate.of(2025, 1, 15)).isEmpty())
    }

    @Test
    fun `integrity triggers reject impossible data`() = runTest {
        seedSemester()
        val session = sessionsOf(today).first()
        val sql = db.openHelper.writableDatabase
        assertTrue(runCatching {
            sql.execSQL("INSERT INTO attendance_outcome(sessionId, kind, revision) VALUES (?, 'MAYBE', 0)",
                arrayOf(session.id.toString()))
        }.isFailure)
        assertTrue(runCatching {
            sql.execSQL("UPDATE opening_balance SET present = 999 WHERE courseId = ?",
                arrayOf(mathId.toString()))
        }.isFailure)
    }
    @Test fun `mark all includes counted extras and leaves supplemental extras alone`() = runTest {
        seedSemester()
        val counted = repository.addExtra(semesterId, today, mathId, 1020, 1080, true)
        val supplemental = repository.addExtra(semesterId, today, mathId, 1080, 1140, false)
        assertEquals(6, repository.markAllPresent(semesterId, today))
        assertEquals(AttendanceOutcome.PRESENT, sessionsOf(today).single { it.id == counted }.outcome)
        assertEquals(AttendanceOutcome.UNRECORDED, sessionsOf(today).single { it.id == supplemental }.outcome)
    }

    @Test fun `empty timetable revision remains visible and projects no old classes`() = runTest {
        seedSemester()
        repository.saveTemplate(semesterId, WeekTemplate(), today)
        assertTrue(repository.currentTemplate(semesterId).first().isEmpty())
        assertEquals(2, repository.observeTemplates(semesterId).first().size)
        assertTrue(dayView(today.plusWeeks(1)).groups.all { group ->
            group.cells.all { it.projectedCourseId == null }
        })
    }

    @Test fun `undo same-date timetable replacement restores the replaced snapshot`() = runTest {
        seedSemester()
        val first = WeekTemplate(mapOf(DayOfWeek.WEDNESDAY to mapOf(1 to mathId)))
        val second = WeekTemplate(mapOf(DayOfWeek.WEDNESDAY to mapOf(2 to physId)))
        repository.saveTemplate(semesterId, first, today)
        repository.saveTemplate(semesterId, second, today)
        assertEquals(UndoResult.DONE, undoLatest())
        assertEquals(first, repository.currentTemplate(semesterId).first())
        assertEquals(mathId, sessionsOf(today).single().courseId)
    }

    @Test fun `weekday copy replaces clean slots outside the source and undo restores them`() = runTest {
        seedSemester()
        val before = sessionsOf(today).map { it.id to it.courseId }.toSet()
        val copied = repository.copyWeekday(semesterId, today, DayOfWeek.MONDAY)
        assertEquals(DayKind.COPIED, copied.kind)
        assertEquals(2, sessionsOf(today).size)
        assertEquals(UndoResult.DONE, undoLatest())
        assertEquals(before, sessionsOf(today).map { it.id to it.courseId }.toSet())
    }

    @Test fun `weekday copy preserves a recorded slot outside the source as a conflict`() = runTest {
        seedSemester()
        val lab = sessionsOf(today).first { it.courseId == labId }
        repository.recordPeriod(lab.id, AttendanceOutcome.PRESENT)
        val result = repository.copyWeekday(semesterId, today, DayOfWeek.MONDAY)
        assertEquals(DayKind.CUSTOM, result.kind)
        assertTrue(result.conflicts.any { it.sessionId == lab.id })
        assertEquals(AttendanceOutcome.PRESENT, sessionsOf(today).single { it.id == lab.id }.outcome)
    }

    @Test fun `weekend without a copied timetable has no normal slots`() = runTest {
        seedSemester()
        assertTrue(dayView(today.plusDays(3)).groups.isEmpty())
    }

    @Test fun `retired courses leave future templates and retain historical statistics`() = runTest {
        seedSemester()
        val before = sessionsOf(monday).first { it.courseId == mathId }
        repository.recordPeriod(before.id, AttendanceOutcome.PRESENT)
        repository.retireCourse(mathId)
        assertTrue(repository.currentTemplate(semesterId).first().assignments.values.none { mathId in it.values })
        assertEquals(1L, stats(mathId).summary.physicallyPresent)
        assertTrue(repository.observeCourses(semesterId).first().single { it.id == mathId.toString() }.retired)
    }

    @Test fun `opening balance changes emit fresh live statistics`() = kotlinx.coroutines.runBlocking {
        seedSemester()
        val channel = kotlinx.coroutines.channels.Channel<List<CourseStats>>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        val collection = launch {
            repository.observeCourseStats(semesterId, today).collect { channel.send(it) }
        }
        try {
            kotlinx.coroutines.withTimeout(10_000) { channel.receive() }
            repository.correctOpeningBalance(OpeningBalance(mathId, monday.plusDays(1), 40, 38))
            val changed = kotlinx.coroutines.withTimeout(10_000) {
                var value: CourseStats
                do { value = channel.receive().single { it.course.id == mathId.toString() } }
                while (value.summary.conducted != 40L)
                value
            }
            assertEquals(38L, changed.summary.credited)
            assertEquals(2L, changed.summary.safeSkips)
        } finally { collection.cancel(); channel.close() }
    }

    @Test fun `backup preview rejects duplicate identities missing balances overflow and bad dates`() = runTest {
        seedSemester()
        val original = repository.exportBackup()
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val backup = json.decodeFromString(BackupFile.serializer(), original.decodeToString())
        val invalid = listOf(
            backup.copy(semesters = backup.semesters + backup.semesters.first()),
            backup.copy(courses = backup.courses + backup.courses.first()),
            backup.copy(versions = backup.versions + backup.versions.first()),
            backup.copy(sessions = backup.sessions + backup.sessions.first()),
            backup.copy(balances = backup.balances.drop(1)),
            backup.copy(balances = backup.balances + backup.balances.first()),
            backup.copy(semesters = listOf(backup.semesters.first().copy(id = "broken"))),
            backup.copy(semesters = listOf(backup.semesters.first().copy(startDay = Long.MAX_VALUE))),
            backup.copy(balances = listOf(backup.balances.first().copy(present = Long.MAX_VALUE,
                approvedDl = Long.MAX_VALUE)) + backup.balances.drop(1)),
        )
        invalid.forEachIndexed { index, bad ->
            val bytes = json.encodeToString(BackupFile.serializer(), bad).toByteArray()
            assertTrue("Invalid backup $index passed preview", runCatching { repository.validateBackup(bytes) }.isFailure)
            assertTrue("Invalid backup $index restored", runCatching { repository.restoreBackup(bytes) }.isFailure)
            org.junit.Assert.assertArrayEquals(original, repository.exportBackup())
        }
    }

}
