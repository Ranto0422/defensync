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

/** Group chat for a single defense. Polls the message list while the screen is open. */
class ChatViewModel : ViewModel() {

    private val repo = ServiceLocator.defenseRepository
    private var defenseId: String? = null
    private var pollJob: Job? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    fun start(id: String) {
        if (defenseId == id) return
        defenseId = id
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                runCatching { repo.messages(id) }.onSuccess { _messages.value = it }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun send(body: String) {
        val id = defenseId ?: return
        val text = body.trim()
        if (text.isEmpty() || _sending.value) return
        _sending.value = true
        viewModelScope.launch {
            runCatching { repo.sendMessage(id, text) }.onSuccess { _messages.value = it }
            _sending.value = false
        }
    }

    fun edit(msgId: String, body: String) {
        val id = defenseId ?: return
        val text = body.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            runCatching { repo.editMessage(id, msgId, text) }.onSuccess { _messages.value = it }
        }
    }

    fun delete(msgId: String) {
        val id = defenseId ?: return
        viewModelScope.launch {
            runCatching { repo.deleteMessage(id, msgId) }.onSuccess { _messages.value = it }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 3000L
    }
}
