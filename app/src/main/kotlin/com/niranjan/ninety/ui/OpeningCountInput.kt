package com.niranjan.ninety.ui

import com.niranjan.ninety.R
import com.niranjan.ninety.domain.MAX_PERIOD_COUNT

/** Returns the presentation error for a count form using the domain's existing bounds. */
internal fun openingCountError(held: Long?, attended: Long?, dutyLeave: Long?): Int? {
    if (held == null || attended == null || dutyLeave == null ||
        minOf(held, attended, dutyLeave) < 0) return R.string.counts_nonnegative
    if (held > MAX_PERIOD_COUNT) return R.string.counts_too_large
    if (attended > held || dutyLeave > held - attended) {
        return R.string.counts_exceed_held
    }
    return null
}
