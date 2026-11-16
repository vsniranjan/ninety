package com.niranjan.ninety.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niranjan.ninety.data.NinetyRepository
import com.niranjan.ninety.data.CourseInput
import com.niranjan.ninety.data.CourseSetup
import com.niranjan.ninety.data.CourseStats
import com.niranjan.ninety.data.SemesterEntity
import com.niranjan.ninety.domain.OpeningBalance
import com.niranjan.ninety.ui.guard
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CoursesViewModel(private val repository: NinetyRepository, private val semesterId: UUID) : ViewModel() {

    private val semester: StateFlow<SemesterEntity?> = repository.observeAppState()
        .map { it.semesters.firstOrNull { s -> s.entity.id == semesterId.toString() }?.entity }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val today: StateFlow<LocalDate> = semester.map {
        it?.let { s -> LocalDate.now(ZoneId.of(s.collegeZone)) } ?: LocalDate.now()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalDate.now())

    val stats: StateFlow<List<CourseStats>?> = today.flatMapLatest { date ->
        repository.observeCourseStats(semesterId, date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages = MutableStateFlow<String?>(null)

    fun addCourse(input: CourseInput, balance: OpeningBalance?) = act {
        repository.addCourse(semesterId, CourseSetup(input = input, balance = balance))
    }

    fun updateCourse(courseId: UUID, input: CourseInput) = act {
        repository.updateCourse(courseId, input)
    }

    fun retireCourse(courseId: UUID) = act { repository.retireCourse(courseId) }

    fun correctBalance(balance: OpeningBalance) = act { repository.correctOpeningBalance(balance) }

    fun resolveOpeningDl(courseId: UUID, approve: Boolean) = act {
        repository.resolveOpeningPendingDl(courseId, approve)
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch { messages.guard(block) }
    }

    fun dismissMessage() { messages.value = null }
}
