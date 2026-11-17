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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
        val model = SetupViewModel(repository)
        model.setSemesterName("Semester 5")
        model.setStartDate(LocalDate.of(2025, 1, 6))
        model.setZone(zone.id)
        return model
    }

    private fun draft(name: String = "Mathematics") =
        CourseDraft(name = name, type = CourseType.THEORY)

    @Test fun semesterStepRequiresFields() = runTest(dispatcher) {
        val model = SetupViewModel(repository)
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
        model.removeCourse(course.id)
        assertTrue(model.state.value.timetable.isEmpty())
    }

    @Test fun savePersistsTheWholeSemester() = runTest(dispatcher) {
        val model = validModel()
        val course = draft()
        model.addCourse(course.copy(conducted = "20", present = "19", loggingDate = "2025-01-07"))
        model.assign(JDayOfWeek.MONDAY, 1, course.id)
        while (model.state.value.step < 3) model.next()
        model.save()
        advanceUntilIdle()
        val state = repository.observeAppState().first()
        assertEquals("Semester 5", state.activeSemester?.entity?.name)
        val stats = repository.observeCourseStats(
            java.util.UUID.fromString(state.activeSemester!!.entity.id),
            LocalDate.of(2025, 1, 8)).first()
        assertEquals(20L, stats.single().summary.conducted)
        assertEquals(19L, stats.single().summary.credited)
    }
}
