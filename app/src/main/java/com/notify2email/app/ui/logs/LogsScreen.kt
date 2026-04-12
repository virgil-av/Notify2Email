package com.notify2email.app.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notify2email.app.domain.model.AppLog
import com.notify2email.app.domain.model.LogLevel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    logs: List<AppLog>,
    onBack: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "System Terminal",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color(0xFFE0E0E0),
                    navigationIconContentColor = Color(0xFFE0E0E0),
                    actionIconContentColor = Color(0xFFE0E0E0)
                )
            )
        },
        containerColor = Color(0xFF000000)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            if (logs.isEmpty()) {
                Text(
                    "NO_LOG_DATA_AVAILABLE",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF444444),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        TerminalLogRow(log)
                    }
                }
            }
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                containerColor = Color(0xFF1E1E1E),
                titleContentColor = Color.White,
                textContentColor = Color.LightGray,
                title = { Text("PURGE_ALL_LOGS?", fontFamily = FontFamily.Monospace) },
                text = { Text("Execution of this command will delete all local event history.", fontFamily = FontFamily.Monospace) },
                confirmButton = {
                    TextButton(onClick = {
                        showClearDialog = false
                        onClearLogs()
                    }) {
                        Text("PROCEED", color = Color.Red, fontFamily = FontFamily.Monospace)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("ABORT", color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                }
            )
        }
    }
}

@Composable
private fun TerminalLogRow(log: AppLog) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val timestamp = timeFormatter.format(Date(log.timestamp))

    val levelColor = when (log.level) {
        LogLevel.DEBUG -> Color(0xFF6272A4) // Muted blue
        LogLevel.INFO -> Color(0xFF50FA7B)  // Green
        LogLevel.WARN -> Color(0xFFFFB86C)  // Orange
        LogLevel.ERROR -> Color(0xFFFF5555) // Red
    }

    val levelTag = when (log.level) {
        LogLevel.DEBUG -> "DBG"
        LogLevel.INFO -> "INF"
        LogLevel.WARN -> "WRN"
        LogLevel.ERROR -> "ERR"
    }

    Text(
        text = buildAnnotatedString {
            // Timestamp
            withStyle(style = SpanStyle(color = Color(0xFF999999))) {
                append("[$timestamp] ")
            }
            
            // Level Tag
            withStyle(style = SpanStyle(color = levelColor, fontWeight = FontWeight.Bold)) {
                append("$levelTag: ")
            }
            
            // Message
            withStyle(style = SpanStyle(color = Color(0xFFE0E0E0))) {
                append(log.message)
            }
        },
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = Modifier.padding(vertical = 1.dp)
    )
}

@Preview
@Composable
private fun LogsScreenPreview() {
    LogsScreen(
        logs = listOf(
            AppLog(1, System.currentTimeMillis() - 5000, "Initializing kernel components...", LogLevel.INFO),
            AppLog(2, System.currentTimeMillis() - 4000, "Scanning for system permissions: GRANTED", LogLevel.DEBUG),
            AppLog(3, System.currentTimeMillis() - 3000, "SMTP_HANDSHAKE: Connected to smtp.gmail.com", LogLevel.INFO),
            AppLog(4, System.currentTimeMillis() - 2000, "LOW_BATTERY_WARNING: Optimization might be throttled", LogLevel.WARN),
            AppLog(5, System.currentTimeMillis() - 1000, "CRITICAL: SSL_CERT_EXPIRED in handshake", LogLevel.ERROR)
        ),
        onBack = {},
        onClearLogs = {}
    )
}
