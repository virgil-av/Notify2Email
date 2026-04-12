package com.notify2email.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notify2email.app.domain.model.Event
import com.notify2email.app.domain.model.SentStatus
import com.notify2email.app.domain.model.EventType
import com.notify2email.app.domain.repository.EventRepository
import com.notify2email.app.domain.repository.PermissionRepository
import com.notify2email.app.domain.repository.ServiceStateRepository
import com.notify2email.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val eventRepository: EventRepository,
    private val serviceStateRepository: ServiceStateRepository,
    private val settingsRepository: SettingsRepository,
    private val permissionRepository: PermissionRepository
) : ViewModel() {

    private val _validationDialog = MutableStateFlow<List<String>?>(null)
    val validationDialog: StateFlow<List<String>?> = _validationDialog.asStateFlow()

    private val _dismissalConfirmation = MutableStateFlow<DismissalTarget?>(null)
    val dismissalConfirmation: StateFlow<DismissalTarget?> = _dismissalConfirmation.asStateFlow()

    private val _showSmtpWarning = MutableStateFlow(true)
    private val _showPermissionWarning = MutableStateFlow(true)

    private val _navigateToSettings = MutableSharedFlow<Int?>()
    val navigateToSettings: SharedFlow<Int?> = _navigateToSettings.asSharedFlow()

    val uiState: StateFlow<UiState> = combine(
        serviceStateRepository.observeServiceState(),
        eventRepository.observeEvents(),
        settingsRepository.observeSettings(),
        _showSmtpWarning,
        _showPermissionWarning
    ) { serviceState, events, settings, showSmtp, showPermission ->
        UiState(
            isServiceRunning = serviceState.isRunning,
            lastSyncMillis = serviceState.lastUpdatedMillis,
            smsSent = events.count { it.type == EventType.SMS && it.sentStatus == SentStatus.SENT },
            callsSent = events.count { it.type == EventType.CALL && it.sentStatus == SentStatus.SENT },
            notificationsSent = events.count {
                it.type == EventType.NOTIFICATION && it.sentStatus == SentStatus.SENT
            },
            lastEvent = events.maxByOrNull { it.timestamp },
            smsEnabled = settings.smsEnabled,
            callsEnabled = settings.callsEnabled,
            notificationsEnabled = settings.notificationsEnabled,
            isSmtpConfigured = settings.host.isNotBlank() && settings.port > 0 && settings.toEmailPrimary.isNotBlank(),
            showSmtpWarning = showSmtp,
            showPermissionWarning = showPermission
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState()
    )

    fun startService(force: Boolean = false) {
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            val permissionState = permissionRepository.getPermissionState()
            val missingItems = mutableListOf<String>()

            // SMTP Checks
            if (settings.host.isBlank()) missingItems.add("SMTP host")
            if (settings.port <= 0) missingItems.add("SMTP port")
            if (settings.toEmailPrimary.isBlank()) missingItems.add("Recipient email")
            
            // Event Type Checks
            if (!settings.smsEnabled && !settings.callsEnabled && !settings.notificationsEnabled) {
                missingItems.add("At least one event type (SMS/Calls/Notifications)")
            }

            // Permission Checks
            if (!permissionState.smsGranted && settings.smsEnabled) missingItems.add("SMS Permission")
            if (!permissionState.callLogGranted && settings.callsEnabled) missingItems.add("Call Log Permission")
            if (!permissionState.notificationAccessGranted && (settings.notificationsEnabled || settings.smsEnabled || settings.callsEnabled)) {
                 missingItems.add("Notification Access")
            }

            if (!force) {
                _validationDialog.value = missingItems
                return@launch
            }

            _validationDialog.value = null
            serviceStateRepository.startService()
        }
    }

    fun requestDismissal(target: DismissalTarget) {
        _dismissalConfirmation.value = target
    }

    fun confirmDismissal() {
        val target = _dismissalConfirmation.value ?: return
        when (target) {
            DismissalTarget.SMTP -> _showSmtpWarning.value = false
            DismissalTarget.PERMISSIONS -> _showPermissionWarning.value = false
        }
        _dismissalConfirmation.value = null
    }

    fun cancelDismissal() {
        _dismissalConfirmation.value = null
    }

    fun dismissValidationDialog() {
        _validationDialog.value = null
    }

    fun performNavigateToSettings(initialTab: Int? = null) {
        viewModelScope.launch {
            _validationDialog.value = null
            _navigateToSettings.emit(initialTab)
        }
    }

    fun stopService() {
        viewModelScope.launch {
            serviceStateRepository.stopService()
        }
    }

    fun setSmsEnabled(enabled: Boolean) {
        updateEventToggle { copy(smsEnabled = enabled) }
    }

    fun setCallsEnabled(enabled: Boolean) {
        updateEventToggle { copy(callsEnabled = enabled) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        updateEventToggle { copy(notificationsEnabled = enabled) }
    }

    private fun updateEventToggle(transform: com.notify2email.app.domain.model.SmtpSettings.() -> com.notify2email.app.domain.model.SmtpSettings) {
        viewModelScope.launch {
            val current = settingsRepository.getSettings()
            settingsRepository.saveSettings(current.transform())
        }
    }

    data class UiState(
        val isServiceRunning: Boolean = false,
        val lastSyncMillis: Long = 0L,
        val smsSent: Int = 0,
        val callsSent: Int = 0,
        val notificationsSent: Int = 0,
        val lastEvent: Event? = null,
        val smsEnabled: Boolean = true,
        val callsEnabled: Boolean = true,
        val notificationsEnabled: Boolean = true,
        val isSmtpConfigured: Boolean = false,
        val showSmtpWarning: Boolean = true,
        val showPermissionWarning: Boolean = true
    )

    enum class DismissalTarget {
        SMTP, PERMISSIONS
    }
}
