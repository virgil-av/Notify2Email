package com.notify2email.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.notify2email.app.domain.model.Event
import com.notify2email.app.domain.model.EventType
import com.notify2email.app.domain.model.PermissionState
import com.notify2email.app.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardViewModel.UiState,
    permissionState: PermissionState,
    validationDialogMissingItems: List<String>?,
    dismissalConfirmation: DashboardViewModel.DismissalTarget?,
    onToggleService: () -> Unit,
    onSmsToggleChanged: (Boolean) -> Unit,
    onCallsToggleChanged: (Boolean) -> Unit,
    onNotificationsToggleChanged: (Boolean) -> Unit,
    onDismissValidationDialog: () -> Unit,
    onNavigateToSettings: (initialTab: Int?) -> Unit,
    onRequestDismissal: (DashboardViewModel.DismissalTarget) -> Unit,
    onConfirmDismissal: () -> Unit,
    onCancelDismissal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard", fontWeight = FontWeight.SemiBold) }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusCard(
                isRunning = uiState.isServiceRunning,
                lastSyncMillis = uiState.lastSyncMillis,
                onToggleService = onToggleService
            )

            if (uiState.showSmtpWarning && !uiState.isSmtpConfigured) {
                WarningCard(
                    title = "SMTP not configured",
                    message = "Please configure your email settings to enable forwarding.",
                    onFixClick = { onNavigateToSettings(0) },
                    onDismissClick = { onRequestDismissal(DashboardViewModel.DismissalTarget.SMTP) }
                )
            }

            if (uiState.showPermissionWarning) {
                PermissionWarningCard(
                    permissionState = permissionState,
                    onFixClick = { onNavigateToSettings(1) },
                    onDismissClick = { onRequestDismissal(DashboardViewModel.DismissalTarget.PERMISSIONS) }
                )
            }


            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CounterCard(
                    icon = Icons.AutoMirrored.Outlined.Message,
                    label = "SMS",
                    value = uiState.smsSent.toString(),
                    modifier = Modifier.weight(1f)
                )
                CounterCard(
                    icon = Icons.Outlined.Call,
                    label = "Calls",
                    value = uiState.callsSent.toString(),
                    modifier = Modifier.weight(1f)
                )
                CounterCard(
                    icon = Icons.Outlined.Notifications,
                    label = "Notifs",
                    value = uiState.notificationsSent.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            EventToggleCard(
                smsEnabled = uiState.smsEnabled,
                callsEnabled = uiState.callsEnabled,
                notificationsEnabled = uiState.notificationsEnabled,
                onSmsToggleChanged = onSmsToggleChanged,
                onCallsToggleChanged = onCallsToggleChanged,
                onNotificationsToggleChanged = onNotificationsToggleChanged
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    validationDialogMissingItems?.let { missingItems ->
        AlertDialog(
            onDismissRequest = onDismissValidationDialog,
            title = { Text("Configuration Required") },
            text = {
                Column {
                    Text("Please configure the following items before starting the service:")
                    Spacer(Modifier.height(8.dp))
                    missingItems.forEach { item ->
                        Text("• $item", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { onNavigateToSettings(null) }) {
                    Text("Go to Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissValidationDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    dismissalConfirmation?.let { target ->
        AlertDialog(
            onDismissRequest = onCancelDismissal,
            title = { Text("Dismiss Warning") },
            text = {
                Text(
                    "Are you sure you want to dismiss this warning? " +
                            "The service may not function correctly without addressing it."
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDismissal,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Dismiss")
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDismissal) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatusCard(
    isRunning: Boolean,
    lastSyncMillis: Long,
    onToggleService: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Service Status",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isRunning) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRunning) "On" else "Off",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = onToggleService,
                    colors = if (isRunning) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text(if (isRunning) "Stop" else "Start")
                }
            }

            if (lastSyncMillis > 0L) {
                Text(
                    text = "Last activity: ${TimeUtils.formatDateTime(lastSyncMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


@Composable
private fun WarningCard(
    title: String,
    message: String,
    onFixClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismissClick,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    )
                ) {
                    Text("Dismiss")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onFixClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onError,
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Fix Now")
                }
            }
        }
    }
}

@Composable
private fun PermissionWarningCard(
    permissionState: PermissionState,
    onFixClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    val missingPermissions = buildList {
        if (!permissionState.smsGranted) add("SMS")
        if (!permissionState.callLogGranted) add("Call log")
        if (!permissionState.notificationAccessGranted) add("Notification access")
        if (!permissionState.postNotificationsGranted) add("Notifications")
        if (!permissionState.batteryOptimizationIgnored) add("Battery optimization")
    }

    if (missingPermissions.isEmpty()) {
        return
    }

    WarningCard(
        title = "Permissions missing",
        message = missingPermissions.joinToString(", "),
        onFixClick = onFixClick,
        onDismissClick = onDismissClick
    )
}

@Composable
private fun CounterCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EventToggleCard(
    smsEnabled: Boolean,
    callsEnabled: Boolean,
    notificationsEnabled: Boolean,
    onSmsToggleChanged: (Boolean) -> Unit,
    onCallsToggleChanged: (Boolean) -> Unit,
    onNotificationsToggleChanged: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Event Types",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Select which event types should be forwarded to your email address.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            EventToggleRow(
                icon = Icons.AutoMirrored.Outlined.Message,
                label = "SMS",
                checked = smsEnabled,
                onCheckedChange = onSmsToggleChanged
            )
            EventToggleRow(
                icon = Icons.Outlined.Call,
                label = "Calls",
                checked = callsEnabled,
                onCheckedChange = onCallsToggleChanged
            )
            EventToggleRow(
                icon = Icons.Outlined.Notifications,
                label = "Notifications",
                checked = notificationsEnabled,
                onCheckedChange = onNotificationsToggleChanged
            )
        }
    }
}

@Composable
private fun EventToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    MaterialTheme {
        DashboardScreen(
            uiState = DashboardViewModel.UiState(
                isServiceRunning = true,
                lastSyncMillis = 1760000500000,
                smsSent = 12,
                callsSent = 4,
                notificationsSent = 31,
                isSmtpConfigured = false
            ),
            permissionState = PermissionState(
                smsGranted = true,
                callLogGranted = false,
                notificationAccessGranted = true,
                postNotificationsGranted = true,
                batteryOptimizationIgnored = false
            ),
            onToggleService = {},
            onSmsToggleChanged = {},
            onCallsToggleChanged = {},
            onNotificationsToggleChanged = {},
            validationDialogMissingItems = null,
            dismissalConfirmation = null,
            onDismissValidationDialog = {},
            onNavigateToSettings = { _ -> },
            onRequestDismissal = {},
            onConfirmDismissal = {},
            onCancelDismissal = {}
        )
    }
}
