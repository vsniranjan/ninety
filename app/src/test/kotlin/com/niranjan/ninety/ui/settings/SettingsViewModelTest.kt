package com.niranjan.ninety.ui.settings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.niranjan.ninety.AppContainer
import com.niranjan.ninety.data.AppSettings
import com.niranjan.ninety.data.BackupExportSettings
import com.niranjan.ninety.data.CourseInput
import com.niranjan.ninety.data.CourseSetup
import com.niranjan.ninety.data.NinetyDatabase
import com.niranjan.ninety.data.SemesterSetup
import com.niranjan.ninety.domain.CourseType
import com.niranjan.ninety.domain.WeekTemplate
import java.io.IOException
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsViewModelTest {
    @Test
    fun `only a successful write records a device local backup timestamp`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clock = Clock.fixed(Instant.parse("2025-01-08T12:00:00Z"), ZoneOffset.UTC)
        val db = Room.inMemoryDatabaseBuilder(context, NinetyDatabase::class.java)
            .addCallback(NinetyDatabase.integrityCallback)
            .allowMainThreadQueries()
            .build()
        try {
            val container = AppContainer(context, db, clock)
            val repository = container.repository
            val model = SettingsViewModel(container)
            val date = LocalDate.now(clock)
            val course = CourseSetup(input = CourseInput("Math", CourseType.THEORY))
            val semesterId = repository.createSemester(SemesterSetup("Spring", date, "UTC", listOf(course),
                WeekTemplate(mapOf(DayOfWeek.WEDNESDAY to mapOf(1 to course.courseId)))))
            repository.markAllPresent(semesterId, date)
            val before = repository.observeCourseStats(semesterId, date).first()
            val journalBefore = repository.observeRecentChanges().first()
            assertEquals(AppSettings(), repository.observeAppState().first().settings)
            assertNull(model.lastBackup.value)

            model.exportBackup(onWritten = { error("Failed export reported success") }) {
                throw IOException("Provider write failed")
            }
            model.busy.first { !it }
            assertNull(model.lastBackup.value)
            assertEquals("Something went wrong. Your data is safe.", model.messages.value)

            var saved: ByteArray? = null
            model.exportBackup(onWritten = {}) { bytes ->
                assertNull(model.lastBackup.value)
                saved = bytes
            }
            model.busy.first { !it }
            assertEquals(clock.instant(), model.lastBackup.value)
            val backup = checkNotNull(saved)
            assertFalse(backup.decodeToString().contains("last_export"))
            repository.restoreBackup(backup)

            val reopened = BackupExportSettings(context, Clock.offset(clock, Duration.ofDays(30)))
            assertEquals(clock.instant(), reopened.lastExport.value)
            assertFalse(model.isBackupOverdue(model.lastBackup.value))
            assertTrue(reopened.isExportOverdue(reopened.lastExport.value))
            assertEquals(before, repository.observeCourseStats(semesterId, date).first())
            assertEquals(journalBefore, repository.observeRecentChanges().first())
        } finally {
            db.close()
            Dispatchers.resetMain()
        }
    }
}
