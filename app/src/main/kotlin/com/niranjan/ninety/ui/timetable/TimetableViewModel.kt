package com.niranjan.ninety.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niranjan.ninety.data.NinetyRepository
import com.niranjan.ninety.data.ConflictInfo
import com.niranjan.ninety.data.CourseEntity
import com.niranjan.ninety.domain.ScheduleVersion
import com.niranjan.ninety.domain.WeekTemplate
import com.niranjan.ninety.ui.guard
import com.niranjan.ninety.ui.userMessage
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimetableViewModel(private val repository: NinetyRepository, private val semesterId: UUID) : ViewModel() {

    val versions: StateFlow<List<ScheduleVersion>> = repository.observeTemplates(semesterId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val courses: StateFlow<List<CourseEntity>> = repository.observeCourses(semesterId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Editable copy of the newest template; null until the user opens the editor. */
    val draft = MutableStateFlow<WeekTemplate?>(null)
    val effectiveFrom = MutableStateFlow(LocalDate.now())
    val messages = MutableStateFlow<String?>(null)
    val saving = MutableStateFlow(false)

    fun startEditing(base: WeekTemplate?, today: LocalDate) {
        draft.value = base ?: WeekTemplate()
        effectiveFrom.value = today
    }

    fun edit(day: DayOfWeek, slot: Int, courseId: UUID?) {
        draft.value = draft.value?.with(day, slot, courseId)
    }

    fun setEffective(date: LocalDate) { effectiveFrom.value = date }

    fun cancelEditing() { draft.value = null }

    fun save(onSaved: (List<ConflictInfo>) -> Unit) {
        val template = draft.value ?: return
        viewModelScope.launch {
            saving.value = true
            runCatching { repository.saveTemplate(semesterId, template, effectiveFrom.value) }
                .onSuccess { conflicts ->
                    draft.value = null
                    onSaved(conflicts)
                }
                .onFailure { messages.value = it.userMessage() }
            saving.value = false
        }
    }

    fun dismissMessage() { messages.value = null }
}
