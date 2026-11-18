package com.niranjan.ninety.ui

import com.niranjan.ninety.R
import com.niranjan.ninety.domain.MAX_PERIOD_COUNT

/** Returns the presentation error for a count form using the domain's existing bounds. */
internal fun openingCountError(held: Long?, attended: Long?, approved: Long?, pending: Long?): Int? {
    if (held == null || attended == null || approved == null || pending == null ||
        minOf(held, attended, approved, pending) < 0) return R.string.counts_nonnegative
    if (held > MAX_PERIOD_COUNT) return R.string.counts_too_large
    if (attended > held || approved > held - attended || pending > held - attended - approved) {
        return R.string.counts_exceed_held
    }
    return null
}
