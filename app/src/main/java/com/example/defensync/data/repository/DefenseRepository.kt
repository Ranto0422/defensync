package com.example.defensync.data.repository

import com.example.defensync.data.dto.CreateDefenseRequest
import com.example.defensync.data.dto.SlotDto
import com.example.defensync.data.dto.UpdateMembersRequest
import com.example.defensync.data.dto.toDomain
import com.example.defensync.data.remote.DefenseApi
import com.example.defensync.domain.model.AlignedSlot
import com.example.defensync.domain.model.ChatMessage
import com.example.defensync.domain.model.DefenseRequest
import com.example.defensync.domain.model.TimeRange
import com.example.defensync.domain.model.Weekday

class DefenseRepository(private val defenseApi: DefenseApi) {

    suspend fun list(): List<DefenseRequest> = defenseApi.list().map { it.toDomain() }

    suspend fun create(
        title: String,
        adviserId: String,
        panelistIds: List<String>,
        studentIds: List<String> = emptyList()
    ): DefenseRequest =
        defenseApi.create(CreateDefenseRequest(title.trim(), adviserId, panelistIds, studentIds)).toDomain()

    suspend fun saveGroupAvailability(
        defenseId: String,
        availability: Map<Weekday, List<TimeRange>>
    ): DefenseRequest =
        defenseApi.updateAvailability(defenseId, availability.toWire()).toDomain()

    suspend fun selectSlot(defenseId: String, slot: AlignedSlot): DefenseRequest =
        defenseApi.selectSlot(defenseId, SlotDto(slot.weekday.name, slot.start)).toDomain()

    suspend fun respond(defenseId: String, accept: Boolean): DefenseRequest =
        defenseApi.respond(defenseId, accept).toDomain()

    suspend fun updateMembers(
        defenseId: String,
        adviserId: String,
        panelistIds: List<String>,
        studentIds: List<String>
    ): DefenseRequest =
        defenseApi.updateMembers(
            defenseId,
            UpdateMembersRequest(adviserId, panelistIds, studentIds)
        ).toDomain()

    suspend fun cancel(defenseId: String): DefenseRequest =
        defenseApi.cancel(defenseId).toDomain()

    suspend fun delete(defenseId: String) = defenseApi.delete(defenseId)

    suspend fun messages(defenseId: String): List<ChatMessage> =
        defenseApi.messages(defenseId).map { it.toDomain() }

    suspend fun sendMessage(defenseId: String, body: String): List<ChatMessage> =
        defenseApi.sendMessage(defenseId, body).map { it.toDomain() }

    suspend fun editMessage(defenseId: String, msgId: String, body: String): List<ChatMessage> =
        defenseApi.editMessage(defenseId, msgId, body).map { it.toDomain() }

    suspend fun deleteMessage(defenseId: String, msgId: String): List<ChatMessage> =
        defenseApi.deleteMessage(defenseId, msgId).map { it.toDomain() }
}
