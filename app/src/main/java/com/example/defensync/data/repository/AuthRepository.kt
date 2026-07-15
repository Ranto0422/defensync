package com.example.defensync.data.repository

import com.example.defensync.core.network.SavedAccount
import com.example.defensync.core.network.TokenStore
import com.example.defensync.data.dto.AuthResponse
import com.example.defensync.data.dto.LoginRequest
import com.example.defensync.data.dto.RegisterRequest
import com.example.defensync.data.dto.toDomain
import com.example.defensync.data.remote.AuthApi
import com.example.defensync.domain.model.Role
import com.example.defensync.domain.model.UserProfile

/** Handles register/login/logout, remembers tokens, and supports multi-account switching. */
class AuthRepository(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore
) {
    val hasToken: Boolean get() = tokenStore.token != null

    /** Accounts remembered on this device (for one-tap switching). */
    val accounts: List<SavedAccount> get() = tokenStore.accounts

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
        return remember(res)
    }

    suspend fun login(email: String, password: String): UserProfile {
        val res = authApi.login(LoginRequest(email.trim(), password))
        return remember(res)
    }

    /** Persist the account and make it active. */
    private fun remember(res: AuthResponse): UserProfile {
        tokenStore.upsertActive(
            SavedAccount(res.user.id, res.user.name, res.user.email, res.user.role, res.token)
        )
        return res.user.toDomain()
    }

    /** Fetch the current user for the active token; used to restore/switch a session. */
    suspend fun me(): UserProfile = authApi.me().user.toDomain()

    /** Ensure the currently-active account is in the remembered list (e.g. a pre-existing session). */
    fun rememberActive(profile: UserProfile) {
        val token = tokenStore.token ?: return
        tokenStore.upsertActive(
            SavedAccount(profile.uid, profile.name, profile.email, profile.role.name, token)
        )
    }

    /** Switch the active account to an already-remembered one (no password needed). */
    fun activate(account: SavedAccount) = tokenStore.setActive(account)

    /** Forget the current account; returns the next remembered account (now active) or null. */
    fun signOutActive(currentUid: String): SavedAccount? = tokenStore.removeActive(currentUid)

    fun signOut() = tokenStore.clear()
}
