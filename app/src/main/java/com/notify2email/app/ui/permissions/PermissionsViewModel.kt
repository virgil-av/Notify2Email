package com.notify2email.app.ui.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notify2email.app.domain.model.PermissionState
import com.notify2email.app.domain.repository.PermissionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PermissionsViewModel(
    private val permissionRepository: PermissionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
        viewModelScope.launch {
            val permissionState = permissionRepository.getPermissionState()
            _uiState.value = UiState(
                permissionState = permissionState,
                hasSmsPermission = permissionState.smsGranted,
                hasCallPermission = permissionState.callLogGranted,
                hasNotificationPermission = permissionState.notificationAccessGranted,
                hasPostNotificationsPermission = permissionState.postNotificationsGranted
            )
        }
    }

    data class UiState(
        val permissionState: PermissionState = PermissionState(
            smsGranted = false,
            callLogGranted = false,
            notificationAccessGranted = false,
            postNotificationsGranted = false,
            batteryOptimizationIgnored = false
        ),
        val hasSmsPermission: Boolean = false,
        val hasCallPermission: Boolean = false,
        val hasNotificationPermission: Boolean = false,
        val hasPostNotificationsPermission: Boolean = false
    )
}
