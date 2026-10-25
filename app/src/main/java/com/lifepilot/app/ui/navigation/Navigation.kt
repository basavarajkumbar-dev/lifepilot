package com.lifepilot.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lifepilot.app.AppContainer
import com.lifepilot.app.LifePilotApp
import com.lifepilot.app.ui.calendar.CalendarScreen
import com.lifepilot.app.ui.common.Formats
import com.lifepilot.app.ui.common.LocalFormats
import com.lifepilot.app.ui.habits.HabitDetailScreen
import com.lifepilot.app.ui.habits.HabitEditorScreen
import com.lifepilot.app.ui.habits.HabitsScreen
import com.lifepilot.app.ui.home.HomeScreen
import com.lifepilot.app.ui.insights.InsightsScreen
import com.lifepilot.app.ui.onboarding.OnboardingScreen
import com.lifepilot.app.ui.quickadd.QuickAddSheet
import com.lifepilot.app.ui.review.DailyPlanScreen
import com.lifepilot.app.ui.review.EndOfDayReviewScreen
import com.lifepilot.app.ui.settings.CategoriesScreen
import com.lifepilot.app.ui.settings.ReliabilityScreen
import com.lifepilot.app.ui.settings.SettingsScreen
import com.lifepilot.app.ui.tasks.TaskDetailScreen
import com.lifepilot.app.ui.tasks.TaskEditorScreen
import com.lifepilot.app.ui.tasks.TaskFilter
import com.lifepilot.app.ui.tasks.TasksScreen
import com.lifepilot.app.ui.theme.LifePilotTheme
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

object Routes {
    const val HOME = "home"
    const val TASKS = "tasks?filter={filter}"
    const val HABITS = "habits"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
    const val OCCURRENCE = "occurrence/{id}"
    const val EDITOR = "editor?taskId={taskId}&date={date}"
    const val CALENDAR = "calendar"
    const val HABIT = "habit/{id}"
    const val HABIT_EDITOR = "habitEditor?habitId={habitId}"
    const val RELIABILITY = "reliability"
    const val PLAN = "plan"
    const val REVIEW = "review"
    const val CATEGORIES = "categories"

    fun tasks(filter: TaskFilter = TaskFilter.ALL) = "tasks?filter=${filter.name}"
    fun occurrence(id: Long) = "occurrence/$id"
    fun newTask(date: LocalDate? = null) = "editor?taskId=0&date=${date ?: ""}"
    fun editTask(taskId: Long) = "editor?taskId=$taskId&date="
    fun habit(id: Long) = "habit/$id"
    fun newHabit() = "habitEditor?habitId=0"
    fun editHabit(id: Long) = "habitEditor?habitId=$id"
}

/** Access to the app's dependency container from composables. */
@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as LifePilotApp).container

private data class Tab(val route: String, val baseRoute: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "home", "Home", Icons.Rounded.Home),
    Tab(Routes.tasks(), "tasks", "Tasks", Icons.Rounded.CheckCircle),
    Tab(Routes.HABITS, "habits", "Habits", Icons.Rounded.Repeat),
    Tab(Routes.INSIGHTS, "insights", "Insights", Icons.Rounded.Insights),
    Tab(Routes.SETTINGS, "settings", "Settings", Icons.Rounded.Settings),
)

@Composable
fun LifePilotRoot(container: AppContainer, pendingRoute: StateFlow<String?>, onRouteConsumed: () -> Unit) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val s = settings
    LifePilotTheme(themeMode = s?.themeMode ?: com.lifepilot.app.domain.model.ThemeMode.DARK) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (s == null) return@Box // first DataStore read takes a few ms; avoid flashing onboarding
            CompositionLocalProvider(LocalFormats provides remember(s) { Formats(s) }) {
                if (!s.onboardingCompleted) OnboardingScreen()
                else MainScaffold(pendingRoute, onRouteConsumed)
            }
        }
    }
}

@Composable
private fun MainScaffold(pendingRoute: StateFlow<String?>, onRouteConsumed: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = tabs.any { it.route == currentRoute || currentRoute?.startsWith(it.baseRoute + "?") == true || currentRoute == it.baseRoute }
    var quickAddOpen by rememberSaveable { mutableStateOf(false) }

    val route by pendingRoute.collectAsStateWithLifecycle()
    LaunchedEffect(route) {
        route?.let {
            nav.navigate(it) { launchSingleTop = true }
            onRouteConsumed()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { if (showBar) BottomBar(nav, currentRoute) },
    ) { padding ->
        AppNavHost(nav, Modifier.fillMaxSize(), padding, onQuickAdd = { quickAddOpen = true })
    }
    if (quickAddOpen) {
        QuickAddSheet(
            onDismiss = { quickAddOpen = false },
            onOpenFullEditor = { date -> quickAddOpen = false; nav.navigate(Routes.newTask(date)) },
        )
    }
}

/**
 * Switches to a top-level tab.
 *
 * Home is the start destination, so "going home" means popping everything above it. It must not
 * use restoreState: the state saved for the start destination contains whatever was stacked on top
 * of Home, and restoring it would put the user right back on the screen they were leaving.
 */
internal fun NavHostController.navigateToTab(route: String, restore: Boolean = true) {
    if (route == Routes.HOME) {
        if (!popBackStack(Routes.HOME, inclusive = false)) navigate(Routes.HOME) { launchSingleTop = true }
        return
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = restore
    }
}

@Composable
private fun BottomBar(nav: NavHostController, currentRoute: String?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route || currentRoute?.startsWith(tab.baseRoute) == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    nav.navigateToTab(tab.route)
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                modifier = Modifier.testTag("tab_${tab.baseRoute}"),
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, modifier: Modifier, padding: androidx.compose.foundation.layout.PaddingValues, onQuickAdd: () -> Unit) {
    val enter = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } }
    val exit = { fadeOut(tween(180)) }
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        modifier = modifier,
        enterTransition = { enter() },
        exitTransition = { exit() },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 12 } },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                contentPadding = padding,
                onQuickAdd = onQuickAdd,
                onOpenOccurrence = { nav.navigate(Routes.occurrence(it)) },
                // Shortcuts to other tabs switch tabs (like the bottom bar) instead of stacking on Home.
                // A filtered Tasks link opens fresh so the requested filter is applied.
                onOpenTasks = { nav.navigateToTab(Routes.tasks(it), restore = false) },
                onOpenHabits = { nav.navigateToTab(Routes.HABITS) },
                onOpenCalendar = { nav.navigate(Routes.CALENDAR) },
                onOpenPlan = { nav.navigate(Routes.PLAN) },
                onOpenReview = { nav.navigate(Routes.REVIEW) },
                onOpenReliability = { nav.navigate(Routes.RELIABILITY) },
            )
        }
        composable(Routes.TASKS, arguments = listOf(navArgument("filter") { type = NavType.StringType; defaultValue = TaskFilter.ALL.name })) { entry ->
            val filter = TaskFilter.entries.firstOrNull { it.name == entry.arguments?.getString("filter") } ?: TaskFilter.ALL
            TasksScreen(
                initialFilter = filter,
                contentPadding = padding,
                onQuickAdd = onQuickAdd,
                onNewTask = { nav.navigate(Routes.newTask()) },
                onOpenOccurrence = { nav.navigate(Routes.occurrence(it)) },
                onOpenCalendar = { nav.navigate(Routes.CALENDAR) },
            )
        }
        composable(Routes.HABITS) {
            HabitsScreen(contentPadding = padding, onOpenHabit = { nav.navigate(Routes.habit(it)) }, onNewHabit = { nav.navigate(Routes.newHabit()) })
        }
        composable(Routes.INSIGHTS) { InsightsScreen(contentPadding = padding) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                contentPadding = padding,
                onOpenReliability = { nav.navigate(Routes.RELIABILITY) },
                onOpenCategories = { nav.navigate(Routes.CATEGORIES) },
            )
        }
        composable(Routes.OCCURRENCE, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            TaskDetailScreen(
                occurrenceId = entry.arguments?.getLong("id") ?: 0L,
                onBack = { nav.popBackStack() },
                onEdit = { taskId -> nav.navigate(Routes.editTask(taskId)) },
                onOpenOccurrence = { nav.navigate(Routes.occurrence(it)) { popUpTo(Routes.OCCURRENCE) { inclusive = true } } },
            )
        }
        composable(
            Routes.EDITOR,
            arguments = listOf(
                navArgument("taskId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("date") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val date = entry.arguments?.getString("date")?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            TaskEditorScreen(
                taskId = entry.arguments?.getLong("taskId")?.takeIf { it > 0 },
                initialDate = date,
                onDone = { nav.popBackStack() },
            )
        }
        composable(Routes.CALENDAR) {
            CalendarScreen(
                onBack = { nav.popBackStack() },
                onOpenOccurrence = { nav.navigate(Routes.occurrence(it)) },
                onNewTask = { nav.navigate(Routes.newTask(it)) },
            )
        }
        composable(Routes.HABIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            HabitDetailScreen(
                habitId = entry.arguments?.getLong("id") ?: 0L,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.editHabit(it)) },
            )
        }
        composable(Routes.HABIT_EDITOR, arguments = listOf(navArgument("habitId") { type = NavType.LongType; defaultValue = 0L })) { entry ->
            HabitEditorScreen(habitId = entry.arguments?.getLong("habitId")?.takeIf { it > 0 }, onDone = { nav.popBackStack() })
        }
        composable(Routes.RELIABILITY) { ReliabilityScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.CATEGORIES) { CategoriesScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.PLAN) { DailyPlanScreen(onBack = { nav.popBackStack() }, onOpenOccurrence = { nav.navigate(Routes.occurrence(it)) }) }
        composable(Routes.REVIEW) { EndOfDayReviewScreen(onBack = { nav.popBackStack() }, onOpenOccurrence = { nav.navigate(Routes.occurrence(it)) }) }
    }
}
