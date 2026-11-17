package com.niranjan.ninety.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.niranjan.ninety.R
import com.niranjan.ninety.ui.courses.CoursesScreen
import com.niranjan.ninety.ui.day.DayScreen
import com.niranjan.ninety.ui.history.HistoryScreen
import com.niranjan.ninety.ui.leave.LeaveScreen
import com.niranjan.ninety.ui.settings.SettingsScreen
import com.niranjan.ninety.ui.setup.SetupScreen
import com.niranjan.ninety.ui.setup.SetupViewModel
import com.niranjan.ninety.ui.timetable.TimetableScreen
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt

private const val ROUTE_TODAY = "today"
private const val ROUTE_DAY = "day/{date}?fromHistory={fromHistory}"
private const val ROUTE_COURSES = "courses"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_LEAVE = "leave"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_TIMETABLE = "timetable"

private const val SLIDE_MS = 160
private fun <T> slideTiming() = tween<T>(durationMillis = SLIDE_MS, easing = FastOutSlowInEasing)

private fun dayRoute(date: LocalDate, fromHistory: Boolean = false) = "day/$date?fromHistory=$fromHistory"

private data class Tab(val route: String, val label: String, val tag: String)
private val TABS = listOf(
    Tab(ROUTE_TODAY, "Today", "tab_today"),
    Tab(ROUTE_COURSES, "Courses", "tab_courses"),
    Tab(ROUTE_HISTORY, "History", "tab_history"),
    Tab(ROUTE_LEAVE, "Leave", "tab_leave"),
    Tab(ROUTE_SETTINGS, "Settings", "tab_settings"),
)

@Composable
fun NinetyApp(appModel: AppViewModel) {
    val state by appModel.appState.collectAsStateWithLifecycle()
    val showSetup by appModel.showSetup.collectAsStateWithLifecycle()
    val setupNonce by appModel.setupNonce.collectAsStateWithLifecycle()
    when {
        !state.loaded -> LoadingScaffold()
        state.needsSetup || showSetup -> {
            val setupModel = ninetyViewModel<SetupViewModel>(key = "setup-$setupNonce") {
                SetupViewModel(it.repository) }
            SetupScreen(setupModel, onSaved = appModel::closeSetup,
                onCancel = if (showSetup && !state.needsSetup) appModel::closeSetup else null)
        }
        else -> MainScaffold(appModel)
    }
}

@Composable
private fun LoadingScaffold() {
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(Modifier.testTag("overview_loading"))
                Text("Opening your attendance…", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(appModel: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val selectedTab = backStack?.tabIndex() ?: 0
    val viewed by appModel.viewedSemester.collectAsStateWithLifecycle()
    val undoable by appModel.undoable.collectAsStateWithLifecycle()
    val appMessage by appModel.messages.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val semesterId = viewed?.let { UUID.fromString(it.entity.id) }
    val collegeZone = viewed?.let { ZoneId.of(it.entity.collegeZone) } ?: ZoneId.systemDefault()
    var today by remember(collegeZone) { mutableStateOf(LocalDate.now(collegeZone)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = LocalDate.now(collegeZone) }
    LaunchedEffect(collegeZone) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            today = LocalDate.now(collegeZone)
        }
    }
    LaunchedEffect(semesterId, today) { appModel.refreshDays() }

    LaunchedEffect(appMessage) { appMessage?.let { snackbar.showSnackbar(it); appModel.dismissMessage() } }
    LaunchedEffect(undoable?.batchId) {
        val action = undoable ?: return@LaunchedEffect
        if (action.createdAt < appModel.sessionStartedAt) return@LaunchedEffect
        val result = snackbar.showSnackbar(action.label, actionLabel = "Undo",
            duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) appModel.undo()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = {
                Column {
                    Text(viewed?.entity?.name ?: "Ninety", style = MaterialTheme.typography.titleLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (viewed?.entity?.archived == true) {
                        Text("Semester done", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            })
        },
        bottomBar = {
            SlidingTabBar(selectedTab) { tab ->
                if (currentRoute != tab.route) nav.navigate(tab.route) {
                    popUpTo(ROUTE_TODAY) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = ROUTE_TODAY, modifier = Modifier.padding(padding).clipToBounds(),
            enterTransition = { slideIntoContainer(slideDirection(), slideTiming()) },
            exitTransition = { slideOutOfContainer(slideDirection(), slideTiming()) },
            popEnterTransition = { slideIntoContainer(slideDirection(back = true), slideTiming()) },
            popExitTransition = { slideOutOfContainer(slideDirection(back = true), slideTiming()) },
        ) {
            composable(ROUTE_TODAY) {
                SemesterContent(semesterId) { id ->
                    DayScreen(id, today, isToday = true,
                        onOpenDay = { nav.navigate(dayRoute(it)) }, snackbar = snackbar)
                }
            }
            composable(ROUTE_DAY, arguments = listOf(
                navArgument("date") { type = NavType.StringType },
                navArgument("fromHistory") { type = NavType.BoolType; defaultValue = false },
            )) { entry ->
                val date = entry.arguments?.getString("date")?.let { LocalDate.parse(it) } ?: today
                val fromHistory = entry.arguments?.getBoolean("fromHistory") == true
                SemesterContent(semesterId) { id ->
                    DayScreen(id, date, isToday = date == today,
                        onOpenDay = { nav.navigate(dayRoute(it, fromHistory)) }, snackbar = snackbar)
                }
            }
            composable(ROUTE_COURSES) {
                SemesterContent(semesterId) { id ->
                    CoursesScreen(id, snackbar, onEditTimetable = { nav.navigate(ROUTE_TIMETABLE) })
                }
            }
            composable(ROUTE_HISTORY) {
                SemesterContent(semesterId) { id ->
                    HistoryScreen(id, today, onOpenDay = { nav.navigate(dayRoute(it, fromHistory = true)) })
                }
            }
            composable(ROUTE_LEAVE) {
                SemesterContent(semesterId) { LeaveScreen(it, snackbar) }
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(snackbar,
                    onNewSemester = { appModel.viewSemester(null); appModel.openSetup() },
                    onViewSemester = { id ->
                        appModel.viewSemester(id)
                        nav.navigate(ROUTE_TODAY) { popUpTo(ROUTE_TODAY) { inclusive = true } }
                    },
                    onActiveChanged = { appModel.viewSemester(null) })
            }
            composable(ROUTE_TIMETABLE) {
                SemesterContent(semesterId) { id -> TimetableScreen(id, snackbar, onDone = { nav.popBackStack() }) }
            }
        }
    }
}

private fun NavBackStackEntry.tabIndex(): Int {
    val tabRoute = when (destination.route) {
        ROUTE_DAY -> if (arguments?.getBoolean("fromHistory") == true) ROUTE_HISTORY else ROUTE_TODAY
        ROUTE_TIMETABLE -> ROUTE_COURSES
        else -> destination.route
    }
    return TABS.indexOfFirst { it.route == tabRoute }.coerceAtLeast(0)
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideDirection(back: Boolean = false):
    AnimatedContentTransitionScope.SlideDirection {
    val initialTab = initialState.tabIndex()
    val targetTab = targetState.tabIndex()
    val initialDay = initialState.arguments?.getString("date")
    val targetDay = targetState.arguments?.getString("date")
    val forward = when {
        initialTab != targetTab -> targetTab > initialTab
        initialDay != null && targetDay != null && initialDay != targetDay -> targetDay > initialDay
        else -> !back
    }
    return if (forward) AnimatedContentTransitionScope.SlideDirection.Start
        else AnimatedContentTransitionScope.SlideDirection.End
}

@Composable
private fun SlidingTabBar(selectedIndex: Int, onSelect: (Tab) -> Unit) {
    val showLabels = LocalDensity.current.fontScale <= 1.2f
    val pillPosition = animateFloatAsState(selectedIndex.toFloat(), slideTiming(), label = "Selected tab")
    val pillTop = if (showLabels) 12.dp else 24.dp
    NavigationBar {
        BoxWithConstraints(Modifier.fillMaxWidth().height(80.dp)) {
            val tabWidth = maxWidth / TABS.size
            val pillWidth = minOf(56.dp, tabWidth)
            Box(Modifier.align(Alignment.TopStart)
                .offset {
                    val start = (tabWidth.toPx() * pillPosition.value + (tabWidth - pillWidth).toPx() / 2f)
                    IntOffset(start.roundToInt(), pillTop.roundToPx())
                }
                .width(pillWidth).height(32.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape))
            Row(Modifier.matchParentSize().selectableGroup()) {
                TABS.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    val interactions = remember { MutableInteractionSource() }
                    val focused by interactions.collectIsFocusedAsState()
                    Column(Modifier.weight(1f).fillMaxHeight()
                        .testTag(tab.tag).selectable(selected, interactionSource = interactions,
                            indication = null, role = Role.Tab, onClick = { onSelect(tab) })
                        .semantics { contentDescription = tab.label }
                        .padding(top = pillTop), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.width(pillWidth).height(32.dp).then(
                            if (focused) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            else Modifier),
                            contentAlignment = Alignment.Center) {
                            Icon(painterResource(tabIcon(tab.tag)), contentDescription = null,
                                modifier = Modifier.size(24.dp), tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (showLabels) Text(tab.label, modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.labelMedium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (selected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun tabIcon(tag: String): Int = when (tag) {
    "tab_today" -> R.drawable.ic_today
    "tab_courses" -> R.drawable.ic_courses
    "tab_history" -> R.drawable.ic_history
    "tab_leave" -> R.drawable.ic_leave
    else -> R.drawable.ic_settings
}

@Composable
private fun SemesterContent(semesterId: UUID?, content: @Composable (UUID) -> Unit) {
    if (semesterId == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No active semester. Open Settings to browse, reopen or create one.")
        }
    } else content(semesterId)
}
