package com.notify2email.app

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notify2email.app.di.appContainer
import com.notify2email.app.ui.AppViewModelFactory
import com.notify2email.app.ui.theme.PhoneEventsToEmailTheme
import com.notify2email.app.ui.dashboard.DashboardViewModel
import com.notify2email.app.ui.events.EventsViewModel
import com.notify2email.app.ui.logs.LogsViewModel
import com.notify2email.app.ui.navigation.AppNavGraph
import com.notify2email.app.ui.permissions.PermissionsViewModel
import com.notify2email.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private var pendingStartServiceAfterPermission = false
    private var lastToastLogId: Long? = null
    private val viewModelFactory by lazy { AppViewModelFactory(applicationContext.appContainer) }
    private val dashboardViewModel: DashboardViewModel by viewModels { viewModelFactory }
    private val eventsViewModel: EventsViewModel by viewModels { viewModelFactory }
    private val logsViewModel: LogsViewModel by viewModels { viewModelFactory }
    private val settingsViewModel: SettingsViewModel by viewModels { viewModelFactory }
    private val permissionsViewModel: PermissionsViewModel by viewModels { viewModelFactory }

    private val postNotificationsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && pendingStartServiceAfterPermission) {
                dashboardViewModel.startService()
            } else if (!granted && pendingStartServiceAfterPermission) {
                Toast.makeText(
                    this,
                    "Notification permission is required to run the foreground service.",
                    Toast.LENGTH_LONG
                ).show()
            }
            pendingStartServiceAfterPermission = false
            permissionsViewModel.refreshPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
            val eventsState by eventsViewModel.uiState.collectAsStateWithLifecycle()
            val logsState by logsViewModel.uiState.collectAsStateWithLifecycle()
            val permissionsState by permissionsViewModel.uiState.collectAsStateWithLifecycle()

            PhoneEventsToEmailTheme(appTheme = settingsState.settings.appTheme) {
                LaunchedEffect(Unit) {
                    settingsViewModel.messages.collectLatest { message ->
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                    }
                }

                LaunchedEffect(logsState.items.firstOrNull()?.id) {
                    val newestLog = logsState.items.firstOrNull() ?: return@LaunchedEffect
                    if (newestLog.id == lastToastLogId) return@LaunchedEffect

                    if (
                        newestLog.message.contains("email sent", ignoreCase = true) ||
                        newestLog.message.contains("delivered to", ignoreCase = true) ||
                        newestLog.message.contains("batched email sent successfully", ignoreCase = true)
                    ) {
                        lastToastLogId = newestLog.id
                        Toast.makeText(this@MainActivity, "Email sent", Toast.LENGTH_SHORT).show()
                    }
                }

                AppNavGraph(
                    dashboardViewModel = dashboardViewModel,
                    dashboardState = dashboardState,
                    events = eventsState.items,
                    logs = logsState.items,
                    smtpSettings = settingsState.settings,
                    notificationFilterSettings = settingsState.notificationFilterSettings,
                    permissionState = permissionsState.permissionState,
                    onToggleService = {
                        handleServiceToggle(dashboardState.isServiceRunning)
                    },
                    onSmsToggleChanged = dashboardViewModel::setSmsEnabled,
                    onCallsToggleChanged = dashboardViewModel::setCallsEnabled,
                    onNotificationsToggleChanged = dashboardViewModel::setNotificationsEnabled,
                    onDeleteEvent = eventsViewModel::deleteEvent,
                    onClearAllEvents = eventsViewModel::clearAllEvents,
                    onClearLogs = logsViewModel::clearLogs,
                    onSaveSettings = settingsViewModel::saveSettings,
                    onTestSmtp = settingsViewModel::testSmtp,
                    onNotificationFilterModeChanged = settingsViewModel::setNotificationFilterMode,
                    onNotificationFilterAppToggled = settingsViewModel::setNotificationFilterAppEnabled,
                    onAppThemeChanged = settingsViewModel::setAppTheme,
                    onPermissionsChanged = permissionsViewModel::refreshPermissions
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionsViewModel.refreshPermissions()
    }

    private fun handleServiceToggle(isRunning: Boolean) {
        if (isRunning) {
            dashboardViewModel.stopService()
            return
        }

        if (needsPostNotificationsPermission()) {
            pendingStartServiceAfterPermission = true
            postNotificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        dashboardViewModel.startService()
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun needsPostNotificationsPermission(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    }
}
