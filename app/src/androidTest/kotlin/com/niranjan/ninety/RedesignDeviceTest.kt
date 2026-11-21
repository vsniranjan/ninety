package com.niranjan.ninety

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import com.niranjan.ninety.data.*
import com.niranjan.ninety.domain.AttendanceOutcome
import com.niranjan.ninety.domain.CourseType
import com.niranjan.ninety.domain.OpeningBalance
import com.niranjan.ninety.domain.WeekTemplate
import com.niranjan.ninety.ui.long
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/** Invoke explicitly; never opens, resets or populates the personal database. */
class RedesignTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, RedesignTestApplication::class.java.name, context)
}

class RedesignTestApplication : NinetyApplication() {
    override val container by lazy {
        AppContainer(this, Room.inMemoryDatabaseBuilder(this, NinetyDatabase::class.java)
            .addCallback(NinetyDatabase.integrityCallback).allowMainThreadQueries().build())
    }
}

@RunWith(AndroidJUnit4::class)
class RedesignDeviceTest {
    private val ui = createAndroidComposeRule<MainActivity>()
    private val isolatedOnly = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                // Skip before ActivityScenario can launch the personal Application.
                org.junit.Assume.assumeTrue("Use only the isolated runner",
                    ApplicationProvider.getApplicationContext<Application>() is RedesignTestApplication)
                base.evaluate()
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(isolatedOnly).around(ui)
    private val app get() = ApplicationProvider.getApplicationContext<NinetyApplication>()
    private val repo get() = app.container.repository
    private val today = LocalDate.now()

    @Test fun isolatedWholeAppReview() = runBlocking {
        org.junit.Assume.assumeTrue("Use only the isolated runner", app is RedesignTestApplication)
        ui.activity.runOnUiThread { ui.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val os = CourseSetup(input = CourseInput("Operating Systems", CourseType.THEORY))
        val network = CourseSetup(input = CourseInput("Computer Networks", CourseType.THEORY))
        val lab = CourseSetup(input = CourseInput("Networks Lab", CourseType.LAB))
        val math = CourseSetup(input = CourseInput("Engineering Mathematics and Applied Computational Methods", CourseType.THEORY))
        fun balanced(c: CourseSetup, n: Long, p: Long, leave: Long = 0) =
            c.copy(balance = OpeningBalance(c.courseId, today, n, p, leave))
        repo.createSemester(SemesterSetup("Previous semester", today.minusDays(42),
            ZoneId.systemDefault().id, listOf(CourseSetup(input = os.input)), WeekTemplate()))
        val semester = repo.createSemester(SemesterSetup("Semester 5", today.minusDays(28),
            ZoneId.systemDefault().id, listOf(balanced(os, 40, 38), balanced(network, 36, 32),
                balanced(lab, 18, 15, 2), balanced(math, 30, 27)),
            WeekTemplate(DayOfWeek.entries.take(5).associateWith {
                mapOf(1 to os.courseId, 2 to network.courseId, 3 to lab.courseId,
                    4 to lab.courseId, 5 to lab.courseId, 6 to math.courseId)
            })))
        repo.updatePreferences("LIGHT", false, false, 1140)
        repo.ensureMaterialized(semester)
        if (today.dayOfWeek.value > 5) repo.copyWeekday(semester, today, DayOfWeek.MONDAY)
        waitFor("mark_all")
        capture("phone-light-today")

        // A one-tap absence survives the bulk present action.
        val osPeriod = repo.observeDay(semester, today).first().groups.first().cells.first().session!!
        ui.onNodeWithTag("period_${osPeriod.id}_absent").performScrollTo().performClick()
        withTimeout(10_000) { repo.observeDay(semester, today).first { it.recorded == 1 } }
        ui.onNodeWithTag("mark_all").performScrollTo().performClick()
        waitFor("day_complete")
        val completed = repo.observeDay(semester, today).first()
        assertEquals(6, completed.recorded)
        assertEquals(AttendanceOutcome.ABSENT, completed.groups.first().cells.first().session!!.outcome)
        capture("phone-light-complete")
        val osSession = completed.groups.first().cells.first().session!!
        ui.onNodeWithTag("period_${osSession.id}").performScrollTo().performClick()
        ui.onNodeWithText("Class not held").assertExists()
        capture("phone-light-period-sheet")
        ui.onNodeWithText("Substitute course").performClick()
        capture("phone-light-substitute-menu")
        back()
        back()

        ui.onNodeWithTag("day_title").performClick()
        capture("phone-light-day-picker")
        cancel()
        ui.onNodeWithTag("day_menu").performClick()
        capture("phone-light-day-menu")
        ui.onNodeWithText("No classes today (holiday)").performClick()
        capture("phone-light-holiday-confirm")
        cancel()
        ui.onNodeWithTag("day_menu").performClick()
        ui.onNodeWithText("Use another day's timetable…").performClick()
        capture("phone-light-weekday-picker")
        back()
        ui.onNodeWithTag("day_menu").performClick()
        ui.onNodeWithText("Add an extra class…").performClick()
        capture("phone-light-extra-class")
        ui.onNodeWithTag("extra_start").performScrollTo().performClick()
        capture("phone-light-extra-time")
        back()
        cancel()

        // Seed duty leave in the same isolated database for the attendance views.
        val labIds = completed.groups.flatMap { it.cells }.mapNotNull { it.session }
            .filter { it.courseId == lab.courseId }.map { it.id }
        repo.markDutyLeave(semester, labIds, replace = true)
        for (theme in listOf("Light", "Dark")) {
            tab("settings")
            ui.onNodeWithText(theme, substring = false).performScrollTo().performClick()
            ui.waitUntil(10_000) {
                ui.onAllNodes(hasText(theme) and isSelected()).fetchSemanticsNodes().isNotEmpty()
            }
            capture("phone-${theme.lowercase()}-settings")
            ui.onNodeWithTag("open_about").performScrollTo()
            capture("phone-${theme.lowercase()}-settings-backup")
            if (theme == "Light") {
                ui.onNodeWithTag("open_about").performClick()
                capture("phone-light-about")
                ui.onNodeWithText("Font license").performClick()
                capture("phone-light-font-license")
                ui.onNodeWithText("Done").performClick()
                ui.onNodeWithTag("recent_changes").performScrollTo().performClick()
                ui.waitUntil(10_000) { ui.onAllNodesWithText("Marked duty leave").fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Marked duty leave").performScrollTo()
                capture("phone-light-recent-changes")
                ui.onNodeWithTag("open_semester_$semester").performScrollTo().performClick()
                capture("phone-light-semester-details")
                ui.onNodeWithTag("semester_lifecycle_$semester").performClick()
                capture("phone-light-semester-finish")
                cancel()
                ui.onNodeWithText("Edit semester").performClick()
                capture("phone-light-semester-edit")
                cancel()
                ui.onNodeWithContentDescription("Back").performClick()
                ui.onNodeWithTag("semester_switcher").performClick()
                capture("phone-light-semester-switcher")
                cancel()
                repo.updatePreferences("LIGHT", false, true, 1140)
                ui.waitUntil(10_000) { ui.onAllNodesWithText("Reminder time").fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Reminder time").performScrollTo().performClick()
                capture("phone-light-reminder-time")
                cancel()
                repo.updatePreferences("LIGHT", false, false, 1140)
            }
            tab("today")
            waitFor("day_complete")
            capture("phone-${theme.lowercase()}-today-complete")
            tab("courses")
            waitFor("courses_list")
            capture("phone-${theme.lowercase()}-courses")
            if (theme == "Light") {
                ui.onNodeWithTag("courses_list").performScrollToNode(hasText("Operating Systems") and hasClickAction())
                ui.onAllNodes(hasText("Operating Systems") and hasClickAction()).onFirst().performClick()
                waitFor("course_detail")
                capture("phone-light-course-details")
                ui.onNodeWithTag("course_rename").performScrollTo().performClick()
                capture("phone-light-course-rename")
                cancel()
                ui.onNodeWithTag("course_balance").performScrollTo().performClick()
                capture("phone-light-course-balance")
                ui.onNode(hasSetTextAction() and hasText("Duty leave")).performScrollTo().performTextReplacement("1")
                ui.onNodeWithText("Save", substring = false).performClick()
                withTimeout(10_000) {
                    repo.observeCourseStats(semester, today).first { courses ->
                        courses.single { it.course.id == os.courseId.toString() }.balance.dutyLeave == 1L
                    }
                }
                ui.onNodeWithTag("course_stop").performScrollTo().performClick()
                capture("phone-light-course-stop")
                cancel()
                ui.onNodeWithTag("tab_courses").performClick()
                ui.onNodeWithTag("courses_list").performScrollToNode(hasTestTag("attendance_rules"))
                ui.onNodeWithTag("attendance_rules").performClick()
                capture("phone-light-attendance-rules")
                back()
                ui.onNodeWithTag("courses_list").performScrollToNode(hasTestTag("add_course"))
                ui.onNodeWithTag("add_course").performClick()
                waitFor("course_name")
                capture("phone-light-course-editor")
                ui.onNodeWithText("Cancel", substring = false).performClick()
            }
            ui.onNodeWithTag("courses_list").performScrollToNode(hasTestTag("open_timetable"))
            ui.onNodeWithTag("open_timetable").performClick()
            waitFor("timetable_edit")
            capture("phone-${theme.lowercase()}-timetable")
            ui.onNodeWithTag("timetable_edit").performClick()
            capture("phone-${theme.lowercase()}-timetable-edit")
            if (theme == "Light") {
                ui.onNodeWithTag("timetable_effective_date").performScrollTo().performClick()
                capture("phone-light-timetable-date")
                back()
                ui.onNodeWithContentDescription("Copy from…").performScrollTo().performClick()
                capture("phone-light-timetable-copy")
                back()
                ui.onAllNodesWithTag("slot_cell").onFirst().performScrollTo().performClick()
                capture("phone-light-timetable-assignment")
                back()
            }
            ui.onNodeWithContentDescription("Back", substring = false).performClick()
            tab("calendar")
            waitFor("month_prev")
            capture("phone-${theme.lowercase()}-calendar")
            if (theme == "Light") {
                tab("today")
                ui.onNodeWithTag("day_menu").performClick()
                ui.onNodeWithText("Duty leave…").performClick()
                waitFor("dl_confirm")
                ui.onNodeWithTag("dl_confirm").assertIsNotEnabled()
                capture("phone-light-leave-form")
                ui.onNodeWithTag("dl_period_${osSession.id}").assertIsOff().performScrollTo()
                    .performClick().assertIsOn()
                ui.onNodeWithTag("dl_confirm").performClick()
                waitFor("confirm_action")
                ui.onNodeWithText("Replace").assertIsDisplayed()
                capture("phone-light-replace-leave-marks")
                ui.onNodeWithText("Cancel").performClick()
            }
        }

        // All four setup steps; cancel the draft rather than save a semester.
        val savedSemesters = repo.observeAppState().first { it.loaded }.semesters
            .map { it.entity.id }.toSet()
        tab("settings")
        ui.onNodeWithText("Light", substring = false).performScrollTo().performClick()
        ui.onNodeWithTag("new_semester").performScrollTo().performClick()
        waitFor("semester_name")
        capture("phone-light-setup-semester")
        ui.onNodeWithText("College timezone").performScrollTo().performClick()
        capture("phone-light-setup-timezones")
        back()
        ui.onNodeWithTag("semester_name").performTextInput("Semester 6")
        ui.onNodeWithTag("start_date").performClick()
        ui.onNodeWithText("OK", substring = false).performClick()
        ui.onNodeWithTag("setup_action").performScrollTo().performClick()
        waitFor("add_course")
        capture("phone-light-setup-courses")
        ui.onNodeWithTag("add_course").performClick()
        ui.onNodeWithTag("draft_name").performTextInput("Data Structures")
        capture("phone-light-setup-course-editor")
        ui.onNodeWithTag("draft_save").performClick()
        ui.onNodeWithTag("setup_action").performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("setup_slot").fetchSemanticsNodes().isNotEmpty() }
        capture("phone-light-setup-timetable")
        ui.onNodeWithTag("setup_action").performScrollTo().performClick()
        ui.onNodeWithText("Your timetable is empty").assertIsDisplayed()
        capture("phone-light-setup-empty-timetable")
        ui.onNodeWithText("Skip for now").performClick()
        ui.onNodeWithText("Not on the weekly timetable").assertIsDisplayed()
        capture("phone-light-setup-review")
        ui.onNodeWithContentDescription("Cancel setup").performScrollTo().performClick()
        capture("phone-light-setup-discard")
        ui.onNodeWithTag("confirm_action").performClick()
        waitFor("tab_today")
        assertEquals(savedSemesters, repo.observeAppState().first { it.loaded }.semesters
            .map { it.entity.id }.toSet())
    }

    @Test fun addCourseWithAttendanceSoFarOffersTimetable() = runBlocking {
        val initial = CourseSetup(input = CourseInput("Operating Systems", CourseType.THEORY))
        val semester = repo.createSemester(SemesterSetup("Course add fixture", today.minusDays(7),
            ZoneId.systemDefault().id, listOf(initial), WeekTemplate()))
        waitFor("tab_courses")
        tab("courses")
        waitFor("courses_list")
        ui.onNodeWithTag("courses_list").performScrollToNode(hasTestTag("add_course"))
        ui.onNodeWithTag("add_course").performClick()
        ui.onNodeWithTag("course_name").performTextInput("Databases")
        ui.onNodeWithText("Attendance so far").performScrollTo().performClick()
        ui.onNodeWithText("Classes held").performScrollTo().performTextReplacement("10")
        ui.onNodeWithText("Attended in person").performScrollTo().performTextReplacement("8")
        ui.onNodeWithText("Duty leave").performScrollTo().performTextReplacement("1")
        ui.onNodeWithTag("course_save").assertIsEnabled().performClick()

        val saved = withTimeout(10_000) {
            repo.observeCourseStats(semester, today).first { list -> list.any { it.course.name == "Databases" } }
                .single { it.course.name == "Databases" }
        }
        assertEquals(10L, saved.balance.conducted)
        assertEquals(8L, saved.balance.present)
        assertEquals(1L, saved.balance.dutyLeave)
        val timetableAction = hasText("Timetable") and hasClickAction() and !hasTestTag("open_timetable")
        ui.waitUntil(10_000) { ui.onAllNodes(timetableAction).fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(timetableAction).performClick()
        waitFor("timetable_edit")
    }

    @Test fun coursesWithUnmarkedPeriodsOnDarkTheme() = runBlocking {
        val lab = CourseSetup(input = CourseInput("Networks Lab", CourseType.LAB))
        val theory = CourseSetup(input = CourseInput("Operating Systems", CourseType.THEORY))
        val lastWeekday = generateSequence(today.minusDays(1)) { it.minusDays(1) }
            .first { it.dayOfWeek.value <= DayOfWeek.FRIDAY.value }
        val semester = repo.createSemester(SemesterSetup("Semester 5", lastWeekday.minusWeeks(1),
            ZoneId.systemDefault().id, listOf(
                lab.copy(balance = OpeningBalance(lab.courseId, lastWeekday, 3, 3)),
                theory.copy(balance = OpeningBalance(theory.courseId, lastWeekday, 4, 3)),
            ), WeekTemplate(mapOf(lastWeekday.dayOfWeek to
                mapOf(1 to theory.courseId, 3 to lab.courseId, 4 to lab.courseId, 5 to lab.courseId)))))
        repo.updatePreferences("DARK", false, false, 1140)
        repo.ensureMaterialized(semester)
        waitFor("tab_courses")
        tab("courses")

        val labRow = "course_row_${lab.courseId}"
        val theoryRow = "course_row_${theory.courseId}"
        waitFor(theoryRow)
        ui.onNodeWithTag(labRow).assert(hasText("3 unmarked") and hasText("No absences to spare"))
        ui.onNodeWithTag(theoryRow).assert(hasText("1 unmarked") and hasText("Attend the next 6 periods"))
        capture("phone-dark-courses-unmarked")
    }

    @Test fun scheduledClassesAndFreePeriodsOnDarkTheme() = runBlocking {
        val theory = CourseSetup(input = CourseInput("Operating Systems", CourseType.THEORY))
        val lab = CourseSetup(input = CourseInput("Networks Lab", CourseType.LAB))
        val date = today.with(java.time.temporal.TemporalAdjusters.previous(DayOfWeek.THURSDAY))
        val semester = repo.createSemester(SemesterSetup("Semester 5", date, ZoneId.systemDefault().id,
            listOf(theory, lab), WeekTemplate(mapOf(date.dayOfWeek to
                mapOf(1 to theory.courseId, 3 to lab.courseId, 4 to lab.courseId, 5 to lab.courseId)))))
        repo.updatePreferences("DARK", false, false, 1140)
        repo.ensureMaterialized(semester)
        waitFor("previous_day")
        repeat((today.toEpochDay() - date.toEpochDay()).toInt()) { offset ->
            ui.onNodeWithTag("previous_day").performClick()
            val expected = today.minusDays(offset + 1L).long()
            ui.waitUntil(10_000) {
                ui.onAllNodes(hasTestTag("day_title") and hasContentDescription(expected))
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
        waitFor("mark_all")
        ui.onNodeWithTag("return_today").assertIsDisplayed()
        ui.onNodeWithText("P2 · 10:00 · Free").assertIsDisplayed()
        ui.onNodeWithContentDescription("P3, Not marked").assertDoesNotExist()
        capture("phone-dark-today-unmarked")

        val labPeriod = repo.observeDay(semester, date).first().groups.single { it.grouped }
            .cells.first().session!!
        val expandLab = ui.onNodeWithTag("group_periods_${labPeriod.id}")
        expandLab.performScrollTo().performClick()
        ui.onNodeWithTag("period_${labPeriod.id}_absent").performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Remaining periods").fetchSemanticsNodes().isNotEmpty() }
        capture("phone-dark-today-expanded")
        expandLab.performScrollTo().performClick()
        ui.onNodeWithContentDescription("P3, Absent").assertExists()
        ui.onNodeWithTag("mark_all").performScrollTo().performClick()
        waitFor("day_complete")
        assertEquals(4, repo.observeDay(semester, date).first().recorded)
        assertEquals(AttendanceOutcome.ABSENT, repo.observeDay(semester, date).first()
            .groups.single { it.grouped }.cells.first().session!!.outcome)
        capture("phone-dark-today-complete")
    }

    private fun cancel() = ui.onNodeWithText("Cancel", substring = false).performClick()

    private fun back() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        ui.waitForIdle()
    }

    private fun tab(name: String) {
        ui.onNodeWithTag("tab_$name").performClick()
        ui.waitForIdle()
    }
    private fun waitFor(tag: String) {
        ui.waitUntil(15_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        ui.waitForIdle()
    }
    private fun capture(name: String) {
        // Compose's test clock owns snackbar delays as well as UI animations.
        ui.mainClock.advanceTimeBy(12_000)
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(450)
        val captureRun = InstrumentationRegistry.getArguments().getString("reviewRun") ?: "manual"
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "redesign-review/$captureRun").apply { mkdirs() }
        val bitmap = checkNotNull((1..3).firstNotNullOfOrNull { attempt ->
            if (attempt > 1) android.os.SystemClock.sleep(300)
            instrumentation.uiAutomation.takeScreenshot()
        }) { "No device screenshot for $name" }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
