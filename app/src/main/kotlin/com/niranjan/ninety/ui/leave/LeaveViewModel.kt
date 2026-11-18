package com.niranjan.ninety.ui.leave

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niranjan.ninety.data.NinetyRepository
import com.niranjan.ninety.data.DlEventView
import com.niranjan.ninety.data.DlStats
import com.niranjan.ninety.data.DlStatus
import com.niranjan.ninety.data.SessionView
import com.niranjan.ninety.ui.guard
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LeaveViewModel(private val repository: NinetyRepository, semesterId: UUID, pendingCourseId: UUID? = null) : ViewModel() {

    private val allEvents = repository.observeDlEvents(semesterId)
    private val visibleEvents = if (pendingCourseId == null) allEvents else allEvents.flatMapLatest { events ->
        val pending = events.filter { it.pending > 0 }
        if (pending.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(pending.map { event ->
                repository.observeDlEventSessions(event.id).map { sessions ->
                    event.takeIf {
                        sessions.any { it.courseId == pendingCourseId && it.dlStatus == DlStatus.PENDING }
                    }
                }
            }) { it.filterNotNull() }
        }
    }
    val events: StateFlow<List<DlEventView>> = visibleEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pendingCourseName = if (pendingCourseId == null) flowOf(null) else repository.observeCourses(semesterId)
        .map { courses -> courses.firstOrNull { it.id == pendingCourseId.toString() }?.name }
    val stats: StateFlow<DlStats?> = repository.observeDlStats(semesterId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Sessions of the event currently expanded in detail view. */
    val expandedEventId = MutableStateFlow<UUID?>(null)
    private val _eventSessions = MutableStateFlow<List<SessionView>>(emptyList())
    val eventSessions: StateFlow<List<SessionView>> = _eventSessions

    init {
        viewModelScope.launch {
            expandedEventId.collectLatest { id ->
                _eventSessions.value = emptyList()
                if (id != null) repository.observeDlEventSessions(id).collect { _eventSessions.value = it }
            }
        }
    }

    fun expand(eventId: UUID?) { expandedEventId.value = eventId }

    fun setStatus(eventId: UUID, status: DlStatus, sessionIds: Set<UUID>? = null) {
        viewModelScope.launch { messages.guard { repository.updateDlStatus(eventId, status, sessionIds) } }
    }

    val messages = MutableStateFlow<String?>(null)
    fun dismissMessage() { messages.value = null }
}
