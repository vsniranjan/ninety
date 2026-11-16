package com.niranjan.ninety.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

/**
 * Institutional slot times from spec §2.2, in minutes since local midnight.
 * Clock durations are scheduling/UX information only and never weight attendance.
 */
object PeriodTimes {
    const val SLOT_COUNT = 6

    private val MON_THU = listOf(540 to 600, 600 to 660, 660 to 720, 780 to 840, 840 to 900, 900 to 960)
    private val FRIDAY = listOf(540 to 585, 585 to 630, 630 to 675, 675 to 720, 840 to 900, 900 to 960)

    /** Six (startMinute, endMinute) pairs for a college weekday; empty on weekends. */
    fun slotTimes(day: DayOfWeek): List<Pair<Int, Int>> = when (day) {
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> emptyList()
        DayOfWeek.FRIDAY -> FRIDAY
        else -> MON_THU
    }

    fun isCollegeWeekday(day: DayOfWeek): Boolean = day in DayOfWeek.MONDAY..DayOfWeek.FRIDAY

    fun label(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
}

/** A complete weekly recurring template: weekday -> slot (1-6) -> course. Only assigned slots stored. */
data class WeekTemplate(val assignments: Map<DayOfWeek, Map<Int, UUID>> = emptyMap()) {
    fun courseAt(day: DayOfWeek, slot: Int): UUID? = assignments[day]?.get(slot)
    fun isEmpty(): Boolean = assignments.values.all { it.isEmpty() }

    fun with(day: DayOfWeek, slot: Int, course: UUID?): WeekTemplate {
        require(PeriodTimes.isCollegeWeekday(day)) { "Recurring timetable covers Monday to Friday only." }
        require(slot in 1..PeriodTimes.SLOT_COUNT) { "Slot must be between 1 and ${PeriodTimes.SLOT_COUNT}." }
        val daySlots = assignments[day].orEmpty().toMutableMap()
        if (course == null) daySlots.remove(slot) else daySlots[slot] = course
        val next = assignments.toMutableMap<DayOfWeek, Map<Int, UUID>>()
        if (daySlots.isEmpty()) next.remove(day) else next[day] = daySlots
        return WeekTemplate(next)
    }
}

/** One immutable weekly template effective from a local date until the next version begins. */
data class ScheduleVersion(
    val id: UUID,
    val semesterId: UUID,
    val effectiveFrom: LocalDate,
    val template: WeekTemplate = WeekTemplate(),
)

/** A slot the recurring timetable assigns on a weekday, with display times. */
data class ExpectedSlot(val slot: Int, val courseId: UUID, val startMinute: Int, val endMinute: Int)

enum class DayKind { REGULAR, COPIED, CUSTOM }
enum class SessionOrigin { TEMPLATE, COPIED, EXTRA }

/**
 * Read-side view of a stored dated session used by the reconciler.
 * [hasOutcome] covers any recorded result; cancelled/pinned sessions are user decisions
 * that the recurring schedule must not silently rewrite.
 */
data class TrackedSession(
    val id: UUID,
    val slot: Int?,
    val courseId: UUID,
    val origin: SessionOrigin,
    val cancelled: Boolean,
    val pinned: Boolean,
    val suppressed: Boolean,
    val hasOutcome: Boolean,
) {
    val protected: Boolean get() = hasOutcome || cancelled || pinned
}

sealed interface ReconcileOp {
    data class Create(val slot: ExpectedSlot) : ReconcileOp
    data class Update(val sessionId: UUID, val slot: ExpectedSlot) : ReconcileOp
    /** A removed generated session with user data is retained but excluded. */
    data class Suppress(val sessionId: UUID) : ReconcileOp
    /** Restore a previously suppressed session now that the template assigns its slot again. */
    data class Restore(val sessionId: UUID, val slot: ExpectedSlot) : ReconcileOp
    data class Delete(val sessionId: UUID) : ReconcileOp
    /** A recorded/protected session disagrees with the template; it is left untouched. */
    data class Conflict(val sessionId: UUID, val expected: ExpectedSlot) : ReconcileOp
}

object ScheduleResolver {
    /** The version governing a date: the latest whose effectiveFrom is not after it. */
    fun effectiveVersion(versions: List<ScheduleVersion>, date: LocalDate): ScheduleVersion? =
        versions.filter { !it.effectiveFrom.isAfter(date) }.maxByOrNull { it.effectiveFrom }

    /** Assigned slots for one weekday under a template, with that weekday's own times. */
    fun expectedSlots(template: WeekTemplate, weekday: DayOfWeek): List<ExpectedSlot> =
        PeriodTimes.slotTimes(weekday).mapIndexedNotNull { index, (start, end) ->
            template.courseAt(weekday, index + 1)?.let { ExpectedSlot(index + 1, it, start, end) }
        }

    /**
     * Diff stored template sessions against the effective weekly template for one date.
     * Recorded outcomes, cancellations, and pinned (hand-edited) sessions are never rewritten;
     * they surface as conflicts or suppressed records instead.
     */
    fun reconcile(expected: List<ExpectedSlot>, existing: List<TrackedSession>): List<ReconcileOp> {
        val ops = mutableListOf<ReconcileOp>()
        val templateOwned = existing.filter { it.origin == SessionOrigin.TEMPLATE }
        val occupied = existing.filter { it.slot != null }.associateBy { it.slot!! }
        val expectedSlots = expected.map { it.slot }.toSet()
        expected.forEach { slot ->
            val current = occupied[slot.slot]
            when {
                current == null -> ops += ReconcileOp.Create(slot)
                // Copied/extra placements are user decisions; the recurring template
                // may not rewrite them. A disagreement only surfaces as a conflict.
                current.origin != SessionOrigin.TEMPLATE ->
                    if (current.courseId != slot.courseId) ops += ReconcileOp.Conflict(current.id, slot)
                current.suppressed -> when {
                    current.courseId != slot.courseId && current.protected -> ops += ReconcileOp.Conflict(current.id, slot)
                    else -> ops += ReconcileOp.Restore(current.id, slot)
                }
                current.courseId == slot.courseId -> ops += ReconcileOp.Update(current.id, slot)
                current.protected -> ops += ReconcileOp.Conflict(current.id, slot)
                else -> ops += ReconcileOp.Update(current.id, slot)
            }
        }
        templateOwned.filter { it.slot !in expectedSlots }.forEach { session ->
            when {
                session.protected -> if (!session.suppressed) ops += ReconcileOp.Suppress(session.id)
                else -> ops += ReconcileOp.Delete(session.id)
            }
        }
        return ops
    }
}
