package com.example.defensync.data.remote

import com.example.defensync.core.network.apiUrl
import com.example.defensync.core.network.orThrow
import com.example.defensync.data.dto.ContactDto
import com.example.defensync.data.dto.InboxItemDto
import com.example.defensync.data.dto.MessageDto
import com.example.defensync.data.dto.SendMessageRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

class DirectMessageApi(private val http: HttpClient) {

    suspend fun contacts(): List<ContactDto> =
        http.get(apiUrl("contacts")).orThrow().body()

    suspend fun inbox(): List<InboxItemDto> =
        http.get(apiUrl("inbox")).orThrow().body()

    suspend fun thread(otherId: String): List<MessageDto> =
        http.get(apiUrl("messages/$otherId")).orThrow().body()

    suspend fun send(otherId: String, body: String): List<MessageDto> =
        http.post(apiUrl("messages/$otherId")) { setBody(SendMessageRequest(body)) }.orThrow().body()

    suspend fun edit(otherId: String, msgId: String, body: String): List<MessageDto> =
        http.put(apiUrl("messages/$otherId/$msgId")) { setBody(SendMessageRequest(body)) }.orThrow().body()

    suspend fun delete(otherId: String, msgId: String): List<MessageDto> =
        http.delete(apiUrl("messages/$otherId/$msgId")).orThrow().body()
}
