package com.example.defensync.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.defensync.core.ServiceLocator
import com.example.defensync.domain.model.TimeConfig
import com.example.defensync.domain.model.TimeRange
import com.example.defensync.domain.model.Weekday
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScheduleUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val dirty: Boolean = false,
    val justSaved: Boolean = false
)

/** Loads and edits the signed-in user's default weekly availability (ranges per day). */
class ScheduleViewModel : ViewModel() {

    private val users = ServiceLocator.userRepository

    private val _selection = MutableStateFlow<Map<Weekday, List<TimeRange>>>(emptyMap())
    val selection: StateFlow<Map<Weekday, List<TimeRange>>> = _selection.asStateFlow()

    private val _ui = MutableStateFlow(ScheduleUiState())
    val ui: StateFlow<ScheduleUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { users.getDefaultAvailability() }.onSuccess { _selection.value = it }
            _ui.value = _ui.value.copy(loading = false)
        }
    }

    fun addRange(day: Weekday) = mutate(day) { it + defaultRange(it) }

    fun removeRange(day: Weekday, index: Int) = mutate(day) { it.filterIndexed { i, _ -> i != index } }

    fun updateRange(day: Weekday, index: Int, range: TimeRange) =
        mutate(day) { list -> list.mapIndexed { i, r -> if (i == index) range else r } }

    private fun mutate(day: Weekday, transform: (List<TimeRange>) -> List<TimeRange>) {
        val current = _selection.value[day].orEmpty()
        _selection.value = _selection.value.toMutableMap().apply { this[day] = transform(current) }
        _ui.value = _ui.value.copy(dirty = true, justSaved = false)
    }

    private fun defaultRange(existing: List<TimeRange>): TimeRange {
        val start = (existing.maxOfOrNull { it.end } ?: TimeConfig.DAY_START)
            .coerceAtMost(TimeConfig.DAY_END - TimeConfig.DEFENSE)
        return TimeRange(start, start + TimeConfig.DEFENSE)
    }

    fun save() {
        if (_ui.value.saving) return
        _ui.value = _ui.value.copy(saving = true, justSaved = false)
        viewModelScope.launch {
            runCatching { users.saveDefaultAvailability(_selection.value) }
                .onSuccess { _ui.value = ScheduleUiState(loading = false, dirty = false, justSaved = true) }
                .onFailure { _ui.value = _ui.value.copy(saving = false) }
        }
    }
}
