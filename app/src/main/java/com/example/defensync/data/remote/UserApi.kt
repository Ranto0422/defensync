package com.example.defensync.data.remote

import com.example.defensync.core.network.apiUrl
import com.example.defensync.core.network.orThrow
import com.example.defensync.data.dto.AvailabilityResponse
import com.example.defensync.data.dto.UserDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody

class UserApi(private val http: HttpClient) {

    suspend fun professors(): List<UserDto> =
        http.get(apiUrl("professors")).orThrow().body()

    suspend fun students(): List<UserDto> =
        http.get(apiUrl("students")).orThrow().body()

    suspend fun getAvailability(): AvailabilityResponse =
        http.get(apiUrl("availability")).orThrow().body()

    suspend fun updateAvailability(availability: Map<String, List<List<Int>>>): AvailabilityResponse =
        http.put(apiUrl("availability")) { setBody(availability) }.orThrow().body()
}
