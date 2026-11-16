package com.niranjan.ninety.ui.history

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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val repository: NinetyRepository, private val semesterId: UUID) : ViewModel() {

    val month = MutableStateFlow(YearMonth.now())
    val summaries: StateFlow<List<DaySummary>> = month.flatMapLatest {
        repository.observeMonth(semesterId, it)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomplete: StateFlow<List<LocalDate>> = repository.observeIncompleteDates(
        semesterId, LocalDate.now().plusDays(1))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun moveMonth(delta: Long) { month.value = month.value.plusMonths(delta) }
}
