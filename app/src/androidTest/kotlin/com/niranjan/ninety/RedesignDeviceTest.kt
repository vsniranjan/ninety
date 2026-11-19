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
        val math = CourseSetup(input = CourseInput("Engineering Mathematics", CourseType.THEORY))
        fun balanced(c: CourseSetup, n: Long, p: Long, pending: Long = 0) =
            c.copy(balance = OpeningBalance(c.courseId, today, n, p, 0, pending))
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
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        ui.waitForIdle()

        // Seed provisional leave in the same isolated database for the leave views.
        val labIds = completed.groups.flatMap { it.cells }.mapNotNull { it.session }
            .filter { it.courseId == lab.courseId }.map { it.id }
        val event = repo.applyDutyLeave(semester, labIds, "Campus event", true).eventId
        for (theme in listOf("Light", "Dark")) {
            tab("settings")
            ui.onNodeWithText(theme, substring = false).performScrollTo().performClick()
            ui.waitUntil(10_000) {
                ui.onAllNodes(hasText(theme) and isSelected()).fetchSemanticsNodes().isNotEmpty()
            }
            capture("phone-${theme.lowercase()}-settings")
            ui.onNodeWithTag("export_backup").performScrollTo()
            capture("phone-${theme.lowercase()}-settings-backup")
            tab("today")
            waitFor("day_complete")
            capture("phone-${theme.lowercase()}-today-complete")
            tab("courses")
            ui.waitUntil(10_000) { ui.onAllNodesWithText("Includes", substring = true).fetchSemanticsNodes().isNotEmpty() }
            capture("phone-${theme.lowercase()}-courses")
            if (theme == "Light") {
                ui.onNodeWithTag("courses_list").performScrollToNode(hasText("Operating Systems") and hasClickAction())
                ui.onAllNodes(hasText("Operating Systems") and hasClickAction()).onFirst().performClick()
                capture("phone-light-course-details")
                ui.onNodeWithTag("tab_courses").performClick()
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
            ui.onNodeWithContentDescription("Back", substring = false).performClick()
            tab("calendar")
            waitFor("month_prev")
            capture("phone-${theme.lowercase()}-calendar")
            tab("courses")
            ui.onNodeWithTag("open_duty_leave").performScrollTo().performClick()
            waitFor("dl_expand_$event")
            if (theme == "Light") {
                ui.onNodeWithTag("add_duty_leave").performScrollTo().performClick()
                ui.onNodeWithText("OK").assertIsEnabled().performClick()
                waitFor("dl_confirm")
                ui.onNodeWithTag("dl_confirm").assertIsNotEnabled()
                ui.onNodeWithText("Cancel").performClick()
                ui.onNodeWithContentDescription("Back", substring = false).performClick()
                waitFor("duty_leave_list")
            }
            capture("phone-${theme.lowercase()}-leave")
            ui.onNodeWithTag("dl_expand_$event").performClick()
            capture("phone-${theme.lowercase()}-leave-details")
            ui.onNodeWithTag("dl_expand_$event").performClick()
        }

        // All four setup steps; cancel the draft rather than save a semester.
        tab("settings")
        ui.onNodeWithText("Light", substring = false).performScrollTo().performClick()
        ui.onNodeWithTag("new_semester").performScrollTo().performClick()
        waitFor("semester_name")
        capture("phone-light-setup-semester")
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
        capture("phone-light-setup-review")
        ui.onNodeWithContentDescription("Cancel setup").performScrollTo().performClick()
        assertEquals(1, repo.observeAppState().first { it.loaded }.semesters.size)
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
        ui.onNodeWithText("Duty leave — pending").performScrollTo().performTextReplacement("1")
        ui.onNodeWithTag("course_save").assertIsEnabled().performClick()

        val saved = withTimeout(10_000) {
            repo.observeCourseStats(semester, today).first { list -> list.any { it.course.name == "Databases" } }
                .single { it.course.name == "Databases" }
        }
        assertEquals(10L, saved.balance.conducted)
        assertEquals(8L, saved.balance.present)
        assertEquals(1L, saved.balance.pendingDl)
        val timetableAction = hasText("Timetable") and hasClickAction() and !hasTestTag("open_timetable")
        ui.waitUntil(10_000) { ui.onAllNodes(timetableAction).fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(timetableAction).performClick()
        waitFor("timetable_edit")
    }

    private fun tab(name: String) {
        ui.onNodeWithTag("tab_$name").performClick()
        ui.waitForIdle()
        if (name == "courses" && ui.onAllNodesWithTag("duty_leave_list").fetchSemanticsNodes().isNotEmpty()) {
            ui.onNodeWithTag("tab_courses").performClick()
            ui.waitForIdle()
        }
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
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
