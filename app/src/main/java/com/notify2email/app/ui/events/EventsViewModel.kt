package com.notify2email.app.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notify2email.app.domain.model.Event
import com.notify2email.app.domain.repository.EventRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EventsViewModel(
    private val eventRepository: EventRepository
) : ViewModel() {

    val uiState: StateFlow<UiState> = eventRepository.observeEvents()
        .map { events -> UiState(items = events) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState()
        )

    fun deleteEvent(id: String) {
        viewModelScope.launch {
            eventRepository.deleteEvent(id)
        }
    }

    fun clearAllEvents() {
        viewModelScope.launch {
            eventRepository.deleteAllEvents()
        }
    }

    data class UiState(
        val items: List<Event> = emptyList()
    )
}
