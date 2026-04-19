package com.notify2email.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.notify2email.app.work.WorkScheduler
import com.notify2email.app.domain.model.AppTheme
import com.notify2email.app.domain.model.DeliveryResult
import com.notify2email.app.domain.model.NotificationFilterMode
import com.notify2email.app.domain.model.NotificationFilterSettings
import com.notify2email.app.domain.model.SmtpSettings
import com.notify2email.app.domain.repository.ServiceStateRepository
import com.notify2email.app.domain.repository.SettingsRepository
import com.notify2email.app.email.SmtpProviderRules
import com.notify2email.app.email.TlsMode
import com.notify2email.app.notifications.NotificationFilterManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val serviceStateRepository: ServiceStateRepository,
    private val notificationFilterManager: NotificationFilterManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observeSettings().collect { settings ->
                _uiState.update { current ->
                    current.copy(settings = settings, isSaving = false, isTesting = false)
                }
            }
        }
        viewModelScope.launch {
            notificationFilterManager.observeSettings().collect { filterSettings ->
                _uiState.update { current ->
                    current.copy(notificationFilterSettings = filterSettings)
                }
            }
        }
    }

    fun saveSettings(settings: SmtpSettings) {
        viewModelScope.launch {
            val normalizedSettings = SmtpProviderRules.normalizeSettings(settings)
            val validationMessage = validateSettings(normalizedSettings)
            if (validationMessage != null) {
                _messages.emit(validationMessage)
                return@launch
            }
            _uiState.update { it.copy(isSaving = true) }
            settingsRepository.saveSettings(normalizedSettings)
            
            // Re-schedule health report in case interval or enabled status changed
            WorkScheduler.scheduleHealthReport(
                context = context,
                intervalHours = normalizedSettings.healthReportIntervalHours,
                enabled = normalizedSettings.healthReportEnabled
            )

            if (!normalizedSettings.enabled) {
                serviceStateRepository.stopService()
            }
            _uiState.update { it.copy(isSaving = false) }
            _messages.emit(buildSaveMessage(normalizedSettings))
        }
    }

    fun testSmtp(settings: SmtpSettings) {
        viewModelScope.launch {
            val normalizedSettings = SmtpProviderRules.normalizeSettings(settings)
            val validationMessage = validateSettings(normalizedSettings)
            if (validationMessage != null) {
                _messages.emit(validationMessage)
                return@launch
            }
            _uiState.update { it.copy(isTesting = true) }
            val result = settingsRepository.testSmtp(normalizedSettings)
            _uiState.update { it.copy(isTesting = false) }
            _messages.emit(result.toMessage(normalizedSettings))
        }
    }

    fun setNotificationFilterMode(mode: NotificationFilterMode) {
        viewModelScope.launch {
            notificationFilterManager.setMode(mode)
        }
    }

    fun setNotificationFilterAppEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            notificationFilterManager.setAppEnabled(packageName, enabled)
        }
    }

    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch {
            val currentSettings = _uiState.value.settings
            settingsRepository.saveSettings(currentSettings.copy(appTheme = theme))
        }
    }

    private fun validateSettings(settings: SmtpSettings): String? {
        return when {
            settings.host.isBlank() -> "SMTP host is required."
            settings.port !in 1..65535 -> "SMTP port must be between 1 and 65535."
            settings.username.isBlank() -> "SMTP username is required."
            settings.password.isBlank() -> "SMTP password is required."
            settings.fromEmail.isBlank() -> "From email is required."
            settings.toEmailPrimary.isBlank() -> "Primary recipient email is required."
            else -> null
        }
    }

    private fun buildSaveMessage(settings: SmtpSettings): String {
        return "Settings saved."
    }

    private fun DeliveryResult.toMessage(settings: SmtpSettings): String {
        return when (this) {
            is DeliveryResult.Success -> "SMTP test email sent successfully."
            is DeliveryResult.Failure -> "SMTP test failed: $message"
            DeliveryResult.Disabled -> "Email sending is disabled."
        }
    }

    data class UiState(
        val settings: SmtpSettings = SmtpSettings(
            host = "",
            port = 587,
            encryption = TlsMode.STARTTLS,
            username = "",
            password = "",
            fromEmail = "",
            toEmailPrimary = "",
            ccEmails = emptyList(),
            bccEmails = emptyList(),
            enabled = false,
            appTheme = AppTheme.LIGHT,
            healthReportEnabled = false,
            healthReportIntervalHours = 1
        ),
        val notificationFilterSettings: NotificationFilterSettings = NotificationFilterSettings(),
        val isSaving: Boolean = false,
        val isTesting: Boolean = false
    )
}
