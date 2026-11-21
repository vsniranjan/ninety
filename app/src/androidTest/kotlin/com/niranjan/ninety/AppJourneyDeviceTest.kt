package com.niranjan.ninety

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.niranjan.ninety.domain.AttendanceOutcome
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Full end-to-end journey on the physical device: the real [MainActivity], real
 * Room database, real repository — driven through the actual UI.
 *
 * The run is self-adapting: if a semester already exists (test re-run), a new one
 * is created through Settings → "New semester" instead. Nothing is uninstalled
 * or cleared; fixture data created here stays on the device and is clearly named
 * "Device Test <suffix>" (spec §10.1.1 — never resets app storage).
 */
@RunWith(AndroidJUnit4::class)
class AppJourneyDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val repo get() =
        (ApplicationProvider.getApplicationContext<android.content.Context>() as NinetyApplication)
            .container.repository

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now(zone)
    private val collegeDay: Boolean = today.dayOfWeek.value <= DayOfWeek.FRIDAY.value

    @Test fun setupTimetableAndDailyLoggingJourney() = runBlocking {
        val suffix = (System.currentTimeMillis() % 100000).toString()
        val semesterName = "Device Test $suffix"

        // --- Gate: fresh install shows setup; re-runs enter it via Settings. ---
        val initialState = withTimeout(15_000) { repo.observeAppState().first { it.loaded } }
        if (initialState.semesters.isNotEmpty()) {
            rule.waitUntil(10_000) { exists("tab_settings") }
            rule.onNodeWithTag("tab_settings").activate()
            rule.waitUntil(10_000) { exists("new_semester") }
            rule.onNodeWithTag("new_semester").performScrollTo().activate()
        }
        rule.waitUntil(15_000) { exists("semester_name") }

        // --- Step 0: semester name + start date = today. ---
        rule.onNodeWithTag("semester_name").performTextInput(semesterName)
        rule.onNodeWithTag("start_date").activate()
        // The picker opens with today selected. Confirm through its semantics;
        // M3 day numbers are accessibility descriptions in the merged tree.
        rule.onNodeWithText("OK").activate()
        rule.onNodeWithTag("setup_action").performScrollTo().activate()   // Continue → courses

        // --- Step 1: one theory course, one lab course. ---
        addCourse("Operating Systems", lab = false)
        addCourse("Networks Lab", lab = true)
        rule.onNodeWithTag("setup_action").performScrollTo().activate()   // Continue → timetable

        // --- Step 2: select today's weekday and assign its six periods. ---
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("setup_slot").fetchSemanticsNodes().isNotEmpty()
        }
        val weekday = if (collegeDay) today.dayOfWeek.value else 1
        rule.onNodeWithTag("weekday_$weekday").performScrollTo().activate()
        armCourse("Operating Systems")
        assignSlot(1)
        armCourse("Networks Lab")
        listOf(3, 4, 5).forEach(::assignSlot)
        rule.onNodeWithTag("setup_action").performScrollTo().activate()   // Continue → review

        // --- Step 3: review and save. ---
        rule.onNodeWithText("Start tracking").performScrollTo().activate()
        if (initialState.activeSemesterId != null) rule.onNodeWithText("Finish semester").activate()
        rule.waitUntil(20_000) { exists("day_title") }

        val semesterId = withTimeout(15_000) {
            repo.observeAppState().first { it.loaded && it.activeSemesterId != null }
        }.activeSemesterId!!
        val saved = withTimeout(10_000) { repo.observeAppState().first { it.loaded } }
            .semesters.single { it.entity.id == semesterId.toString() }.entity
        assertEquals(semesterName, saved.name)
        assertEquals(today.toEpochDay(), saved.startDay)

        if (!collegeDay) {
            // The setup fixture assigned Monday. Copy it through the UI on weekends
            // so the rest of the journey runs every day of the week.
            rule.onNodeWithTag("day_menu").activate()
            rule.onNodeWithText("Use another day's timetable…").activate()
            rule.onNodeWithText("Monday").activate()
        }

        // --- Today shows the materialized day: theory row + 3-period lab card. ---
        rule.waitUntil(10_000) { exists("group_present") }
        rule.onNodeWithContentDescription("View periods").assertExists()
        rule.onNodeWithText("Operating Systems", substring = true).assertExists()
        rule.onNodeWithText("Mark all present").assertIsDisplayed()

        // --- One-tap lab group action credits all three underlying periods. ---
        rule.onNodeWithTag("group_present").activate()
        await {
            val day = repo.observeDay(semesterId, today).first()
            val lab = day.groups.single { it.grouped }.cells.mapNotNull { it.session }
            lab.size == 3 && lab.all { it.outcome == AttendanceOutcome.PRESENT }
        }

        // --- Mark-all fills the remaining unrecorded session only. ---
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Mark remaining present").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("mark_all").performScrollTo().activate()
        await {
            val stats = repo.observeCourseStats(semesterId, today).first()
            stats.single { it.course.name == "Operating Systems" }.summary.physicallyPresent == 1L &&
                stats.single { it.course.name == "Networks Lab" }.summary.physicallyPresent == 3L
        }
        rule.waitUntil(10_000) { exists("day_complete") }
        rule.onNodeWithTag("mark_all").assertDoesNotExist()
        rule.onNodeWithText("Networks Lab").assertExists()

        // --- Holiday marks the day, then snackbar Undo restores it. ---
        rule.onNodeWithTag("day_menu").activate()
        rule.onNodeWithText("No classes today (holiday)").activate()
        rule.onNodeWithText("Mark holiday").activate()
        await { repo.observeDay(semesterId, today).first().holiday }
        rule.waitUntil(6_000) {
            rule.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Undo").activate()
        await { !repo.observeDay(semesterId, today).first().holiday }

        // A cancelled period stays reachable and restoring it preserves its result.
        val theoryId = withTimeout(10_000) {
            repo.observeDay(semesterId, today).first().groups.flatMap { it.cells }
                .mapNotNull { it.session }.single { it.courseName == "Operating Systems" }.id
        }
        val theory = rule.onNodeWithTag("period_$theoryId", useUnmergedTree = true)
        theory.performScrollTo().activate()
        rule.onNodeWithText("Class not held").performScrollTo().activate()
        await {
            repo.observeDay(semesterId, today).first().groups.flatMap { it.cells }
                .any { it.session?.let { period -> period.id == theoryId && period.cancelled } == true }
        }
        theory.performScrollTo().activate()
        rule.onNodeWithTag("restore_period").activate()
        await {
            repo.observeCourseStats(semesterId, today).first()
                .single { it.course.name == "Operating Systems" }.summary.physicallyPresent == 1L
        }

        // Changing recorded lab periods to DL requires an explicit replacement decision.
        val firstLabPeriod = repo.observeDay(semesterId, today).first().groups.single { it.grouped }.cells.first().session!!
        rule.onNodeWithTag("group_periods_${firstLabPeriod.id}").performScrollTo().activate()
        rule.onNodeWithTag("group_leave").performScrollTo().activate()
        rule.onNodeWithTag("dl_confirm").activate()
        rule.waitUntil(10_000) { exists("confirm_action") }
        rule.onNodeWithText("Replace recorded attendance?").assertExists()
        assertTrue(repo.observeCourseStats(semesterId, today).first().all { it.summary.dutyLeave == 0L })
        rule.onNodeWithTag("confirm_action").activate()
        await { repo.observeCourseStats(semesterId, today).first()
            .single { it.course.name == "Networks Lab" }.summary.dutyLeave == 3L }

        // --- Tabs render with the recorded data. ---
        rule.onNodeWithTag("tab_courses").activate()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("Operating Systems", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Networks Lab", substring = true).assertExists()
        rule.onNodeWithTag("tab_calendar").activate()
        rule.onNodeWithTag("tab_settings").activate()
        rule.onAllNodesWithText(semesterName, substring = true).onFirst().assertExists()
        rule.onNodeWithTag("open_semester_$semesterId").performScrollTo().activate()
        val lifecycle = rule.onNodeWithTag("semester_lifecycle_$semesterId")
        lifecycle.performScrollTo().activate()
        rule.onNodeWithTag("confirm_action").activate()
        await { repo.observeAppState().first().activeSemesterId == null }
        rule.onNodeWithTag("browse_$semesterId").performScrollTo().activate()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Finished", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("tab_settings").activate()
        rule.onNodeWithTag("open_semester_$semesterId").performScrollTo().activate()
        lifecycle.performScrollTo().activate()
        await { repo.observeAppState().first().activeSemesterId == semesterId }
        rule.onNodeWithTag("tab_today").activate()
        rule.waitUntil(10_000) { exists("day_title") }
    }

    /** Types a course into the draft dialog and saves it. */
    private fun addCourse(name: String, lab: Boolean) {
        rule.waitUntil(10_000) { exists("add_course") }
        rule.onNodeWithTag("add_course").activate()
        rule.waitUntil(5_000) { exists("draft_name") }
        rule.onNodeWithTag("draft_name").performTextInput(name)
        if (lab) {
            rule.onAllNodesWithText("Lab", substring = false).onFirst().activate()
        }
        rule.onNodeWithTag("draft_save").activate()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("draft_name").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun armCourse(courseName: String) {
        rule.onNode(hasTestTag("timetable_course_chip") and hasText(courseName)).performScrollTo().activate()
    }

    /** Period rows for the selected weekday are ordered P1–P6. */
    private fun assignSlot(slot: Int) {
        rule.onAllNodesWithTag("setup_slot")[slot - 1].performScrollTo().activate()
    }

    /** Use the exposed UI action directly; MIUI can drop injected touch on a clipped node. */
    private fun SemanticsNodeInteraction.activate() = performSemanticsAction(SemanticsActions.OnClick) {
        check(it()) { "The app did not handle the click action." }
    }

    private fun exists(tag: String) =
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** Polls a repository assertion until it holds or the deadline passes. */
    private suspend fun await(timeoutMs: Long = 10_000, block: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (withTimeout(5_000) { block() }) return
            delay(200)
        }
        fail("Timed out waiting for expected repository state")
    }
}
