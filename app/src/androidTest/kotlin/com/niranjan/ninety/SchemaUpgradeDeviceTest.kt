package com.niranjan.ninety

import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.niranjan.ninety.data.NinetyDatabase
import com.niranjan.ninety.data.RoomSetupRepository
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SchemaUpgradeDeviceTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NinetyDatabase::class.java)

    @Test fun legacyDevelopmentDataSurvivesEndDateRemovalAndManualCompletion() = runBlocking {
        val name = "upgrade-${UUID.randomUUID()}.db"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val semesterId = UUID.randomUUID().toString()
        val courseId = UUID.randomUUID().toString()
        try {
            helper.createDatabase(name, 1).apply {
                execSQL("INSERT INTO semester VALUES (?, 'Semester 5', 1, 100, 'UTC', 0)", arrayOf(semesterId))
                execSQL("INSERT INTO course VALUES (?, ?, 'Operating Systems', 'CS301', 'CS301', 'THEORY', NULL, 0)",
                    arrayOf(courseId, semesterId))
                execSQL("INSERT INTO opening_balance VALUES (?, 50, 40, 37, 0, 1, 2)", arrayOf(courseId))
                execSQL("INSERT INTO app_settings VALUES (1, ?, 'DARK', 4)", arrayOf(semesterId))
                close()
            }
            helper.runMigrationsAndValidate(name, 2, true, NinetyDatabase.MIGRATION_1_2).close()
            val db = NinetyDatabase.create(context, name)
            try {
                val repo = RoomSetupRepository(db)
                val overview = repo.overview.first().single()
                assertEquals(courseId, overview.course.id.toString())
                assertEquals(38L, overview.summary.credited)
                assertEquals(2L, overview.summary.safeSkips)
                db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
                repo.setSemesterCompleted(UUID.fromString(semesterId), true)
                assertNull(db.setupDao().activeSemesterId())
                assertTrue(repo.overview.first().single().semester.completed)
                repo.setSemesterCompleted(UUID.fromString(semesterId), false)
                assertEquals(38L, repo.overview.first().single().summary.credited)
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
