package com.niranjan.ninety.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niranjan.ninety.data.CourseInput
import com.niranjan.ninety.data.CourseSetup
import com.niranjan.ninety.data.CourseStats
import com.niranjan.ninety.data.NinetyRepository
import com.niranjan.ninety.domain.OpeningBalance
import com.niranjan.ninety.ui.guard
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CoursesViewModel(
    private val repository: NinetyRepository,
    private val semesterId: UUID,
    today: LocalDate,
) : ViewModel() {

    val stats: StateFlow<List<CourseStats>?> = repository.observeCourseStats(semesterId, today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages = MutableStateFlow<String?>(null)
    val saving = MutableStateFlow(false)
    val courseAdded = MutableStateFlow(false)

    fun addCourse(setup: CourseSetup) {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            messages.guard {
                repository.addCourse(semesterId, setup)
                courseAdded.value = true
            }
            saving.value = false
        }
    }

    fun updateCourse(courseId: UUID, input: CourseInput) = act {
        repository.updateCourse(courseId, input)
    }

    fun retireCourse(courseId: UUID) = act { repository.retireCourse(courseId) }

    fun resumeCourse(courseId: UUID) = act { repository.resumeCourse(courseId) }

    fun correctBalance(balance: OpeningBalance) = act { repository.correctOpeningBalance(balance) }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch { messages.guard(block) }
    }

    fun dismissMessage() { messages.value = null }
}
