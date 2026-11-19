package com.niranjan.ninety.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niranjan.ninety.data.NinetyRepository
import com.niranjan.ninety.data.DaySummary
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val repository: NinetyRepository, private val semesterId: UUID, semesterStart: LocalDate, today: LocalDate) : ViewModel() {

    private val firstMonth = YearMonth.from(semesterStart)
    val month = MutableStateFlow(maxOf(firstMonth, YearMonth.from(today)))
    val summaries: StateFlow<List<DaySummary>> = month.flatMapLatest {
        repository.observeMonth(semesterId, it)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomplete: StateFlow<List<DaySummary>> = repository.observeIncompleteDates(semesterId, today)
        .flatMapLatest { dates ->
            val months = dates.map(YearMonth::from).distinct()
            if (months.isEmpty()) {
                flowOf(emptyList())
            } else {
                val incompleteDates = dates.toSet()
                combine(months.map { repository.observeMonth(semesterId, it) }) { summaries ->
                    summaries.flatMap { month -> month.filter { it.date in incompleteDates } }
                        .filter { it.incomplete && !it.holiday }
                        .sortedByDescending { it.date }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun moveMonth(delta: Long) { month.value = maxOf(firstMonth, month.value.plusMonths(delta)) }

    fun showToday(today: LocalDate) { month.value = maxOf(firstMonth, YearMonth.from(today)) }
}
