package com.niranjan.ninety.ui.setup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.niranjan.ninety.data.NinetyDatabase
import com.niranjan.ninety.data.RoomNinetyRepository
import com.niranjan.ninety.domain.CourseType
import java.time.Clock
import java.time.DayOfWeek as JDayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2025-01-08T12:00:00Z"), zone)
    private lateinit var db: NinetyDatabase
    private lateinit var repository: RoomNinetyRepository

    @Before fun before() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), NinetyDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = RoomNinetyRepository(db, clock)
    }

    @After fun after() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun validModel(): SetupViewModel {
        val model = SetupViewModel(repository, db, clock)
        model.setSemesterName("Semester 5")
        model.setStartDate(LocalDate.of(2025, 1, 6))
        model.setZone(zone.id)
        return model
    }

    private fun draft(name: String = "Mathematics") =
        CourseDraft(name = name, type = CourseType.THEORY)

    @Test fun semesterStepRequiresFields() = runTest(dispatcher) {
        val model = SetupViewModel(repository, db, clock)
        model.next()
        assertNotNull(model.state.value.error)
        model.setSemesterName("Sem")
        model.next() // still missing start date
        assertNotNull(model.state.value.error)
        model.setStartDate(LocalDate.of(2025, 1, 6))
        model.next()
        assertNull(model.state.value.error)
        assertEquals(1, model.state.value.step)
    }

    @Test fun coursesStepRequiresAtLeastOneCourse() = runTest(dispatcher) {
        val model = validModel()
        model.next()
        model.next() // empty course list
        assertNotNull(model.state.value.error)
        assertEquals(1, model.state.value.step)
        model.addCourse(draft())
        model.next()
        assertEquals(2, model.state.value.step)
    }

    @Test fun duplicateNamesAreRejected() = runTest(dispatcher) {
        val model = validModel()
        model.addCourse(draft())
        model.addCourse(draft(name = " mathematics "))
        model.next()
        model.next()
        assertNotNull(model.state.value.error)
    }

    @Test fun impossibleOpeningCountsAreRejected() = runTest(dispatcher) {
        val model = validModel()
        model.addCourse(draft().copy(conducted = "10", present = "12", loggingDate = "2025-01-07"))
        model.next()
        model.next()
        assertNotNull(model.state.value.error)
    }

    @Test fun removingACourseDropsItsTimetableSlots() = runTest(dispatcher) {
        val model = validModel()
        val course = draft()
        model.addCourse(course)
        model.assign(JDayOfWeek.MONDAY, 1, course.id)
        val removed = model.removeCourse(course.id)!!
        assertTrue(model.state.value.timetable.isEmpty())
        model.restoreCourse(removed)
        assertEquals(course, model.state.value.courses.single())
        assertEquals(course.id, model.state.value.timetable[JDayOfWeek.MONDAY to 1])
    }

    @Test fun midsemesterChoicePrefillsCollegeTodayAndLetsUsersMarkPastDays() = runTest(dispatcher) {
        val model = validModel()
        val today = LocalDate.of(2025, 1, 8)
        model.setStartDate(today.minusDays(3))
        assertFalse(model.state.value.asksAboutPastAttendance(today))
        model.setStartDate(today.minusDays(4))
        val course = draft()
        assertTrue(model.state.value.asksAboutPastAttendance(today))
        assertEquals(today.toString(), model.state.value.courseForSetup(course, today).loggingDate)

        model.setEnterAttendanceSoFar(false)
        val previouslyEntered = course.copy(loggingDate = today.toString(), conducted = "3", present = "2")
        val manual = model.state.value.courseForSetup(previouslyEntered, today)
        assertEquals("", manual.loggingDate)
        assertFalse(manual.hasBalance)
        model.setEnterAttendanceSoFar(true)
        assertEquals(previouslyEntered, model.state.value.courseForSetup(previouslyEntered, today))
    }

    @Test fun attendanceSoFarStartsDatedTrackingTodayEvenWithZeroCounts() = runTest(dispatcher) {
        val model = validModel()
        val today = LocalDate.of(2025, 1, 8)
        model.setStartDate(today.minusWeeks(1))
        val course = draft()
        model.addCourse(course)
        model.assign(JDayOfWeek.MONDAY, 1, course.id)
        model.save()
        model.state.first { !it.saving }

        val semesterId = repository.observeAppState().first().activeSemesterId!!
        repository.ensureMaterialized(semesterId)
        assertEquals(today, repository.observeCourseStats(semesterId, today).first().single().balance.loggingStartDate)
        assertTrue(repository.observeIncompleteDates(semesterId, today).first().isEmpty())
    }

    @Test fun emptyTimetableRequiresAnExplicitSkip() = runTest(dispatcher) {
        val model = validModel()
        model.addCourse(draft())
        model.next()
        model.next()
        model.next()
        assertEquals(2, model.state.value.step)
        assertTrue(model.state.value.confirmEmptyTimetable)

        model.dismissEmptyTimetable()
        assertEquals(2, model.state.value.step)
        assertFalse(model.state.value.confirmEmptyTimetable)

        model.next()
        model.skipEmptyTimetable()
        assertEquals(3, model.state.value.step)
        assertFalse(model.state.value.confirmEmptyTimetable)
    }

    @Test fun creatingReplacementFinishesThePreviousSemesterWhenSelected() = runTest(dispatcher) {
        val first = validModel()
        first.addCourse(draft())
        first.save()
        first.state.first { !it.saving }
        val previousId = repository.observeAppState().first().activeSemesterId!!

        val replacement = validModel()
        replacement.setSemesterName("Semester 6")
        replacement.addCourse(draft())
        replacement.save(finishPrevious = previousId)
        replacement.state.first { !it.saving }

        val state = repository.observeAppState().first()
        assertEquals("Semester 6", state.activeSemester?.entity?.name)
        assertTrue(state.semesters.single { it.entity.id == previousId.toString() }.entity.archived)
    }

    @Test fun creatingReplacementCanKeepThePreviousSemesterPaused() = runTest(dispatcher) {
        val first = validModel()
        first.addCourse(draft())
        first.save()
        first.state.first { !it.saving }
        val previousId = repository.observeAppState().first().activeSemesterId!!

        val replacement = validModel()
        replacement.setSemesterName("Semester 6")
        replacement.addCourse(draft())
        replacement.save()
        replacement.state.first { !it.saving }

        val state = repository.observeAppState().first()
        assertEquals("Semester 6", state.activeSemester?.entity?.name)
        assertFalse(state.semesters.single { it.entity.id == previousId.toString() }.entity.archived)
        repository.setActiveSemester(previousId)
        assertEquals(previousId, repository.observeAppState().first().activeSemesterId)
    }

    @Test fun failedReplacementLeavesThePreviousSemesterCurrent() = runTest(dispatcher) {
        val first = validModel()
        first.addCourse(draft())
        first.save()
        first.state.first { !it.saving }
        val previousId = repository.observeAppState().first().activeSemesterId!!

        val replacement = validModel()
        replacement.setSemesterName("") // The repository rejects this after finishing the old semester.
        replacement.addCourse(draft())
        replacement.save(finishPrevious = previousId)
        replacement.state.first { !it.saving }

        assertNotNull(replacement.state.value.error)
        val state = repository.observeAppState().first()
        assertEquals(previousId, state.activeSemesterId)
        assertFalse(state.semesters.single().entity.archived)
    }

    @Test fun optionalReminderKeepsExistingDisplayAndReminderTimePreferences() = runTest(dispatcher) {
        val first = validModel()
        first.addCourse(draft())
        first.save()
        first.state.first { !it.saving }
        repository.updatePreferences("DARK", false, false, 1200)
        val settings = repository.observeAppState().first().settings

        val reconciled = CompletableDeferred<Unit>()
        val model = SetupViewModel(repository, db, clock,
            reconcileReminders = { reconciled.complete(Unit) })
        model.setSemesterName("Semester 6")
        model.setStartDate(LocalDate.of(2025, 1, 8))
        model.setZone(zone.id)
        model.addCourse(draft())
        model.setEnableReminder(true)
        model.save(settings = settings)

        model.state.first { !it.saving }
        assertTrue(model.state.value.saved)
        reconciled.await()
        assertEquals(settings.copy(reminderEnabled = true), repository.observeAppState().first().settings)
    }

    @Test fun savePersistsTheWholeSemester() = runTest(dispatcher) {
        val model = validModel()
        val course = draft()
        model.addCourse(course.copy(conducted = "20", present = "19", loggingDate = "2025-01-07"))
        model.assign(JDayOfWeek.MONDAY, 1, course.id)
        while (model.state.value.step < 3) model.next()
        model.save()
        model.state.first { !it.saving }
        val state = repository.observeAppState().first()
        assertEquals("Semester 5", state.activeSemester?.entity?.name)
        val stats = repository.observeCourseStats(
            java.util.UUID.fromString(state.activeSemester!!.entity.id),
            LocalDate.of(2025, 1, 8)).first()
        assertEquals(20L, stats.single().summary.conducted)
        assertEquals(19L, stats.single().summary.credited)
    }
}
