package com.niranjan.ninety.domain

import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Bounds every total so multiplying by ten remains exact in a signed Long. */
const val MAX_PERIOD_COUNT: Long = Long.MAX_VALUE / 10

enum class CourseType { THEORY, LAB, OTHER }

data class Semester(
    val id: UUID,
    val name: String,
    val startDate: LocalDate,
    val collegeZone: ZoneId,
    val completed: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "Enter a semester name." }
    }
}

data class Course(
    val id: UUID,
    val semesterId: UUID,
    val name: String,
    val code: String,
    val type: CourseType,
) {
    init {
        require(name.isNotBlank()) { "Enter a course name." }
        require(code.isNotBlank()) { "Enter a course code." }
    }
}

data class OpeningBalance(
    val courseId: UUID,
    val loggingStartDate: LocalDate,
    val conducted: Long = 0,
    val present: Long = 0,
    val approvedDl: Long = 0,
    val pendingDl: Long = 0,
) {
    init {
        require(conducted in 0..MAX_PERIOD_COUNT) { "Conducted count is outside the supported range." }
        require(present in 0..conducted) { "Present count cannot exceed conducted periods." }
        require(approvedDl in 0..(conducted - present)) { "Approved DL exceeds remaining conducted periods." }
        require(pendingDl in 0..(conducted - present - approvedDl)) {
            "Present + approved DL + pending DL cannot exceed conducted periods."
        }
    }
}

enum class AttendanceOutcome { UNRECORDED, PRESENT, ABSENT, DL_PENDING, DL_APPROVED, DL_REJECTED }

/** One academic period. Clock duration is intentionally absent from the calculation input. */
data class PeriodFact(
    val id: UUID,
    val courseId: UUID,
    val date: LocalDate,
    val outcome: AttendanceOutcome,
    val countsTowardAttendance: Boolean = true,
    val cancelled: Boolean = false,
    val holiday: Boolean = false,
)

data class AttendanceSummary(
    val conducted: Long,
    val physicallyPresent: Long,
    val approvedDl: Long,
    val pendingDl: Long,
    val datedRejectedDl: Long,
    val missingPastPeriods: Long,
    val incompleteDates: Set<LocalDate>,
) {
    val credited: Long get() = physicallyPresent + approvedDl + pendingDl
    val confirmedCredited: Long get() = physicallyPresent + approvedDl
    val isProvisional: Boolean get() = pendingDl > 0
    val hasIncompleteData: Boolean get() = missingPastPeriods > 0
    val meetsMinimum: Boolean? get() = if (conducted == 0L) null else 10 * credited >= 9 * conducted
    val safeSkips: Long? get() = if (conducted == 0L) null else maxOf(0, 10 * credited - 9 * conducted) / 9
    val recovery: Long? get() = if (conducted == 0L) null else maxOf(0, 9 * conducted - 10 * credited)
}

object AttendanceEngine {
    /**
     * Callers supply the course's expected dated periods, including unrecorded ones.
     * A future schedule resolver must include unopened dates to establish complete coverage.
     * Duplicate identities and mixed-course inputs fail rather than silently inflating totals.
     */
    fun calculate(
        opening: OpeningBalance,
        periods: List<PeriodFact> = emptyList(),
        asOfDate: LocalDate,
    ): AttendanceSummary {
        require(periods.all { it.courseId == opening.courseId }) { "Calculate one course at a time." }
        require(periods.map { it.id }.toSet().size == periods.size) { "Duplicate dated period identity." }
        val includeOpening = !asOfDate.isBefore(opening.loggingStartDate)
        var n = if (includeOpening) opening.conducted else 0
        var p = if (includeOpening) opening.present else 0
        var da = if (includeOpening) opening.approvedDl else 0
        var dp = if (includeOpening) opening.pendingDl else 0
        var rejected = 0L
        var missing = 0L
        val incompleteDates = sortedSetOf<LocalDate>()
        periods.forEach { period ->
            if (period.date.isBefore(opening.loggingStartDate) || period.date.isAfter(asOfDate) ||
                !period.countsTowardAttendance || period.cancelled || period.holiday
            ) return@forEach
            if (period.outcome == AttendanceOutcome.UNRECORDED) {
                if (period.date.isBefore(asOfDate)) {
                    missing++
                    incompleteDates += period.date
                }
                return@forEach
            }
            require(n < MAX_PERIOD_COUNT) { "Total attendance exceeds supported range." }
            n++
            when (period.outcome) {
                AttendanceOutcome.PRESENT -> p++
                AttendanceOutcome.DL_APPROVED -> da++
                AttendanceOutcome.DL_PENDING -> dp++
                AttendanceOutcome.DL_REJECTED -> rejected++
                AttendanceOutcome.ABSENT, AttendanceOutcome.UNRECORDED -> Unit
            }
        }
        return AttendanceSummary(n, p, da, dp, rejected, missing, incompleteDates.toSet())
    }
}
