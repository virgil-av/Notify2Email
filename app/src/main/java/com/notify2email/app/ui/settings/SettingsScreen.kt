package com.notify2email.app.ui.settings

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.notify2email.app.domain.model.AppTheme
import com.notify2email.app.domain.model.NotificationFilterMode
import com.notify2email.app.domain.model.NotificationFilterSettings
import com.notify2email.app.domain.model.PermissionState
import com.notify2email.app.domain.model.SmtpSettings
import com.notify2email.app.email.SendEmailResult
import com.notify2email.app.email.SmtpProviderRules
import com.notify2email.app.email.SmtpTestHelper
import com.notify2email.app.email.TlsMode
import com.notify2email.app.permissions.PermissionNavigator
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import com.notify2email.app.ui.components.InfoDialog
import com.notify2email.app.util.EmailValidator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initialSettings: SmtpSettings,
    notificationFilterSettings: NotificationFilterSettings,
    permissionState: PermissionState,
    onSaveSettings: (SmtpSettings) -> Unit,
    onTestSmtp: ((SmtpSettings) -> Unit)? = null,
    onNotificationFilterModeChanged: (NotificationFilterMode) -> Unit = {},
    onNotificationFilterAppToggled: (String, Boolean) -> Unit = { _, _ -> },
    onAppThemeChanged: (AppTheme) -> Unit = {},
    onPermissionsChanged: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
    initialTabIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(initialTabIndex) }
    
    // Ensure tab updates when navigating from Dashboard with a specific tab index
    LaunchedEffect(initialTabIndex) {
        selectedTabIndex = initialTabIndex
    }

    val tabs = listOf("SMTP", "Access", "Filters")
    
    var infoDialogContent by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = {
                        onAppThemeChanged(
                            if (initialSettings.appTheme == AppTheme.LIGHT) AppTheme.DARK else AppTheme.LIGHT
                        )
                    }) {
                        Icon(
                            imageVector = if (initialSettings.appTheme == AppTheme.LIGHT) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Toggle Theme"
                        )
                    }
                    IconButton(onClick = onOpenLogs) {
                        Icon(Icons.Default.Description, contentDescription = "Logs")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> SmtpSection(
                    settings = initialSettings,
                    onSave = onSaveSettings,
                    onTest = onTestSmtp,
                    onShowInfo = { title, msg -> infoDialogContent = title to msg }
                )
                1 -> AccessSection(
                    permissionState = permissionState,
                    onPermissionsChanged = onPermissionsChanged,
                    onShowInfo = { title, msg -> infoDialogContent = title to msg }
                )
                2 -> FiltersSection(
                    notificationFilterSettings = notificationFilterSettings,
                    onAppToggled = onNotificationFilterAppToggled,
                    onShowInfo = { title, msg -> infoDialogContent = title to msg }
                )
            }
        }
    }

    infoDialogContent?.let { content ->
        InfoDialog(
            title = content.first!!,
            message = content.second!!,
            onDismiss = { infoDialogContent = null }
        )
    }
}

@Composable
private fun SmtpSection(
    settings: SmtpSettings,
    onSave: (SmtpSettings) -> Unit,
    onTest: ((SmtpSettings) -> Unit)?,
    onShowInfo: (String, String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var host by remember(settings) { mutableStateOf(settings.host) }
    var port by remember(settings) { mutableStateOf(settings.port.toString()) }
    var encryption by remember(settings) { mutableStateOf(settings.encryption) }
    var username by remember(settings) { mutableStateOf(settings.username) }
    var password by remember(settings) { mutableStateOf(settings.password) }
    var passwordVisible by remember { mutableStateOf(false) }
    var fromEmail by remember(settings) { mutableStateOf(settings.fromEmail) }
    var toEmailPrimary by remember(settings) { mutableStateOf(settings.toEmailPrimary) }
    var ccEmails by remember(settings) { mutableStateOf<List<String>>(settings.ccEmails) }
    var bccEmails by remember(settings) { mutableStateOf<List<String>>(settings.bccEmails) }
    var subjectPattern by remember(settings) { mutableStateOf(settings.subjectPattern) }
    var enabled by remember(settings) { mutableStateOf(settings.enabled) }

    // Auto-update encryption based on port
    androidx.compose.runtime.LaunchedEffect(port) {
        when (port) {
            "465", "468" -> encryption = TlsMode.SSL_TLS
            "587" -> encryption = TlsMode.STARTTLS
        }
    }

    var fromEmailError by remember { mutableStateOf<String?>(null) }
    var toEmailPrimaryError by remember { mutableStateOf<String?>(null) }
    var ccEmailsErrors by remember { mutableStateOf<List<String?>>(emptyList()) }
    var bccEmailsErrors by remember { mutableStateOf<List<String?>>(emptyList()) }

    fun validate(): Boolean {
        var isValid = true
        
        if (fromEmail.isBlank()) {
            fromEmailError = "Required"
            isValid = false
        } else if (!EmailValidator.isValid(fromEmail)) {
            fromEmailError = "Invalid email"
            isValid = false
        } else {
            fromEmailError = null
        }

        if (toEmailPrimary.isBlank()) {
            toEmailPrimaryError = "Required"
            isValid = false
        } else if (!EmailValidator.isValid(toEmailPrimary)) {
            toEmailPrimaryError = "Invalid email"
            isValid = false
        } else {
            toEmailPrimaryError = null
        }

        val ccErrors = ccEmails.map { email ->
            when {
                email.isBlank() -> null
                !EmailValidator.isValid(email) -> "Invalid format"
                email.trim().lowercase() == toEmailPrimary.trim().lowercase() -> "Duplicate of primary"
                ccEmails.count { it.trim().lowercase() == email.trim().lowercase() } > 1 -> "Duplicate CC"
                else -> null
            }
        }
        ccEmailsErrors = ccErrors
        if (ccErrors.any { it != null }) isValid = false

        val bccErrors = bccEmails.map { email ->
            when {
                email.isBlank() -> null
                !EmailValidator.isValid(email) -> "Invalid format"
                email.trim().lowercase() == toEmailPrimary.trim().lowercase() -> "Duplicate of primary"
                ccEmails.any { it.trim().lowercase() == email.trim().lowercase() } -> "Duplicate of CC"
                bccEmails.count { it.trim().lowercase() == email.trim().lowercase() } > 1 -> "Duplicate BCC"
                else -> null
            }
        }
        bccEmailsErrors = bccErrors
        if (bccErrors.any { it != null }) isValid = false

        return isValid
    }

    val yahooWarning = remember(host) { SmtpProviderRules.yahooPasswordWarning(host) }

    fun currentSettings() = SmtpSettings(
        host = host.trim(),
        port = port.toIntOrNull() ?: 587,
        encryption = encryption,
        username = username.trim(),
        password = password,
        fromEmail = fromEmail.trim(),
        toEmailPrimary = toEmailPrimary.trim(),
        ccEmails = EmailValidator.normalizeRecipients(ccEmails, toEmailPrimary),
        bccEmails = EmailValidator.normalizeRecipients(bccEmails, toEmailPrimary, excludeFrom = ccEmails),
        subjectPattern = subjectPattern.trim(),
        enabled = enabled,
        smsEnabled = settings.smsEnabled,
        callsEnabled = settings.callsEnabled,
        notificationsEnabled = settings.notificationsEnabled
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Delivery Credentials", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = { onShowInfo("SMTP Settings", "Configure the outgoing mail server details (host, port, and security) used to send event notifications. For example, Gmail uses smtp.gmail.com with port 587.") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Info",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("SMTP Host") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        autoCorrect = false
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = port, 
                        onValueChange = { port = it.filter(Char::isDigit) }, 
                        modifier = Modifier.weight(1f), 
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Box(modifier = Modifier.weight(1.5f)) {
                        EncryptionField(selected = encryption, onSelected = { encryption = it })
                    }
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Username") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        autoCorrect = false
                    )
                )
                OutlinedTextField(
                    value = password, 
                    onValueChange = { password = it }, 
                    modifier = Modifier.fillMaxWidth(), 
                    label = { Text("Password") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        val description = if (passwordVisible) "Hide password" else "Show password"
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = description)
                        }
                    }
                )
                yahooWarning?.let {
                    val warningColor = androidx.compose.ui.graphics.Color(0xFFCA8A04) // More readable deep yellow/orange
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                onShowInfo(
                                    "Yahoo App Password",
                                    "Yahoo requires a special 'App Password' for third-party apps to send emails. You can generate this in your Yahoo Account Security page under 'Generate app password'."
                                )
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "More info",
                                modifier = Modifier.size(18.dp),
                                tint = warningColor
                            )
                        }
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = warningColor
                        )
                    }
                }
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Email Addresses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = { onShowInfo("Recipient Emails", "Define the destination email addresses for notifications. A primary recipient is required, while CC and BCC are optional.") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Info",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedTextField(
                    value = fromEmail, 
                    onValueChange = { fromEmail = it; fromEmailError = null }, 
                    modifier = Modifier.fillMaxWidth(), 
                    label = { Text("Sender (From)") },
                    isError = fromEmailError != null,
                    supportingText = fromEmailError?.let { errorText -> { Text(errorText) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        autoCorrect = false
                    )
                )
                OutlinedTextField(
                    value = toEmailPrimary, 
                    onValueChange = { toEmailPrimary = it; toEmailPrimaryError = null }, 
                    modifier = Modifier.fillMaxWidth(), 
                    label = { Text("Primary Recipient") },
                    isError = toEmailPrimaryError != null,
                    supportingText = toEmailPrimaryError?.let { errorText -> { Text(errorText) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        autoCorrect = false
                    )
                )
                
                Text("CC Recipients", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                
                ccEmails.forEachIndexed { index, email ->
                    val error = ccEmailsErrors.getOrNull(index)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { newEmail: String ->
                                val newList = ccEmails.toMutableList()
                                newList[index] = newEmail
                                ccEmails = newList
                                ccEmailsErrors = emptyList() // Clear errors on change
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("CC ${index + 1}") },
                            singleLine = true,
                            isError = error != null,
                            supportingText = error?.let { errorText -> { Text(errorText) } },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                autoCorrect = false
                            )
                        )
                        IconButton(onClick = {
                            val newList = ccEmails.toMutableList()
                            newList.removeAt(index)
                            ccEmails = newList
                            ccEmailsErrors = emptyList()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove CC")
                        }
                    }
                }

                if (ccEmails.size < 5) {
                    OutlinedButton(
                        onClick = { ccEmails = ccEmails + "" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add CC Recipient")
                    }
                }

                Text("BCC Recipients", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                
                bccEmails.forEachIndexed { index, email ->
                    val error = bccEmailsErrors.getOrNull(index)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { newEmail: String ->
                                val newList = bccEmails.toMutableList()
                                newList[index] = newEmail
                                bccEmails = newList
                                bccEmailsErrors = emptyList()
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("BCC ${index + 1}") },
                            singleLine = true,
                            isError = error != null,
                            supportingText = error?.let { errorText -> { Text(errorText) } },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                autoCorrect = false
                            )
                        )
                        IconButton(onClick = {
                            val newList = bccEmails.toMutableList()
                            newList.removeAt(index)
                            bccEmails = newList
                            bccEmailsErrors = emptyList()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove BCC")
                        }
                    }
                }

                if (bccEmails.size < 5) {
                    OutlinedButton(
                        onClick = { bccEmails = bccEmails + "" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add BCC Recipient")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Subject Customization", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    IconButton(
                        onClick = { onShowInfo("Subject Pattern", "Customize the email subject line. Use {type} (e.g., SMS) and {source} (e.g., Contact Name) as placeholders.") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Info",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = subjectPattern,
                    onValueChange = { subjectPattern = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Subject Pattern (Optional)") },
                    placeholder = { Text("[Notify2Email] New {type} from {source}") },
                    singleLine = true
                )
            }
        }

        ListItem(
            headlineContent = { Text("Enable Delivery") },
            supportingContent = { Text("Allow outbound emails to be sent.") },
            trailingContent = { Switch(checked = enabled, onCheckedChange = { enabled = it }) }
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { 
                    if (validate()) {
                        onSave(currentSettings()) 
                    } else {
                        Toast.makeText(context, "Please fix the errors below", Toast.LENGTH_SHORT).show()
                    }
                }, 
                modifier = Modifier.weight(1f)
            ) {
                Text("Save")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    if (validate()) {
                        val s = currentSettings()
                        if (onTest != null) onTest(s) else {
                            coroutineScope.launch {
                                val res = SmtpTestHelper().sendTestEmail(s)
                                Toast.makeText(context, if (res is SendEmailResult.Success) "Success" else "Failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "Please fix the errors below", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("Test Connection")
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun AccessSection(
    permissionState: PermissionState,
    onPermissionsChanged: () -> Unit,
    onShowInfo: (String, String) -> Unit
) {
    val context = LocalContext.current
    
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onPermissionsChanged() }
    val callLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onPermissionsChanged() }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onPermissionsChanged() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("System Access", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = { onShowInfo("Permissions", "Grant the necessary permissions to allow the app to monitor messages, call logs, and app notifications.") },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Info",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AccessCard(
            title = "SMS Access",
            isGranted = permissionState.smsGranted,
            onClick = { smsLauncher.launch(Manifest.permission.RECEIVE_SMS) },
            onInfoClick = { onShowInfo("SMS Access", "Allows the app to detect incoming text messages and forward them to your email.") }
        )

        AccessCard(
            title = "Call Logs",
            isGranted = permissionState.callLogGranted,
            onClick = { callLauncher.launch(Manifest.permission.READ_CALL_LOG) },
            onInfoClick = { onShowInfo("Call Logs", "Allows the app to detect missed and incoming calls to send summary alerts to your email.") }
        )

        AccessCard(
            title = "Notification Listener",
            isGranted = permissionState.notificationAccessGranted,
            onClick = { PermissionNavigator.openNotificationListenerSettings(context) },
            onInfoClick = { onShowInfo("Notification Listener", "Enables the app to capture notifications from other installed applications. You can manage which apps to monitor in the Filters tab.") }
        )

        AccessCard(
            title = "Battery Optimization",
            isGranted = permissionState.batteryOptimizationIgnored,
            onClick = { PermissionNavigator.openAppSettings(context) },
            onInfoClick = { onShowInfo("Battery Optimization", "Prevents Android from pausing the app in the background. This ensures that events are captured and emails are sent without delay.") }
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            AccessCard(
                title = "Service Notification",
                isGranted = permissionState.postNotificationsGranted,
                onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onInfoClick = { onShowInfo("Service Notification", "Enables a persistent notification that ensures the background service remains active and reliable on Android 13 and above.") }
            )
        }

        Spacer(Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = { 
                PermissionNavigator.openAppSettings(context)
                onPermissionsChanged() 
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Settings, null)
            Spacer(Modifier.width(8.dp))
            Text("Android System Settings")
        }
    }
}

@Composable
private fun AccessCard(
    title: String,
    isGranted: Boolean,
    onClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        )
    ) {
        ListItem(
            headlineContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onInfoClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Info",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(title, fontWeight = FontWeight.SemiBold)
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isGranted) {
                        Text("On", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Text("Off", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersSection(
    notificationFilterSettings: NotificationFilterSettings,
    onAppToggled: (String, Boolean) -> Unit,
    onShowInfo: (String, String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(notificationFilterSettings.apps, searchQuery) {
        notificationFilterSettings.apps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Notification Filters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = { onShowInfo("App Filters", "By default, the app monitors notifications from all sources. You can manually disable specific apps here. Note: apps will only appear in this list after they have posted at least one notification while the service is active.") },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Info",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search discovered apps...") },
            leadingIcon = { Icon(Icons.Default.FilterList, null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (filteredApps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize().padding(bottom = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) 
                                "No apps discovered yet.\nApps will appear here after they post a notification." 
                                else "No matching apps found.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                items(filteredApps, key = { it.packageName }) { app ->
                    NotificationFilterRow(
                        appName = app.appName,
                        packageName = app.packageName,
                        enabled = app.enabled,
                        isSystemApp = app.isSystemApp,
                        onToggle = { onAppToggled(app.packageName, it) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationFilterRow(
    appName: String,
    packageName: String,
    enabled: Boolean,
    isSystemApp: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

    ListItem(
        leadingContent = {
            if (icon != null) {
                Image(
                    bitmap = icon.toBitmapSafely().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Card(modifier = Modifier.size(40.dp)) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(appName.take(1).uppercase())
                    }
                }
            }
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(appName, style = MaterialTheme.typography.titleSmall)
                if (isSystemApp) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.extraSmall,
                    ) {
                        Text(
                            text = "SYSTEM",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        },
        supportingContent = { Text(packageName, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EncryptionField(
    selected: TlsMode,
    onSelected: (TlsMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = when (selected) {
                TlsMode.SSL_TLS -> "SSL/TLS"
                TlsMode.STARTTLS -> "STARTTLS"
                TlsMode.NONE -> "None"
            },
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            label = { Text("Security") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(text = { Text("SSL/TLS") }, onClick = { onSelected(TlsMode.SSL_TLS); expanded = false })
            DropdownMenuItem(text = { Text("STARTTLS") }, onClick = { onSelected(TlsMode.STARTTLS); expanded = false })
            DropdownMenuItem(text = { Text("None") }, onClick = { onSelected(TlsMode.NONE); expanded = false })
        }
    }
}

private fun Drawable.toBitmapSafely(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val w = intrinsicWidth.takeIf { it > 0 } ?: 96
    val h = intrinsicHeight.takeIf { it > 0 } ?: 96
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MaterialTheme {
        SettingsScreen(
            initialSettings = SmtpSettings(
                host = "smtp.example.com",
                port = 587,
                encryption = TlsMode.STARTTLS,
                username = "user@example.com",
                password = "secret",
                fromEmail = "from@example.com",
                toEmailPrimary = "primary@example.com",
                ccEmails = listOf("cc1@example.com"),
                enabled = true
            ),
            notificationFilterSettings = NotificationFilterSettings(
                mode = NotificationFilterMode.BLACKLIST
            ),
            permissionState = PermissionState(
                smsGranted = true,
                callLogGranted = false,
                notificationAccessGranted = true,
                postNotificationsGranted = true,
                batteryOptimizationIgnored = false
            ),
            onSaveSettings = {},
            onTestSmtp = {},
            onPermissionsChanged = {},
            onOpenLogs = {}
        )
    }
}
