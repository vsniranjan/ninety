package com.niranjan.ninety.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
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

private const val ROUTE_SETUP = "setup"
private const val ROUTE_TODAY = "today"
private const val ROUTE_DAY = "day/{date}"
private const val ROUTE_COURSES = "courses"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_LEAVE = "leave"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_TIMETABLE = "timetable"

private fun dayRoute(date: LocalDate) = "day/$date"

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
            SetupScreen(setupModel, onSaved = appModel::closeSetup)
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
    val viewed by appModel.viewedSemester.collectAsStateWithLifecycle()
    val undoable by appModel.undoable.collectAsStateWithLifecycle()
    val appMessage by appModel.messages.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val semesterId = viewed?.let { UUID.fromString(it.entity.id) }
    val collegeZone = viewed?.let { ZoneId.of(it.entity.collegeZone) } ?: ZoneId.systemDefault()
    val today = LocalDate.now(collegeZone)

    LaunchedEffect(appMessage) { appMessage?.let { snackbar.showSnackbar(it); appModel.dismissMessage() } }
    LaunchedEffect(undoable?.batchId) {
        val action = undoable ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(action.label, actionLabel = "Undo",
            duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) appModel.undo()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = {
                Column {
                    Text(viewed?.entity?.name ?: "Ninety", style = MaterialTheme.typography.titleLarge)
                    if (viewed?.entity?.archived == true) {
                        Text("Semester done", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            })
        },
        bottomBar = {
            NavigationBar {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            if (currentRoute != tab.route) nav.navigate(tab.route) {
                                popUpTo(ROUTE_TODAY) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(painterResource(tabIcon(tab.tag)), contentDescription = null) },
                        label = { Text(tab.label) },
                        modifier = Modifier.testTag(tab.tag))
                }
            }
        },
    ) { padding ->
        if (semesterId == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No active semester. Open Settings to activate or create one.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(24.dp))
            }
            return@Scaffold
        }
        NavHost(nav, startDestination = ROUTE_TODAY, modifier = Modifier.padding(padding)) {
            composable(ROUTE_TODAY) {
                DayScreen(semesterId, today, isToday = true,
                    onOpenDay = { nav.navigate(dayRoute(it)) }, snackbar = snackbar)
            }
            composable(ROUTE_DAY, arguments = listOf(navArgument("date") { type = NavType.StringType })) { entry ->
                val date = entry.arguments?.getString("date")?.let { LocalDate.parse(it) } ?: today
                DayScreen(semesterId, date, isToday = date == today,
                    onOpenDay = { nav.navigate(dayRoute(it)) }, snackbar = snackbar)
            }
            composable(ROUTE_COURSES) {
                CoursesScreen(semesterId, snackbar, onEditTimetable = { nav.navigate(ROUTE_TIMETABLE) })
            }
            composable(ROUTE_HISTORY) {
                HistoryScreen(semesterId, onOpenDay = { nav.navigate(dayRoute(it)) })
            }
            composable(ROUTE_LEAVE) { LeaveScreen(semesterId, snackbar) }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(snackbar, onNewSemester = appModel::openSetup)
            }
            composable(ROUTE_TIMETABLE) { TimetableScreen(semesterId, snackbar, onDone = { nav.popBackStack() }) }
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
