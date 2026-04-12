package com.notify2email.app.ui.events

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.notify2email.app.domain.model.Event
import com.notify2email.app.domain.model.EventType
import com.notify2email.app.domain.model.SentStatus
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    events: List<Event>,
    onDeleteEvent: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("All", "SMS", "Calls", "Notifications")

    val filteredEvents = remember(events, selectedTabIndex) {
        when (selectedTabIndex) {
            1 -> events.filter { it.type == EventType.SMS }
            2 -> events.filter { it.type == EventType.CALL }
            3 -> events.filter { it.type == EventType.NOTIFICATION }
            else -> events
        }
    }

    var showClearAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Events", fontWeight = FontWeight.SemiBold) },
                actions = {
                    if (events.isNotEmpty()) {
                        IconButton(onClick = { showClearAllDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear all events",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            if (index == 0) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            } else {
                                Icon(
                                    imageVector = when (index) {
                                        1 -> Icons.AutoMirrored.Outlined.Message
                                        2 -> Icons.Outlined.Call
                                        else -> Icons.Outlined.Notifications
                                    },
                                    contentDescription = title,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (filteredEvents.isEmpty()) {
                    EmptyState(
                        message = if (events.isEmpty()) "No events yet."
                        else "No ${tabs[selectedTabIndex]} events found."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(
                            items = filteredEvents,
                            key = { it.id }
                        ) { event ->
                            SwipeableEventCard(
                                event = event,
                                onDelete = { onDeleteEvent(event.id) }
                            )
                        }
                    }
                }
            }
        }

        if (showClearAllDialog) {
            AlertDialog(
                onDismissRequest = { showClearAllDialog = false },
                icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                title = { Text("Clear History?") },
                text = { Text("This will permanently delete all captured events. This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onClearAll()
                            showClearAllDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAllDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableEventCard(
    event: Event,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    else -> Color.Transparent
                }, label = "backgroundColor"
            )

            val scale by animateFloatAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) 0.75f else 1f,
                label = "iconScale"
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .background(color, MaterialTheme.shapes.medium)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.scale(scale),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        content = {
            EventCard(event = event)
        }
    )
}

@Composable
private fun EventCard(event: Event) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.small
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = event.source,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Column {
                    Text(
                        text = event.content,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!event.errorMessage.isNullOrBlank()) {
                        Text(
                            text = event.errorMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(event.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            },
            leadingContent = {
                Icon(
                    imageVector = getEventIcon(event.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingContent = {
                StatusIndicator(event.sentStatus)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun StatusIndicator(status: SentStatus) {
    val icon = when (status) {
        SentStatus.SENT -> Icons.Default.CheckCircle
        SentStatus.FAILED -> Icons.Default.Error
        SentStatus.PENDING -> Icons.Default.Schedule
        SentStatus.SKIPPED -> Icons.Default.Block
        SentStatus.WAITING_IN_QUEUE -> Icons.Default.Schedule
        SentStatus.SMTP_NOT_CONFIGURED -> Icons.Default.Warning
    }
    val color = when (status) {
        SentStatus.SENT -> Color(0xFF2E7D32)
        SentStatus.FAILED -> MaterialTheme.colorScheme.error
        SentStatus.PENDING -> Color(0xFFB26A00)
        SentStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
        SentStatus.WAITING_IN_QUEUE -> Color(0xFF0277BD) // Blue for queue
        SentStatus.SMTP_NOT_CONFIGURED -> MaterialTheme.colorScheme.error
    }

    Icon(
        imageVector = icon,
        contentDescription = status.name,
        tint = color.copy(alpha = 0.8f),
        modifier = Modifier.size(16.dp)
    )
}

@Composable
private fun EmptyState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getEventIcon(type: EventType): ImageVector {
    return when (type) {
        EventType.SMS -> Icons.AutoMirrored.Outlined.Message
        EventType.CALL -> Icons.Outlined.Call
        EventType.NOTIFICATION -> Icons.Outlined.Notifications
    }
}

private fun parseEventContent(event: Event): Pair<String, String> {
    return when (event.type) {
        EventType.SMS -> {
            val parts = event.content.split(": ", limit = 2)
            if (parts.size == 2) parts[0] to parts[1]
            else "SMS Message" to event.content
        }
        EventType.CALL -> {
            if (event.content.contains("from ", ignoreCase = true)) {
                "Incoming Call" to event.content.substringAfter("from ").trim()
            } else {
                "Phone Call" to event.content
            }
        }
        EventType.NOTIFICATION -> {
            val parts = event.content.split(": ", limit = 2)
            if (parts.size == 2) parts[0] to parts[1]
            else "Notification" to event.content
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EventsScreenPreview() {
    MaterialTheme {
        EventsScreen(
            events = listOf(
                Event(
                    id = "1",
                    type = EventType.SMS,
                    source = "John",
                    content = "Your package has arrived at the pickup locker.",
                    timestamp = 1760000000000,
                    sentStatus = SentStatus.SENT
                ),
                Event(
                    id = "2",
                    type = EventType.CALL,
                    source = "+40 700 000 000",
                    content = "Incoming call from +40 700 000 000",
                    timestamp = 1760001000000,
                    sentStatus = SentStatus.WAITING_IN_QUEUE
                ),
                Event(
                    id = "3",
                    type = EventType.NOTIFICATION,
                    source = "Slack",
                    content = "Release build passed all checks and was deployed.",
                    timestamp = 1760002000000,
                    sentStatus = SentStatus.SMTP_NOT_CONFIGURED,
                    errorMessage = "SMTP not configured"
                )
            ),
            onDeleteEvent = {},
            onClearAll = {}
        )
    }
}
