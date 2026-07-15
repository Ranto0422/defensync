package com.example.defensync.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.defensync.core.ServiceLocator
import com.example.defensync.domain.model.InboxItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The unified inbox: all group chats + DMs, with unread counts. Polls while subscribed. */
class MessagesViewModel : ViewModel() {

    private val repo = ServiceLocator.directMessageRepository

    private val _items = MutableStateFlow<List<InboxItem>>(emptyList())
    val items: StateFlow<List<InboxItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Total unread across every conversation — used for the Messages tab badge. */
    val totalUnread: StateFlow<Int> = _items
        .map { list -> list.sumOf { it.unread } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            while (isActive) {
                runCatching { repo.inbox() }.onSuccess { _items.value = it }
                _loading.value = false
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Refresh the inbox immediately (e.g. after an account switch) instead of waiting for the poll. */
    fun reload() {
        viewModelScope.launch {
            runCatching { repo.inbox() }.onSuccess { _items.value = it }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 4000L
    }
}
