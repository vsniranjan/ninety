package com.niranjan.ninety.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.niranjan.ninety.R

/** Translate older journal labels and validation messages at the presentation boundary. */
@Composable
internal fun displayMessage(message: String): String {
    val label = when (message) {
        "Marked present" -> R.string.lab_periods_marked_present
        "Marked absent" -> R.string.lab_periods_marked_absent
        "Class substituted" -> R.string.class_course_changed
        "Period marked not conducted" -> R.string.class_marked_not_held
        "Period restored" -> R.string.class_restored
        "Marked as holiday" -> R.string.holiday_marked
        "Holiday reversed" -> R.string.holiday_restored
        "Copied timetable" -> R.string.day_timetable_updated
        "Opening balance updated" -> R.string.updated_before_tracking
        "Opening duty leave approved" -> R.string.approved_before_tracking
        "Opening duty leave rejected" -> R.string.rejected_before_tracking
        "First logging date cannot precede the semester start." -> R.string.tracking_date_not_before_start
        "Opening attendance needs historical dates before the first logging date." -> R.string.tracking_date_after_start
        "Historical attendance cannot start in the future." -> R.string.tracking_date_by_today
        "The first logging date is locked once attendance is recorded." -> R.string.balance_date_locked
        "Unknown opening balance." -> R.string.before_tracking_unavailable
        "No pending opening duty leave to resolve." -> R.string.no_pending_before_tracking
        "Another semester is already active." -> R.string.reopen_requires_no_current
        "Only the active semester can be marked done." -> R.string.only_finish_current
        "Retired courses cannot stay on the timetable." -> R.string.stopped_course_timetable
        "This period is marked not conducted." -> R.string.class_not_held
        else -> return message
    }
    return stringResource(label)
}
