package com.niranjan.ninety.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.niranjan.ninety.AppContainer
import com.niranjan.ninety.MainActivity
import com.niranjan.ninety.NinetyApplication
import com.niranjan.ninety.data.*
import com.niranjan.ninety.domain.CourseType
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
import org.robolectric.RuntimeEnvironment
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
    private lateinit var eventId: UUID
    private val today = LocalDate.now()

    private val fixture = object : ExternalResource() {
        override fun before() = runBlocking {
            if (testName.methodName.startsWith("twoHundredPercent")) RuntimeEnvironment.setFontScale(2f)
            val theory = CourseSetup(input = CourseInput("Operating Systems", CourseType.THEORY))
            val lab = CourseSetup(input = CourseInput("Networks Lab", CourseType.LAB))
            labId = lab.courseId
            semesterId = repo.createSemester(SemesterSetup("V1 UI fixture", today, ZoneId.systemDefault().id,
                listOf(theory, lab), WeekTemplate(DayOfWeek.entries.take(5).associateWith {
                    mapOf(1 to theory.courseId, 3 to labId, 4 to labId, 5 to labId)
                })))
            repo.ensureMaterialized(semesterId)
            // Use a copied weekday when a developer runs the tests on a weekend.
            if (today.dayOfWeek.value > 5) repo.copyWeekday(semesterId, today, DayOfWeek.MONDAY)
            val ids = repo.observeDay(semesterId, today).first().groups.flatMap { it.cells }
                .mapNotNull { it.session }.filter { it.courseId == labId }.map { it.id }
            eventId = repo.applyDutyLeave(semesterId, ids, "Campus event", false).eventId
        }
        override fun after() { app.container.database.close(); RuntimeEnvironment.setFontScale(1f) }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(testName).around(fixture).around(compose)

    @Test fun completedSemesterCanBeBrowsedAndReopenedFromSettings() = runBlocking {
        compose.waitUntil(15_000) { exists("tab_settings") }
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("semester_lifecycle_$semesterId").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_action").performClick()
        withTimeout(10_000) { repo.observeAppState().first { it.activeSemesterId == null } }
        compose.onNodeWithTag("tab_today").performClick()
        compose.onNodeWithText("No active semester.", substring = true).assertExists()
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("browse_$semesterId").performScrollTo().performClick()
        compose.waitUntil(10_000) { exists("day_title") }
        compose.onNodeWithText("Semester done").assertExists()
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("semester_lifecycle_$semesterId").performScrollTo().performClick()
        withTimeout(10_000) { repo.observeAppState().first { it.activeSemesterId == semesterId } }
        compose.onNodeWithTag("tab_today").performClick()
        compose.waitUntil(10_000) { exists("day_title") }
    }

    @Test fun partialLeaveApprovalAndOpeningAnotherEventRefreshesItsPeriods(): Unit = runBlocking {
        compose.waitUntil(15_000) { exists("tab_leave") }
        compose.onNodeWithTag("tab_leave").performClick()
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
    }

    @Test fun twoHundredPercentTextKeepsActionLabelsWholeInBothThemes() {
        compose.waitUntil(15_000) { exists("tab_today") }
        assertEquals(2f, compose.activity.resources.configuration.fontScale, 0.01f)
        compose.waitUntil(15_000) { exists("group_present") }
        compose.onNodeWithTag("group_present").performScrollTo()
        capture("before-theme-today-200")
        assertOneLine("Present")
        for ((theme, name) in listOf("Light" to "light", "Dark" to "dark")) {
            compose.onNodeWithTag("tab_settings").performClick()
            compose.onNodeWithText(theme, substring = false).performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasText(theme) and isSelected()).fetchSemanticsNodes().isNotEmpty()
            }
            capture("$name-settings-200")
            assertOneLine("System")
            compose.onNodeWithTag("tab_today").performClick()
            compose.onNodeWithTag("group_present").performScrollTo()
            capture("$name-today-200")
            assertOneLine("Present")
            compose.onNodeWithTag("tab_courses").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Provisional").fetchSemanticsNodes().isNotEmpty() }
            capture("$name-courses-200")
            compose.onNodeWithTag("tab_leave").performClick()
            compose.waitUntil(10_000) { exists("dl_expand_$eventId") }
            capture("$name-leave-200")
        }
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
