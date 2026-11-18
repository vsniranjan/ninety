package com.niranjan.ninety.ui

import java.text.DecimalFormatSymbols
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun aCourseJustBelowTheMinimumDoesNotDisplayNinetyPercent() {
        val decimal = DecimalFormatSymbols.getInstance().decimalSeparator
        assertEquals("89${decimal}9 %", percentOf(8999, 10000))
        assertEquals("90${decimal}0 %", percentOf(9, 10))
    }
}
