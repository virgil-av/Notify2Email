package com.notify2email.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.notify2email.app.domain.model.Event
import com.notify2email.app.domain.model.AppLog
import com.notify2email.app.domain.model.NotificationFilterMode
import com.notify2email.app.domain.model.NotificationFilterSettings
import com.notify2email.app.domain.model.PermissionState
import com.notify2email.app.domain.model.SmtpSettings
import com.notify2email.app.ui.dashboard.DashboardScreen
import com.notify2email.app.ui.dashboard.DashboardViewModel
import com.notify2email.app.ui.events.EventsScreen
import com.notify2email.app.ui.logs.LogsScreen
import com.notify2email.app.ui.settings.SettingsScreen

@Composable
fun AppNavGraph(
    dashboardViewModel: DashboardViewModel,
    dashboardState: DashboardViewModel.UiState,
    events: List<Event>,
    logs: List<AppLog>,
    smtpSettings: SmtpSettings,
    notificationFilterSettings: NotificationFilterSettings,
    permissionState: PermissionState,
    onToggleService: (force: Boolean) -> Unit,
    onSmsToggleChanged: (Boolean) -> Unit,
    onCallsToggleChanged: (Boolean) -> Unit,
    onNotificationsToggleChanged: (Boolean) -> Unit,
    onHealthReportToggleChanged: (Boolean) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onClearAllEvents: () -> Unit,
    onClearLogs: () -> Unit,
    onSaveSettings: (SmtpSettings) -> Unit,
    onTestSmtp: (SmtpSettings) -> Unit,
    onNotificationFilterModeChanged: (NotificationFilterMode) -> Unit,
    onNotificationFilterAppToggled: (String, Boolean) -> Unit,
    onAppThemeChanged: (com.notify2email.app.domain.model.AppTheme) -> Unit,
    onPermissionsChanged: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry.value?.destination

    val validationDialogState by dashboardViewModel.validationDialog.collectAsStateWithLifecycle()
    val dismissalConfirmationState by dashboardViewModel.dismissalConfirmation.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        dashboardViewModel.navigateToSettings.collectLatest { initialTab ->
            navController.navigate(AppDestination.Settings.createRoute(initialTab)) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val showBottomBar = currentDestination
        ?.hierarchy
        ?.any { destination ->
            bottomNavDestinations.any { it.route == destination.route }
        } == true || currentDestination?.route?.startsWith("settings") == true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavDestinations.forEach { destination ->
                        val selected = currentDestination
                            ?.hierarchy
                            ?.any { it.route == destination.route } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label
                                )
                            },
                            label = {
                                Text(destination.label)
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppDestination.Dashboard.route) {
                DashboardScreen(
                    uiState = dashboardState,
                    permissionState = permissionState,
                    validationDialogMissingItems = validationDialogState,
                    dismissalConfirmation = dismissalConfirmationState,
                    onToggleService = onToggleService,
                    onSmsToggleChanged = onSmsToggleChanged,
                    onCallsToggleChanged = onCallsToggleChanged,
                    onNotificationsToggleChanged = onNotificationsToggleChanged,
                    onHealthReportToggleChanged = onHealthReportToggleChanged,
                    onDismissValidationDialog = dashboardViewModel::dismissValidationDialog,
                    onNavigateToSettings = { initialTab -> dashboardViewModel.performNavigateToSettings(initialTab) },
                    onRequestDismissal = dashboardViewModel::requestDismissal,
                    onConfirmDismissal = dashboardViewModel::confirmDismissal,
                    onCancelDismissal = dashboardViewModel::cancelDismissal
                )
            }

            composable(AppDestination.Events.route) {
                EventsScreen(
                    events = events,
                    onDeleteEvent = onDeleteEvent,
                    onClearAll = onClearAllEvents
                )
            }

            composable(
                route = AppDestination.Settings.routePattern,
                arguments = listOf(
                    navArgument("tab") {
                        type = NavType.IntType
                        defaultValue = 0
                    }
                )
            ) { backStackEntry ->
                val initialTab = backStackEntry.arguments?.getInt("tab") ?: 0
                SettingsScreen(
                    initialSettings = smtpSettings,
                    notificationFilterSettings = notificationFilterSettings,
                    permissionState = permissionState,
                    onSaveSettings = onSaveSettings,
                    onTestSmtp = onTestSmtp,
                    onNotificationFilterModeChanged = onNotificationFilterModeChanged,
                    onNotificationFilterAppToggled = onNotificationFilterAppToggled,
                    onAppThemeChanged = onAppThemeChanged,
                    onPermissionsChanged = onPermissionsChanged,
                    onOpenLogs = {
                        navController.navigate(AppDestination.Logs.route)
                    },
                    initialTabIndex = initialTab
                )
            }

            composable(AppDestination.Logs.route) {
                LogsScreen(
                    logs = logs,
                    onBack = { navController.popBackStack() },
                    onClearLogs = onClearLogs
                )
            }
        }
    }
}
