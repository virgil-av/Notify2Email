package com.notify2email.app.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.notify2email.app.MainActivity
import com.notify2email.app.R
import com.notify2email.app.email.HtmlEmail
import com.notify2email.app.email.SendEmailResult
import com.notify2email.app.email.SmtpConfig
import com.notify2email.app.email.SmtpConfigProvider
import com.notify2email.app.email.SmtpEmailSender
import com.notify2email.app.email.SmtpTestHelper
import com.notify2email.app.email.TlsMode
import com.notify2email.app.events.EventsActivity
import com.notify2email.app.notifications.PhoneNotificationListenerService
import com.notify2email.app.permissions.AppPermission
import com.notify2email.app.permissions.PermissionManager
import com.notify2email.app.permissions.PermissionNavigator
import com.notify2email.app.services.EventForegroundService
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.notify2email.app.domain.model.SmtpSettings

class SettingsActivity : AppCompatActivity() {

    private lateinit var smtpHostInput: EditText
    private lateinit var smtpPortInput: EditText
    private lateinit var encryptionInput: AutoCompleteTextView
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var fromEmailInput: EditText
    private lateinit var toEmailInput: EditText
    private lateinit var ccEmailsInput: EditText
    private lateinit var bccEmailsInput: EditText
    private lateinit var enableSendingSwitch: MaterialSwitch
    private lateinit var saveButton: MaterialButton
    private lateinit var testButton: MaterialButton
    private lateinit var smsPermissionButton: MaterialButton
    private lateinit var callLogPermissionButton: MaterialButton
    private lateinit var notificationAccessButton: MaterialButton

    private val preferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private lateinit var permissionManager: PermissionManager
    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pendingPostNotificationsGrantedAction: (() -> Unit)? = null

    private val smsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            handlePermissionResult(
                appPermission = AppPermission.READ_SMS,
                granted = granted,
                grantedMessage = R.string.settings_sms_permission_granted,
                deniedMessage = R.string.settings_sms_permission_denied
            )
        }

    private val callLogPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            handlePermissionResult(
                appPermission = AppPermission.READ_CALL_LOG,
                granted = granted,
                grantedMessage = R.string.settings_call_permission_granted,
                deniedMessage = R.string.settings_call_permission_denied
            )
        }

    private val postNotificationsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            handlePermissionResult(
                appPermission = AppPermission.POST_NOTIFICATIONS,
                granted = granted,
                grantedMessage = R.string.settings_notifications_permission_granted,
                deniedMessage = R.string.settings_notifications_permission_denied
            )
            if (granted) {
                pendingPostNotificationsGrantedAction?.invoke()
            }
            pendingPostNotificationsGrantedAction = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        permissionManager = PermissionManager(this)
        bindViews()
        bindNavigation()
        configureEncryptionMenu()
        populateFields()
        bindActions()
    }

    override fun onDestroy() {
        screenScope.cancel()
        super.onDestroy()
    }

    private fun bindViews() {
        smtpHostInput = findViewById(R.id.edit_smtp_host)
        smtpPortInput = findViewById(R.id.edit_smtp_port)
        encryptionInput = findViewById(R.id.edit_encryption)
        usernameInput = findViewById(R.id.edit_username)
        passwordInput = findViewById(R.id.edit_password)
        fromEmailInput = findViewById(R.id.edit_from_email)
        toEmailInput = findViewById(R.id.edit_to_email)
        ccEmailsInput = findViewById(R.id.edit_cc_emails)
        bccEmailsInput = findViewById(R.id.edit_bcc_emails)
        enableSendingSwitch = findViewById(R.id.switch_enable_sending)
        saveButton = findViewById(R.id.button_save_settings)
        testButton = findViewById(R.id.button_test_smtp)
        smsPermissionButton = findViewById(R.id.button_request_sms_permission)
        callLogPermissionButton = findViewById(R.id.button_request_call_permission)
        notificationAccessButton = findViewById(R.id.button_open_notification_access)
    }

    private fun bindNavigation() {
        findViewById<MaterialButton>(R.id.button_nav_home).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        findViewById<MaterialButton>(R.id.button_nav_events).setOnClickListener {
            startActivity(Intent(this, EventsActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.button_nav_settings).isEnabled = false
    }

    private fun configureEncryptionMenu() {
        val items = listOf(TlsMode.STARTTLS.name, TlsMode.SSL_TLS.name, TlsMode.NONE.name)
        encryptionInput.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                items
            )
        )
    }

    private fun populateFields() {
        smtpHostInput.setText(preferences.getString(KEY_SMTP_HOST, ""))
        smtpPortInput.setText(preferences.getInt(KEY_SMTP_PORT, DEFAULT_SMTP_PORT).toString())
        encryptionInput.setText(preferences.getString(KEY_TLS_MODE, TlsMode.STARTTLS.name), false)
        usernameInput.setText(preferences.getString(KEY_SMTP_USERNAME, ""))
        passwordInput.setText(preferences.getString(KEY_PASSWORD, ""))
        fromEmailInput.setText(preferences.getString(KEY_FROM_ADDRESS, ""))
        toEmailInput.setText(preferences.getString(KEY_TO_ADDRESS_PRIMARY, ""))
        ccEmailsInput.setText(preferences.getString(KEY_CC_ADDRESSES, ""))
        bccEmailsInput.setText(preferences.getString(KEY_BCC_ADDRESSES, ""))
        enableSendingSwitch.isChecked = preferences.getBoolean(SmtpConfigProvider.KEY_EMAIL_ENABLED, true)
        updatePermissionButtons()
    }

    private fun bindActions() {
        saveButton.setOnClickListener { saveSettings() }
        testButton.setOnClickListener { testSmtp() }
        smsPermissionButton.setOnClickListener {
            requestRuntimePermission(
                appPermission = AppPermission.READ_SMS,
                launcher = { smsPermissionLauncher.launch(Manifest.permission.READ_SMS) }
            )
        }
        callLogPermissionButton.setOnClickListener {
            requestRuntimePermission(
                appPermission = AppPermission.READ_CALL_LOG,
                launcher = { callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG) }
            )
        }
        notificationAccessButton.setOnClickListener {
            PermissionNavigator.openNotificationListenerSettings(this)
        }
    }

    private fun saveSettings() {
        val host = smtpHostInput.text.toString().trim()
        val port = smtpPortInput.text.toString().trim().toIntOrNull()
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString()
        val fromEmail = fromEmailInput.text.toString().trim()
        val toEmail = toEmailInput.text.toString().trim()
        val ccEmails = ccEmailsInput.text.toString().trim()
        val bccEmails = bccEmailsInput.text.toString().trim()
        val enableSending = enableSendingSwitch.isChecked

        if (host.isBlank()) {
            smtpHostInput.error = getString(R.string.settings_error_host_required)
            smtpHostInput.requestFocus()
            return
        }
        if (port == null || port !in 1..65535) {
            smtpPortInput.error = getString(R.string.settings_error_port_invalid)
            smtpPortInput.requestFocus()
            return
        }
        if (username.isBlank()) {
            usernameInput.error = getString(R.string.settings_error_username_required)
            usernameInput.requestFocus()
            return
        }
        if (fromEmail.isBlank()) {
            fromEmailInput.error = getString(R.string.settings_error_from_required)
            fromEmailInput.requestFocus()
            return
        }
        if (toEmail.isBlank()) {
            toEmailInput.error = getString(R.string.settings_error_to_required)
            toEmailInput.requestFocus()
            return
        }

        preferences.edit()
            .putString(KEY_SMTP_HOST, host)
            .putInt(KEY_SMTP_PORT, port)
            .putString(KEY_SMTP_USERNAME, username)
            .putString(KEY_FROM_ADDRESS, fromEmail)
            .putString(KEY_TO_ADDRESS_PRIMARY, toEmail)
            .putString(KEY_CC_ADDRESSES, ccEmails)
            .putString(KEY_BCC_ADDRESSES, bccEmails)
            .putString(KEY_PASSWORD, password)
            .putString(KEY_TLS_MODE, encryptionInput.text.toString().ifBlank { TlsMode.STARTTLS.name })
            .putBoolean(SmtpConfigProvider.KEY_EMAIL_ENABLED, enableSending)
            .putBoolean(SmtpConfigProvider.KEY_NOTIFICATIONS_ENABLED, enableSending)
            .putBoolean(SmtpConfigProvider.KEY_SMS_ENABLED, enableSending)
            .putBoolean(SmtpConfigProvider.KEY_CALL_LOGS_ENABLED, enableSending)
            .apply()

        if (enableSending) {
            requestPostNotificationsPermissionIfNeeded {
                EventForegroundService.start(this)
            }
        } else {
            EventForegroundService.stop(this)
        }

        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        updatePermissionButtons()
    }

    private fun testSmtp() {
        val settings = buildSmtpSettingsOrNull() ?: return
        testButton.isEnabled = false

        screenScope.launch {
            val result = withContext(Dispatchers.IO) {
                SmtpTestHelper().sendTestEmail(settings)
            }
            testButton.isEnabled = true
            val message = when (result) {
                is SendEmailResult.Success -> getString(R.string.settings_test_success)
                is SendEmailResult.TransientFailure -> getString(R.string.settings_test_failure, result.error)
                is SendEmailResult.PermanentFailure -> getString(R.string.settings_test_failure, result.error)
                is SendEmailResult.RetryExhausted -> getString(R.string.settings_test_failure, result.error)
            }
            Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun buildSmtpSettingsOrNull(): SmtpSettings? {
        val host = smtpHostInput.text.toString().trim()
        val port = smtpPortInput.text.toString().trim().toIntOrNull()
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString()
        val encryption = encryptionInput.text.toString().ifBlank { TlsMode.STARTTLS.name }
        val fromEmail = fromEmailInput.text.toString().trim()
        val toEmail = toEmailInput.text.toString().trim()
        val ccEmails = ccEmailsInput.text.toString().trim()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val bccEmails = bccEmailsInput.text.toString().trim()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (host.isBlank() || port == null || username.isBlank() || password.isBlank() ||
            fromEmail.isBlank() || toEmail.isBlank()
        ) {
            Toast.makeText(this, R.string.settings_test_missing_fields, Toast.LENGTH_SHORT).show()
            return null
        }

        return SmtpSettings(
            host = host,
            port = port,
            encryption = runCatching { TlsMode.valueOf(encryption) }.getOrDefault(TlsMode.STARTTLS),
            username = username,
            password = password,
            fromEmail = fromEmail,
            toEmailPrimary = toEmail,
            ccEmails = ccEmails,
            bccEmails = bccEmails,
            enabled = enableSendingSwitch.isChecked
        )
    }

    private fun updatePermissionButtons() {
        smsPermissionButton.text = if (permissionManager.isGranted(AppPermission.READ_SMS)) {
            getString(R.string.settings_sms_permission_granted_button)
        } else {
            getString(R.string.settings_sms_permission_button)
        }
        callLogPermissionButton.text = if (permissionManager.isGranted(AppPermission.READ_CALL_LOG)) {
            getString(R.string.settings_call_permission_granted_button)
        } else {
            getString(R.string.settings_call_permission_button)
        }
        notificationAccessButton.text = if (PhoneNotificationListenerService.isAccessGranted(this)) {
            getString(R.string.settings_notification_access_connected)
        } else {
            getString(R.string.settings_notification_access_button)
        }
    }

    private fun requestRuntimePermission(
        appPermission: AppPermission,
        launcher: () -> Unit
    ) {
        if (permissionManager.isGranted(appPermission)) {
            Toast.makeText(this, "${appPermission.title} already granted", Toast.LENGTH_SHORT).show()
            updatePermissionButtons()
            return
        }

        if (permissionManager.isPermanentlyDenied(appPermission)) {
            permissionManager.showPermanentlyDeniedDialog(appPermission)
            return
        }

        if (permissionManager.shouldShowRationale(appPermission)) {
            permissionManager.showDeniedExplanation(appPermission) {
                permissionManager.markRequested(appPermission)
                launcher()
            }
            return
        }

        permissionManager.markRequested(appPermission)
        launcher()
    }

    private fun requestPostNotificationsPermissionIfNeeded(onGrantedOrNotRequired: () -> Unit) {
        if (!AppPermission.POST_NOTIFICATIONS.isRuntimePermissionRequired()) {
            onGrantedOrNotRequired()
            return
        }

        if (permissionManager.isGranted(AppPermission.POST_NOTIFICATIONS)) {
            onGrantedOrNotRequired()
            return
        }

        pendingPostNotificationsGrantedAction = onGrantedOrNotRequired

        if (permissionManager.isPermanentlyDenied(AppPermission.POST_NOTIFICATIONS)) {
            permissionManager.showPermanentlyDeniedDialog(AppPermission.POST_NOTIFICATIONS)
            pendingPostNotificationsGrantedAction = null
            return
        }

        if (permissionManager.shouldShowRationale(AppPermission.POST_NOTIFICATIONS)) {
            permissionManager.showDeniedExplanation(AppPermission.POST_NOTIFICATIONS) {
                permissionManager.markRequested(AppPermission.POST_NOTIFICATIONS)
                postNotificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            return
        }

        permissionManager.markRequested(AppPermission.POST_NOTIFICATIONS)
        postNotificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun handlePermissionResult(
        appPermission: AppPermission,
        granted: Boolean,
        grantedMessage: Int,
        deniedMessage: Int
    ) {
        if (granted) {
            Toast.makeText(this, grantedMessage, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, deniedMessage, Toast.LENGTH_SHORT).show()
            if (permissionManager.isPermanentlyDenied(appPermission)) {
                permissionManager.showPermanentlyDeniedDialog(appPermission)
            } else {
                permissionManager.showDeniedExplanation(appPermission)
            }
        }
        updatePermissionButtons()
    }

    companion object {
        private const val PREFS_NAME = "smtp_settings"
        private const val KEY_SMTP_HOST = "smtp_host"
        private const val KEY_SMTP_PORT = "smtp_port"
        private const val KEY_SMTP_USERNAME = "smtp_username"
        private const val KEY_FROM_ADDRESS = "smtp_from_address"
        private const val KEY_TO_ADDRESS_PRIMARY = "smtp_to_address_primary"
        private const val KEY_CC_ADDRESSES = "smtp_cc_addresses"
        private const val KEY_BCC_ADDRESSES = "smtp_bcc_addresses"
        private const val KEY_TLS_MODE = "smtp_tls_mode"
        private const val KEY_PASSWORD = "smtp_password"
        private const val DEFAULT_SMTP_PORT = 587
    }
}
