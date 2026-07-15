package com.example.defensync.ui.defense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.defensync.core.ServiceLocator
import com.example.defensync.domain.model.AlignedSlot
import com.example.defensync.domain.model.DefenseRequest
import com.example.defensync.domain.model.TimeRange
import com.example.defensync.domain.model.UserProfile
import com.example.defensync.domain.model.Weekday
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface SubmitState {
    data object Idle : SubmitState
    data object Submitting : SubmitState
    data object Success : SubmitState
    data class Error(val message: String) : SubmitState
}

/**
 * Backs the defense list/create/respond flows. Since REST has no push, the list is refreshed
 * on a short poll while the screen is active, plus immediately after any mutation.
 */
class DefenseViewModel : ViewModel() {

    private val repo = ServiceLocator.defenseRepository
    private val users = ServiceLocator.userRepository

    private val _defenses = MutableStateFlow<List<DefenseRequest>>(emptyList())
    val defenses: StateFlow<List<DefenseRequest>> = _defenses.asStateFlow()

    /** Every scheduled defense in the system, for flagging double-booking conflicts as a time is picked. */
    private val _calendarEntries = MutableStateFlow<List<com.example.defensync.domain.model.CalendarEntry>>(emptyList())
    val calendarEntries: StateFlow<List<com.example.defensync.domain.model.CalendarEntry>> = _calendarEntries.asStateFlow()

    private val _faculty = MutableStateFlow<List<UserProfile>>(emptyList())
    val faculty: StateFlow<List<UserProfile>> = _faculty.asStateFlow()

    private val _students = MutableStateFlow<List<UserProfile>>(emptyList())
    val students: StateFlow<List<UserProfile>> = _students.asStateFlow()

    private val _submit = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submit: StateFlow<SubmitState> = _submit.asStateFlow()

    /** Last error from an inline action (e.g. a rejected slot: conflict or daily cap). Null when clear. */
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init {
        refreshPeople()
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Reload professors + students. Called on init and when the create/edit forms open, so newly
     *  registered people show up without an app restart. */
    fun refreshPeople() {
        viewModelScope.launch {
            runCatching { users.professors() }.onSuccess { _faculty.value = it }
        }
        viewModelScope.launch {
            runCatching { users.students() }.onSuccess { _students.value = it }
        }
    }

    private suspend fun refresh() {
        runCatching { repo.list() }.onSuccess { _defenses.value = it }
        runCatching { repo.calendar() }.onSuccess { _calendarEntries.value = it }
    }

    /** Refresh account-scoped data immediately (e.g. after an account switch) rather than waiting for
     *  the next poll, so this tab doesn't briefly show the previous account's defenses. */
    fun reload() {
        refreshPeople()
        viewModelScope.launch { refresh() }
    }

    fun resetSubmit() { _submit.value = SubmitState.Idle }

    fun createDefense(
        title: String,
        @Suppress("UNUSED_PARAMETER") student: UserProfile,
        adviser: UserProfile,
        panelists: List<UserProfile>,
        coStudentIds: List<String> = emptyList()
    ) {
        if (_submit.value == SubmitState.Submitting) return
        _submit.value = SubmitState.Submitting
        viewModelScope.launch {
            runCatching { repo.create(title, adviser.uid, panelists.map { it.uid }, coStudentIds) }
                .onSuccess {
                    refresh()
                    _submit.value = SubmitState.Success
                }
                .onFailure { _submit.value = SubmitState.Error(it.message ?: "Failed to create group") }
        }
    }

    fun saveGroupAvailability(defenseId: String, availability: Map<Weekday, List<TimeRange>>) {
        viewModelScope.launch {
            runCatching { repo.saveGroupAvailability(defenseId, availability) }
                .onSuccess { updateOne(it) }
        }
    }

    fun updateMembers(
        defenseId: String,
        adviserId: String,
        panelistIds: List<String>,
        studentIds: List<String>
    ) {
        if (_submit.value == SubmitState.Submitting) return
        _submit.value = SubmitState.Submitting
        viewModelScope.launch {
            runCatching { repo.updateMembers(defenseId, adviserId, panelistIds, studentIds) }
                .onSuccess {
                    updateOne(it)
                    _submit.value = SubmitState.Success
                }
                .onFailure { _submit.value = SubmitState.Error(it.message ?: "Failed to update group") }
        }
    }

    fun clearActionError() { _actionError.value = null }

    fun selectSlot(defenseId: String, slot: AlignedSlot) {
        viewModelScope.launch {
            runCatching { repo.selectSlot(defenseId, slot) }
                .onSuccess {
                    _actionError.value = null
                    updateOne(it)
                }
                .onFailure { _actionError.value = it.message ?: "Could not set the time" }
        }
    }

    fun respond(defenseId: String, accept: Boolean) {
        viewModelScope.launch {
            runCatching { repo.respond(defenseId, accept) }.onSuccess { updateOne(it) }
        }
    }

    fun cancel(defenseId: String) {
        viewModelScope.launch {
            runCatching { repo.cancel(defenseId) }.onSuccess { updateOne(it) }
        }
    }

    fun deleteDefense(defenseId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            runCatching { repo.delete(defenseId) }.onSuccess {
                _defenses.value = _defenses.value.filterNot { it.id == defenseId }
                onDeleted()
            }
        }
    }

    /** Replace one defense in the list immediately, so the UI reacts without waiting for a poll. */
    private fun updateOne(updated: DefenseRequest) {
        _defenses.value = _defenses.value.map { if (it.id == updated.id) updated else it }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 4000L
    }
}
