package com.niranjan.ninety.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.niranjan.ninety.domain.Course
import com.niranjan.ninety.domain.CourseType
import com.niranjan.ninety.domain.OpeningBalance
import com.niranjan.ninety.domain.Semester
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import android.database.sqlite.SQLiteDatabase
import java.io.File
import org.json.JSONObject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RoomSetupRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: NinetyDatabase
    private val clock = Clock.fixed(Instant.parse("2026-11-11T08:00:00Z"), ZoneId.of("Asia/Kolkata"))
    private val databaseName = "test-${UUID.randomUUID()}.db"

    @Before fun open() { db = NinetyDatabase.create(context, databaseName) }
    @After fun close() { db.close(); context.deleteDatabase(databaseName) }

    private fun setup(): InitialSetup {
        val s = Semester(UUID.randomUUID(), "Semester 5", LocalDate.of(2026, 8, 1),
            ZoneId.of("Asia/Kolkata"))
        val c = Course(UUID.randomUUID(), s.id, "Operating Systems", "CS301", CourseType.THEORY)
        return InitialSetup(s, c, OpeningBalance(c.id, LocalDate.of(2026, 11, 11), 40, 38))
    }

    @Test fun balanceSurvivesDatabaseCloseAndReopen() = runBlocking {
        RoomSetupRepository(db, clock).createInitialSetup(setup())
        db.close()
        db = NinetyDatabase.create(context, databaseName)
        val result = RoomSetupRepository(db, clock).overview.first().single()
        assertEquals("CS301", result.course.code)
        assertEquals(40L, result.summary.conducted)
        assertEquals(38L, result.summary.credited)
        assertEquals(2L, result.summary.safeSkips)
        assertEquals(ZoneId.of("Asia/Kolkata"), result.semester.collegeZone)
    }

    @Test fun pendingBalanceIsProvisionalAndConfirmedIsSecondary() = runBlocking {
        val setup = setup().let { it.copy(balance = it.balance.copy(present = 37, pendingDl = 1)) }
        val repository = RoomSetupRepository(db, clock)
        repository.createInitialSetup(setup)
        val result = repository.overview.first().single().summary
        assertEquals(38L, result.credited)
        assertEquals(37L, result.confirmedCredited)
        assertEquals(2L, result.safeSkips)
        assertTrue(result.isProvisional)
    }

    @Test fun repeatedAndConcurrentSetupCannotOverwriteExistingData() = runBlocking {
        val repository = RoomSetupRepository(db, clock)
        val first = async { runCatching { repository.createInitialSetup(setup()) } }
        val second = async { runCatching { repository.createInitialSetup(setup()) } }
        assertEquals(1, listOf(first.await(), second.await()).count { it.isSuccess })
        assertEquals(1, db.setupDao().semesterCount())
        assertEquals(1, repository.overview.first().size)
    }

    @Test fun crossOwnershipAndInvalidCoverageAreRejectedBeforeWrites() = runBlocking {
        val valid = setup()
        val invalid = listOf(
            valid.copy(course = valid.course.copy(semesterId = UUID.randomUUID())),
            valid.copy(balance = valid.balance.copy(loggingStartDate = valid.semester.startDate.minusDays(1))),
            valid.copy(balance = valid.balance.copy(loggingStartDate = valid.semester.startDate)),
            valid.copy(balance = valid.balance.copy(loggingStartDate = LocalDate.of(2026, 12, 1))),
        )
        invalid.forEach { assertTrue(runCatching { RoomSetupRepository(db, clock).createInitialSetup(it) }.isFailure) }
        assertEquals(0, db.setupDao().semesterCount())
    }

    @Test fun sqliteRejectsInvalidCountsEvenOutsideRepository() = runBlocking {
        RoomSetupRepository(db, clock).createInitialSetup(setup())
        val sql = db.openHelper.writableDatabase
        assertThrows(Exception::class.java) {
            sql.execSQL("UPDATE opening_balance SET pendingDl = 5")
        }
        assertThrows(Exception::class.java) {
            sql.execSQL("UPDATE opening_balance SET conducted = -1")
        }
        assertEquals(40L, RoomSetupRepository(db, clock).overview.first().single().summary.conducted)
    }

    @Test fun foreignKeysUniqueCodesAndSingletonAreEnforced() = runBlocking {
        val initial = setup()
        RoomSetupRepository(db, clock).createInitialSetup(initial)
        val sql = db.openHelper.writableDatabase
        assertThrows(Exception::class.java) { sql.execSQL("DELETE FROM semester") }
        assertThrows(Exception::class.java) { sql.execSQL("UPDATE course SET semesterId = 'missing'") }
        assertThrows(Exception::class.java) { sql.execSQL("UPDATE app_settings SET id = 2") }
        assertTrue(runCatching {
            db.setupDao().insertCourse(CourseEntity(UUID.randomUUID().toString(), initial.semester.id.toString(),
                "Duplicate code", "CS301", "CS301", "THEORY"))
        }.isFailure)
    }

    @Test fun transactionFailureRollsBackAllWrites() = runBlocking {
        val result = runCatching {
            db.withTransaction {
                db.setupDao().insertSemester(SemesterEntity(UUID.randomUUID().toString(), "Rollback", 1, "UTC"))
                error("Injected failure")
            }
        }
        assertTrue(result.isFailure)
        assertEquals(0, db.setupDao().semesterCount())
        assertTrue(RoomSetupRepository(db, clock).overview.first().isEmpty())
    }

    @Test fun zeroOpeningForFutureSemesterIsPermitted() = runBlocking {
        val s = setup().semester.copy(startDate = LocalDate.of(2027, 1, 1))
        val c = setup().course.copy(semesterId = s.id)
        val repo = RoomSetupRepository(db, clock)
        repo.createInitialSetup(InitialSetup(s, c, OpeningBalance(c.id, s.startDate)))
        assertNull(repo.overview.first().single().summary.safeSkips)
    }

    @Test fun manualCompletionAndReopenPreserveAttendanceAndActivePointer() = runBlocking {
        val initial = setup()
        val repo = RoomSetupRepository(db, clock)
        repo.createInitialSetup(initial)
        repo.setSemesterCompleted(initial.semester.id, true)
        assertNull(db.setupDao().activeSemesterId())
        assertTrue(repo.overview.first().single().semester.completed)
        assertEquals(38L, repo.overview.first().single().summary.credited)
        db.close()
        db = NinetyDatabase.create(context, databaseName)
        val reopened = RoomSetupRepository(db, clock)
        assertTrue(reopened.overview.first().single().semester.completed)
        reopened.setSemesterCompleted(initial.semester.id, false)
        assertEquals(initial.semester.id.toString(), db.setupDao().activeSemesterId())
        assertFalse(reopened.overview.first().single().semester.completed)
        assertEquals(2L, reopened.overview.first().single().summary.safeSkips)
    }

    @Test fun completionOfUnknownSemesterRollsBack() = runBlocking {
        val initial = setup()
        val repo = RoomSetupRepository(db, clock)
        repo.createInitialSetup(initial)
        assertTrue(runCatching { repo.setSemesterCompleted(UUID.randomUUID(), true) }.isFailure)
        assertEquals(initial.semester.id.toString(), db.setupDao().activeSemesterId())
        assertFalse(repo.overview.first().single().semester.completed)
    }

    @Test fun migrationFromDevelopmentSchemaPreservesIdentitiesCountsAndGuards() = runBlocking {
        db.close()
        context.deleteDatabase(databaseName)
        val old = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null)
        val schema = JSONObject(File("schemas/com.niranjan.ninety.data.NinetyDatabase/1.json").readText()).getJSONObject("database")
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql")
                .replace("\${TABLE_NAME}", entity.getString("tableName")))
        }
        val setupQueries = schema.getJSONArray("setupQueries")
        for (i in 0 until setupQueries.length()) old.execSQL(setupQueries.getString(i))
        val original = setup()
        old.execSQL("INSERT INTO semester VALUES (?, ?, ?, ?, ?, 0)", arrayOf<Any>(original.semester.id.toString(), "Semester 5",
            original.semester.startDate.toEpochDay(), LocalDate.of(2026, 12, 31).toEpochDay(), "Asia/Kolkata"))
        old.execSQL("INSERT INTO course VALUES (?, ?, 'Operating Systems', 'CS301', 'CS301', 'THEORY', NULL, 0)",
            arrayOf(original.course.id.toString(), original.semester.id.toString()))
        old.execSQL("INSERT INTO opening_balance VALUES (?, ?, 40, 37, 0, 1, 7)",
            arrayOf<Any>(original.course.id.toString(), original.balance.loggingStartDate.toEpochDay()))
        old.execSQL("INSERT INTO app_settings VALUES (1, ?, 'DARK', 3)", arrayOf(original.semester.id.toString()))
        old.version = 1
        old.close()
        db = NinetyDatabase.create(context, databaseName)
        val repo = RoomSetupRepository(db, clock)
        val restored = repo.overview.first().single()
        assertEquals(original.course.id, restored.course.id)
        assertEquals(38L, restored.summary.credited)
        assertEquals(2L, restored.summary.safeSkips)
        val sql = db.openHelper.writableDatabase
        sql.query("SELECT theme, revision FROM app_settings").use {
            assertTrue(it.moveToFirst()); assertEquals("DARK", it.getString(0)); assertEquals(3, it.getInt(1))
        }
        sql.query("PRAGMA table_info(semester)").use {
            while (it.moveToNext()) assertNotEquals("endDay", it.getString(1))
        }
        sql.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        assertThrows(Exception::class.java) { sql.execSQL("UPDATE opening_balance SET pendingDl = 100") }
        repo.setSemesterCompleted(original.semester.id, true)
        assertTrue(repo.overview.first().single().semester.completed)
    }
}
