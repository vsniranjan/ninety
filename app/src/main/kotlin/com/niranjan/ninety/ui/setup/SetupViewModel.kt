package com.niranjan.ninety.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niranjan.ninety.data.CourseInput
import com.niranjan.ninety.data.CourseSetup
import com.niranjan.ninety.data.NinetyRepository
import com.niranjan.ninety.data.SemesterSetup
import com.niranjan.ninety.domain.CourseType
import com.niranjan.ninety.domain.OpeningBalance
import com.niranjan.ninety.domain.WeekTemplate
import com.niranjan.ninety.ui.openingCountError
import com.niranjan.ninety.ui.userMessage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CourseDraft(
    val id: UUID = UUID.randomUUID(),
    val name: String = "",
    val type: CourseType = CourseType.THEORY,
    /** ISO date when dated logging begins for this course; blank = semester start. */
    val loggingDate: String = "",
    val conducted: String = "0",
    val present: String = "0",
    val approvedDl: String = "0",
    val pendingDl: String = "0",
) {
    val hasBalance: Boolean get() = (conducted.toLongOrNull() ?: 0L) > 0L
}

data class SetupState(
    val step: Int = 0,
    val semesterName: String = "",
    val startDate: LocalDate? = null,
    val zoneId: String = ZoneId.systemDefault().id,
    val courses: List<CourseDraft> = emptyList(),
    /** weekday -> slot -> draft course id */
    val timetable: Map<Pair<DayOfWeek, Int>, UUID> = emptyMap(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    val stepCount: Int get() = 4
}

class SetupViewModel(private val repository: NinetyRepository) : ViewModel() {
    private val _state = MutableStateFlow(SetupState())
    val state = _state.asStateFlow()

    fun setSemesterName(value: String) = _state.update { it.copy(semesterName = value, error = null) }
    fun setStartDate(value: LocalDate) = _state.update { it.copy(startDate = value, error = null) }
    fun setZone(value: String) = _state.update { it.copy(zoneId = value, error = null) }

    fun addCourse(draft: CourseDraft) = _state.update {
        it.copy(courses = it.courses + draft, error = null)
    }

    fun updateCourse(draft: CourseDraft) = _state.update {
        it.copy(courses = it.courses.map { c -> if (c.id == draft.id) draft else c }, error = null)
    }

    fun removeCourse(id: UUID) = _state.update {
        it.copy(courses = it.courses.filterNot { c -> c.id == id },
            timetable = it.timetable.filterValues { c -> c != id })
    }

    fun assign(day: DayOfWeek, slot: Int, courseId: UUID?) = _state.update {
        val next = it.timetable.toMutableMap()
        if (courseId == null) next.remove(day to slot) else next[day to slot] = courseId
        it.copy(timetable = next)
    }

    fun copyDay(from: DayOfWeek, to: DayOfWeek) = _state.update { s ->
        val source = s.timetable.filterKeys { it.first == from }.mapKeys { to to it.key.second }
        s.copy(timetable = s.timetable.filterKeys { it.first != to } + source)
    }

    fun next() {
        val s = _state.value
        val error = when (s.step) {
            0 -> validateSemester(s)
            1 -> validateCourses(s)
            else -> null
        }
        if (error != null) _state.update { it.copy(error = error) }
        else _state.update { it.copy(step = (it.step + 1).coerceAtMost(it.stepCount - 1), error = null) }
    }

    fun back() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0), error = null) }

    private fun validateSemester(s: SetupState): String? {
        if (s.semesterName.isBlank()) return "Enter a semester name."
        if (s.startDate == null) return "Choose the semester start date."
        runCatching { ZoneId.of(s.zoneId) }.onFailure { return "Choose a valid timezone." }
        return null
    }

    private fun validateCourses(s: SetupState): String? {
        if (s.courses.isEmpty()) return "Add at least one course."
        s.courses.forEach { c ->
            if (c.name.isBlank()) return "Enter a course name."
            if (openingCountError(c.conducted.toLongOrNull(), c.present.toLongOrNull(),
                    c.approvedDl.toLongOrNull(), c.pendingDl.toLongOrNull()) != null) {
                return "Check the counts for ${c.name}."
            }
            if (c.hasBalance) {
                val logging = c.loggingDate.toLocalDateOrNull()
                    ?: return "Choose the date tracking starts for ${c.name}."
                val start = s.startDate!!
                if (logging <= start) return "Choose a tracking start date for ${c.name} after the semester start."
            }
        }
        val names = s.courses.map { it.name.trim().lowercase(Locale.ROOT) }
        if (names.distinct().size != names.size) return "Course names must be unique within a semester."
        return null
    }

    private fun String?.toLocalDateOrNull(): LocalDate? =
        this?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun save() {
        val s = _state.value
        val error = validateCourses(s)
        if (error != null) { _state.update { it.copy(error = error, step = 1) }; return }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            runCatching {
                val courses = s.courses.map { c ->
                    val balance = if (c.hasBalance) {
                        OpeningBalance(c.id, c.loggingDate.toLocalDateOrNull()!!,
                            c.conducted.toLong(), c.present.toLong(), c.approvedDl.toLong(), c.pendingDl.toLong())
                    } else {
                        OpeningBalance(c.id, c.loggingDate.toLocalDateOrNull() ?: s.startDate!!)
                    }
                    CourseSetup(c.id, CourseInput(c.name, c.type), balance)
                }
                val timetable = WeekTemplate(s.timetable.entries.groupBy({ it.key.first },
                    { it.key.second to it.value }).mapValues { (_, p) -> p.toMap() })
                repository.createSemester(SemesterSetup(s.semesterName.trim(), s.startDate!!, s.zoneId, courses, timetable))
            }.onSuccess { _state.update { st -> st.copy(saved = true) } }
            .onFailure { _state.update { st -> st.copy(error = it.userMessage()) } }
            _state.update { it.copy(saving = false) }
        }
    }
}
