package com.notify2email.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val routePattern: String = route
) {
    data object Dashboard : AppDestination(
        route = "dashboard",
        label = "Dashboard",
        icon = Icons.Outlined.Home
    )

    data object Events : AppDestination(
        route = "events",
        label = "Events",
        icon = Icons.Outlined.History
    )

    data object Settings : AppDestination(
        route = "settings",
        label = "Settings",
        icon = Icons.Outlined.Settings,
        routePattern = "settings?tab={tab}"
    ) {
        fun createRoute(tab: Int?) = if (tab != null) "settings?tab=$tab" else "settings"
    }

    data object Logs : AppDestination(
        route = "logs",
        label = "Logs",
        icon = Icons.AutoMirrored.Outlined.Notes
    )
}

val bottomNavDestinations = listOf(
    AppDestination.Dashboard,
    AppDestination.Events,
    AppDestination.Settings
)
