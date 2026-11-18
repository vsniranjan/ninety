package com.niranjan.ninety.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.niranjan.ninety.AppContainer
import com.niranjan.ninety.NinetyApplication
import com.niranjan.ninety.data.AppState
import com.niranjan.ninety.data.BackupException
import com.niranjan.ninety.data.UndoResult
import com.niranjan.ninety.data.UndoableAction
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Creates a ViewModel bound to the app container, keyed by an optional stable key. */
@Composable
inline fun <reified VM : ViewModel> ninetyViewModel(key: String? = null,
    noinline factory: (AppContainer) -> VM): VM {
    val app = LocalContext.current.applicationContext as NinetyApplication
    return viewModel(key = key, factory = viewModelFactory { initializer { factory(app.container) } })
}

fun Throwable.userMessage(): String = when (this) {
    is BackupException -> message ?: "This backup could not be used."
    is IllegalArgumentException, is IllegalStateException -> message ?: "That change could not be applied."
    else -> "Something went wrong. Your data is safe."
}

/** Runs [block], surfacing user-readable failures through [messages]. */
suspend fun MutableStateFlow<String?>.guard(block: suspend () -> Unit) {
    runCatching { block() }.onFailure { value = it.userMessage() }
}

class AppViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository

    val appState: StateFlow<AppState> = repository.observeAppState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppState())

    /** Manual setup launches (Settings → new semester); nonce forces a fresh draft. */
    val showSetup = MutableStateFlow(false)
    val setupNonce = MutableStateFlow(0)
    fun openSetup() { setupNonce.value++; showSetup.value = true }
    fun closeSetup() { showSetup.value = false }

    /** The semester the main screens show; defaults to the active semester. */
    val viewedSemesterId = MutableStateFlow<UUID?>(null)
    val viewedSemester = kotlinx.coroutines.flow.combine(appState, viewedSemesterId) { state, viewed ->
        state.semesters.firstOrNull { it.entity.id == viewed?.toString() } ?: state.activeSemester
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val sessionStartedAt = container.clock.millis()
    val undoable: StateFlow<UndoableAction?> = repository.observeUndoable()
        // Single marks and lab batches share a kind in the existing journal.
        .filter { it?.kind != "record" || it.label != "Attendance updated" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val undoBusy = MutableStateFlow(false)
    val messages = MutableStateFlow<String?>(null)

    init {
        // Materialize obligations up to today whenever the active semester changes.
        viewModelScope.launch {
            appState.collectLatest { state ->
                state.activeSemester?.let { semester ->
                    runCatching { repository.ensureMaterialized(UUID.fromString(semester.entity.id)) }
                }
            }
        }
    }

    fun viewSemester(id: UUID?) { viewedSemesterId.value = id }

    fun refreshDays() {
        viewModelScope.launch {
            appState.value.activeSemesterId?.let { id ->
                messages.guard { repository.ensureMaterialized(id) }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { AppViewModel(container) }
        }
    }

    fun undo(batchId: UUID) {
        viewModelScope.launch {
            undoBusy.value = true
            messages.guard {
                when (repository.undo(batchId)) {
                    UndoResult.DONE -> Unit
                    UndoResult.STALE -> messages.value = "That change can no longer be undone."
                    UndoResult.NOT_FOUND -> messages.value = "Nothing left to undo."
                }
            }
            undoBusy.value = false
        }
    }

    fun dismissMessage() { messages.value = null }
}
