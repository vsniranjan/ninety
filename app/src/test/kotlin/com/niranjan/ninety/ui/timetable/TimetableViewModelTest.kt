package com.niranjan.ninety.ui.timetable

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.niranjan.ninety.data.ConflictInfo
import com.niranjan.ninety.data.CourseInput
import com.niranjan.ninety.data.CourseSetup
import com.niranjan.ninety.data.NinetyDatabase
import com.niranjan.ninety.data.RoomNinetyRepository
import com.niranjan.ninety.data.SemesterSetup
import com.niranjan.ninety.domain.AttendanceOutcome
import com.niranjan.ninety.domain.CourseType
import com.niranjan.ninety.domain.WeekTemplate
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimetableViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2025, 1, 8)
    private val clock = Clock.fixed(Instant.parse("2025-01-08T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: NinetyDatabase
    private lateinit var repository: RoomNinetyRepository

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), NinetyDatabase::class.java).build()
        repository = RoomNinetyRepository(database, clock)
    }

    @After fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test fun previewCanBeCancelledBeforeSavingOrConfirmedToKeepRecordedPeriods() = runTest(dispatcher) {
        val theory = CourseSetup(input = CourseInput("Mathematics", CourseType.THEORY))
        val lab = CourseSetup(input = CourseInput("Physics Lab", CourseType.LAB))
        val template = WeekTemplate(mapOf(today.dayOfWeek to mapOf(1 to theory.courseId)))
        val semesterId = repository.createSemester(SemesterSetup(
            "Semester", today, ZoneOffset.UTC.id, listOf(theory, lab), template))
        repository.ensureMaterialized(semesterId)
        val session = repository.observeDay(semesterId, today).first()
            .groups.flatMap { it.cells }.single { it.slot == 1 }.session!!
        repository.recordPeriod(session.id, AttendanceOutcome.PRESENT)

        val model = TimetableViewModel(repository, semesterId)
        model.startEditing(template, today)
        model.edit(today.dayOfWeek, 1, lab.courseId)
        var savedConflicts: List<ConflictInfo>? = null
        model.save(today) { savedConflicts = it }
        model.saving.first { !it }
        assertEquals(session.id, model.preview.value?.single()?.sessionId)
        assertNull(savedConflicts)
        assertEquals(template, repository.observeTemplates(semesterId).first().single().template)

        model.cancelPreview()
        assertNotNull(model.draft.value)
        assertNull(model.preview.value)

        model.save(today) { savedConflicts = it }
        model.saving.first { !it }
        model.confirmSave { savedConflicts = it }
        model.saving.first { !it }
        assertNull(model.draft.value)
        assertEquals(session.id, savedConflicts?.single()?.sessionId)
        assertEquals(lab.courseId, repository.observeTemplates(semesterId).first().single()
            .template.courseAt(today.dayOfWeek, 1))
        val retained = repository.observeDay(semesterId, today).first()
            .groups.flatMap { it.cells }.single { it.slot == 1 }.session!!
        assertEquals(theory.courseId, retained.courseId)
        assertEquals(AttendanceOutcome.PRESENT, retained.outcome)
    }
}
