package com.niranjan.ninety.domain

import java.math.BigInteger
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class AttendanceEngineTest {
    private val course = UUID.randomUUID()
    private val today = LocalDate.of(2026, 11, 11)
    private val cutoff = today.minusDays(10)
    private fun balance(n: Long = 0, p: Long = 0, da: Long = 0, dp: Long = 0) =
        OpeningBalance(course, cutoff, n, p, da, dp)
    private fun period(
        outcome: AttendanceOutcome,
        date: LocalDate = today,
        counted: Boolean = true,
        cancelled: Boolean = false,
        holiday: Boolean = false,
    ) = PeriodFact(UUID.randomUUID(), course, date, outcome, counted, cancelled, holiday)
    private fun calculate(opening: OpeningBalance, vararg facts: PeriodFact) =
        AttendanceEngine.calculate(opening, facts.toList(), today)

    @Test fun requiredExamples() {
        val high = calculate(balance(40, 38))
        assertEquals(38L, high.credited)
        assertEquals(2L, high.safeSkips)
        assertEquals(true, high.meetsMinimum)
        val boundary = calculate(balance(10, 9))
        assertEquals(true, boundary.meetsMinimum)
        assertEquals(0L, boundary.safeSkips)
        assertEquals(0L, boundary.recovery)
        val low = calculate(balance(10, 8))
        assertEquals(false, low.meetsMinimum)
        assertEquals(10L, low.recovery)
        assertEquals(0L, low.safeSkips)
    }

    @Test fun noDataHasNoMeaningfulPercentageOrAllowance() {
        val result = calculate(balance())
        assertEquals(0L, result.conducted)
        assertNull(result.meetsMinimum)
        assertNull(result.safeSkips)
        assertNull(result.recovery)
    }

    @Test fun partialLabRetainsThreeIndependentUnits() {
        val result = calculate(balance(), period(AttendanceOutcome.PRESENT),
            period(AttendanceOutcome.PRESENT), period(AttendanceOutcome.ABSENT))
        assertEquals(3L, result.conducted)
        assertEquals(2L, result.credited)
    }

    @Test fun fridayPeriodAndCopiedSaturdayEachCountOnceWhileExtraDoesNot() {
        val friday = today.minusDays(5)
        val saturday = friday.plusDays(1)
        val result = calculate(balance(), period(AttendanceOutcome.PRESENT, friday),
            period(AttendanceOutcome.PRESENT, saturday), period(AttendanceOutcome.ABSENT, saturday, counted = false))
        assertEquals(2L, result.conducted)
        assertEquals(2L, result.credited)
    }

    @Test fun pendingDrivesPrimarySafeSkipsAndApprovalRejectionRecalculate() {
        val fact = period(AttendanceOutcome.DL_PENDING)
        val pending = calculate(balance(39, 37), fact)
        assertEquals(38L, pending.credited)
        assertEquals(37L, pending.confirmedCredited)
        assertEquals(2L, pending.safeSkips)
        assertTrue(pending.isProvisional)
        val approved = calculate(balance(39, 37), fact.copy(outcome = AttendanceOutcome.DL_APPROVED))
        assertEquals(pending.credited, approved.credited)
        assertEquals(38L, approved.confirmedCredited)
        assertFalse(approved.isProvisional)
        val rejected = calculate(balance(39, 37), fact.copy(outcome = AttendanceOutcome.DL_REJECTED))
        assertEquals(40L, rejected.conducted)
        assertEquals(37L, rejected.credited)
        assertEquals(1L, rejected.datedRejectedDl)
        assertEquals(1L, rejected.safeSkips)
    }

    @Test fun excludedPeriodsNeverEnterEitherTotalOrMissingWarnings() {
        val excluded = listOf(
            period(AttendanceOutcome.PRESENT, today.plusDays(1)),
            period(AttendanceOutcome.PRESENT, cutoff.minusDays(1)),
            period(AttendanceOutcome.PRESENT, cancelled = true),
            period(AttendanceOutcome.DL_PENDING, holiday = true),
            period(AttendanceOutcome.PRESENT, counted = false),
            period(AttendanceOutcome.UNRECORDED, today.minusDays(1), holiday = true),
            period(AttendanceOutcome.UNRECORDED, today.minusDays(1), counted = false),
        )
        val result = AttendanceEngine.calculate(balance(), excluded, today)
        assertEquals(0L, result.conducted)
        assertEquals(0L, result.credited)
        assertFalse(result.hasIncompleteData)
    }

    @Test fun unrecordedPastWarnsWithoutBecomingAbsentAndTodayIsNotHistorical() {
        val result = calculate(balance(10, 9), period(AttendanceOutcome.UNRECORDED, today.minusDays(1)),
            period(AttendanceOutcome.UNRECORDED, today.minusDays(1)), period(AttendanceOutcome.UNRECORDED))
        assertEquals(10L, result.conducted)
        assertEquals(2L, result.missingPastPeriods)
        assertEquals(setOf(today.minusDays(1)), result.incompleteDates)
    }

    @Test fun openingAndNewRecordsCombineExactlyOnce() {
        val result = calculate(balance(40, 35, 2, 1), period(AttendanceOutcome.PRESENT), period(AttendanceOutcome.ABSENT))
        assertEquals(42L, result.conducted)
        assertEquals(36L, result.physicallyPresent)
        assertEquals(39L, result.credited)
        assertEquals(38L, result.confirmedCredited)
    }

    @Test fun holidayAndCancellationRestorationRetainPriorFacts() {
        val original = period(AttendanceOutcome.DL_PENDING)
        assertEquals(0L, calculate(balance(), original.copy(holiday = true)).conducted)
        assertEquals(0L, calculate(balance(), original.copy(cancelled = true)).conducted)
        assertEquals(1L, calculate(balance(), original).credited)
    }

    @Test fun beforeCutoffDoesNotApplyAnAggregateFromTheFuture() {
        assertEquals(0L, AttendanceEngine.calculate(balance(40, 38), asOfDate = cutoff.minusDays(1)).conducted)
    }

    @Test fun invalidBalancesAndArithmeticOverflowAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { balance(-1) }
        assertThrows(IllegalArgumentException::class.java) { balance(10, 8, 2, 1) }
        assertThrows(IllegalArgumentException::class.java) { balance(10, -1) }
        assertThrows(IllegalArgumentException::class.java) { balance(10, 0, -1) }
        assertThrows(IllegalArgumentException::class.java) { balance(10, 0, 0, -1) }
        assertThrows(IllegalArgumentException::class.java) { balance(Long.MAX_VALUE) }
        val maximum = balance(MAX_PERIOD_COUNT, MAX_PERIOD_COUNT)
        assertEquals(MAX_PERIOD_COUNT / 9, calculate(maximum).safeSkips)
        assertThrows(IllegalArgumentException::class.java) { calculate(maximum, period(AttendanceOutcome.PRESENT)) }
    }

    @Test fun duplicateAndMixedCourseInputsFailClosed() {
        val fact = period(AttendanceOutcome.PRESENT)
        assertThrows(IllegalArgumentException::class.java) { calculate(balance(), fact, fact) }
        assertThrows(IllegalArgumentException::class.java) { calculate(balance(), fact.copy(courseId = UUID.randomUUID())) }
    }

    @Test fun boundedExhaustiveOracleProvesThresholdAndMinimalAllowances() {
        // Independently search feasible future counts with arbitrary-precision comparisons.
        for (n in 1L..80L) for (a in 0L..n) {
            val result = calculate(balance(n, a))
            fun meets(credit: Long, conducted: Long) =
                BigInteger.valueOf(credit).multiply(BigInteger.TEN) >=
                    BigInteger.valueOf(conducted).multiply(BigInteger.valueOf(9))
            assertEquals(meets(a, n), result.meetsMinimum)
            val expectedSkip = (0L..n).takeWhile { meets(a, n + it) }.lastOrNull() ?: 0L
            val expectedRecovery = (0L..(9 * n)).first { meets(a + it, n + it) }
            assertEquals("N=$n A=$a", expectedSkip, result.safeSkips)
            assertEquals("N=$n A=$a", expectedRecovery, result.recovery)
            assertTrue(calculate(balance(n + 1, a + 1)).credited * n >= a * (n + 1))
        }
    }
}
