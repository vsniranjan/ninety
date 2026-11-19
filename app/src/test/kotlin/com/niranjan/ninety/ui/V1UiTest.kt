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
import org.robolectric.RuntimeEnvironment
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
    private lateinit var eventId: UUID
    private lateinit var openingCourseId: UUID
    private val today = LocalDate.now()
    private val oldest = today.minusDays(28).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }

    private val fixture = object : ExternalResource() {
        override fun before() = runBlocking {
            if (testName.methodName.startsWith("twoHundredPercent")) RuntimeEnvironment.setFontScale(2f)
            val theory = CourseSetup(input = CourseInput("Operating Systems", CourseType.THEORY))
            val lab = CourseSetup(input = CourseInput("Networks Lab", CourseType.LAB))
            labId = lab.courseId
            val start = when (testName.methodName) {
                "backlogOpensOldestDayAndListsAllDates" -> oldest
                "openingLeaveCanBeResolvedFromDutyLeave" -> today.minusDays(1)
                else -> today
            }
            semesterId = repo.createSemester(SemesterSetup("V1 UI fixture", start, ZoneId.systemDefault().id,
                listOf(theory, lab), WeekTemplate(DayOfWeek.entries.take(5).associateWith {
                    mapOf(1 to theory.courseId, 3 to labId, 4 to labId, 5 to labId)
                })))
            repo.ensureMaterialized(semesterId)
            // Use a copied weekday when a developer runs the tests on a weekend.
            if (today.dayOfWeek.value > 5) repo.copyWeekday(semesterId, today, DayOfWeek.MONDAY)
            val leaveCourse = if (testName.methodName.startsWith("twoHundredPercent")) theory.courseId else labId
            val ids = repo.observeDay(semesterId, today).first().groups.flatMap { it.cells }
                .mapNotNull { it.session }.filter { it.courseId == leaveCourse }.map { it.id }
            if (testName.methodName != "todayKeepsPeriodsVisibleAndEditableAfterBulkLogging") {
                eventId = repo.applyDutyLeave(semesterId, ids, "Campus event", false).eventId
            }
            if (testName.methodName == "openingLeaveCanBeResolvedFromDutyLeave") {
                openingCourseId = UUID.randomUUID()
                repo.addCourse(semesterId, CourseSetup(openingCourseId, CourseInput("Databases", CourseType.THEORY),
                    OpeningBalance(openingCourseId, today, 10, 8, 0, 1)))
            }
            if (testName.methodName == "browsedSemesterBannerPersistsAcrossTabsAndExitsInOneTap") {
                repo.setSemesterCompleted(semesterId, true)
                repo.createSemester(SemesterSetup("Current semester", today, ZoneId.systemDefault().id,
                    listOf(CourseSetup(input = CourseInput("Databases", CourseType.THEORY))), WeekTemplate()))
            }
        }
        override fun after() { app.container.database.close(); RuntimeEnvironment.setFontScale(1f) }
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
        compose.onNodeWithContentDescription("P3, Absent").assertExists()
        compose.onNodeWithContentDescription("P4, Present").assertExists()
        val completed = repo.observeDay(semesterId, today).first()
        assertEquals(4, completed.recorded)
        assertEquals(AttendanceOutcome.ABSENT, completed.groups.single { it.grouped }.cells.first().session!!.outcome)
        capture("today-complete")

        compose.onNodeWithTag("period_${first.id}_present").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("period_${first.id}_present") and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Marked 3 periods present").assertExists()
        compose.onNodeWithTag("period_actions_${first.id}").performScrollTo().performClick()
        compose.onNodeWithText("Clear mark").performClick()
        compose.waitUntil(10_000) { exists("mark_all") }
        compose.onNodeWithText("All 4 marked · 3 present · 1 absent").assertDoesNotExist()
    }

    @Test fun extraClassUsesCourseAndTimePickersAndDefaultsToExcluded(): Unit = runBlocking {
        compose.waitUntil(15_000) { exists("day_menu") }
        compose.onNodeWithTag("day_menu").performClick()
        compose.onNodeWithText("Add an extra class…").performClick()
        val helper = compose.onNodeWithText("Doesn’t count toward attendance")
        helper.assertExists()
        compose.onNodeWithTag("extra_course").performClick()
        compose.onNode(hasText("Networks Lab") and hasAnyAncestor(isPopup())).performClick()
        for (field in listOf("extra_start", "extra_end")) {
            compose.onNodeWithTag(field).performScrollTo().performClick()
            compose.onNodeWithTag("extra_set_time").performClick()
        }
        compose.onNodeWithTag("extra_counting").performScrollTo().performClick()
        helper.assertDoesNotExist()
        compose.onNodeWithTag("extra_counting").performClick()
        helper.assertExists()
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
        compose.onNodeWithTag("return_today").performClick()
        waitForDay(today)
        compose.onNodeWithTag("next_day").performClick()
        waitForDay(today.plusDays(1))
        compose.onNodeWithTag("day_title").performClick()
        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithTag("tab_courses").performClick()
        val courseRow = "course_row_$labId"
        compose.waitUntil(10_000) { exists("courses_list") }
        compose.onNodeWithText("Some earlier days aren’t marked.", substring = true).assertExists()
        compose.onNodeWithTag("courses_list").performScrollToNode(hasTestTag(courseRow))
        compose.onNodeWithTag(courseRow).performClick()
        val latestCourseDate = "course_unmarked_${dates.last()}"
        compose.waitUntil(10_000) { exists("course_detail") }
        compose.onNodeWithTag("course_detail").performScrollToNode(hasTestTag(latestCourseDate))
        compose.onNodeWithTag(latestCourseDate).performClick()
        waitForDay(dates.last())
        compose.onNodeWithTag("tab_courses").assertIsSelected()
        compose.onNodeWithTag("tab_courses").performClick()
        compose.waitUntil(10_000) { exists("courses_list") && !exists("day_title") }

        compose.onNodeWithTag("tab_calendar").performClick()
        val oldestRow = hasText("${dates[1].weekdayAndDate()} · 4 of 4 not marked")
        val latestRow = hasText("${dates.last().weekdayAndDate()} · 4 of 4 not marked")
        val history = compose.onNodeWithTag("calendar_list")
        compose.waitUntil(10_000) { exists("day_cell_$today") }
        history.performScrollToNode(latestRow)
        compose.onNode(latestRow).assertIsDisplayed()
        history.performScrollToNode(oldestRow)
        compose.onNode(oldestRow).performSemanticsAction(SemanticsActions.OnClick)
        waitForDay(dates[1])
        compose.onNodeWithTag("previous_day").performClick()
        waitForDay(oldest)
        compose.onNodeWithTag("next_day").performClick()
        waitForDay(dates[1])
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(10_000) { exists("calendar_list") && !exists("day_title") }
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
        compose.onNodeWithTag("calendar_today").assertIsDisplayed().performClick()
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

    @Test fun partialLeaveApprovalAndOpeningAnotherEventRefreshesItsPeriods(): Unit = runBlocking {
        compose.waitUntil(15_000) { exists("tab_courses") }
        compose.onNodeWithTag("tab_courses").performClick()
        openDutyLeave()
        compose.waitUntil(10_000) { exists("dl_expand_$eventId") }
        compose.onNodeWithTag("dl_expand_$eventId").performScrollTo().performClick()
        val sessions = withTimeout(10_000) { repo.observeDlEventSessions(eventId).first() }
        compose.onNodeWithTag("dl_status_${sessions.first().id}").performScrollTo().performClick()
        compose.onNodeWithText("Approved", substring = false).performClick()
        withTimeout(10_000) { repo.observeDlEvents(semesterId).first { it.single().approved == 1 } }
        compose.onNodeWithText("Mixed approval").assertExists()
        // A different event must replace the active subscription, rather than stay on the first event.
        val theory = repo.observeDay(semesterId, today).first().groups.flatMap { it.cells }
            .mapNotNull { it.session }.single { it.courseId != labId }
        val second = repo.applyDutyLeave(semesterId, listOf(theory.id), "Second event", false).eventId
        compose.waitUntil(10_000) { exists("dl_expand_$second") }
        compose.onNodeWithTag("dl_expand_$second").performScrollTo().performClick()
        compose.waitUntil(10_000) { exists("dl_status_${theory.id}") }
        compose.onNodeWithTag("dl_status_${theory.id}").performScrollTo().assertIsDisplayed()

        val periodDay = "dl_open_day_${theory.id}"
        compose.onNodeWithTag(periodDay).performScrollTo().performClick()
        waitForDay(today)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(10_000) { exists("duty_leave_list") }
        val remove = "dl_remove_$second"
        compose.onNodeWithTag("duty_leave_list").performScrollToNode(hasTestTag(remove))
        compose.onNodeWithTag(remove).performClick()
        compose.onNodeWithTag("confirm_action").performClick()
        withTimeout(10_000) { repo.observeDlEvents(semesterId).first { it.first { event -> event.id == second }.periods == 0 } }
        compose.waitUntil(10_000) { !exists("dl_expand_$second") &&
            compose.onAllNodesWithText("Duty leave request removed").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(10_000) {
            exists("dl_expand_$second") || compose.onAllNodesWithText("That change can no longer be undone.").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, repo.observeDlEvents(semesterId).first().first { it.id == second }.pending)
    }

    @Test fun twoHundredPercentTextKeepsIconActionsReachableInBothThemes() {
        compose.waitUntil(15_000) { exists("tab_today") }
        assertEquals(2f, compose.activity.resources.configuration.fontScale, 0.01f)
        for (label in listOf("Today", "Courses", "Calendar", "Settings")) {
            compose.onNode(hasTestTag("tab_${label.lowercase()}") and hasText(label)).assertIsDisplayed()
        }
        compose.waitUntil(15_000) { exists("group_present") }
        compose.onNodeWithTag("group_present").performScrollTo()
        capture("before-theme-today-200")
        compose.onNodeWithTag("group_present").assertIsDisplayed()
        val firstLabPeriod = runBlocking {
            repo.observeDay(semesterId, today).first().groups.single { it.grouped }.cells.first().session!!
        }
        val expandLab = compose.onNodeWithTag("group_periods_${firstLabPeriod.id}")
        expandLab.performScrollTo().performClick()
        val markPresent = "Mark present · Networks Lab · P3"
        compose.onNodeWithContentDescription(markPresent).performScrollTo().assertIsDisplayed()
        val actions = compose.onNodeWithTag("period_${firstLabPeriod.id}").fetchSemanticsNode()
            .config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertEquals(listOf(markPresent, "Mark absent · Networks Lab · P3"), actions.map { it.label })
        capture("today-expanded-200")
        expandLab.performScrollTo().performClick()
        for ((theme, name) in listOf("Light" to "light", "Dark" to "dark")) {
            compose.onNodeWithTag("tab_settings").performClick()
            compose.onNodeWithText(theme, substring = false).performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasText(theme) and isSelected()).fetchSemanticsNodes().isNotEmpty()
            }
            capture("$name-settings-200")
            compose.onNodeWithText("System").assertIsDisplayed()
            compose.onNodeWithTag("tab_today").performClick()
            compose.waitUntil(10_000) { exists("group_present") }
            compose.onNodeWithTag("group_present").performScrollTo()
            capture("$name-today-200")
            compose.onNodeWithTag("group_present").assertIsDisplayed()
            compose.onNodeWithTag("tab_courses").performClick()
            compose.waitUntil(10_000) { exists("courses_list") || exists("duty_leave_list") }
            if (exists("duty_leave_list")) compose.onNodeWithTag("tab_courses").performClick()
            val comparison = hasText("0.0 % if leave is rejected")
            compose.waitUntil(10_000) { exists("courses_list") }
            compose.onNodeWithTag("courses_list").performScrollToNode(comparison)
            compose.onNode(comparison).assertIsDisplayed()
            compose.onNodeWithText("counting 1 pending duty-leave period", substring = true).assertExists()
            capture("$name-courses-200")
            openDutyLeave()
            compose.waitUntil(10_000) { exists("dl_totals") }
            compose.onNodeWithTag("duty_leave_list").performScrollToNode(hasTestTag("dl_expand_$eventId"))
            capture("$name-leave-200")
            compose.onNodeWithTag("tab_calendar").performClick()
            compose.waitUntil(10_000) { exists("day_cell_$today") }
            compose.onNodeWithText("Different timetable").assertIsDisplayed()
            capture("$name-calendar-200")
        }
    }

    @Test fun coursesLinkToPendingLeaveAndSettingsSharesTheExplainer() {
        compose.waitUntil(15_000) { exists("tab_courses") }
        compose.onNodeWithTag("tab_courses").performClick()
        val courseRow = "course_row_$labId"
        compose.waitUntil(10_000) { exists("courses_list") }
        compose.onNodeWithText("1 at the limit", substring = true).assertExists()
        compose.onNodeWithTag("courses_list").performScrollToNode(hasTestTag(courseRow))
        compose.onNodeWithTag(courseRow).assert(hasText("counting 3 pending duty-leave periods", substring = true)).performClick()
        compose.waitUntil(10_000) { exists("course_detail") }
        compose.onNodeWithTag("course_detail").performScrollToNode(hasTestTag("course_duty_leave"))
        compose.onNodeWithTag("course_duty_leave").performClick()
        compose.waitUntil(10_000) { exists("dl_expand_$eventId") }
        compose.onNodeWithText("Networks Lab", substring = false).assertIsDisplayed()
        compose.onNodeWithTag("tab_courses").performClick()
        compose.waitUntil(10_000) { exists("courses_list") }

        compose.onNodeWithTag("attendance_rules").performClick()
        val units = "“Can miss” is measured in periods."
        compose.onNodeWithText(units, substring = true).assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()

        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("open_about").performScrollTo().performClick()
        compose.onNodeWithText("How attendance is calculated").performClick()
        compose.onNodeWithText(units, substring = true).assertIsDisplayed()
    }

    @Test fun openingLeaveCanBeResolvedFromDutyLeave() {
        compose.waitUntil(15_000) { exists("tab_courses") }
        compose.onNodeWithTag("tab_courses").performClick()
        openDutyLeave()
        val approve = hasTestTag("dl_opening_approve_$openingCourseId")
        compose.waitUntil(10_000) { exists("duty_leave_list") }
        compose.onNodeWithTag("duty_leave_list").performScrollToNode(approve)
        compose.onNodeWithText("From before you started tracking").assertExists()
        compose.onNode(approve).performClick()
        compose.waitUntil(10_000) { !exists("dl_opening_approve_$openingCourseId") }
        assertEquals(1L, runBlocking {
            repo.observeCourseStats(semesterId, today).first()
                .single { it.balance.courseId == openingCourseId }.balance.approvedDl
        })
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
        compose.onAllNodesWithTag("slot_cell").onFirst().performScrollTo().performClick()
        compose.onNode(hasText("Free") and hasAnyAncestor(isPopup())).performClick()

        compose.onNodeWithTag("timetable_cancel").performScrollTo().performClick()
        compose.onNodeWithText("Discard timetable changes?").assertIsDisplayed()
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

    private fun openDutyLeave() {
        compose.waitUntil(10_000) { exists("courses_list") }
        compose.onNodeWithTag("courses_list").performScrollToNode(hasTestTag("open_duty_leave"))
        compose.onNodeWithTag("open_duty_leave").performClick()
    }

    private fun waitForDay(date: LocalDate) {
        val heading = hasTestTag("day_title") and hasContentDescription(date.long())
        compose.waitUntil(10_000) { compose.onAllNodes(heading).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun assertOneLine(label: String) {
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onAllNodesWithText(label, substring = false).onFirst()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Missing text layout for $label", layouts.isNotEmpty())
        val result = layouts.first()
        assertEquals("$label must not break inside a word", 1, result.lineCount)
        assertFalse("$label must not be ellipsized", result.isLineEllipsized(0))
        // FlowRow can reuse a paragraph measured with more space than the text
        // occupies. Compare the rendered line, not that paragraph's constraint.
        assertTrue("$label must fit its measured width",
            result.getLineRight(0) - result.getLineLeft(0) <= result.size.width + 1f)
    }

    private fun capture(name: String) {
        val folder = File(System.getProperty("ninety.screenshots"), "v1").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(folder, "$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
}
