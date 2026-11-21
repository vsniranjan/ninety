package com.niranjan.ninety.data

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.niranjan.ninety.domain.AttendanceOutcome
import com.niranjan.ninety.domain.CourseType
import com.niranjan.ninety.domain.DayKind
import com.niranjan.ninety.domain.OpeningBalance
import com.niranjan.ninety.domain.WeekTemplate
import com.niranjan.ninety.platform.ReminderNotifier
import com.niranjan.ninety.ui.userMessage
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
import org.robolectric.Shadows.shadowOf
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
    fun `reminder action marks only today and replaces its notification with confirmation`() = runTest {
        seedSemester()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val manager = app.getSystemService(NotificationManager::class.java)
        val periods = sessionsOf(today)
        val math = periods.first { it.courseId == mathId }
        val physics = periods.first { it.courseId == physId }
        val lab = periods.filter { it.courseId == labId }
        repository.recordPeriod(math.id, AttendanceOutcome.ABSENT)
        repository.recordPeriod(physics.id, AttendanceOutcome.DUTY_LEAVE)
        repository.setCancelled(lab.first().id, true)
        val mathBefore = stats(mathId).summary
        val physicsBefore = stats(physId).summary
        val labBefore = stats(labId).summary

        assertEquals(0, repository.markPresentFromReminder(semesterId, today))
        assertTrue(manager.activeNotifications.isEmpty())
        repository.updatePreferences("SYSTEM", true, true, 19 * 60)
        shadowOf(app).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        ReminderNotifier.runCheck(app, repository, clock)
        assertTrue(manager.activeNotifications.isEmpty())
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        ReminderNotifier.runCheck(app, repository, clock)
        val reminder = manager.activeNotifications.single().notification
        assertEquals("All present", reminder.actions.single().title.toString())
        assertEquals(labBefore, stats(labId).summary)

        assertEquals(0, repository.markPresentFromReminder(semesterId, monday))
        assertEquals(0, repository.markPresentFromReminder(UUID.randomUUID(), today))
        assertTrue(sessionsOf(monday).none { it.recorded })

        ReminderNotifier.markAllPresent(app, repository, clock, semesterId, today)
        val confirmation = manager.activeNotifications.single().notification
        assertEquals("Marked 2 periods present.", confirmation.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertTrue(confirmation.actions.isNullOrEmpty())
        assertEquals(mathBefore, stats(mathId).summary)
        assertEquals(physicsBefore, stats(physId).summary)
        assertEquals(2L, stats(labId).summary.physicallyPresent)
        assertEquals(2L, stats(labId).summary.conducted)
        assertEquals(0, repository.markPresentFromReminder(semesterId, today))
        assertEquals(UndoResult.DONE, undoLatest())
        assertEquals(labBefore, stats(labId).summary)
        repository.setHoliday(semesterId, today, true)
        assertEquals(0, repository.markPresentFromReminder(semesterId, today))
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
    fun `future holiday preserves recorded history and stays out of attendance obligations`() = runTest {
        seedSemester()
        repository.markAllPresent(semesterId, monday)
        val before = stats(mathId).summary
        val future = today.plusDays(7)

        repository.setHoliday(semesterId, future, true)
        repository.ensureDay(semesterId, future)
        repository.ensureMaterialized(semesterId)
        assertTrue(dayView(future).holiday)
        assertEquals(0, dayView(future).total)
        assertTrue(sessionsOf(future).isEmpty())
        assertEquals(before, stats(mathId).summary)
        assertTrue(runCatching { repository.copyWeekday(semesterId, future, DayOfWeek.MONDAY) }.isFailure)
        assertTrue(runCatching { repository.addExtra(semesterId, future, mathId, 900, 960, true) }.isFailure)

        repository.restoreBackup(repository.exportBackup())
        val later = RoomNinetyRepository(db, Clock.offset(clock, java.time.Duration.ofDays(8)))
        later.ensureMaterialized(semesterId)
        val holiday = later.observeDay(semesterId, future).first()
        assertTrue(holiday.holiday)
        assertEquals(0, holiday.total)
        assertFalse(future in later.observeIncompleteDates(semesterId, future.plusDays(1)).first())
        assertEquals(before, stats(mathId).summary)

        later.setHoliday(semesterId, future, false)
        assertEquals(5, later.observeDay(semesterId, future).first().total)
        assertTrue(future in later.observeIncompleteDates(semesterId, future.plusDays(1)).first())
        assertEquals(before, stats(mathId).summary)
    }

    @Test
    fun `removing a future holiday restores the preview and its undo restores the holiday`() = runTest {
        seedSemester()
        val future = today.plusDays(7)
        val original = dayView(future)
        repository.setHoliday(semesterId, future, true)
        repository.setHoliday(semesterId, future, false)
        assertEquals(original, dayView(future))
        assertEquals(UndoResult.DONE, undoLatest())
        assertTrue(dayView(future).holiday)
    }

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
    fun `single duty leave can be corrected or cleared with undo`() = runTest {
        seedSemester()
        val physics = sessionsOf(today).single { it.courseId == physId }
        repository.recordPeriod(physics.id, AttendanceOutcome.DUTY_LEAVE)
        assertEquals(1L, stats(physId).summary.dutyLeave)
        assertEquals(1L, stats(physId).summary.credited)

        repository.recordPeriod(physics.id, AttendanceOutcome.ABSENT)
        assertEquals(1L, stats(physId).summary.conducted)
        assertEquals(0L, stats(physId).summary.credited)
        assertEquals(UndoResult.DONE, undoLatest())
        assertEquals(1L, stats(physId).summary.dutyLeave)

        repository.recordPeriod(physics.id, null)
        assertEquals(0L, stats(physId).summary.conducted)
        assertEquals(UndoResult.DONE, undoLatest())
        assertEquals(AttendanceOutcome.DUTY_LEAVE, sessionsOf(today).single { it.id == physics.id }.outcome)
    }

    @Test
    fun `bulk duty leave confirms all replacements and undoes the lab as one change`() = runTest {
        seedSemester()
        val lab = sessionsOf(today).filter { it.courseId == labId }
        val (absent, present, unrecorded) = lab
        repository.recordPeriod(absent.id, AttendanceOutcome.ABSENT)
        repository.recordPeriod(present.id, AttendanceOutcome.PRESENT)
        val ids = lab.map { it.id } + unrecorded.id
        val original = listOf(AttendanceOutcome.ABSENT, AttendanceOutcome.PRESENT, AttendanceOutcome.UNRECORDED)
        suspend fun labOutcomes() = sessionsOf(today).filter { it.courseId == labId }.map { it.outcome }

        assertEquals(setOf(absent.id, present.id), repository.markDutyLeave(semesterId, ids).toSet())
        assertEquals(original, labOutcomes())
        assertTrue(repository.markDutyLeave(semesterId, ids, replace = true).isEmpty())
        assertEquals(3L, stats(labId).summary.dutyLeave)
        assertEquals(3L, stats(labId).summary.credited)
        assertEquals(3L, stats(labId).summary.conducted)

        val undoable = repository.observeUndoable().first()
        assertTrue(repository.markDutyLeave(semesterId, ids).isEmpty())
        assertEquals(undoable, repository.observeUndoable().first())
        repository.markAllPresent(semesterId, today)
        assertEquals(List(3) { AttendanceOutcome.DUTY_LEAVE }, labOutcomes())
        assertEquals(UndoResult.DONE, undoLatest()) // Mark remaining present.
        assertEquals(UndoResult.DONE, undoLatest()) // All three duty leave marks.
        assertEquals(original, labOutcomes())
    }

    @Test
    fun `ineligible periods cannot receive duty leave and a bulk failure leaves other marks alone`() = runTest {
        seedSemester()
        val physics = sessionsOf(today).single { it.courseId == physId }
        val extra = repository.addExtra(semesterId, today, physId, 970, 1010, counting = false)
        val futureDate = today.plusDays(1)
        val laterRepository = RoomNinetyRepository(db, Clock.offset(clock, java.time.Duration.ofDays(1)))
        val future = laterRepository.addExtra(semesterId, futureDate, physId, 970, 1010, counting = true)
        suspend fun reject(block: suspend () -> Unit) {
            assertTrue(runCatching { block() }.exceptionOrNull() is UserFacingException)
        }

        reject { repository.recordPeriod(extra, AttendanceOutcome.DUTY_LEAVE) }
        reject { repository.markDutyLeave(semesterId, listOf(physics.id, extra)) }
        reject { repository.recordPeriod(future, AttendanceOutcome.DUTY_LEAVE) }
        assertEquals(0L, stats(physId).summary.conducted)

        repository.setCancelled(physics.id, true)
        reject { repository.markDutyLeave(semesterId, listOf(physics.id)) }
        repository.setCancelled(physics.id, false)
        repository.setHoliday(semesterId, today, true)
        reject { repository.recordPeriod(physics.id, AttendanceOutcome.DUTY_LEAVE) }
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
    fun `recent changes exposes independent undo and hides superseded revisions`() = runTest {
        seedSemester()
        val math = sessionsOf(today).first { it.courseId == mathId }
        val phys = sessionsOf(today).first { it.courseId == physId }
        val previousMath = stats(mathId).summary
        repository.recordPeriod(phys.id, AttendanceOutcome.PRESENT)
        val independent = repository.observeUndoable().first()!!
        repository.recordPeriod(math.id, AttendanceOutcome.PRESENT)
        val original = repository.observeUndoable().first()!!
        repository.recordPeriod(math.id, null)
        val cleared = repository.observeUndoable().first()!!
        repository.recordPeriod(math.id, AttendanceOutcome.ABSENT)
        val newest = repository.observeUndoable().first()!!
        val before = stats(mathId).summary

        val changes = repository.observeRecentChanges().first()
        assertEquals(listOf(newest, independent), changes)
        assertEquals(clock.millis(), changes.first().createdAt)
        assertEquals("Attendance updated", changes.first().label)
        repository.restoreBackup(repository.exportBackup())
        assertEquals(changes, repository.observeRecentChanges().first())
        assertEquals(UndoResult.STALE, repository.undo(original.batchId))
        assertEquals(before, stats(mathId).summary)
        assertEquals(UndoResult.DONE, repository.undo(independent.batchId))
        assertEquals(before, stats(mathId).summary)
        assertEquals(UndoResult.DONE, repository.undo(newest.batchId))
        assertEquals(listOf(cleared), repository.observeRecentChanges().first())
        assertEquals(UndoResult.DONE, repository.undo(cleared.batchId))
        assertEquals(listOf(original), repository.observeRecentChanges().first())
        assertEquals(UndoResult.DONE, repository.undo(original.batchId))
        assertEquals(previousMath, stats(mathId).summary)
        assertTrue(repository.observeRecentChanges().first().isEmpty())
    }

    @Test
    fun `recent changes can undo mark all on a newly materialized date`() = runTest {
        seedSemester()
        val later = RoomNinetyRepository(db, Clock.offset(clock, java.time.Duration.ofDays(7)))
        val future = today.plusDays(7)
        val before = later.observeCourseStats(semesterId, future).first()
        assertEquals(5, later.markAllPresent(semesterId, future))
        val change = later.observeRecentChanges().first().single()
        assertEquals(UndoResult.DONE, later.undo(change.batchId))
        assertEquals(before, later.observeCourseStats(semesterId, future).first())
        assertTrue(later.observeRecentChanges().first().isEmpty())
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
        repository.correctOpeningBalance(OpeningBalance(mathId, monday.plusDays(1), 10, 8, dutyLeave = 1))
        repository.markAllPresent(semesterId, today)
        val phys = sessionsOf(today).single { it.courseId == physId }
        repository.markDutyLeave(semesterId, listOf(phys.id), replace = true)
        val before = repository.observeCourseStats(semesterId, today).first()
            .associate { it.course.id to it.summary }
        val bytes = repository.exportBackup()

        val db2 = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), NinetyDatabase::class.java)
            .addCallback(NinetyDatabase.integrityCallback).allowMainThreadQueries().build()
        try {
            val repo2 = RoomNinetyRepository(db2, clock)
            val preview = repo2.validateBackup(bytes)
            assertEquals(3, preview.formatVersion)
            assertEquals(1, preview.semesters)
            assertEquals(3, preview.courses)
            repo2.restoreBackup(bytes)
            val after = repo2.observeCourseStats(semesterId, today).first()
                .associate { it.course.id to it.summary }
            before.forEach { (courseId, summary) ->
                val restored = after.getValue(courseId)
                assertEquals("$courseId conducted", summary.conducted, restored.conducted)
                assertEquals("$courseId credited", summary.credited, restored.credited)
                assertEquals("$courseId duty leave", summary.dutyLeave, restored.dutyLeave)
            }
            assertEquals(AttendanceOutcome.DUTY_LEAVE,
                repo2.observeDay(semesterId, today).first().groups.flatMap { it.cells }
                    .single { it.session?.id == phys.id }.session?.outcome)
        } finally {
            db2.close()
        }
    }

    @Test
    fun `only user written rule messages reach feedback and failed mutations keep attendance`() = runTest {
        seedSemester()
        repository.markAllPresent(semesterId, monday)
        val before = stats(mathId).summary
        val journalBefore = repository.observeRecentChanges().first()
        val rule = runCatching { repository.markAllPresent(semesterId, today.plusDays(1)) }.exceptionOrNull()
        assertTrue(rule is UserFacingException)
        assertEquals("Future classes cannot be recorded yet.", rule!!.userMessage())
        val internal = runCatching { repository.recordPeriod(UUID.randomUUID(), AttendanceOutcome.PRESENT) }
            .exceptionOrNull()
        assertEquals("Unknown class period.", internal!!.message)
        assertEquals("Something went wrong. Your data is safe.", internal.userMessage())
        assertEquals("Something went wrong. Your data is safe.",
            runCatching { check(false) }.exceptionOrNull()!!.userMessage())
        val backup = runCatching { repository.validateBackup("invalid".toByteArray()) }.exceptionOrNull()
        assertTrue(backup is BackupException)
        assertEquals("This file is not a valid Ninety backup.", backup!!.userMessage())
        assertEquals(before, stats(mathId).summary)
        assertEquals(journalBefore, repository.observeRecentChanges().first())
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
    fun `old backup formats are rejected before restoring any data`() = runTest {
        seedSemester()
        val period = sessionsOf(today).first { it.courseId == labId }
        repository.recordPeriod(period.id, AttendanceOutcome.PRESENT)
        val fresh = Json.parseToJsonElement(repository.exportBackup().decodeToString()).jsonObject

        for (version in 1..2) {
            val legacy = JsonObject(fresh + ("format" to JsonPrimitive(version))).toString().encodeToByteArray()
            for (operation in listOf<suspend () -> Unit>(
                { repository.validateBackup(legacy) }, { repository.restoreBackup(legacy) })) {
                val failure = runCatching { operation() }.exceptionOrNull()
                assertTrue(failure is BackupException)
                assertTrue(failure?.message.orEmpty().contains("unsupported version ($version)"))
            }
        }
        assertEquals(AttendanceOutcome.PRESENT, sessionsOf(today).single { it.id == period.id }.outcome)
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

    @Test fun `resuming a course enables timetable assignment and preserves its balance and marks`() = runTest {
        seedSemester()
        repository.correctOpeningBalance(OpeningBalance(mathId, today, conducted = 10, present = 9))
        repository.recordPeriod(sessionsOf(today).first { it.courseId == mathId }.id, AttendanceOutcome.PRESENT)
        val before = stats(mathId)
        repository.retireCourse(mathId)
        val cleanTemplate = repository.currentTemplate(semesterId).first()
        val effective = today.plusDays(1)
        assertTrue(runCatching { repository.saveTemplate(semesterId, defaultTemplate(), effective) }.isFailure)

        repository.resumeCourse(mathId)
        val resume = repository.observeUndoable().first()!!
        assertEquals(cleanTemplate, repository.currentTemplate(semesterId).first())
        assertFalse(stats(mathId).course.retired)
        assertEquals(before.balance, stats(mathId).balance)
        assertEquals(before.summary, stats(mathId).summary)
        assertTrue(runCatching {
            repository.correctOpeningBalance(OpeningBalance(mathId, monday))
        }.isFailure)

        assertEquals(UndoResult.DONE, repository.undo(resume.batchId))
        assertTrue(stats(mathId).course.retired)
        assertEquals(before.summary, stats(mathId).summary)
        repository.resumeCourse(mathId)
        val latestResume = repository.observeUndoable().first()!!
        repository.saveTemplate(semesterId, defaultTemplate(), effective)
        assertEquals(UndoResult.STALE, repository.undo(latestResume.batchId))
        assertEquals(defaultTemplate(), repository.currentTemplate(semesterId).first())
        assertEquals(before.summary, stats(mathId).summary)
        repository.restoreBackup(repository.exportBackup())
        assertFalse(stats(mathId).course.retired)
        assertEquals(before.summary, stats(mathId).summary)
    }

    @Test fun `semester start respects opening balances for stopped courses`() = runTest {
        seedSemester(template = WeekTemplate())
        repository.correctOpeningBalance(OpeningBalance(physId, today))
        repository.correctOpeningBalance(OpeningBalance(labId, today))
        val cutoff = monday.plusDays(1)
        repository.correctOpeningBalance(OpeningBalance(mathId, cutoff, conducted = 10, present = 9))
        repository.retireCourse(mathId)
        val before = stats(mathId).summary

        assertTrue(runCatching { repository.updateSemester(semesterId, "Spring", today, zone.id) }.isFailure)
        repository.resumeCourse(mathId)
        assertEquals(cutoff, stats(mathId).balance.loggingStartDate)
        assertEquals(before, stats(mathId).summary)
    }

    @Test fun `stopping and resuming again supersedes the old resume undo`() = runTest {
        seedSemester()
        repository.retireCourse(mathId)
        repository.resumeCourse(mathId)
        val first = repository.observeUndoable().first()!!
        repository.retireCourse(mathId)
        repository.resumeCourse(mathId)
        assertEquals(UndoResult.NOT_FOUND, repository.undo(first.batchId))
        assertFalse(stats(mathId).course.retired)
    }

    @Test fun `opening balance changes emit fresh live statistics`() = kotlinx.coroutines.runBlocking {
        seedSemester()
        val balance = OpeningBalance(mathId, monday.plusDays(1), 40, 37)
        repository.correctOpeningBalance(balance)
        val channel = kotlinx.coroutines.channels.Channel<List<CourseStats>>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        val collection = launch {
            repository.observeCourseStats(semesterId, today).collect { channel.send(it) }
        }
        try {
            kotlinx.coroutines.withTimeout(10_000) { channel.receive() }
            repository.correctOpeningBalance(balance.copy(dutyLeave = 1))
            val changed = kotlinx.coroutines.withTimeout(10_000) {
                var value: CourseStats
                do { value = channel.receive().single { it.course.id == mathId.toString() } }
                while (value.summary.dutyLeave != 1L)
                value
            }
            assertEquals(40L, changed.summary.conducted)
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
                dutyLeave = Long.MAX_VALUE)) + backup.balances.drop(1)),
        )
        invalid.forEachIndexed { index, bad ->
            val bytes = json.encodeToString(BackupFile.serializer(), bad).toByteArray()
            assertTrue("Invalid backup $index passed preview", runCatching { repository.validateBackup(bytes) }.isFailure)
            assertTrue("Invalid backup $index restored", runCatching { repository.restoreBackup(bytes) }.isFailure)
            org.junit.Assert.assertArrayEquals(original, repository.exportBackup())
        }
    }

}
