package com.niranjan.ninety.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.niranjan.ninety.domain.MAX_PERIOD_COUNT
import kotlinx.coroutines.flow.Flow

data class StoredOverview(
    val semesterId: String,
    val semesterName: String,
    val startDay: Long,
    val collegeZone: String,
    val archived: Boolean,
    val courseId: String,
    val courseName: String,
    val courseType: String,
    val loggingStartDay: Long,
    val conducted: Long,
    val present: Long,
    val dutyLeave: Long,
    val balanceRevision: Long,
)

/** Dated session joined with its (optional) outcome and owning day for display and stats. */
data class SessionRow(
    val id: String,
    val dayId: String,
    val semesterId: String,
    val courseId: String,
    val slot: Int?,
    val origin: String,
    val counting: Boolean,
    val startMinute: Int,
    val endMinute: Int,
    val cancelled: Boolean,
    val pinned: Boolean,
    val suppressed: Boolean,
    val revision: Long,
    val day: Long,
    val dayKind: String,
    val holiday: Boolean,
    val outcomeKind: String?,
    val outcomeRevision: Long?,
)

data class DaySummaryRow(
    val id: String,
    val day: Long,
    val kind: String,
    val holiday: Boolean,
    val copiedWeekday: Int?,
    val total: Int,
    val recorded: Int,
    val sessionCount: Int,
)

@Dao
interface SetupDao {
    @Insert suspend fun insertSemester(value: SemesterEntity)
    @Insert suspend fun insertCourse(value: CourseEntity)
    @Insert suspend fun insertBalance(value: OpeningBalanceEntity)
    @Insert suspend fun insertSettings(value: AppSettingsEntity)
    @Query("SELECT COUNT(*) FROM semester") suspend fun semesterCount(): Int
    @Query("SELECT * FROM semester WHERE id = :id") suspend fun semester(id: String): SemesterEntity?
    @Query("SELECT * FROM semester ORDER BY startDay DESC, name") fun observeSemesters(): Flow<List<SemesterEntity>>
    @Query("SELECT * FROM semester ORDER BY startDay DESC, name") suspend fun semesters(): List<SemesterEntity>
    @Query("UPDATE semester SET archived = :completed, completedDay = :completedDay WHERE id = :semesterId AND archived != :completed")
    suspend fun markSemesterCompleted(semesterId: String, completed: Boolean, completedDay: Long?): Int
    @Query("UPDATE semester SET materializedDay = :day WHERE id = :semesterId")
    suspend fun setMaterializedDay(semesterId: String, day: Long?): Int
    @Query("UPDATE semester SET name = :name, startDay = :startDay, collegeZone = :zone WHERE id = :id")
    suspend fun updateSemester(id: String, name: String, startDay: Long, zone: String): Int
    @Query("SELECT activeSemesterId FROM app_settings WHERE id = 1") suspend fun activeSemesterId(): String?
    @Query("SELECT * FROM app_settings WHERE id = 1") fun observeSettings(): Flow<AppSettingsEntity?>
    @Query("SELECT * FROM app_settings WHERE id = 1") suspend fun settings(): AppSettingsEntity?
    @Query("UPDATE app_settings SET activeSemesterId = NULL, revision = revision + 1 WHERE id = 1 AND activeSemesterId = :semesterId")
    suspend fun clearActiveSemester(semesterId: String): Int
    @Query("UPDATE app_settings SET activeSemesterId = :semesterId, revision = revision + 1 WHERE id = 1 AND activeSemesterId IS NULL")
    suspend fun activateSemester(semesterId: String): Int
    @Query("UPDATE app_settings SET activeSemesterId = :semesterId, revision = revision + 1 WHERE id = 1")
    suspend fun switchActiveSemester(semesterId: String): Int
    @Query("UPDATE app_settings SET theme = :theme, haptics = :haptics, reminderEnabled = :reminderEnabled, reminderMinute = :reminderMinute, revision = revision + 1 WHERE id = 1")
    suspend fun updatePreferences(theme: String, haptics: Boolean, reminderEnabled: Boolean, reminderMinute: Int): Int
    @Query("SELECT * FROM semester WHERE id = :id") fun observeSemester(id: String): Flow<SemesterEntity?>
    @Query("SELECT * FROM course WHERE id = :id") suspend fun course(id: String): CourseEntity?
    @Query("SELECT * FROM course WHERE semesterId = :semesterId ORDER BY name COLLATE NOCASE, id") fun observeCourses(semesterId: String): Flow<List<CourseEntity>>
    @Query("SELECT * FROM course WHERE semesterId = :semesterId ORDER BY name COLLATE NOCASE, id") suspend fun courses(semesterId: String): List<CourseEntity>
    @Query("UPDATE course SET name = :name, type = :type, colorArgb = :colorArgb WHERE id = :id")
    suspend fun updateCourse(id: String, name: String, type: String, colorArgb: Long?): Int
    @Query("UPDATE course SET retired = :retired WHERE id = :id") suspend fun setCourseRetired(id: String, retired: Boolean): Int
    @Query("SELECT * FROM opening_balance WHERE courseId = :courseId") suspend fun balance(courseId: String): OpeningBalanceEntity?
    @Query("SELECT * FROM opening_balance") suspend fun balances(): List<OpeningBalanceEntity>
    @Query("SELECT b.* FROM opening_balance b JOIN course c ON c.id = b.courseId WHERE c.semesterId = :semesterId")
    fun observeBalances(semesterId: String): Flow<List<OpeningBalanceEntity>>
    @Query("UPDATE opening_balance SET loggingStartDay = :loggingStartDay, conducted = :conducted, present = :present, dutyLeave = :dutyLeave, revision = revision + 1 WHERE courseId = :courseId")
    suspend fun updateBalance(courseId: String, loggingStartDay: Long, conducted: Long, present: Long, dutyLeave: Long): Int
    @Query("UPDATE opening_balance SET loggingStartDay = :loggingStartDay, conducted = :conducted, present = :present, dutyLeave = :dutyLeave, revision = :revision WHERE courseId = :courseId")
    suspend fun restoreBalance(courseId: String, loggingStartDay: Long, conducted: Long, present: Long, dutyLeave: Long, revision: Long): Int
    @Transaction
    @Query("""
        SELECT s.id AS semesterId, s.name AS semesterName, s.startDay, s.collegeZone, s.archived,
               c.id AS courseId, c.name AS courseName, c.type AS courseType,
               b.loggingStartDay, b.conducted, b.present, b.dutyLeave,
               b.revision AS balanceRevision
        FROM app_settings a
        JOIN semester s ON s.id = COALESCE(a.activeSemesterId, (SELECT id FROM semester ORDER BY rowid DESC LIMIT 1))
        JOIN course c ON c.semesterId = s.id
        JOIN opening_balance b ON b.courseId = c.id
        WHERE a.id = 1 AND c.retired = 0
        ORDER BY c.name COLLATE NOCASE, c.id
    """)
    fun observeOverview(): Flow<List<StoredOverview>>
}

@Dao
interface ScheduleDao {
    @Query("DELETE FROM template_slot WHERE versionId = :versionId AND weekday = :weekday AND slot = :slot")
    suspend fun deleteSlot(versionId: String, weekday: Int, slot: Int): Int
    @Insert suspend fun insertVersion(value: ScheduleVersionEntity)
    @Insert suspend fun insertSlots(values: List<TemplateSlotEntity>)
    @Query("DELETE FROM template_slot WHERE versionId = :versionId") suspend fun deleteSlots(versionId: String): Int
    @Query("DELETE FROM schedule_version WHERE id = :versionId") suspend fun deleteVersion(versionId: String): Int
    @Query("SELECT * FROM schedule_version WHERE semesterId = :semesterId AND effectiveDay = :effectiveDay")
    suspend fun versionAt(semesterId: String, effectiveDay: Long): ScheduleVersionEntity?
    @Query("SELECT * FROM schedule_version WHERE semesterId = :semesterId ORDER BY effectiveDay")
    suspend fun versions(semesterId: String): List<ScheduleVersionEntity>
    @Query("SELECT * FROM schedule_version WHERE semesterId = :semesterId ORDER BY effectiveDay")
    fun observeVersions(semesterId: String): Flow<List<ScheduleVersionEntity>>
    @Query("SELECT * FROM template_slot WHERE versionId = :versionId") suspend fun slots(versionId: String): List<TemplateSlotEntity>
    @Query("SELECT s.id AS versionId, s.effectiveDay AS effectiveDay, t.weekday AS weekday, t.slot AS slot, t.courseId AS courseId FROM schedule_version s LEFT JOIN template_slot t ON t.versionId = s.id WHERE s.semesterId = :semesterId ORDER BY s.effectiveDay")
    fun observeTemplates(semesterId: String): Flow<List<TemplateRow>>
    @Query("SELECT DISTINCT versionId FROM template_slot WHERE courseId = :courseId")
    suspend fun versionsForCourse(courseId: String): List<String>
    @Query("SELECT * FROM schedule_version WHERE id = :id") suspend fun version(id: String): ScheduleVersionEntity?
}

data class TemplateRow(val effectiveDay: Long, val weekday: Int?, val slot: Int?, val courseId: String?, val versionId: String)

@Dao
interface JournalDao {
    // Days
    @Insert suspend fun insertDay(value: DayInstanceEntity)
    @Query("SELECT * FROM day_instance WHERE semesterId = :semesterId AND day = :day")
    suspend fun day(semesterId: String, day: Long): DayInstanceEntity?
    @Query("SELECT * FROM day_instance WHERE id = :id") suspend fun dayById(id: String): DayInstanceEntity?
    @Query("SELECT * FROM day_instance WHERE semesterId = :semesterId AND day = :day")
    fun observeDay(semesterId: String, day: Long): Flow<DayInstanceEntity?>
    @Query("UPDATE day_instance SET holiday = :holiday, revision = revision + 1 WHERE id = :id")
    suspend fun setHoliday(id: String, holiday: Boolean): Int
    @Query("UPDATE day_instance SET kind = :kind, holiday = :holiday, copiedWeekday = :copiedWeekday, revision = :revision WHERE id = :id")
    suspend fun restoreDay(id: String, kind: String, holiday: Boolean, copiedWeekday: Int?, revision: Long): Int
    @Query("UPDATE day_instance SET kind = :kind, copiedWeekday = :copiedWeekday, revision = revision + 1 WHERE id = :id")
    suspend fun updateDayKind(id: String, kind: String, copiedWeekday: Int?): Int
    @Query("DELETE FROM day_instance WHERE id = :id") suspend fun deleteDay(id: String): Int
    @Query("SELECT MIN(d.day) FROM session s JOIN day_instance d ON s.dayId = d.id WHERE s.semesterId = :semesterId")
    suspend fun earliestSessionDay(semesterId: String): Long?
    @Query("""SELECT d.id, d.day, d.kind, d.holiday, d.copiedWeekday,
        (SELECT COUNT(*) FROM session s WHERE s.dayId = d.id AND s.suppressed = 0 AND s.cancelled = 0 AND s.counting = 1) AS total,
        (SELECT COUNT(*) FROM session s JOIN attendance_outcome o ON o.sessionId = s.id WHERE s.dayId = d.id AND s.suppressed = 0 AND s.cancelled = 0 AND s.counting = 1) AS recorded,
        (SELECT COUNT(*) FROM session s WHERE s.dayId = d.id) AS sessionCount
        FROM day_instance d WHERE d.semesterId = :semesterId AND d.day BETWEEN :from AND :to ORDER BY d.day""")
    fun observeDaySummaries(semesterId: String, from: Long, to: Long): Flow<List<DaySummaryRow>>
    @Query("SELECT * FROM day_instance WHERE semesterId = :semesterId AND day BETWEEN :from AND :to")
    suspend fun days(semesterId: String, from: Long, to: Long): List<DayInstanceEntity>

    // Sessions
    @Insert suspend fun insertSession(value: SessionEntity)
    @Query("SELECT * FROM session WHERE id = :id") suspend fun session(id: String): SessionEntity?
    @Query("SELECT * FROM session WHERE dayId = :dayId") suspend fun sessionsForDay(dayId: String): List<SessionEntity>
    @Query("UPDATE session SET courseId = :courseId, startMinute = :start, endMinute = :end, suppressed = 0, revision = revision + 1 WHERE id = :id")
    suspend fun reconcileSession(id: String, courseId: String, start: Int, end: Int): Int
    @Query("UPDATE session SET suppressed = :suppressed, revision = revision + 1 WHERE id = :id")
    suspend fun setSuppressed(id: String, suppressed: Boolean): Int
    @Query("UPDATE session SET cancelled = :cancelled, revision = revision + 1 WHERE id = :id")
    suspend fun setCancelled(id: String, cancelled: Boolean): Int
    @Query("UPDATE session SET counting = :counting, revision = revision + 1 WHERE id = :id")
    suspend fun setCounting(id: String, counting: Boolean): Int
    @Query("UPDATE session SET courseId = :courseId, pinned = 1, revision = revision + 1 WHERE id = :id")
    suspend fun substituteCourse(id: String, courseId: String): Int
    @Query("UPDATE session SET origin = :origin, courseId = :courseId, startMinute = :start, endMinute = :end, counting = :counting, suppressed = 0, revision = revision + 1 WHERE id = :id")
    suspend fun adoptSession(id: String, origin: String, courseId: String, start: Int, end: Int, counting: Boolean): Int
    @Query("UPDATE session SET courseId = :courseId, origin = :origin, counting = :counting, startMinute = :start, endMinute = :end, cancelled = :cancelled, pinned = :pinned, suppressed = :suppressed, revision = :revision WHERE id = :id")
    suspend fun restoreSession(id: String, courseId: String, origin: String, counting: Boolean,
        start: Int, end: Int, cancelled: Boolean, pinned: Boolean, suppressed: Boolean, revision: Long): Int
    @Query("DELETE FROM session WHERE id = :id") suspend fun deleteSession(id: String): Int
    @Query("""SELECT s.*, d.day, d.kind AS dayKind, d.holiday,
            o.kind AS outcomeKind, o.revision AS outcomeRevision
        FROM session s JOIN day_instance d ON d.id = s.dayId
        LEFT JOIN attendance_outcome o ON o.sessionId = s.id
        WHERE s.dayId = :dayId ORDER BY s.slot IS NULL, s.slot, s.startMinute""")
    fun observeSessionsForDay(dayId: String): Flow<List<SessionRow>>
    @Query("""SELECT s.*, d.day, d.kind AS dayKind, d.holiday,
            o.kind AS outcomeKind, o.revision AS outcomeRevision
        FROM session s JOIN day_instance d ON d.id = s.dayId
        LEFT JOIN attendance_outcome o ON o.sessionId = s.id
        WHERE s.semesterId = :semesterId ORDER BY d.day, s.slot""")
    fun observeSessionRows(semesterId: String): Flow<List<SessionRow>>
    @Query("""SELECT s.*, d.day, d.kind AS dayKind, d.holiday,
            o.kind AS outcomeKind, o.revision AS outcomeRevision
        FROM session s JOIN day_instance d ON d.id = s.dayId
        LEFT JOIN attendance_outcome o ON o.sessionId = s.id
        WHERE s.semesterId = :semesterId AND s.courseId = :courseId""")
    suspend fun sessionRowsForCourse(semesterId: String, courseId: String): List<SessionRow>
    @Query("""SELECT s.*, d.day, d.kind AS dayKind, d.holiday,
            o.kind AS outcomeKind, o.revision AS outcomeRevision
        FROM session s JOIN day_instance d ON d.id = s.dayId
        LEFT JOIN attendance_outcome o ON o.sessionId = s.id
        WHERE s.dayId IN (SELECT id FROM day_instance WHERE semesterId = :semesterId AND day BETWEEN :from AND :to)""")
    suspend fun sessionRowsInRange(semesterId: String, from: Long, to: Long): List<SessionRow>
    // Outcomes
    @Insert suspend fun insertOutcome(value: AttendanceOutcomeEntity)
    @Query("SELECT * FROM attendance_outcome WHERE sessionId = :sessionId") suspend fun outcome(sessionId: String): AttendanceOutcomeEntity?
    @Query("UPDATE attendance_outcome SET kind = :kind, revision = revision + 1 WHERE sessionId = :sessionId")
    suspend fun updateOutcome(sessionId: String, kind: String): Int
    @Query("UPDATE attendance_outcome SET kind = :kind, revision = :revision WHERE sessionId = :sessionId")
    suspend fun restoreOutcome(sessionId: String, kind: String, revision: Long): Int
    @Query("DELETE FROM attendance_outcome WHERE sessionId = :sessionId") suspend fun deleteOutcome(sessionId: String): Int
    // Change batches for guarded undo and history inspection
    @Insert suspend fun insertBatch(value: ChangeBatchEntity)
    @Query("SELECT * FROM change_batch WHERE id = :id") suspend fun batch(id: String): ChangeBatchEntity?
    @Query("SELECT * FROM change_batch WHERE undoneAt IS NULL ORDER BY createdAt DESC, rowid DESC LIMIT 1")
    fun observeUndoable(): Flow<ChangeBatchEntity?>
    @Query("SELECT * FROM change_batch WHERE undoneAt IS NULL ORDER BY rowid DESC")
    suspend fun pendingBatches(): List<ChangeBatchEntity>
    @Query("UPDATE change_batch SET undoneAt = :at WHERE id = :id") suspend fun markUndone(id: String, at: Long): Int
    @Query("DELETE FROM change_batch WHERE kind = :kind AND undoneAt IS NULL")
    suspend fun discardUndoForKind(kind: String): Int
    @Query("DELETE FROM change_batch WHERE id NOT IN (SELECT id FROM change_batch ORDER BY createdAt DESC, rowid DESC LIMIT :keep)")
    suspend fun pruneBatches(keep: Int): Int

    // Whole-table reads for backup export
    @Query("SELECT * FROM day_instance WHERE semesterId = :semesterId") suspend fun allDays(semesterId: String): List<DayInstanceEntity>
    @Query("SELECT * FROM session WHERE semesterId = :semesterId") suspend fun allSessions(semesterId: String): List<SessionEntity>
    @Query("SELECT * FROM attendance_outcome") suspend fun allOutcomes(): List<AttendanceOutcomeEntity>
    @Query("SELECT * FROM change_batch ORDER BY rowid") suspend fun allBatches(): List<ChangeBatchEntity>
}

@Database(
    entities = [
        SemesterEntity::class, CourseEntity::class, OpeningBalanceEntity::class, AppSettingsEntity::class,
        ScheduleVersionEntity::class, TemplateSlotEntity::class, DayInstanceEntity::class,
        SessionEntity::class, AttendanceOutcomeEntity::class, ChangeBatchEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class NinetyDatabase : RoomDatabase() {
    abstract fun setupDao(): SetupDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun journalDao(): JournalDao

    companion object {
        fun create(context: Context, name: String = "ninety.db"): NinetyDatabase =
            Room.databaseBuilder(context.applicationContext, NinetyDatabase::class.java, name)
                .addCallback(integrityCallback)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        // Rebuild all related tables in dependency order; works on API 26 SQLite,
        // where ALTER TABLE DROP COLUMN is unavailable. Preserve every identity/count.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Data-only temporary tables avoid depending on platform-specific
                // foreign-key rewriting during ALTER TABLE RENAME.
                db.execSQL("CREATE TEMP TABLE migration_semester AS SELECT id, name, startDay, collegeZone, archived FROM semester")
                listOf("course", "opening_balance", "app_settings").forEach {
                    db.execSQL("CREATE TEMP TABLE migration_$it AS SELECT * FROM $it")
                }
                listOf("opening_balance", "app_settings", "course", "semester").forEach { db.execSQL("DROP TABLE $it") }
                db.execSQL("""CREATE TABLE semester (
                    id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, startDay INTEGER NOT NULL,
                    collegeZone TEXT NOT NULL, archived INTEGER NOT NULL)""")
                db.execSQL("""CREATE TABLE course (
                    id TEXT NOT NULL PRIMARY KEY, semesterId TEXT NOT NULL, name TEXT NOT NULL,
                    code TEXT NOT NULL, normalizedCode TEXT NOT NULL, type TEXT NOT NULL,
                    colorArgb INTEGER, retired INTEGER NOT NULL,
                    FOREIGN KEY(semesterId) REFERENCES semester(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
                db.execSQL("""CREATE TABLE opening_balance (
                    courseId TEXT NOT NULL PRIMARY KEY, loggingStartDay INTEGER NOT NULL,
                    conducted INTEGER NOT NULL, present INTEGER NOT NULL, approvedDl INTEGER NOT NULL,
                    pendingDl INTEGER NOT NULL, revision INTEGER NOT NULL,
                    FOREIGN KEY(courseId) REFERENCES course(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
                db.execSQL("""CREATE TABLE app_settings (
                    id INTEGER NOT NULL PRIMARY KEY, activeSemesterId TEXT, theme TEXT NOT NULL,
                    revision INTEGER NOT NULL,
                    FOREIGN KEY(activeSemesterId) REFERENCES semester(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
                listOf("semester", "course", "opening_balance", "app_settings").forEach {
                    db.execSQL("INSERT INTO $it SELECT * FROM migration_$it")
                    db.execSQL("DROP TABLE migration_$it")
                }
                db.execSQL("CREATE INDEX index_course_semesterId ON course(semesterId)")
                db.execSQL("CREATE UNIQUE INDEX index_course_semesterId_normalizedCode ON course(semesterId, normalizedCode)")
                db.execSQL("CREATE INDEX index_app_settings_activeSemesterId ON app_settings(activeSemesterId)")
            }
        }

        // Add dated scheduling/attendance tables and lifecycle/preference columns.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE semester ADD COLUMN completedDay INTEGER")
                db.execSQL("ALTER TABLE semester ADD COLUMN materializedDay INTEGER")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN haptics INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN reminderMinute INTEGER NOT NULL DEFAULT 1140")
                db.execSQL("""CREATE TABLE schedule_version (
                    id TEXT NOT NULL PRIMARY KEY, semesterId TEXT NOT NULL, effectiveDay INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    FOREIGN KEY(semesterId) REFERENCES semester(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
                db.execSQL("""CREATE TABLE template_slot (
                    versionId TEXT NOT NULL, weekday INTEGER NOT NULL, slot INTEGER NOT NULL,
                    courseId TEXT NOT NULL, PRIMARY KEY(versionId, weekday, slot),
                    FOREIGN KEY(versionId) REFERENCES schedule_version(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(courseId) REFERENCES course(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
                db.execSQL("""CREATE TABLE day_instance (
                    id TEXT NOT NULL PRIMARY KEY, semesterId TEXT NOT NULL, day INTEGER NOT NULL,
                    kind TEXT NOT NULL, holiday INTEGER NOT NULL, copiedWeekday INTEGER,
                    revision INTEGER NOT NULL,
                    FOREIGN KEY(semesterId) REFERENCES semester(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
                db.execSQL("""CREATE TABLE session (
                    id TEXT NOT NULL PRIMARY KEY, dayId TEXT NOT NULL, semesterId TEXT NOT NULL,
                    courseId TEXT NOT NULL, slot INTEGER, origin TEXT NOT NULL, counting INTEGER NOT NULL,
                    startMinute INTEGER NOT NULL, endMinute INTEGER NOT NULL, cancelled INTEGER NOT NULL,
                    pinned INTEGER NOT NULL, suppressed INTEGER NOT NULL, revision INTEGER NOT NULL,
                    FOREIGN KEY(dayId) REFERENCES day_instance(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(semesterId) REFERENCES semester(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(courseId) REFERENCES course(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
                db.execSQL("""CREATE TABLE attendance_outcome (
                    sessionId TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, dlStatus TEXT,
                    dlEventId TEXT, revision INTEGER NOT NULL,
                    FOREIGN KEY(sessionId) REFERENCES session(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(dlEventId) REFERENCES dl_event(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
                db.execSQL("""CREATE TABLE dl_event (
                    id TEXT NOT NULL PRIMARY KEY, semesterId TEXT NOT NULL, title TEXT,
                    createdAt INTEGER NOT NULL,
                    FOREIGN KEY(semesterId) REFERENCES semester(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
                db.execSQL("""CREATE TABLE change_batch (
                    id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, createdAt INTEGER NOT NULL,
                    undoneAt INTEGER, payload TEXT NOT NULL)""")
                db.execSQL("CREATE UNIQUE INDEX index_schedule_version_semesterId_effectiveDay ON schedule_version(semesterId, effectiveDay)")
                db.execSQL("CREATE INDEX index_schedule_version_semesterId ON schedule_version(semesterId)")
                db.execSQL("CREATE INDEX index_template_slot_courseId ON template_slot(courseId)")
                db.execSQL("CREATE UNIQUE INDEX index_day_instance_semesterId_day ON day_instance(semesterId, day)")
                db.execSQL("CREATE INDEX index_session_dayId ON session(dayId)")
                db.execSQL("CREATE INDEX index_session_semesterId ON session(semesterId)")
                db.execSQL("CREATE INDEX index_session_courseId ON session(courseId)")
                db.execSQL("CREATE UNIQUE INDEX index_session_dayId_slot ON session(dayId, slot)")
                db.execSQL("CREATE INDEX index_attendance_outcome_dlEventId ON attendance_outcome(dlEventId)")
                db.execSQL("CREATE INDEX index_dl_event_semesterId ON dl_event(semesterId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE attendance_outcome_new (
                    sessionId TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, revision INTEGER NOT NULL,
                    FOREIGN KEY(sessionId) REFERENCES session(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
                db.execSQL("""INSERT INTO attendance_outcome_new (sessionId, kind, revision)
                    SELECT sessionId, kind, revision FROM attendance_outcome WHERE kind IN ('PRESENT', 'ABSENT')""")
                db.execSQL("DROP TABLE attendance_outcome")
                db.execSQL("ALTER TABLE attendance_outcome_new RENAME TO attendance_outcome")
                db.execSQL("DROP TABLE dl_event")

                db.execSQL("""CREATE TABLE opening_balance_new (
                    courseId TEXT NOT NULL PRIMARY KEY, loggingStartDay INTEGER NOT NULL,
                    conducted INTEGER NOT NULL, present INTEGER NOT NULL, dutyLeave INTEGER NOT NULL,
                    revision INTEGER NOT NULL,
                    FOREIGN KEY(courseId) REFERENCES course(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
                db.execSQL("""INSERT INTO opening_balance_new (courseId, loggingStartDay, conducted, present, dutyLeave, revision)
                    SELECT courseId, loggingStartDay, conducted, present, 0, revision FROM opening_balance""")
                db.execSQL("DROP TABLE opening_balance")
                db.execSQL("ALTER TABLE opening_balance_new RENAME TO opening_balance")

                // Old undo entries can contain duty leave event links and approval states.
                db.execSQL("DELETE FROM change_batch")
            }
        }

        /** Mirror cross-field invariants in SQLite, including direct DAO writes. */
        val integrityCallback = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                listOf("INSERT", "UPDATE").forEach { action ->
                    fun guard(table: String, suffix: String, invalid: String) {
                        db.execSQL("""
                            CREATE TRIGGER IF NOT EXISTS guard_${table}_${suffix}_${action.lowercase()}
                            BEFORE $action ON $table WHEN $invalid
                            BEGIN SELECT RAISE(ABORT, 'Invalid $table data'); END
                        """.trimIndent())
                    }
                    guard("semester", "fields", """
                        length(trim(NEW.name)) = 0 OR
                        (NEW.archived = 1 AND EXISTS (SELECT 1 FROM app_settings WHERE activeSemesterId = NEW.id))
                    """.trimIndent())
                    guard("course", "fields", """
                        length(trim(NEW.name)) = 0 OR length(trim(NEW.code)) = 0 OR
                        length(trim(NEW.normalizedCode)) = 0 OR
                        NEW.type NOT IN ('THEORY', 'LAB', 'OTHER')
                    """.trimIndent())
                    guard("opening_balance", "counts", """
                        NEW.conducted < 0 OR NEW.conducted > $MAX_PERIOD_COUNT OR
                        NEW.present < 0 OR NEW.present > NEW.conducted OR
                        NEW.dutyLeave < 0 OR NEW.dutyLeave > NEW.conducted - NEW.present OR
                        NEW.revision < 0 OR NOT EXISTS (
                          SELECT 1 FROM course c JOIN semester s ON s.id = c.semesterId
                          WHERE c.id = NEW.courseId AND NEW.loggingStartDay >= s.startDay
                        )
                    """.trimIndent())
                    guard("app_settings", "singleton", """
                        NEW.id != 1 OR NEW.theme NOT IN ('SYSTEM', 'LIGHT', 'DARK') OR
                        NEW.reminderMinute < 0 OR NEW.reminderMinute > 1439 OR
                        (NEW.activeSemesterId IS NOT NULL AND NOT EXISTS
                          (SELECT 1 FROM semester s WHERE s.id = NEW.activeSemesterId AND s.archived = 0))
                    """.trimIndent())
                    guard("schedule_version", "fields", """
                        NOT EXISTS (SELECT 1 FROM semester s WHERE s.id = NEW.semesterId)
                    """.trimIndent())
                    guard("template_slot", "fields", """
                        NEW.weekday NOT BETWEEN 1 AND 5 OR NEW.slot NOT BETWEEN 1 AND 6 OR
                        NOT EXISTS (
                          SELECT 1 FROM schedule_version v JOIN course c ON c.id = NEW.courseId
                          WHERE v.id = NEW.versionId AND c.semesterId = v.semesterId)
                    """.trimIndent())
                    guard("day_instance", "fields", """
                        NEW.kind NOT IN ('REGULAR', 'COPIED', 'CUSTOM') OR
                        (NEW.copiedWeekday IS NOT NULL AND NEW.copiedWeekday NOT BETWEEN 1 AND 5) OR
                        NEW.revision < 0 OR
                        NOT EXISTS (SELECT 1 FROM semester s WHERE s.id = NEW.semesterId)
                    """.trimIndent())
                    guard("session", "fields", """
                        NEW.origin NOT IN ('TEMPLATE', 'COPIED', 'EXTRA') OR
                        (NEW.origin = 'EXTRA' AND NEW.slot IS NOT NULL) OR
                        (NEW.origin != 'EXTRA' AND NEW.slot IS NULL) OR
                        (NEW.slot IS NOT NULL AND NEW.slot NOT BETWEEN 1 AND 6) OR
                        NEW.startMinute < 0 OR NEW.endMinute > 1440 OR NEW.startMinute >= NEW.endMinute OR
                        NEW.revision < 0 OR
                        NOT EXISTS (SELECT 1 FROM day_instance d WHERE d.id = NEW.dayId AND d.semesterId = NEW.semesterId) OR
                        NOT EXISTS (SELECT 1 FROM course c WHERE c.id = NEW.courseId AND c.semesterId = NEW.semesterId)
                    """.trimIndent())
                    guard("attendance_outcome", "fields", """
                        NEW.kind NOT IN ('PRESENT', 'ABSENT', 'DUTY_LEAVE') OR
                        NEW.revision < 0
                    """.trimIndent())
                    guard("change_batch", "fields", "length(trim(NEW.kind)) = 0 OR length(trim(NEW.payload)) = 0")
                }
            }
        }
    }
}
