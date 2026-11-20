package com.niranjan.ninety.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.niranjan.ninety.data.AppSettings
import com.niranjan.ninety.data.CourseInput
import com.niranjan.ninety.data.CourseSetup
import com.niranjan.ninety.data.NinetyDatabase
import com.niranjan.ninety.data.NinetyRepository
import com.niranjan.ninety.data.SemesterSetup
import com.niranjan.ninety.domain.CourseType
import com.niranjan.ninety.domain.OpeningBalance
import com.niranjan.ninety.domain.WeekTemplate
import com.niranjan.ninety.ui.openingCountError
import com.niranjan.ninety.ui.userMessage
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
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
    val enterAttendanceSoFar: Boolean = true,
    val courses: List<CourseDraft> = emptyList(),
    /** weekday -> slot -> draft course id */
    val timetable: Map<Pair<DayOfWeek, Int>, UUID> = emptyMap(),
    val enableReminder: Boolean = false,
    val confirmEmptyTimetable: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    val stepCount: Int get() = 4
    val hasDraft: Boolean get() = semesterName.isNotBlank() || startDate != null || courses.isNotEmpty() ||
        zoneId != ZoneId.systemDefault().id || enableReminder

    fun asksAboutPastAttendance(today: LocalDate): Boolean =
        startDate?.isBefore(today.minusDays(3)) == true

    fun offersAttendanceSoFar(today: LocalDate): Boolean =
        startDate?.isBefore(today) == true && (!asksAboutPastAttendance(today) || enterAttendanceSoFar)

    fun prefillsAttendanceSoFar(today: LocalDate): Boolean =
        asksAboutPastAttendance(today) && enterAttendanceSoFar

    fun courseForSetup(course: CourseDraft, today: LocalDate): CourseDraft = when {
        !offersAttendanceSoFar(today) -> course.copy(loggingDate = "", conducted = "0", present = "0",
            approvedDl = "0", pendingDl = "0")
        prefillsAttendanceSoFar(today) && course.loggingDate.isBlank() -> course.copy(loggingDate = today.toString())
        else -> course
    }
}

internal data class RemovedCourse(val draft: CourseDraft, val index: Int,
    val assignments: Map<Pair<DayOfWeek, Int>, UUID>)

class SetupViewModel(private val repository: NinetyRepository, private val database: NinetyDatabase,
    private val clock: Clock, private val reconcileReminders: suspend () -> Unit = {},
    private val onReminderError: (String) -> Unit = {}) : ViewModel() {
    fun today(zoneId: String): LocalDate = LocalDate.now(clock.withZone(ZoneId.of(zoneId)))

    private val _state = MutableStateFlow(SetupState())
    val state = _state.asStateFlow()

    fun setSemesterName(value: String) = _state.update { it.copy(semesterName = value, error = null) }
    fun setStartDate(value: LocalDate) = _state.update { it.copy(startDate = value, error = null) }
    fun setZone(value: String) = _state.update { it.copy(zoneId = value, error = null) }

    fun setEnterAttendanceSoFar(value: Boolean) = _state.update { it.copy(enterAttendanceSoFar = value) }

    fun setEnableReminder(value: Boolean) = _state.update { it.copy(enableReminder = value) }

    fun addCourse(draft: CourseDraft) = _state.update {
        it.copy(courses = it.courses + draft, error = null)
    }

    fun updateCourse(draft: CourseDraft) = _state.update {
        it.copy(courses = it.courses.map { c -> if (c.id == draft.id) draft else c }, error = null)
    }

    internal fun removeCourse(id: UUID): RemovedCourse? {
        val s = _state.value
        val index = s.courses.indexOfFirst { it.id == id }
        if (index == -1) return null
        val removed = RemovedCourse(s.courses[index], index, s.timetable.filterValues { it == id })
        _state.value = s.copy(courses = s.courses.filterNot { it.id == id },
            timetable = s.timetable - removed.assignments.keys)
        return removed
    }

    internal fun restoreCourse(removed: RemovedCourse) = _state.update { s ->
        if (s.saving || s.saved) return@update s
        val restored = s.courses.toMutableList().apply { add(removed.index.coerceAtMost(size), removed.draft) }
        s.copy(courses = restored, timetable = removed.assignments + s.timetable, error = null)
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
        if (_state.value.step == 2 && _state.value.timetable.isEmpty()) {
            _state.update { it.copy(confirmEmptyTimetable = true) }
            return
        }
        advance()
    }

    fun skipEmptyTimetable() {
        if (_state.value.confirmEmptyTimetable) advance()
    }

    fun dismissEmptyTimetable() = _state.update { it.copy(confirmEmptyTimetable = false) }

    private fun advance() {
        val s = _state.value
        val error = when (s.step) {
            0 -> validateSemester(s)
            1 -> validateCourses(s)
            else -> null
        }
        if (error != null) _state.update { it.copy(error = error) }
        else _state.update { it.copy(step = (it.step + 1).coerceAtMost(it.stepCount - 1),
            confirmEmptyTimetable = false, error = null) }
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
        val today = today(s.zoneId)
        s.courses.map { s.courseForSetup(it, today) }.forEach { c ->
            if (c.name.isBlank()) return "Enter a course name."
            if (openingCountError(c.conducted.toLongOrNull(), c.present.toLongOrNull(),
                    c.approvedDl.toLongOrNull(), c.pendingDl.toLongOrNull()) != null) {
                return "Check the counts for ${c.name}."
            }
            if (c.hasBalance || c.loggingDate.isNotBlank()) {
                val logging = c.loggingDate.toLocalDateOrNull()
                    ?: return "Choose the date tracking starts for ${c.name}."
                val start = s.startDate!!
                if (logging < start || (c.hasBalance && logging == start)) {
                    return "Choose a tracking start date for ${c.name} after the semester start."
                }
                if (logging > today) return "Choose today or an earlier tracking start date for ${c.name}."
            }
        }
        val names = s.courses.map { it.name.trim().lowercase(Locale.ROOT) }
        if (names.distinct().size != names.size) return "Course names must be unique within a semester."
        return null
    }

    private fun String?.toLocalDateOrNull(): LocalDate? =
        this?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun save(finishPrevious: UUID? = null, settings: AppSettings = AppSettings()) {
        val s = _state.value
        if (s.saving || s.saved) return
        val error = validateCourses(s)
        if (error != null) { _state.update { it.copy(error = error, step = 1) }; return }
        _state.update { it.copy(saving = true, error = null) }
        val turnOnReminder = s.enableReminder && !settings.reminderEnabled
        viewModelScope.launch {
            try {
                val today = today(s.zoneId)
                val courses = s.courses.map { s.courseForSetup(it, today) }.map { c ->
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
                val setup = SemesterSetup(s.semesterName.trim(), s.startDate!!, s.zoneId, courses, timetable)
                // Finish while the old semester is current. Roll back both operations if setup fails.
                database.withTransaction {
                    if (finishPrevious != null) repository.setSemesterCompleted(finishPrevious, true)
                    repository.createSemester(setup)
                    if (turnOnReminder) {
                        repository.updatePreferences(settings.theme, settings.haptics, true, settings.reminderMinute)
                    }
                }
                _state.update { it.copy(saved = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(error = error.userMessage()) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
            if (turnOnReminder && _state.value.saved) {
                try {
                    reconcileReminders()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    onReminderError(error.userMessage())
                }
            }
        }
    }
}
