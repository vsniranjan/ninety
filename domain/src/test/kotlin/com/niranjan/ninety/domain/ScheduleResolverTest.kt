package com.niranjan.ninety.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ScheduleResolverTest {
    private val courseA = UUID.randomUUID()
    private val courseB = UUID.randomUUID()
    private val semester = UUID.randomUUID()

    @Test fun institutionalTimesMatchSpecAndNeverAffectWeight() {
        assertEquals(listOf(540 to 600, 600 to 660, 660 to 720, 780 to 840, 840 to 900, 900 to 960),
            PeriodTimes.slotTimes(DayOfWeek.WEDNESDAY))
        assertEquals(listOf(540 to 585, 585 to 630, 630 to 675, 675 to 720, 840 to 900, 900 to 960),
            PeriodTimes.slotTimes(DayOfWeek.FRIDAY))
        assertEquals(listOf(540 to 600, 600 to 660, 660 to 720, 780 to 840, 840 to 900, 900 to 960),
            PeriodTimes.slotTimes(DayOfWeek.THURSDAY))
        assertTrue(PeriodTimes.slotTimes(DayOfWeek.SATURDAY).isEmpty())
        assertTrue(PeriodTimes.slotTimes(DayOfWeek.SUNDAY).isEmpty())
    }

    @Test fun effectiveVersionIsLatestNotAfterDate() {
        val v1 = ScheduleVersion(UUID.randomUUID(), semester, LocalDate.of(2026, 8, 1))
        val v2 = ScheduleVersion(UUID.randomUUID(), semester, LocalDate.of(2026, 10, 1))
        val future = ScheduleVersion(UUID.randomUUID(), semester, LocalDate.of(2027, 1, 1))
        val versions = listOf(future, v2, v1)
        assertNull(ScheduleResolver.effectiveVersion(versions, LocalDate.of(2026, 7, 31)))
        assertEquals(v1.id, ScheduleResolver.effectiveVersion(versions, LocalDate.of(2026, 8, 1))?.id)
        assertEquals(v1.id, ScheduleResolver.effectiveVersion(versions, LocalDate.of(2026, 9, 30))?.id)
        assertEquals(v2.id, ScheduleResolver.effectiveVersion(versions, LocalDate.of(2026, 10, 1))?.id)
        assertEquals(v2.id, ScheduleResolver.effectiveVersion(versions, LocalDate.of(2026, 12, 31))?.id)
        assertEquals(future.id, ScheduleResolver.effectiveVersion(versions, LocalDate.of(2027, 6, 1))?.id)
    }

    @Test fun expectedSlotsCarryWeekdayOwnTimes() {
        val template = WeekTemplate(mapOf(
            DayOfWeek.MONDAY to mapOf(1 to courseA, 3 to courseB),
            DayOfWeek.FRIDAY to mapOf(5 to courseA)))
        assertEquals(listOf(ExpectedSlot(1, courseA, 540, 600), ExpectedSlot(3, courseB, 660, 720)),
            ScheduleResolver.expectedSlots(template, DayOfWeek.MONDAY))
        assertEquals(listOf(ExpectedSlot(5, courseA, 840, 900)),
            ScheduleResolver.expectedSlots(template, DayOfWeek.FRIDAY))
        assertTrue(ScheduleResolver.expectedSlots(template, DayOfWeek.TUESDAY).isEmpty())
    }

    private fun tracked(slot: Int?, course: UUID = courseA, hasOutcome: Boolean = false,
        cancelled: Boolean = false, pinned: Boolean = false, suppressed: Boolean = false,
        origin: SessionOrigin = SessionOrigin.TEMPLATE) =
        TrackedSession(UUID.randomUUID(), slot, course, origin, cancelled, pinned, suppressed, hasOutcome)

    @Test fun reconcileCreatesOnlyMissingSlots() {
        val expected = listOf(ExpectedSlot(1, courseA, 540, 600), ExpectedSlot(2, courseB, 600, 660))
        val ops = ScheduleResolver.reconcile(expected, listOf(tracked(1, courseA)))
        assertTrue(ops.any { it is ReconcileOp.Create && it.slot.slot == 2 })
        assertTrue(ops.none { it is ReconcileOp.Create && it.slot.slot == 1 })
        assertTrue(ops.none { it is ReconcileOp.Conflict })
    }

    @Test fun recordedSessionsAreNeverRewrittenOrDeleted() {
        val expected = listOf(ExpectedSlot(1, courseB, 540, 600))
        val recorded = tracked(1, courseA, hasOutcome = true)
        val ops = ScheduleResolver.reconcile(expected, listOf(recorded))
        assertTrue(ops.single() is ReconcileOp.Conflict)
        val removed = tracked(4, courseA, hasOutcome = true)
        val ops2 = ScheduleResolver.reconcile(expected, listOf(recorded, removed))
        assertTrue(ops2.any { it is ReconcileOp.Suppress && it.sessionId == removed.id })
        assertTrue(ops2.none { it is ReconcileOp.Delete })
    }

    @Test fun untouchedStaleSessionsAreDeletedNotSuppressed() {
        val stale = tracked(4)
        val ops = ScheduleResolver.reconcile(emptyList(), listOf(stale))
        assertEquals(listOf(ReconcileOp.Delete(stale.id)), ops)
        assertTrue(ScheduleResolver.reconcile(emptyList(),
            listOf(stale.copy(cancelled = true))).single() is ReconcileOp.Suppress)
    }

    @Test fun readdedSlotRestoresSuppressedSession() {
        val gone = tracked(1, suppressed = true)
        val ops = ScheduleResolver.reconcile(listOf(ExpectedSlot(1, courseA, 540, 600)), listOf(gone))
        assertEquals(listOf(ReconcileOp.Restore(gone.id, ExpectedSlot(1, courseA, 540, 600))), ops)
        val otherCourse = tracked(1, courseB, suppressed = true, hasOutcome = true)
        assertTrue(ScheduleResolver.reconcile(listOf(ExpectedSlot(1, courseA, 540, 600)),
            listOf(otherCourse)).single() is ReconcileOp.Conflict)
    }

    @Test fun extrasAndCopiedSessionsAreUntouchedByReconcile() {
        val extra = tracked(null, origin = SessionOrigin.EXTRA)
        val copied = tracked(2, origin = SessionOrigin.COPIED)
        assertTrue(ScheduleResolver.reconcile(emptyList(), listOf(extra, copied)).isEmpty())
    }
}
