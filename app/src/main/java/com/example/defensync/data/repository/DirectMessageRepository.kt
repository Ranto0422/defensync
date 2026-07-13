package com.example.defensync.data.repository

import com.example.defensync.data.dto.toDomain
import com.example.defensync.data.remote.DirectMessageApi
import com.example.defensync.domain.model.ChatMessage
import com.example.defensync.domain.model.Contact
import com.example.defensync.domain.model.InboxItem

class DirectMessageRepository(private val api: DirectMessageApi) {

    suspend fun contacts(): List<Contact> = api.contacts().map { it.toDomain() }

    suspend fun inbox(): List<InboxItem> = api.inbox().map { it.toDomain() }

    suspend fun thread(otherId: String): List<ChatMessage> = api.thread(otherId).map { it.toDomain() }

    suspend fun send(otherId: String, body: String): List<ChatMessage> =
        api.send(otherId, body).map { it.toDomain() }

    suspend fun edit(otherId: String, msgId: String, body: String): List<ChatMessage> =
        api.edit(otherId, msgId, body).map { it.toDomain() }

    suspend fun delete(otherId: String, msgId: String): List<ChatMessage> =
        api.delete(otherId, msgId).map { it.toDomain() }
}
