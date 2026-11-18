package com.niranjan.ninety.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niranjan.ninety.data.NinetyRepository
import com.niranjan.ninety.data.CourseEntity
import com.niranjan.ninety.data.CopyResult
import com.niranjan.ninety.data.DayView
import com.niranjan.ninety.domain.AttendanceOutcome
import com.niranjan.ninety.ui.guard
import com.niranjan.ninety.ui.userMessage
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModel(private val repository: NinetyRepository, private val semesterId: UUID, initialDate: LocalDate) : ViewModel() {

    private val date = MutableStateFlow(initialDate)
    val day: StateFlow<DayView?> = date.flatMapLatest {
        runCatching { repository.ensureDay(semesterId, it) }
        repository.observeDay(semesterId, it)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun observeEarlierIncomplete(today: LocalDate) = repository.observeIncompleteDates(semesterId, today)

    val courses: StateFlow<List<CourseEntity>> = repository.observeCourses(semesterId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val messages = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    fun setDate(value: LocalDate) { date.value = value }

    fun record(sessionId: UUID, outcome: AttendanceOutcome?) = act {
        repository.recordPeriod(sessionId, outcome)
    }

    fun recordUnrecorded(sessionIds: List<UUID>, outcome: AttendanceOutcome) = act {
        repository.recordUnrecorded(sessionIds, outcome)
    }

    fun markAllPresent() = act {
        val marked = repository.markAllPresent(semesterId, date.value)
        if (marked == 0) messages.value = "Nothing left to mark."
    }

    fun setHoliday(holiday: Boolean) = act { repository.setHoliday(semesterId, date.value, holiday) }

    fun setCancelled(sessionId: UUID, cancelled: Boolean) = act {
        repository.setCancelled(sessionId, cancelled)
    }

    fun substitute(sessionId: UUID, courseId: UUID) = act {
        repository.substituteCourse(sessionId, courseId)
    }

    fun addExtra(courseId: UUID, startMinute: Int, endMinute: Int, counting: Boolean) = act {
        repository.addExtra(semesterId, date.value, courseId, startMinute, endMinute, counting)
    }

    fun setExtraCounting(sessionId: UUID, counting: Boolean) = act {
        repository.setExtraCounting(sessionId, counting)
    }

    fun removeExtra(sessionId: UUID) = act { repository.deleteExtra(sessionId) }

    fun copyDay(source: DayOfWeek, onResult: (CopyResult) -> Unit) {
        viewModelScope.launch {
            busy.value = true
            runCatching { repository.copyWeekday(semesterId, date.value, source) }
                .onSuccess(onResult)
                .onFailure { messages.value = it.userMessage() }
            busy.value = false
        }
    }

    fun applyDutyLeave(sessionIds: List<UUID>, title: String?, replace: Boolean,
        onConflict: (List<UUID>) -> Unit = {}) {
        viewModelScope.launch {
            busy.value = true
            runCatching { repository.applyDutyLeave(semesterId, sessionIds, title, replace) }
                .onSuccess { result -> if (result.conflicts.isNotEmpty()) onConflict(result.conflicts) }
                .onFailure { messages.value = it.userMessage() }
            busy.value = false
        }
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            messages.guard(block)
            busy.value = false
        }
    }

    /** Whether record taps should perform haptic confirmation. */
    val haptics = repository.observeAppState().map { it.settings.haptics }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun dismissMessage() { messages.value = null }
}
