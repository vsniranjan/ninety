package com.niranjan.ninety

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.niranjan.ninety.data.InitialSetup
import com.niranjan.ninety.data.NinetyDatabase
import com.niranjan.ninety.data.RoomSetupRepository
import com.niranjan.ninety.domain.Course
import com.niranjan.ninety.domain.CourseType
import com.niranjan.ninety.domain.OpeningBalance
import com.niranjan.ninety.domain.Semester
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessPersistenceDeviceTest {
    /**
     * Default suite verifies close/reopen. Two separate invocations with
     * persistenceStage=write/read allow an actual force-stop between stages.
     * This named fixture never accesses the production database.
     */
    @Test fun attendanceSurvivesReopeningInANewProcess() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val stage = InstrumentationRegistry.getArguments().getString("persistenceStage")
        val name = "phase1-process-persistence-fixture.db"
        val courseId = UUID.fromString("97bb04ce-689d-49c4-924d-e0307d6da9c1")
        if (stage != "read") {
            context.deleteDatabase(name)
            val semester = Semester(UUID.fromString("f55aac53-7f22-4e70-8d7b-9ea8917cbe87"),
                "Persistence fixture", LocalDate.of(2020, 1, 1), ZoneId.of("UTC"))
            val course = Course(courseId, semester.id, "Persistence course", CourseType.THEORY)
            val db = NinetyDatabase.create(context, name)
            try {
                RoomSetupRepository(db).createInitialSetup(InitialSetup(semester, course,
                    OpeningBalance(courseId, LocalDate.of(2020, 2, 1), 40, 38)))
            } finally { db.close() }
        }
        if (stage == "write") return@runBlocking
        assertTrue("The write stage must persist the database", context.getDatabasePath(name).exists())
        val db = NinetyDatabase.create(context, name)
        try {
            val result = RoomSetupRepository(db).overview.first().single()
            assertEquals(courseId, result.course.id)
            assertEquals(40L, result.summary.conducted)
            assertEquals(38L, result.summary.credited)
            assertEquals(2L, result.summary.safeSkips)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
