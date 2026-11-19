package com.niranjan.ninety.ui.leave

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niranjan.ninety.data.DlStatus
import com.niranjan.ninety.data.NinetyRepository
import com.niranjan.ninety.ui.guard
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
internal class LeaveViewModel(
    private val repository: NinetyRepository,
    semesterId: UUID,
    courseId: UUID?,
    today: LocalDate,
) : ViewModel() {
    private val allEvents = repository.observeDlEvents(semesterId).map { events ->
        events.filter { it.periods > 0 }
    }

    val events = (if (courseId == null) allEvents else allEvents.flatMapLatest { events ->
        if (events.isEmpty()) flowOf(emptyList())
        else combine(events.map { event ->
            repository.observeDlEventSessions(event.id).map { sessions ->
                event.takeIf { sessions.any { it.courseId == courseId } }
            }
        }) { it.filterNotNull() }
    }).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val opening = repository.observeCourseStats(semesterId, today)
        .map { courses -> courses.filter { courseId == null || it.balance.courseId == courseId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val expandedEventId = MutableStateFlow<UUID?>(null)
    val expandedEvent = expandedEventId.asStateFlow()
    val eventPeriods = expandedEventId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeDlEventSessions(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun expand(eventId: UUID?) { expandedEventId.value = eventId }

    fun setStatus(eventId: UUID, status: DlStatus, sessionIds: Set<UUID>? = null) {
        viewModelScope.launch { messages.guard { repository.updateDlStatus(eventId, status, sessionIds) } }
    }

    fun resolveOpening(courseId: UUID, approve: Boolean) {
        viewModelScope.launch { messages.guard { repository.resolveOpeningPendingDl(courseId, approve) } }
    }

    fun removeRequest(eventId: UUID) {
        viewModelScope.launch { messages.guard { repository.removeDutyLeaveRequest(eventId) } }
    }

    val messages = MutableStateFlow<String?>(null)
    fun dismissMessage() { messages.value = null }
}
