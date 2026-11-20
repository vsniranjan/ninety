package com.niranjan.ninety.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niranjan.ninety.AppContainer
import com.niranjan.ninety.data.BackupPreview
import com.niranjan.ninety.data.SemesterInfo
import com.niranjan.ninety.ui.guard
import com.niranjan.ninety.ui.userMessage
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository

    val appState = repository.observeAppState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.niranjan.ninety.data.AppState())
    val messages = MutableStateFlow<String?>(null)
    val info = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    /** Preview of a validated backup awaiting the user's restore confirmation. */
    val pendingRestore = MutableStateFlow<Pair<ByteArray, BackupPreview>?>(null)

    fun setTheme(theme: String) = act { repository.updatePreferences(theme, haptics(), reminderOn(), reminderMinute()) }
    fun setHaptics(on: Boolean) = act { repository.updatePreferences(theme(), on, reminderOn(), reminderMinute()) }
    fun setReminder(on: Boolean, minute: Int? = null) = act {
        repository.updatePreferences(theme(), haptics(), on, minute ?: reminderMinute())
        container.reminders.reconcile()
    }
    fun setReminderTime(minute: Int) = act {
        repository.updatePreferences(theme(), haptics(), true, minute)
        container.reminders.reconcile()
    }

    private fun theme() = appState.value.settings.theme
    private fun haptics() = appState.value.settings.haptics
    private fun reminderOn() = appState.value.settings.reminderEnabled
    private fun reminderMinute() = appState.value.settings.reminderMinute

    val semesterEditError = MutableStateFlow<String?>(null)
    val savingSemester = MutableStateFlow(false)

    fun updateSemester(info: SemesterInfo, name: String, startDate: LocalDate, zoneId: String, onSaved: () -> Unit) {
        if (savingSemester.value) return
        savingSemester.value = true
        semesterEditError.value = null
        viewModelScope.launch {
            try {
                repository.updateSemester(UUID.fromString(info.entity.id), name, startDate, zoneId)
                onSaved()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                semesterEditError.value = error.userMessage()
            } finally {
                savingSemester.value = false
            }
        }
    }

    fun clearSemesterEditError() { semesterEditError.value = null }

    fun completeSemester(info: SemesterInfo, completed: Boolean, onSaved: () -> Unit = {}) = act {
        repository.setSemesterCompleted(UUID.fromString(info.entity.id), completed)
        onSaved()
    }

    fun activateSemester(info: SemesterInfo, onSaved: () -> Unit) = act {
        repository.setActiveSemester(UUID.fromString(info.entity.id))
        onSaved()
    }

    fun exportBackup(onWritten: () -> Unit, writeBytes: suspend (ByteArray) -> Unit) {
        viewModelScope.launch {
            busy.value = true
            runCatching {
                val bytes = repository.exportBackup()
                withContext(Dispatchers.IO) { writeBytes(bytes) }
            }.onSuccess { onWritten() }
                .onFailure { messages.value = it.userMessage() }
            busy.value = false
        }
    }

    fun previewRestore(bytes: ByteArray) {
        viewModelScope.launch {
            busy.value = true
            runCatching { repository.validateBackup(bytes) }
                .onSuccess { pendingRestore.value = bytes to it }
                .onFailure { messages.value = it.userMessage() }
            busy.value = false
        }
    }

    fun confirmRestore() {
        val pending = pendingRestore.value ?: return
        viewModelScope.launch {
            busy.value = true
            runCatching {
                repository.restoreBackup(pending.first)
                container.reminders.reconcile()
            }
                .onSuccess { info.value = "Backup restored." }
                .onFailure { messages.value = it.userMessage() }
            pendingRestore.value = null
            busy.value = false
        }
    }

    fun cancelRestore() { pendingRestore.value = null }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch { messages.guard(block) }
    }

    fun dismissMessages() { messages.value = null; info.value = null }
}
