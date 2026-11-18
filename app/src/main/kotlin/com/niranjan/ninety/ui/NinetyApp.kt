package com.niranjan.ninety.ui

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.niranjan.ninety.R
import com.niranjan.ninety.ui.courses.CoursesScreen
import com.niranjan.ninety.ui.day.DayScreen
import com.niranjan.ninety.ui.designsystem.ActionIcon
import com.niranjan.ninety.ui.designsystem.EmptyState
import com.niranjan.ninety.ui.history.HistoryScreen
import com.niranjan.ninety.ui.leave.LeaveScreen
import com.niranjan.ninety.ui.settings.SettingsScreen
import com.niranjan.ninety.ui.setup.SetupScreen
import com.niranjan.ninety.ui.setup.SetupViewModel
import com.niranjan.ninety.ui.timetable.TimetableExitHandler
import com.niranjan.ninety.ui.timetable.TimetableScreen
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt

private const val ROUTE_TODAY = "day"
private const val ROUTE_DAY = "$ROUTE_TODAY?date={date}&origin={origin}&addDutyLeave={addDutyLeave}"
private const val ROUTE_COURSES = "courses"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_LEAVE = "leave"
private const val ROUTE_COURSE_PENDING_LEAVE = "courses/{courseId}/pendingLeave"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_TIMETABLE = "timetable"

private const val SLIDE_MS = 160
private fun <T> slideTiming() = tween<T>(durationMillis = SLIDE_MS, easing = FastOutSlowInEasing)

private fun dayRoute(date: LocalDate, origin: String, addDutyLeave: Boolean = false) =
    "day?date=$date&origin=$origin&addDutyLeave=$addDutyLeave"

private val NavBackStackEntry.isDatedDay: Boolean
    get() = destination.route == ROUTE_DAY && arguments?.getString("date") != null

private data class Tab(val route: String, @StringRes val label: Int, val tag: String)
private val TABS = listOf(
    Tab(ROUTE_TODAY, R.string.today, "tab_today"),
    Tab(ROUTE_COURSES, R.string.courses, "tab_courses"),
    Tab(ROUTE_HISTORY, R.string.history, "tab_history"),
    Tab(ROUTE_LEAVE, R.string.leave_tab, "tab_leave"),
    Tab(ROUTE_SETTINGS, R.string.settings, "tab_settings"),
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
                CircularProgressIndicator(Modifier.size(24.dp).testTag("overview_loading"), strokeWidth = 2.dp)

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
    val appState by appModel.appState.collectAsStateWithLifecycle()
    val activeSemester = appState.activeSemester
    val browsingOtherSemester = viewed != null && viewed?.entity?.id != activeSemester?.entity?.id
    val archived = viewed?.entity?.archived == true
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
    val undoLabel = stringResource(R.string.undo)
    val undoMessage = undoable?.let { displayMessage(it.label) }
    LaunchedEffect(undoable?.batchId) {
        val action = undoable ?: return@LaunchedEffect
        if (action.createdAt < appModel.sessionStartedAt) return@LaunchedEffect
        val result = snackbar.showSnackbar(undoMessage ?: action.label, actionLabel = undoLabel,
            duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) appModel.undo(action.batchId)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    val expanded = maxWidth >= 600.dp
    var timetableExit by remember { mutableStateOf<TimetableExitHandler?>(null) }
    fun leaveCurrentScreen(navigate: () -> Unit) {
        val confirmExit = timetableExit
        if (currentRoute == ROUTE_TIMETABLE && confirmExit != null) confirmExit(navigate)
        else navigate()
    }
    val selectTab: (Tab) -> Unit = { tab ->
        val currentTabRoot = if (currentRoute == ROUTE_DAY && backStack?.isDatedDay == false) ROUTE_TODAY else currentRoute
        if (currentTabRoot != tab.route || backStack?.isDatedDay == true) leaveCurrentScreen {
            if (backStack?.isDatedDay == true) nav.popBackStack()
            nav.navigate(tab.route) {
                popUpTo(nav.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    fun switchViewedSemester(id: UUID?, openSemesterSettings: Boolean = false) {
        if (backStack?.isDatedDay == true) nav.popBackStack()
        TABS.forEach { nav.clearBackStack(it.route) }
        appModel.viewSemester(id)
        nav.navigate(ROUTE_TODAY) { popUpTo(nav.graph.startDestinationId) { inclusive = true } }
        if (openSemesterSettings) nav.navigate(ROUTE_SETTINGS)
    }
    val openSettings = { selectTab(TABS.last()) }
    val openTimetable = { nav.navigate(ROUTE_TIMETABLE) }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            val barColor = if (browsingOtherSemester) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.background
            Column(Modifier.background(barColor)) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = barColor),
                    title = {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        if (backStack?.isDatedDay == true || currentRoute in listOf(ROUTE_TIMETABLE, ROUTE_COURSE_PENDING_LEAVE)) {
                            ActionIcon(R.drawable.ic_back, stringResource(R.string.back), { leaveCurrentScreen { nav.popBackStack() } })
                        }
                    },
                    actions = {
                        Column(Modifier.widthIn(max = 180.dp).padding(end = 24.dp),
                            horizontalAlignment = Alignment.End) {
                            viewed?.entity?.name?.let { Text(it, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            if (archived) Text(stringResource(R.string.finished),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    })
                if (browsingOtherSemester) {
                    val name = viewed?.entity?.name.orEmpty()
                    val activeName = activeSemester?.entity?.name
                    val message = if (activeName != null) {
                        stringResource(R.string.viewing_semester_back_to_active, name, activeName)
                    } else stringResource(R.string.viewing_semester_back_to_settings, name)
                    TextButton(
                        onClick = { leaveCurrentScreen { switchViewedSemester(null, activeName == null) } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("exit_browsed_semester"),
                    ) {
                        Text(message, textAlign = TextAlign.Center)
                    }
                }
            }
        },
        bottomBar = { if (!expanded) SlidingTabBar(selectedTab, selectTab) },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (expanded) NavigationRail(containerColor = MaterialTheme.colorScheme.background) {
                TABS.forEachIndexed { index, tab ->
                    val label = stringResource(tab.label)
                    NavigationRailItem(selected = selectedTab == index, onClick = { selectTab(tab) },
                        modifier = Modifier.testTag(tab.tag).semantics { contentDescription = label },
                        icon = { Icon(painterResource(tabIcon(tab.tag)), null, Modifier.size(22.dp)) },
                        label = { Text(label) })
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
        NavHost(nav, startDestination = ROUTE_TODAY, modifier = Modifier.fillMaxSize().widthIn(max = 760.dp).clipToBounds(),
            enterTransition = { slideIntoContainer(slideDirection(), slideTiming()) },
            exitTransition = { slideOutOfContainer(slideDirection(), slideTiming()) },
            popEnterTransition = { slideIntoContainer(slideDirection(back = true), slideTiming()) },
            popExitTransition = { slideOutOfContainer(slideDirection(back = true), slideTiming()) },
        ) {
            composable(ROUTE_DAY, arguments = listOf(
                navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("origin") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("addDutyLeave") { type = NavType.BoolType; defaultValue = false },
            )) { entry ->
                val initialDate = entry.arguments?.getString("date")?.let(LocalDate::parse)
                SemesterContent(semesterId, openSettings) { id ->
                    DayScreen(id, initialDate, today, snackbar = snackbar, onEditTimetable = openTimetable,
                        addDutyLeave = entry.arguments?.getBoolean("addDutyLeave") == true, archived = archived)
                }
            }
            composable(ROUTE_COURSES) {
                SemesterContent(semesterId, openSettings) { id ->
                    CoursesScreen(id, snackbar, onEditTimetable = openTimetable,
                        onOpenDay = { nav.navigate(dayRoute(it, ROUTE_COURSES)) },
                        onOpenPendingLeave = { nav.navigate("courses/$it/pendingLeave") })
                }
            }
            composable(ROUTE_COURSE_PENDING_LEAVE) { entry ->
                val courseId = UUID.fromString(requireNotNull(entry.arguments?.getString("courseId")))
                SemesterContent(semesterId, openSettings) { id ->
                    LeaveScreen(id, snackbar, today, pendingCourseId = courseId) { date ->
                        nav.navigate(dayRoute(date, ROUTE_COURSES, addDutyLeave = true))
                    }
                }
            }
            composable(ROUTE_HISTORY) {
                SemesterContent(semesterId, openSettings) { id ->
                    HistoryScreen(id, today, onOpenDay = { nav.navigate(dayRoute(it, ROUTE_HISTORY)) })
                }
            }
            composable(ROUTE_LEAVE) {
                SemesterContent(semesterId, openSettings) { LeaveScreen(it, snackbar, today) { date -> nav.navigate(dayRoute(date, ROUTE_LEAVE, addDutyLeave = true)) } }
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(snackbar,
                    onNewSemester = { appModel.viewSemester(null); appModel.openSetup() },
                    onViewSemester = { switchViewedSemester(it) },
                    onActiveChanged = { appModel.viewSemester(null) })
            }
            composable(ROUTE_TIMETABLE) {
                SemesterContent(semesterId, openSettings) { id ->
                    TimetableScreen(id, today, snackbar, onDone = { nav.popBackStack() },
                        registerExitHandler = { timetableExit = it })
                }
            }
        }
            }
        }
    }
    }
}

private fun NavBackStackEntry.tabIndex(): Int {
    val tabRoute = when (destination.route) {
        ROUTE_DAY -> arguments?.getString("origin") ?: ROUTE_TODAY
        ROUTE_TIMETABLE, ROUTE_COURSE_PENDING_LEAVE -> ROUTE_COURSES
        else -> destination.route
    }
    return TABS.indexOfFirst { it.route == tabRoute }.coerceAtLeast(0)
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideDirection(back: Boolean = false):
    AnimatedContentTransitionScope.SlideDirection {
    val initialTab = initialState.tabIndex()
    val targetTab = targetState.tabIndex()
    val forward = if (initialTab == targetTab) !back else targetTab > initialTab
    return if (forward) AnimatedContentTransitionScope.SlideDirection.Start
        else AnimatedContentTransitionScope.SlideDirection.End
}

@Composable
private fun SlidingTabBar(selectedIndex: Int, onSelect: (Tab) -> Unit) {
    val pillPosition = animateFloatAsState(selectedIndex.toFloat(), slideTiming(), label = "Selected tab")
    val pillTop = 12.dp
    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val labels = TABS.map { stringResource(it.label) }
            val labelStyle = MaterialTheme.typography.labelSmall
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val minimum = 48.dp
            val extraWidths = with(density) {
                labels.map { label ->
                    val textWidth = measurer.measure(label, style = labelStyle, maxLines = 1).size.width.toDp()
                    (textWidth + 4.dp - minimum).coerceAtLeast(0.dp)
                }
            }
            val requiredWidth = extraWidths.fold(0.dp) { total, width -> total + width }
            val availableWidth = (maxWidth - minimum * TABS.size).coerceAtLeast(0.dp)
            val fit = if (requiredWidth > availableWidth) availableWidth / requiredWidth else 1f
            val distributedWidth = (availableWidth - requiredWidth).coerceAtLeast(0.dp) / TABS.size
            val tabWidths = extraWidths.map { minimum + it * fit + distributedWidth }
            val pillWidth = minOf(56.dp, tabWidths.min())
            Box(Modifier.align(Alignment.TopStart)
                .offset {
                    val position = pillPosition.value
                    val index = position.toInt().coerceIn(tabWidths.indices)
                    val fraction = position - index
                    val left = tabWidths.take(index).fold(0.dp) { total, width -> total + width }
                    val nextWidth = tabWidths.getOrElse(index + 1) { tabWidths[index] }
                    val center = left + tabWidths[index] / 2 + (tabWidths[index] + nextWidth) * (fraction / 2)
                    IntOffset((center - pillWidth / 2).roundToPx(), pillTop.roundToPx())
                }
                .width(pillWidth).height(32.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp)))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup()) {
                TABS.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    val label = labels[index]
                    val interactions = remember { MutableInteractionSource() }
                    val focused by interactions.collectIsFocusedAsState()
                    Column(Modifier.weight(tabWidths[index].value).fillMaxHeight().heightIn(min = 72.dp)
                        .testTag(tab.tag).selectable(selected, interactionSource = interactions,
                            indication = androidx.compose.material3.ripple(), role = Role.Tab, onClick = { onSelect(tab) })
                        .semantics { contentDescription = label }
                        .padding(top = pillTop, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.width(pillWidth).height(32.dp).then(
                            if (focused) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            else Modifier),
                            contentAlignment = Alignment.Center) {
                            Icon(painterResource(tabIcon(tab.tag)), contentDescription = null,
                                modifier = Modifier.size(24.dp), tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(label, modifier = Modifier.padding(top = 4.dp),
                            style = labelStyle, textAlign = TextAlign.Center,
                            maxLines = if (fit < 1f) 1 else 2,
                            autoSize = if (fit < 1f) TextAutoSize.StepBased(9.sp, labelStyle.fontSize) else null,
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
private fun SemesterContent(semesterId: UUID?, onOpenSettings: () -> Unit,
    content: @Composable (UUID) -> Unit) {
    if (semesterId == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            EmptyState(stringResource(R.string.no_current_semester), "",
                actionLabel = stringResource(R.string.open_settings), onAction = onOpenSettings)
        }
    } else content(semesterId)
}
