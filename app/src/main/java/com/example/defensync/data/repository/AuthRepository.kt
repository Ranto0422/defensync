package com.example.defensync.data.repository

import com.example.defensync.core.network.TokenStore
import com.example.defensync.data.dto.LoginRequest
import com.example.defensync.data.dto.RegisterRequest
import com.example.defensync.data.dto.toDomain
import com.example.defensync.data.remote.AuthApi
import com.example.defensync.domain.model.Role
import com.example.defensync.domain.model.UserProfile

/** Handles register/login/logout and remembers the token. */
class AuthRepository(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore
) {
    val hasToken: Boolean get() = tokenStore.token != null

    suspend fun register(
        name: String,
        email: String,
        password: String,
        role: Role,
        studentNumber: String?
    ): UserProfile {
        val res = authApi.register(
            RegisterRequest(name.trim(), email.trim(), password, role.name, studentNumber?.trim())
        )
        tokenStore.token = res.token
        return res.user.toDomain()
    }

    suspend fun login(email: String, password: String): UserProfile {
        val res = authApi.login(LoginRequest(email.trim(), password))
        tokenStore.token = res.token
        return res.user.toDomain()
    }

    /** Fetch the current user for a stored token; used to restore a session on launch. */
    suspend fun me(): UserProfile = authApi.me().user.toDomain()

    fun signOut() = tokenStore.clear()
}
