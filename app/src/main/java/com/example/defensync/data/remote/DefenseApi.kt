package com.example.defensync.data.remote

import com.example.defensync.core.network.apiUrl
import com.example.defensync.core.network.orThrow
import com.example.defensync.data.dto.CalendarEntryDto
import com.example.defensync.data.dto.CreateDefenseRequest
import com.example.defensync.data.dto.DefenseDto
import com.example.defensync.data.dto.MessageDto
import com.example.defensync.data.dto.RespondRequest
import com.example.defensync.data.dto.SendMessageRequest
import com.example.defensync.data.dto.SlotDto
import com.example.defensync.data.dto.UpdateMembersRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

class DefenseApi(private val http: HttpClient) {

    suspend fun list(): List<DefenseDto> =
        http.get(apiUrl("defenses")).orThrow().body()

    /** Every scheduled defense in the system (shared calendar), regardless of participation. */
    suspend fun calendar(): List<CalendarEntryDto> =
        http.get(apiUrl("calendar")).orThrow().body()

    suspend fun create(req: CreateDefenseRequest): DefenseDto =
        http.post(apiUrl("defenses")) { setBody(req) }.orThrow().body()

    suspend fun get(id: String): DefenseDto =
        http.get(apiUrl("defenses/$id")).orThrow().body()

    suspend fun updateAvailability(id: String, availability: Map<String, List<List<Int>>>): DefenseDto =
        http.put(apiUrl("defenses/$id/availability")) { setBody(availability) }.orThrow().body()

    suspend fun updateMembers(id: String, req: UpdateMembersRequest): DefenseDto =
        http.put(apiUrl("defenses/$id/members")) { setBody(req) }.orThrow().body()

    suspend fun selectSlot(id: String, slot: SlotDto): DefenseDto =
        http.post(apiUrl("defenses/$id/slot")) { setBody(slot) }.orThrow().body()

    suspend fun respond(id: String, accept: Boolean): DefenseDto =
        http.post(apiUrl("defenses/$id/respond")) { setBody(RespondRequest(accept)) }.orThrow().body()

    suspend fun cancel(id: String): DefenseDto =
        http.post(apiUrl("defenses/$id/cancel")).orThrow().body()

    suspend fun delete(id: String) {
        http.delete(apiUrl("defenses/$id")).orThrow()
    }

    suspend fun messages(id: String): List<MessageDto> =
        http.get(apiUrl("defenses/$id/messages")).orThrow().body()

    suspend fun sendMessage(id: String, body: String): List<MessageDto> =
        http.post(apiUrl("defenses/$id/messages")) { setBody(SendMessageRequest(body)) }.orThrow().body()

    suspend fun editMessage(id: String, msgId: String, body: String): List<MessageDto> =
        http.put(apiUrl("defenses/$id/messages/$msgId")) { setBody(SendMessageRequest(body)) }.orThrow().body()

    suspend fun deleteMessage(id: String, msgId: String): List<MessageDto> =
        http.delete(apiUrl("defenses/$id/messages/$msgId")).orThrow().body()
}
