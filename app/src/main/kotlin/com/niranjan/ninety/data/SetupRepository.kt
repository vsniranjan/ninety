package com.niranjan.ninety.data

import androidx.room.withTransaction
import com.niranjan.ninety.domain.AttendanceEngine
import com.niranjan.ninety.domain.AttendanceSummary
import com.niranjan.ninety.domain.Course
import com.niranjan.ninety.domain.CourseType
import com.niranjan.ninety.domain.OpeningBalance
import com.niranjan.ninety.domain.Semester
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class InitialSetup(val semester: Semester, val course: Course, val balance: OpeningBalance) {
    fun validate(today: LocalDate) {
        require(!semester.completed) { "A new semester starts active." }
        require(course.semesterId == semester.id && balance.courseId == course.id) { "Invalid course ownership." }
        require(!balance.loggingStartDate.isBefore(semester.startDate)) { "First logging date cannot precede the semester start." }
        require(balance.conducted == 0L || !balance.loggingStartDate.isAfter(today)) {
            "Historical attendance cannot start in the future."
        }
        require(balance.conducted == 0L || balance.loggingStartDate.isAfter(semester.startDate)) {
            "Opening attendance needs historical dates before the first logging date."
        }
    }
}

data class CourseOverview(val semester: Semester, val course: Course, val balance: OpeningBalance, val summary: AttendanceSummary)

/** Legacy single-course setup contract kept for the retained Phase 1 repository. */
interface SetupRepository {
    val overview: Flow<List<CourseOverview>>
    suspend fun createInitialSetup(setup: InitialSetup)
    suspend fun setSemesterCompleted(semesterId: UUID, completed: Boolean)
}

class RoomSetupRepository(
    private val database: NinetyDatabase,
    private val clock: Clock = Clock.systemUTC(),
) : SetupRepository {
    override val overview = database.setupDao().observeOverview().map { rows ->
        rows.map { row ->
            val semester = Semester(UUID.fromString(row.semesterId), row.semesterName,
                LocalDate.ofEpochDay(row.startDay), ZoneId.of(row.collegeZone), row.archived)
            val course = Course(UUID.fromString(row.courseId), semester.id, row.courseName,
                CourseType.valueOf(row.courseType))
            val balance = OpeningBalance(course.id, LocalDate.ofEpochDay(row.loggingStartDay),
                row.conducted, row.present, row.approvedDl, row.pendingDl)
            CourseOverview(semester, course, balance, AttendanceEngine.calculate(balance,
                asOfDate = LocalDate.now(clock.withZone(semester.collegeZone))))
        }
    }

    override suspend fun createInitialSetup(setup: InitialSetup) {
        setup.validate(LocalDate.now(clock.withZone(setup.semester.collegeZone)))
        database.withTransaction {
            check(database.setupDao().semesterCount() == 0) { "Setup has already been completed. Reload your courses." }
            val s = setup.semester
            val c = setup.course
            val b = setup.balance
            database.setupDao().insertSemester(SemesterEntity(s.id.toString(), s.name.trim(),
                s.startDate.toEpochDay(), s.collegeZone.id))
            database.setupDao().insertCourse(CourseEntity(c.id.toString(), s.id.toString(), c.name.trim(), c.type.name))
            database.setupDao().insertBalance(OpeningBalanceEntity(c.id.toString(), b.loggingStartDate.toEpochDay(),
                b.conducted, b.present, b.approvedDl, b.pendingDl))
            database.setupDao().insertSettings(AppSettingsEntity(activeSemesterId = s.id.toString()))
        }
    }

    override suspend fun setSemesterCompleted(semesterId: UUID, completed: Boolean) {
        database.withTransaction {
            val dao = database.setupDao()
            val active = dao.activeSemesterId()
            if (completed) {
                check(active == semesterId.toString()) { "Only the active semester can be marked done." }
                check(dao.clearActiveSemester(semesterId.toString()) == 1)
                check(dao.markSemesterCompleted(semesterId.toString(), true, LocalDate.now(clock).toEpochDay()) == 1)
            } else {
                check(active == null) { "Another semester is already active." }
                check(dao.markSemesterCompleted(semesterId.toString(), false, null) == 1)
                check(dao.activateSemester(semesterId.toString()) == 1)
            }
        }
    }
}
