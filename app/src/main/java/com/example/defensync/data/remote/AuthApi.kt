package com.example.defensync.data.remote

import com.example.defensync.core.network.apiUrl
import com.example.defensync.core.network.orThrow
import com.example.defensync.data.dto.AuthResponse
import com.example.defensync.data.dto.LoginRequest
import com.example.defensync.data.dto.MeResponse
import com.example.defensync.data.dto.RegisterRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class AuthApi(private val http: HttpClient) {

    suspend fun register(req: RegisterRequest): AuthResponse =
        http.post(apiUrl("auth/register")) { setBody(req) }.orThrow().body()

    suspend fun login(req: LoginRequest): AuthResponse =
        http.post(apiUrl("auth/login")) { setBody(req) }.orThrow().body()

    suspend fun me(): MeResponse =
        http.get(apiUrl("auth/me")).orThrow().body()
}
