package com.niranjan.ninety.ui

import com.niranjan.ninety.domain.PeriodTimes
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Locale is read per call so formats follow a locale change without a restart.
fun LocalDate.long(): String =
    format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault()))
fun LocalDate.medium(): String =
    format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
fun LocalDate.weekdayAndDate(): String =
    format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
fun LocalDate.short(): String =
    format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

fun DayOfWeek.shortName(): String = getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
fun DayOfWeek.fullName(): String = getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())

/** Monday–Friday as an iterable list (ClosedRange<DayOfWeek> is not iterable). */
fun collegeWeekdays(): List<DayOfWeek> = DayOfWeek.entries.take(5)

fun timeRange(startMinute: Int, endMinute: Int): String =
    "${PeriodTimes.label(startMinute)} – ${PeriodTimes.label(endMinute)}"

/** Truncate the display to one decimal without changing the attendance decision. */
fun percentOf(credited: Long, conducted: Long): String =
    if (conducted == 0L) "—" else {
        val percent = credited.toBigDecimal().movePointRight(2)
            .divide(conducted.toBigDecimal(), 1, RoundingMode.DOWN)
        "%.1f %%".format(Locale.getDefault(), percent)
    }

fun slotLabel(slot: Int?): String = slot?.let { "P$it" } ?: "Extra"
