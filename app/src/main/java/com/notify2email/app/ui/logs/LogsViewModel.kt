package com.notify2email.app.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notify2email.app.domain.model.AppLog
import com.notify2email.app.domain.repository.LogRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LogsViewModel(
    private val logRepository: LogRepository
) : ViewModel() {

    val uiState: StateFlow<UiState> = logRepository.observeLogs()
        .map { logs -> UiState(items = logs) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState()
        )

    fun clearLogs() {
        viewModelScope.launch {
            logRepository.clearLogs()
        }
    }

    data class UiState(
        val items: List<AppLog> = emptyList()
    )
}
