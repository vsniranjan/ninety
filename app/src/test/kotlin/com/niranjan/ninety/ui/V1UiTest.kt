package com.niranjan.ninety.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.niranjan.ninety.AppContainer
import com.niranjan.ninety.MainActivity
import com.niranjan.ninety.NinetyApplication
import com.niranjan.ninety.data.*
import com.niranjan.ninety.domain.AttendanceOutcome
import com.niranjan.ninety.domain.CourseType
import com.niranjan.ninety.domain.OpeningBalance
import com.niranjan.ninety.domain.WeekTemplate
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** No production database is opened by these UI regressions. */
class V1UiApplication : NinetyApplication() {
    override val container by lazy {
        AppContainer(this, Room.inMemoryDatabaseBuilder(this, NinetyDatabase::class.java)
            .addCallback(NinetyDatabase.integrityCallback).allowMainThreadQueries().build())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = V1UiApplication::class, qualifiers = "w393dp-h873dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class V1UiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val testName = TestName()
    private val app get() = ApplicationProvider.getApplicationContext<V1UiApplication>()
    private val repo get() = app.container.repository
    private lateinit var semesterId: UUID
    private lateinit var labId: UUID
    private lateinit var openingCourseId: UUID
    private val today = LocalDate.now()
    private val oldest = today.minusDays(28).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }

    private val fixture = object : ExternalResource() {
        override fun before() = runBlocking {
            val theory = CourseSetup(input = CourseInput("Operating Systems", CourseType.THEORY))
            val lab = CourseSetup(input = CourseInput("Networks Lab", CourseType.LAB))
            labId = lab.courseId
            val start = when (testName.methodName) {
                "backlogOpensOldestDayAndListsAllDates" -> oldest
                "openingDutyLeaveIsIncludedInCourseTotals", "yesterdayCanBeMarkedPresentInTwoActions",
                "calendarAttendanceStaysInSheetAndKeepsExistingMarks" -> today.minusDays(1)
                else -> today
            }
            semesterId = repo.createSemester(SemesterSetup("V1 UI fixture", start, ZoneId.systemDefault().id,
                listOf(theory, lab), WeekTemplate(DayOfWeek.entries.take(5).associateWith {
                    mapOf(1 to theory.courseId, 3 to labId, 4 to labId, 5 to labId)
                })))
            repo.ensureMaterialized(semesterId)
            // Use a copied weekday when a developer runs the tests on a weekend.
            if (today.dayOfWeek.value > 5) repo.copyWeekday(semesterId, today, DayOfWeek.MONDAY)
            if (testName.methodName in listOf("yesterdayCanBeMarkedPresentInTwoActions",
                    "calendarAttendanceStaysInSheetAndKeepsExistingMarks")) {
                val yesterday = today.minusDays(1)
                if (yesterday.dayOfWeek.value > 5) repo.copyWeekday(semesterId, yesterday, DayOfWeek.MONDAY)
            }
            val ids = repo.observeDay(semesterId, today).first().groups.flatMap { it.cells }
                .mapNotNull { it.session }.filter { it.courseId == labId }.map { it.id }
            if (testName.methodName !in listOf("todayKeepsPeriodsVisibleAndEditableAfterBulkLogging",
                    "twoOfThreeLabPeriodsCanBeMarkedInThreeActionsWithoutADialog",
                    "bulkDutyLeaveFromDayConfirmsReplacement")) {
                repo.markDutyLeave(semesterId, ids)
            }
            if (testName.methodName == "openingDutyLeaveIsIncludedInCourseTotals") {
                openingCourseId = UUID.randomUUID()
                repo.addCourse(semesterId, CourseSetup(openingCourseId, CourseInput("Databases", CourseType.THEORY),
                    OpeningBalance(openingCourseId, today, 10, 8, 1)))
            }
            if (testName.methodName == "browsedSemesterBannerPersistsAcrossTabsAndExitsInOneTap") {
                repo.setSemesterCompleted(semesterId, true)
                repo.createSemester(SemesterSetup("Current semester", today, ZoneId.systemDefault().id,
                    listOf(CourseSetup(input = CourseInput("Databases", CourseType.THEORY))), WeekTemplate()))
            }
        }
        override fun after() { app.container.database.close() }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(testName).around(fixture).around(compose)

    @Test fun todayKeepsPeriodsVisibleAndEditableAfterBulkLogging(): Unit = runBlocking {
        compose.waitUntil(15_000) { exists("mark_all") }
        compose.onNodeWithText("Mark all present").assertIsDisplayed()
        for (label in listOf("Today", "Courses", "Calendar", "Settings")) {
            compose.onNode(hasTestTag("tab_${label.lowercase()}") and hasText(label)).assertIsDisplayed()
        }
        compose.onNodeWithTag("group_leave").assertDoesNotExist()
        capture("today-ready")

        val lab = repo.observeDay(semesterId, today).first().groups.single { it.grouped }
        val first = lab.cells.first().session!!
        compose.onNodeWithTag("group_periods_${first.id}").performScrollTo().performClick()
        compose.onNodeWithTag("period_${first.id}_absent").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Mark remaining present").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Undo").assertDoesNotExist()
        compose.onNodeWithTag("mark_all").performScrollTo().performClick()
        compose.waitUntil(10_000) { exists("day_complete") }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Marked 3 periods present").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("All 4 marked · 3 present · 1 absent").assertIsDisplayed()
        compose.onNodeWithTag("mark_all").assertDoesNotExist()
        compose.onNodeWithText("Networks Lab").assertExists()
        compose.onNodeWithTag("period_${first.id}_absent").assertIsSelected()
        val expandLab = compose.onNodeWithTag("group_periods_${first.id}")
        expandLab.performScrollTo().performClick()
        compose.onNodeWithContentDescription("P3, Absent").assertExists()
        compose.onNodeWithContentDescription("P4, Present").assertExists()
        val completed = repo.observeDay(semesterId, today).first()
        assertEquals(4, completed.recorded)
        assertEquals(AttendanceOutcome.ABSENT, completed.groups.single { it.grouped }.cells.first().session!!.outcome)
        capture("today-complete")

        expandLab.performScrollTo().performClick()
        compose.onNodeWithTag("period_${first.id}_present").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("period_${first.id}_present") and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Marked 3 periods present").assertExists()
        compose.onNodeWithTag("period_actions_${first.id}").performScrollTo().performClick()
        capture("period-actions-sheet", compose.onNode(isDialog()))
        compose.onNodeWithText("Clear mark").performClick()
        compose.waitUntil(10_000) { exists("mark_all") }
        compose.onNodeWithText("All 4 marked · 3 present · 1 absent").assertDoesNotExist()
    }

    @Test fun yesterdayCanBeMarkedPresentInTwoActions(): Unit = runBlocking {
        val yesterday = today.minusDays(1)
        compose.waitUntil(15_000) { exists("previous_day") }
        compose.onNodeWithTag("previous_day").performClick()
        waitForDay(yesterday)
        compose.onNode(hasTestTag("mark_all") and hasText("Mark all present"))
            .performScrollTo().performClick()
        compose.waitUntil(10_000) { exists("day_complete") }
        val completed = repo.observeDay(semesterId, yesterday).first()
        assertEquals(4, completed.recorded)
        assertTrue(completed.groups.flatMap { it.cells }.mapNotNull { it.session }
            .all { it.outcome == AttendanceOutcome.PRESENT })
    }

    @Test fun twoOfThreeLabPeriodsCanBeMarkedInThreeActionsWithoutADialog(): Unit = runBlocking {
        compose.waitUntil(15_000) { exists("group_present") }
        val first = repo.observeDay(semesterId, today).first().groups.single { it.grouped }
            .cells.first().session!!
        compose.onNodeWithTag("group_periods_${first.id}").performScrollTo().performClick()
        compose.onNodeWithTag("period_${first.id}_absent").performScrollTo().performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Remaining periods").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("group_present").performScrollTo().performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.waitUntil(10_000) { !exists("group_present") }
        val outcomes = repo.observeDay(semesterId, today).first().groups.single { it.grouped }
            .cells.map { it.session?.outcome }
        assertEquals(listOf(AttendanceOutcome.ABSENT, AttendanceOutcome.PRESENT, AttendanceOutcome.PRESENT), outcomes)
    }

    @Test fun dutyLeaveCanBeMarkedDirectlyAndCorrectedFromCalendar(): Unit = runBlocking {
        compose.waitUntil(15_000) { exists("day_title") }
        val theory = repo.observeDay(semesterId, today).first().groups.flatMap { it.cells }
            .mapNotNull { it.session }.single { it.courseId != labId }
        val periodActions = "period_actions_${theory.id}"
        compose.onNodeWithTag(periodActions).performScrollTo().performClick()
        compose.onNodeWithTag("action_duty_leave").performClick()
        compose.waitUntil(10_000) { exists("day_complete") }
        assertEquals(1L, repo.observeCourseStats(semesterId, today).first()
            .single { it.course.id == theory.courseId.toString() }.summary.dutyLeave)

        compose.onNodeWithTag("tab_calendar").performClick()
        val cell = "day_cell_$today"
        compose.waitUntil(10_000) { exists(cell) }
        compose.onNodeWithTag(cell).performClick()
        compose.waitUntil(10_000) { exists("calendar_attendance_date") }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Duty leave").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("calendar_open_full_day").performClick()
        waitForDay(today)
        compose.onNodeWithTag(periodActions).performScrollTo().performClick()
        compose.onNodeWithTag("action_absent").performClick()
        val markedAbsent = hasTestTag("period_${theory.id}_absent") and isSelected()
        compose.waitUntil(10_000) { compose.onAllNodes(markedAbsent).fetchSemanticsNodes().isNotEmpty() }
        val corrected = repo.observeCourseStats(semesterId, today).first()
            .single { it.course.id == theory.courseId.toString() }.summary
        assertEquals(1L, corrected.conducted)
        assertEquals(0L, corrected.credited)
    }

    @Test fun extraClassUsesCourseAndTimePickersAndDefaultsToExcluded(): Unit = runBlocking {
        compose.waitUntil(15_000) { exists("day_menu") }
        compose.onNodeWithTag("day_menu").performClick()
        capture("day-actions-menu", compose.onNode(isPopup()))
        compose.onNodeWithText("Add an extra class…").performClick()
        val counting = compose.onNodeWithTag("extra_counting")
        counting.assertIsOff()
        capture("extra-class-form", compose.onNode(isDialog()))
        compose.onNodeWithTag("extra_course").performClick()
        compose.onNode(hasText("Networks Lab") and hasAnyAncestor(isPopup())).performClick()
        for (field in listOf("extra_start", "extra_end")) {
            compose.onNodeWithTag(field).performScrollTo().performClick()
            if (field == "extra_start") capture("extra-time-picker", compose.onNode(isDialog()))
            compose.onNodeWithTag("extra_set_time").performClick()
        }
        counting.performScrollTo().performClick().assertIsOn()
        counting.performClick().assertIsOff()
        compose.onNodeWithTag("extra_add").assertIsEnabled().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Doesn't count").fetchSemanticsNodes().isNotEmpty()
        }
        val extra = repo.observeDay(semesterId, today).first().extras.single()
        assertEquals(labId, extra.courseId)
        assertEquals(17 * 60, extra.startMinute)
        assertEquals(18 * 60, extra.endMinute)
        assertFalse(extra.counting)
    }

    @Test fun futureDayCanBeMarkedAsAHolidayWhileOtherDayActionsAreUnavailable() {
        compose.waitUntil(15_000) { exists("next_day") }
        val before = runBlocking { repo.observeCourseStats(semesterId, today).first() }
        val future = today.plusDays(1)
        compose.onNodeWithTag("next_day").performClick()
        waitForDay(future)
        compose.onNodeWithText("Attendance opens on this date.").assertIsDisplayed()
        compose.onNodeWithTag("day_menu").performClick()
        compose.onNodeWithText("Use another day's timetable…").assertIsNotEnabled()
        compose.onNodeWithText("Add an extra class…").assertIsNotEnabled()
        compose.onNodeWithText("Duty leave…").assertIsNotEnabled()
        compose.onNodeWithText("Mark holiday").performClick()
        compose.onNodeWithTag("confirm_action").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Holiday").fetchSemanticsNodes().isNotEmpty()
        }
        runBlocking {
            assertTrue(repo.observeDay(semesterId, future).first().holiday)
            assertEquals(before, repo.observeCourseStats(semesterId, today).first())
        }
    }

    @Test fun backlogOpensOldestDayAndListsAllDates(): Unit = runBlocking {
        val dates = repo.observeIncompleteDates(semesterId, today).first()
        assertEquals(oldest, dates.first())
        assertTrue(dates.size > 12)
        compose.waitUntil(15_000) { exists("catch_up") }
        compose.onNodeWithTag("catch_up").performClick()
        waitForDay(oldest)
        compose.onNodeWithTag("mark_all").performScrollTo().performClick()
        compose.waitUntil(10_000) { exists("next_unmarked_day") }
        compose.onNodeWithTag("next_unmarked_day").performScrollTo().performClick()
        waitForDay(dates[1])
        compose.onNodeWithTag("return_today").assert(hasText("Back to today")).performClick()
        waitForDay(today)
        compose.onNodeWithTag("next_day").performClick()
        waitForDay(today.plusDays(1))
        compose.onNodeWithTag("day_title").performClick()
        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithTag("tab_courses").performClick()
        val courseRow = "course_row_$labId"
        compose.waitUntil(10_000) { exists("courses_list") }
        compose.onNodeWithTag("courses_list").performScrollToNode(hasTestTag(courseRow))
        compose.onNodeWithTag(courseRow).assert(hasText("unmarked", substring = true)).performClick()
        val latestCourseDate = "course_unmarked_${dates.last()}"
        compose.waitUntil(10_000) { exists("course_detail") }
        compose.onNodeWithTag("course_detail").performScrollToNode(hasTestTag(latestCourseDate))
        compose.onNodeWithTag(latestCourseDate).performClick()
        waitForDay(dates.last())
        compose.onNodeWithTag("tab_courses").assertIsSelected()
        compose.onNodeWithTag("tab_courses").performClick()
        compose.waitUntil(10_000) { exists("courses_list") && !exists("day_title") }

        compose.onNodeWithTag("tab_calendar").performClick()
        val oldestRow = hasText(dates[1].weekdayAndDate()) and hasText("4 periods not marked")
        val latestRow = hasText(dates.last().weekdayAndDate()) and hasText("4 periods not marked")
        val history = compose.onNodeWithTag("calendar_list")
        compose.waitUntil(10_000) { exists("day_cell_$today") }
        history.performScrollToNode(latestRow)
        compose.onNode(latestRow).assertIsDisplayed()
        history.performScrollToNode(oldestRow)
        compose.onNode(oldestRow).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { exists("calendar_open_full_day") }
        compose.onNodeWithTag("calendar_open_full_day").performClick()
        waitForDay(dates[1])
        compose.onNodeWithTag("previous_day").performClick()
        waitForDay(oldest)
        compose.onNodeWithTag("next_day").performClick()
        waitForDay(dates[1])
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(10_000) { exists("calendar_list") && !exists("day_title") }
    }

    @Test fun calendarAttendanceStaysInSheetAndKeepsExistingMarks(): Unit = runBlocking {
        val yesterday = today.minusDays(1)
        val period = repo.observeDay(semesterId, yesterday).first().groups.single { it.grouped }
            .cells.first().session!!
        compose.waitUntil(15_000) { exists("tab_calendar") }
        compose.onNodeWithTag("tab_calendar").performClick()
        if (yesterday.month != today.month) compose.onNodeWithTag("month_prev").performClick()
        compose.onNodeWithTag("day_cell_$yesterday").performClick()
        compose.waitUntil(10_000) { exists("calendar_mark_all") }
        compose.onNodeWithTag("calendar_attendance_date").assertTextEquals(yesterday.long())
        compose.onNodeWithTag("day_title").assertDoesNotExist()
        compose.onNodeWithTag("calendar_period_${period.id}_absent").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("calendar_mark_all") and hasText("Mark remaining present"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("calendar_mark_all").performClick()
        compose.waitUntil(10_000) { !exists("calendar_mark_all") }
        compose.onNodeWithTag("calendar_period_${period.id}_absent").assertIsSelected()
        assertEquals(4, repo.observeDay(semesterId, yesterday).first().recorded)
        compose.onNodeWithTag("calendar_period_${period.id}_present").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("calendar_period_${period.id}_present") and isSelected())
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("calendar_attendance_close").performClick()
        compose.waitUntil(10_000) { !exists("calendar_attendance_date") }
        compose.onNodeWithTag("day_cell_$yesterday").assertIsDisplayed()
        assertEquals(AttendanceOutcome.PRESENT, repo.observeDay(semesterId, yesterday).first()
            .groups.single { it.grouped }.cells.first().session!!.outcome)
    }

    @Test fun calendarStartsAtTheSemesterAndTodayResetsThePager() {
        compose.waitUntil(15_000) { exists("day_title") }
        compose.onNodeWithTag("previous_day").performClick()
        waitForDay(today.minusDays(1))
        compose.onNodeWithText("Before the semester started").assertIsDisplayed()
        compose.onNodeWithTag("tab_today").performClick()
        waitForDay(today)

        compose.onNodeWithTag("tab_calendar").performClick()
        compose.onNodeWithTag("month_prev").assertIsNotEnabled()
        if (today.dayOfMonth > 1) {
            compose.onNodeWithTag("day_cell_${today.minusDays(1)}").assertIsNotEnabled()
        }
        compose.onNodeWithTag("month_next").performClick()
        compose.onNodeWithTag("calendar_today").assertIsDisplayed().assert(hasText("Back to today")).performClick()
        compose.onNodeWithTag("day_cell_$today").assertIsDisplayed()
        compose.onNodeWithTag("month_prev").assertIsNotEnabled()
    }

    @Test fun completedSemesterCanBeBrowsedAndReopenedFromSettings() = runBlocking {
        compose.waitUntil(15_000) { exists("tab_settings") }
        compose.onNodeWithTag("tab_settings").performClick()
        openSemester()
        val lifecycle = "semester_lifecycle_$semesterId"
        compose.waitUntil(10_000) { exists(lifecycle) }
        compose.onNodeWithTag(lifecycle).performScrollTo().performClick()
        compose.onNodeWithTag("confirm_action").performClick()
        withTimeout(10_000) { repo.observeAppState().first { it.activeSemesterId == null } }
        compose.onNodeWithTag("tab_today").performClick()
        compose.onNodeWithText("No current semester.", substring = true).assertExists()
        compose.onNodeWithTag("tab_settings").performClick()
        openSemester()
        compose.waitUntil(10_000) { exists("browse_$semesterId") }
        compose.onNodeWithTag("browse_$semesterId").performScrollTo().performClick()
        compose.waitUntil(10_000) { exists("day_title") }
        compose.onNodeWithText("V1 UI fixture · Finished").assertExists()
        compose.onNodeWithTag("tab_settings").performClick()
        openSemester()
        compose.waitUntil(10_000) { exists(lifecycle) }
        compose.onNodeWithTag(lifecycle).performScrollTo().performClick()
        withTimeout(10_000) { repo.observeAppState().first { it.activeSemesterId == semesterId } }
        compose.onNodeWithTag("tab_today").performClick()
        compose.waitUntil(10_000) { exists("day_title") }
    }

    @Test fun semesterSettingsCanRenameAndSearchTheFullTimezoneList(): Unit = runBlocking {
        compose.waitUntil(15_000) { exists("tab_settings") }
        compose.onNodeWithTag("tab_settings").performClick()
        openSemester()
        compose.onNodeWithText("View records").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Edit semester").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Start date").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Semester name")).performTextReplacement("Renamed semester")
        compose.onNodeWithText("College timezone").performScrollTo().performClick()
        compose.onNodeWithText("Search timezones").assertIsDisplayed()
        capture("college-timezone")
        compose.onNodeWithContentDescription("Back", substring = false).performClick()
        compose.onNodeWithText("Edit semester").assertIsDisplayed()
        compose.onNodeWithText("College timezone").performScrollTo().performClick()
        compose.onNodeWithText("Search timezones").performTextInput("Chatham")
        compose.onNodeWithText("Pacific/Chatham").performClick()
        compose.onNodeWithText("Save", substring = false).performClick()
        val updated = withTimeout(10_000) {
            repo.observeAppState().first { it.activeSemester?.entity?.name == "Renamed semester" }
        }
        assertEquals("Pacific/Chatham", updated.activeSemester?.entity?.collegeZone)
        assertEquals(today, updated.activeSemester?.semester?.startDate)
    }

    @Test fun browsedSemesterBannerPersistsAcrossTabsAndExitsInOneTap() {
        compose.waitUntil(15_000) { exists("tab_settings") }
        val banner = "exit_browsed_semester"
        compose.onNodeWithTag(banner).assertDoesNotExist()
        compose.onNodeWithTag("semester_switcher").performClick()
        compose.onNodeWithTag("switch_semester_$semesterId").performClick()
        compose.waitUntil(10_000) { exists(banner) }
        for (tab in listOf("today", "courses", "calendar", "settings")) {
            compose.onNodeWithTag("tab_$tab").performClick()
            compose.onNodeWithTag(banner).assertIsDisplayed()
            compose.onNodeWithText("Viewing V1 UI fixture · Back to Current semester").assertIsDisplayed()
        }
        compose.onNodeWithTag(banner).performClick()
        compose.waitUntil(10_000) { !exists(banner) && exists("day_title") }
        compose.onNodeWithText("Current semester").assertIsDisplayed()
    }

    @Test fun bulkDutyLeaveFromDayConfirmsReplacement(): Unit = runBlocking {
        compose.waitUntil(15_000) { exists("mark_all") }
        compose.onNodeWithTag("mark_all").performScrollTo().performClick()
        compose.waitUntil(10_000) { exists("day_complete") }
        compose.onNodeWithTag("day_menu").performClick()
        compose.onNodeWithText("Duty leave…").performClick()
        compose.onNodeWithTag("dl_confirm").assertIsNotEnabled()
        compose.onNodeWithText("Select all periods").performClick()
        compose.onNodeWithTag("dl_confirm").performClick()
        compose.waitUntil(10_000) { exists("confirm_action") }
        compose.onNodeWithText("Replace recorded attendance?").assertExists()
        assertTrue(repo.observeCourseStats(semesterId, today).first().all { it.summary.dutyLeave == 0L })
        compose.onNodeWithTag("confirm_action").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("4 on duty leave", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(4L, repo.observeCourseStats(semesterId, today).first().sumOf { it.summary.dutyLeave })
    }

    @Test fun coursesShowDutyLeaveTotalsAndSettingsSharesTheExplainer() {
        compose.waitUntil(15_000) { exists("tab_courses") }
        compose.onNodeWithTag("tab_courses").performClick()
        val courseRow = "course_row_$labId"
        compose.waitUntil(10_000) { exists("courses_list") }
        compose.onNodeWithTag("courses_list").performScrollToNode(hasTestTag(courseRow))
        compose.onNodeWithTag(courseRow).assert(hasText(percentOf(3, 3))).performClick()
        compose.waitUntil(10_000) { exists("course_detail") }
        compose.onNodeWithText("Duty leave").assertExists()
        compose.onNodeWithText("Counted as attended: 3 of 3").assertExists()
        compose.onNodeWithTag("tab_courses").performClick()
        compose.waitUntil(10_000) { exists("courses_list") }

        compose.onNodeWithTag("attendance_rules").performClick()
        val units = "“Can miss” is measured in periods."
        compose.onNodeWithText(units, substring = true).assertIsDisplayed()
        capture("attendance-rules-sheet", compose.onNode(isDialog()))
        compose.onNodeWithText("Done").performClick()

        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("open_about").performScrollTo().performClick()
        compose.onNodeWithText("How attendance is calculated").performClick()
        compose.onNodeWithText(units, substring = true).assertIsDisplayed()
    }

    @Test fun settingsCanUndoARetainedAttendanceChange() {
        compose.waitUntil(15_000) { exists("tab_settings") }
        val session = runBlocking {
            repo.observeDay(semesterId, today).first().groups.flatMap { it.cells }
                .mapNotNull { it.session }.first { !it.recorded }
        }
        val change = runBlocking {
            repo.recordPeriod(session.id, AttendanceOutcome.PRESENT)
            repo.observeRecentChanges().first().first()
        }

        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("recent_changes").performScrollTo().performClick()
        val undoTag = "undo_change_${change.batchId}"
        compose.waitUntil(10_000) { exists(undoTag) }
        compose.onNodeWithTag(undoTag).performScrollTo().performClick()
        compose.waitUntil(10_000) { !exists(undoTag) }
        assertEquals(null, runBlocking { app.container.database.journalDao().outcome(session.id.toString()) })
    }

    @Test fun openingDutyLeaveIsIncludedInCourseTotals() {
        compose.waitUntil(15_000) { exists("tab_courses") }
        compose.onNodeWithTag("tab_courses").performClick()
        val courseRow = "course_row_$openingCourseId"
        compose.waitUntil(10_000) { exists("courses_list") }
        compose.onNodeWithTag("courses_list").performScrollToNode(hasTestTag(courseRow))
        compose.onNodeWithTag(courseRow).performClick()
        compose.waitUntil(10_000) { exists("course_detail") }
        compose.onNodeWithText("Counted as attended: 9 of 10").assertExists()
        compose.onNodeWithText("Duty leave").assertExists()
    }

    @Test fun stoppedCoursesCanBeResumedFromTheCollapsedSection() {
        compose.waitUntil(15_000) { exists("tab_courses") }
        compose.onNodeWithTag("tab_courses").performClick()
        val courseRow = "course_row_$labId"
        compose.waitUntil(10_000) { exists(courseRow) }
        compose.onNodeWithTag(courseRow).performClick()
        compose.onNodeWithTag("course_detail").performScrollToNode(hasTestTag("course_stop"))
        compose.onNodeWithTag("course_stop").performClick()
        compose.onNodeWithText("You can resume tracking", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("confirm_action").performClick()

        compose.onNodeWithTag("tab_courses").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("No longer tracked").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(courseRow).assertDoesNotExist()
        compose.onNodeWithTag("courses_list").performScrollToNode(hasText("No longer tracked"))
        compose.onNodeWithText("No longer tracked").performClick()
        compose.onNodeWithTag("courses_list").performScrollToNode(hasTestTag(courseRow))
        compose.onNodeWithTag(courseRow).assertIsDisplayed()
        compose.onNodeWithTag(courseRow).performClick()
        compose.onNodeWithTag("course_detail").performScrollToNode(hasTestTag("course_resume"))
        compose.onNodeWithTag("course_resume").performClick()
        compose.waitUntil(10_000) {
            runBlocking { repo.observeCourses(semesterId).first().any { it.id == labId.toString() && !it.retired } }
        }
        compose.waitUntil(10_000) { exists("course_stop") }
        compose.onNodeWithTag("course_detail").performScrollToNode(hasTestTag("course_stop"))
        compose.onNodeWithTag("course_stop").assertIsDisplayed()
    }

    @Test fun timetablePromptsBeforeLeavingAnEditedDraft() {
        compose.waitUntil(15_000) { exists("tab_courses") }
        compose.onNodeWithTag("tab_courses").performClick()
        compose.waitUntil(10_000) { exists("open_timetable") }
        compose.onNodeWithTag("open_timetable").performClick()
        compose.waitUntil(10_000) { exists("timetable_edit") }
        compose.onNodeWithTag("timetable_edit").performClick()
        val weekday = today.dayOfWeek.takeIf { it.value <= 5 } ?: DayOfWeek.MONDAY
        compose.onNodeWithTag("weekday_${weekday.value}").assertIsSelected()
        capture("timetable-editor")
        val courseChip = compose.onNode(hasTestTag("timetable_course_chip") and hasText("Networks Lab"))
        courseChip.performScrollTo().performClick().assertIsSelected()
        val firstPeriod = compose.onAllNodesWithTag("slot_cell").onFirst()
        firstPeriod.performScrollTo().performClick()
        firstPeriod.assert(hasText("Networks Lab"))
        firstPeriod.performClick()
        firstPeriod.assert(hasText("Free"))

        val destination = if (weekday == DayOfWeek.FRIDAY) DayOfWeek.MONDAY else DayOfWeek.FRIDAY
        compose.onNodeWithTag("weekday_${destination.value}").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Copy from…").performScrollTo().performClick()
        compose.onNode(hasText(weekday.fullName()) and hasAnyAncestor(isPopup())).performClick()
        firstPeriod.assert(hasText("Free"))
        compose.onNodeWithContentDescription("Choose course for period 1").performScrollTo().performClick()
        compose.onNode(hasText("Networks Lab") and hasAnyAncestor(isPopup())).performClick()
        firstPeriod.assert(hasText("Networks Lab"))

        compose.onNodeWithTag("timetable_cancel").performScrollTo().performClick()
        compose.onNodeWithText("Discard timetable changes?").assertIsDisplayed()
        capture("timetable-discard-dialog", compose.onNode(isDialog()))
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("timetable_save").assertIsDisplayed()

        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithText("Discard").performClick()
        compose.onNodeWithText("Theme").performScrollTo().assertIsDisplayed()
    }

    private fun openSemester() {
        val tag = "open_semester_$semesterId"
        val alreadyOpen = "browse_$semesterId"
        compose.waitUntil(10_000) { exists(tag) || exists(alreadyOpen) }
        if (exists(tag)) compose.onNodeWithTag(tag).performScrollTo().performClick()
    }

    private fun waitForDay(date: LocalDate) {
        val heading = hasTestTag("day_title") and hasContentDescription(date.long())
        compose.waitUntil(10_000) { compose.onAllNodes(heading).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String, node: SemanticsNodeInteraction = compose.onRoot()) {
        val folder = File(System.getProperty("ninety.screenshots"), "v1").apply { mkdirs() }
        node.captureToImage().asAndroidBitmap().let { bitmap ->
            File(folder, "$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
}
