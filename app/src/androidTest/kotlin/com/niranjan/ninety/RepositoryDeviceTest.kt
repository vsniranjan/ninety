package com.niranjan.ninety

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.niranjan.ninety.data.CourseInput
import com.niranjan.ninety.data.CourseSetup
import com.niranjan.ninety.data.DlStatus
import com.niranjan.ninety.data.NinetyDatabase
import com.niranjan.ninety.data.RoomNinetyRepository
import com.niranjan.ninety.data.SemesterSetup
import com.niranjan.ninety.domain.CourseType
import com.niranjan.ninety.domain.OpeningBalance
import com.niranjan.ninety.domain.WeekTemplate
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Behavioral verification of the repository against real Android SQLite on a
 * physical device. Fixtures use in-memory databases only — the installed app's
 * storage is never opened or modified (spec §10.1.1).
 *
 * The semester starts on the current week's Monday so all scheduled sessions are
 * materialized and recordable regardless of which weekday the suite runs on.
 */
@RunWith(AndroidJUnit4::class)
class RepositoryDeviceTest {
    private val zone = ZoneId.systemDefault()
    private val monday: LocalDate = LocalDate.now(zone)
        .minusDays((LocalDate.now(zone).dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

    private lateinit var db: NinetyDatabase
    private lateinit var repository: RoomNinetyRepository

    private val theoryId = UUID.randomUUID()
    private val labId = UUID.randomUUID()
    private var semesterId: UUID = UUID(0, 0)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), NinetyDatabase::class.java)
            .addCallback(NinetyDatabase.integrityCallback)
            .build()
        repository = RoomNinetyRepository(db, Clock.systemUTC())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed() {
        semesterId = repository.createSemester(SemesterSetup("Device fixture", monday, zone.id,
            listOf(
                CourseSetup(theoryId, CourseInput("Operating Systems", CourseType.THEORY),
                    OpeningBalance(theoryId, monday)),
                CourseSetup(labId, CourseInput("Networks Lab", CourseType.LAB),
                    OpeningBalance(labId, monday)),
            ),
            WeekTemplate(mapOf(DayOfWeek.MONDAY to
                mapOf(1 to theoryId, 3 to labId, 4 to labId, 5 to labId)))))
        repository.ensureMaterialized(semesterId)
    }

    private suspend fun day(date: LocalDate = monday) =
        repository.observeDay(semesterId, date).first()

    private suspend fun stats(courseId: UUID, asOf: LocalDate = LocalDate.now(zone)) =
        repository.observeCourseStats(semesterId, asOf).first()
            .single { it.course.id == courseId.toString() }.summary

    @Test fun materializedDayGroupsConsecutiveLabSlots() = runBlocking {
        seed()
        val view = day()
        assertEquals(4, view.total)
        val labGroup = view.groups.single { it.grouped }
        assertEquals(3, labGroup.cells.size)
        assertEquals(labId, labGroup.courseId)
        assertEquals(listOf(3, 4, 5), labGroup.cells.map { it.slot })
        assertEquals(theoryId, view.groups
            .first { g -> g.cells.any { it.session?.courseId == theoryId } }.courseId)
    }

    @Test fun markAllPresentRecordsEveryCountingSession() = runBlocking {
        seed()
        assertEquals(4, repository.markAllPresent(semesterId, monday))
        val lab = stats(labId)
        assertEquals(3L, lab.conducted)
        assertEquals(3L, lab.credited)
        val theory = stats(theoryId)
        assertEquals(1L, theory.conducted)
        assertEquals(1L, theory.physicallyPresent)
        assertEquals(4, day().groups.flatMap { it.cells }.count { it.session?.recorded == true })
    }

    @Test fun cancelledSessionsAreSkippedByMarkAll() = runBlocking {
        seed()
        val theory = day().groups.flatMap { it.cells }.mapNotNull { it.session }
            .single { it.courseId == theoryId }
        repository.setCancelled(theory.id, true)
        assertEquals(3, repository.markAllPresent(semesterId, monday))
        assertEquals(0L, stats(theoryId).conducted)
        assertFalse(day().groups.flatMap { it.cells }.single { it.session?.id == theory.id }
            .session!!.loggable)
    }

    @Test fun pendingDutyLeaveCreditsProvisionallyThenApprovalConfirms() = runBlocking {
        seed()
        val theory = day().groups.flatMap { it.cells }.mapNotNull { it.session }
            .single { it.courseId == theoryId }
        val result = repository.applyDutyLeave(semesterId, listOf(theory.id), "Seminar", replace = false)
        assertEquals(1, result.applied)
        assertTrue(result.conflicts.isEmpty())

        var summary = stats(theoryId)
        assertEquals(1L, summary.conducted)
        assertEquals(1L, summary.credited)
        assertEquals(0L, summary.confirmedCredited)
        assertTrue(summary.isProvisional)

        repository.updateDlStatus(result.eventId, DlStatus.APPROVED)
        summary = stats(theoryId)
        assertEquals(1L, summary.credited)
        assertEquals(1L, summary.confirmedCredited)
        assertFalse(summary.isProvisional)

        repository.updateDlStatus(result.eventId, DlStatus.REJECTED)
        summary = stats(theoryId)
        assertEquals(1L, summary.conducted)
        assertEquals(0L, summary.credited)
    }

    @Test fun holidayExcludesSessionsAndUndoRestoresThem() = runBlocking {
        seed()
        repository.markAllPresent(semesterId, monday)
        repository.setHoliday(semesterId, monday, true)
        assertEquals(0L, stats(labId).conducted)
        assertTrue(day().holiday)

        val batch = repository.observeUndoable().first { it != null }!!
        assertEquals(com.niranjan.ninety.data.UndoResult.DONE, repository.undo(batch.batchId))
        assertFalse(day().holiday)
        assertEquals(3L, stats(labId).conducted)
    }

    @Test fun backupRoundTripPreservesAllState() = runBlocking {
        seed()
        repository.markAllPresent(semesterId, monday)
        val theory = day().groups.flatMap { it.cells }.mapNotNull { it.session }
            .single { it.courseId == theoryId }
        repository.applyDutyLeave(semesterId, listOf(theory.id), "Seminar", replace = true)

        val bytes = repository.exportBackup()
        val preview = repository.validateBackup(bytes)
        assertEquals(1, preview.semesters)
        assertEquals(2, preview.courses)
        assertEquals(4, preview.sessions)
        assertEquals(4, preview.outcomes)
        assertEquals(1, preview.dlEvents)

        repository.restoreBackup(bytes)
        assertEquals(4, day().groups.flatMap { it.cells }.count { it.session?.recorded == true })
        assertEquals(1L, stats(theoryId).pendingDl)
        assertEquals(3L, stats(labId).physicallyPresent)
    }

    @Test fun corruptBackupIsRejectedWithoutTouchingData() = runBlocking {
        seed()
        repository.markAllPresent(semesterId, monday)
        try {
            repository.restoreBackup("{\"formatVersion\":99}".toByteArray())
            fail("Expected BackupException")
        } catch (_: com.niranjan.ninety.data.BackupException) { }
        assertEquals(4, day().groups.flatMap { it.cells }.count { it.session?.recorded == true })
    }

    @Test fun sqliteRejectsOrphanedAttendanceOutcome() = runBlocking {
        seed()
        var raised = false
        try {
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO attendance_outcome VALUES (?, 'PRESENT', NULL, NULL, 0)",
                arrayOf(UUID.randomUUID().toString()))
        } catch (expected: android.database.sqlite.SQLiteConstraintException) {
            raised = true
        }
        assertTrue("Foreign key must reject an outcome for a missing session", raised)

        var triggerBlocked = false
        try {
            val anySession = day().groups.flatMap { it.cells }.first { it.session != null }.session!!
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO attendance_outcome VALUES (?, 'DL', NULL, NULL, 0)",
                arrayOf(anySession.id.toString()))
        } catch (expected: android.database.sqlite.SQLiteConstraintException) {
            triggerBlocked = true
        }
        assertTrue("Integrity trigger must reject a DL outcome without event/status", triggerBlocked)
    }
}
