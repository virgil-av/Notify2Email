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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.clickable
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
    onToggleService: (force: Boolean) -> Unit,
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
                oldestQueueTimeMillis = uiState.oldestQueueTimeMillis,
                onToggleService = { onToggleService(false) }
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

            FooterSection()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    validationDialogMissingItems?.let { missingItems ->
        AlertDialog(
            onDismissRequest = onDismissValidationDialog,
            title = { Text("Start Event Capture") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The service will run in the background to capture your selected phone events and forward them to your configured email address.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (missingItems.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Warning: Some items require attention:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Column(
                            modifier = Modifier.padding(start = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            missingItems.forEach { item ->
                                Text(
                                    text = "• $item",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Text(
                        text = "Would you like to start the service now?",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onToggleService(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (missingItems.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Continue and Start")
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
    oldestQueueTimeMillis: Long?,
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
                            containerColor = Color(0xFFD32F2F), // Distinct Red for Stop
                            contentColor = Color.White
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text(if (isRunning) "Stop" else "Start")
                }
            }

            val oldestQueueTime = oldestQueueTimeMillis
            if (oldestQueueTime != null) {
                var secondsLeft by remember(oldestQueueTime) {
                    val elapsed = System.currentTimeMillis() - oldestQueueTime
                    val remaining = (60000L - elapsed).coerceAtLeast(0L) / 1000
                    mutableLongStateOf(remaining)
                }

                LaunchedEffect(oldestQueueTime) {
                    while (secondsLeft > 0) {
                        delay(1000)
                        val elapsed = System.currentTimeMillis() - oldestQueueTime
                        secondsLeft = (60000L - elapsed).coerceAtLeast(0L) / 1000
                    }
                }

                if (secondsLeft > 0) {
                    Text(
                        text = "Event mail will be sent in: $secondsLeft seconds",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
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
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onDismissClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(end = 24.dp), // Space for close button
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Capture & Status",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                var showInfo by remember { mutableStateOf(false) }
                
                Box {
                    IconButton(
                        onClick = { showInfo = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (showInfo) {
                        AlertDialog(
                            onDismissRequest = { showInfo = false },
                            title = { Text("Information") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Select which activities should be forwarded to your email:", fontWeight = FontWeight.Bold)
                                    Text("• SMS: Incoming text messages.")
                                    Text("• Calls: Missed, rejected, or ignored calls.")
                                    Text("• Apps: Notifications from other selected applications.")
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showInfo = false }) {
                                    Text("Got it")
                                }
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactToggle(
                    icon = Icons.AutoMirrored.Outlined.Message,
                    label = "SMS",
                    checked = smsEnabled,
                    onCheckedChange = onSmsToggleChanged,
                    modifier = Modifier.weight(1f)
                )
                CompactToggle(
                    icon = Icons.Outlined.Call,
                    label = "Calls",
                    checked = callsEnabled,
                    onCheckedChange = onCallsToggleChanged,
                    modifier = Modifier.weight(1f)
                )
                CompactToggle(
                    icon = Icons.Outlined.Notifications,
                    label = "Apps",
                    checked = notificationsEnabled,
                    onCheckedChange = onNotificationsToggleChanged,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CompactToggle(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.7f)
        )
    }
}

@Composable
private fun FooterSection() {
    val uriHandler = LocalUriHandler.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clickable { uriHandler.openUri("https://github.com/virgil-av/Notify2Email") }
                .padding(4.dp)
        ) {
            // Using a simple text for "GitHub" as we don't have the SVG icon in standard Icons
            Text(
                text = "GitHub",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline
            )
            Text(
                text = "Notify2Email",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Text(
            text = "Version 1.1.1",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
            onToggleService = { _ -> },
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
