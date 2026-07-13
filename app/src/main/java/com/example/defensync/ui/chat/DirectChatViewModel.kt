package com.example.defensync.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.defensync.core.ServiceLocator
import com.example.defensync.domain.model.ChatMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A private 1-on-1 thread with one other user. Polls while the screen is open. */
class DirectChatViewModel : ViewModel() {

    private val repo = ServiceLocator.directMessageRepository
    private var otherId: String? = null
    private var pollJob: Job? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    fun start(id: String) {
        if (otherId == id) return
        otherId = id
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                runCatching { repo.thread(id) }.onSuccess { _messages.value = it }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun send(body: String) {
        val id = otherId ?: return
        val text = body.trim()
        if (text.isEmpty() || _sending.value) return
        _sending.value = true
        viewModelScope.launch {
            runCatching { repo.send(id, text) }.onSuccess { _messages.value = it }
            _sending.value = false
        }
    }

    fun edit(msgId: String, body: String) {
        val id = otherId ?: return
        val text = body.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            runCatching { repo.edit(id, msgId, text) }.onSuccess { _messages.value = it }
        }
    }

    fun delete(msgId: String) {
        val id = otherId ?: return
        viewModelScope.launch {
            runCatching { repo.delete(id, msgId) }.onSuccess { _messages.value = it }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 3000L
    }
}
