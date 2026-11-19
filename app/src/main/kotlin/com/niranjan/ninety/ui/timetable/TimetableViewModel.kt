package com.niranjan.ninety.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niranjan.ninety.data.ConflictInfo
import com.niranjan.ninety.data.CourseEntity
import com.niranjan.ninety.data.NinetyRepository
import com.niranjan.ninety.domain.ScheduleVersion
import com.niranjan.ninety.domain.WeekTemplate
import com.niranjan.ninety.ui.userMessage
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimetableViewModel(private val repository: NinetyRepository, private val semesterId: UUID) : ViewModel() {

    val versions: StateFlow<List<ScheduleVersion>?> = repository.observeTemplates(semesterId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val courses: StateFlow<List<CourseEntity>> = repository.observeCourses(semesterId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Editable copy of the newest template; null until the user opens the editor. */
    val draft = MutableStateFlow<WeekTemplate?>(null)
    val effectiveFrom = MutableStateFlow(LocalDate.now())
    val messages = MutableStateFlow<String?>(null)
    private val _preview = MutableStateFlow<List<ConflictInfo>?>(null)
    val preview: StateFlow<List<ConflictInfo>?> = _preview
    val saving = MutableStateFlow(false)

    private var originalTemplate: WeekTemplate? = null
    private var originalEffectiveDate: LocalDate? = null

    fun hasChanges(): Boolean = draft.value != null &&
        (draft.value != originalTemplate || effectiveFrom.value != originalEffectiveDate)

    fun startEditing(base: WeekTemplate?, today: LocalDate) {
        originalTemplate = base ?: WeekTemplate()
        originalEffectiveDate = today
        draft.value = originalTemplate
        effectiveFrom.value = today
    }

    fun edit(day: DayOfWeek, slot: Int, courseId: UUID?) {
        draft.value = draft.value?.with(day, slot, courseId)
    }

    fun copyDay(from: DayOfWeek, to: DayOfWeek) {
        val template = draft.value ?: return
        val source = template.assignments[from].orEmpty()
        val assignments = if (source.isEmpty()) template.assignments - to
            else template.assignments + (to to source)
        draft.value = WeekTemplate(assignments)
    }

    fun setEffective(date: LocalDate) { effectiveFrom.value = date }

    fun cancelEditing() { draft.value = null }

    fun save(today: LocalDate, onSaved: (List<ConflictInfo>) -> Unit) =
        saveDraft(previewFirst = effectiveFrom.value == today, onSaved)

    fun confirmSave(onSaved: (List<ConflictInfo>) -> Unit) {
        if (_preview.value != null) saveDraft(previewFirst = false, onSaved)
    }

    fun cancelPreview() { _preview.value = null }

    private fun saveDraft(previewFirst: Boolean, onSaved: (List<ConflictInfo>) -> Unit) {
        val template = draft.value ?: return
        val date = effectiveFrom.value
        if (saving.value) return
        saving.value = true
        _preview.value = null
        viewModelScope.launch {
            try {
                if (previewFirst) {
                    val conflicts = repository.previewTemplateConflicts(semesterId, template, date)
                    if (conflicts.isNotEmpty()) {
                        _preview.value = conflicts
                        return@launch
                    }
                }
                val conflicts = repository.saveTemplate(semesterId, template, date)
                draft.value = null
                onSaved(conflicts)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                messages.value = error.userMessage()
            } finally {
                saving.value = false
            }
        }
    }

    fun dismissMessage() { messages.value = null }
}
