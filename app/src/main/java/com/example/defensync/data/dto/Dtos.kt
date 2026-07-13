package com.example.defensync.data.dto

import com.example.defensync.domain.model.AlignedSlot
import com.example.defensync.domain.model.DefenseRequest
import com.example.defensync.domain.model.DefenseStatus
import com.example.defensync.domain.model.ResponseStatus
import com.example.defensync.domain.model.Role
import com.example.defensync.domain.model.TimeRange
import com.example.defensync.domain.model.UserProfile
import com.example.defensync.domain.model.Weekday
import kotlinx.serialization.Serializable

/* ---------- Request bodies ---------- */

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val role: String,
    val studentNumber: String? = null
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class CreateDefenseRequest(
    val title: String,
    val adviserId: String,
    val panelistIds: List<String>,
    val studentIds: List<String> = emptyList()
)

@Serializable
data class UpdateMembersRequest(
    val adviserId: String,
    val panelistIds: List<String>,
    val studentIds: List<String>
)

/** A picked defense start: weekday name + start minutes-from-midnight. */
@Serializable
data class SlotDto(val weekday: String, val start: Int)

@Serializable
data class RespondRequest(val accept: Boolean)

@Serializable
data class SendMessageRequest(val body: String)

@Serializable
data class MessageDto(
    val id: String,
    val senderId: String,
    val senderName: String = "",
    val body: String = "",
    val createdAt: Long = 0L,
    val edited: Boolean = false
)

@Serializable
data class ContactDto(
    val id: String,
    val name: String,
    val role: String,
    val lastMessage: String? = null,
    val lastAt: Long = 0L
)

@Serializable
data class InboxItemDto(
    val type: String,
    val key: String,
    val title: String = "",
    val subtitle: String = "",
    val lastAt: Long = 0L,
    val unread: Int = 0
)

/* ---------- Response bodies ---------- */

@Serializable
data class AuthResponse(val token: String, val user: UserDto)

@Serializable
data class MeResponse(val user: UserDto)

/** availability: weekday -> list of [start, end] pairs (minutes). */
@Serializable
data class AvailabilityResponse(val availability: Map<String, List<List<Int>>> = emptyMap())

@Serializable
data class ErrorDto(val error: String = "Request failed")

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val studentNumber: String? = null,
    val availability: Map<String, List<List<Int>>> = emptyMap()
)

@Serializable
data class DefenseDto(
    val id: String,
    val title: String = "",
    val studentId: String,
    val studentName: String = "",
    val studentIds: List<String> = emptyList(),
    val studentNames: List<String> = emptyList(),
    val adviserId: String = "",
    val adviserName: String = "",
    val panelistIds: List<String> = emptyList(),
    val panelistNames: List<String> = emptyList(),
    val availability: Map<String, Map<String, List<List<Int>>>> = emptyMap(),
    val slot: SlotDto? = null,
    val responses: Map<String, String> = emptyMap(),
    val status: String = "SCHEDULING",
    val createdAt: Long = 0L,
    val unreadMessages: Int = 0
)

/* ---------- DTO -> domain mapping ---------- */

fun List<List<Int>>.toRanges(): List<TimeRange> =
    mapNotNull { if (it.size >= 2) TimeRange(it[0], it[1]) else null }

fun Map<String, List<List<Int>>>.toRangeMap(): Map<String, List<TimeRange>> =
    mapValues { it.value.toRanges() }

fun MessageDto.toDomain(): com.example.defensync.domain.model.ChatMessage =
    com.example.defensync.domain.model.ChatMessage(id, senderId, senderName, body, createdAt, edited)

fun ContactDto.toDomain(): com.example.defensync.domain.model.Contact =
    com.example.defensync.domain.model.Contact(id, name, Role.from(role), lastMessage, lastAt)

fun InboxItemDto.toDomain(): com.example.defensync.domain.model.InboxItem =
    com.example.defensync.domain.model.InboxItem(
        type = if (type.equals("group", true)) com.example.defensync.domain.model.ConversationType.GROUP
        else com.example.defensync.domain.model.ConversationType.DM,
        key = key,
        title = title,
        subtitle = subtitle,
        lastAt = lastAt,
        unread = unread
    )

fun UserDto.toDomain(): UserProfile = UserProfile(
    uid = id,
    name = name,
    email = email,
    role = Role.from(role),
    studentNumber = studentNumber,
    availability = availability.toRangeMap()
)

fun DefenseDto.toDomain(): DefenseRequest = DefenseRequest(
    id = id,
    title = title,
    studentId = studentId,
    studentName = studentName,
    studentIds = studentIds,
    studentNames = studentNames,
    adviserId = adviserId,
    adviserName = adviserName,
    panelistIds = panelistIds,
    panelistNames = panelistNames,
    availability = availability.mapValues { (_, days) -> days.toRangeMap() },
    slot = slot?.let { AlignedSlot(Weekday.from(it.weekday), it.start) },
    responses = responses.mapValues { (_, v) ->
        runCatching { ResponseStatus.valueOf(v) }.getOrDefault(ResponseStatus.PENDING)
    },
    status = DefenseStatus.from(status),
    createdAt = createdAt,
    unreadMessages = unreadMessages
)
