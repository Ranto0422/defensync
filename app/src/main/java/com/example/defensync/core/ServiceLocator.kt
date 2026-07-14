package com.example.defensync.core

import android.content.Context
import com.example.defensync.core.network.ApiConfig
import com.example.defensync.core.network.ServerConfig
import com.example.defensync.core.network.TokenStore
import com.example.defensync.core.network.createHttpClient
import com.example.defensync.data.remote.AuthApi
import com.example.defensync.data.remote.DefenseApi
import com.example.defensync.data.remote.DirectMessageApi
import com.example.defensync.data.remote.UserApi
import com.example.defensync.data.repository.AuthRepository
import com.example.defensync.data.repository.DefenseRepository
import com.example.defensync.data.repository.DirectMessageRepository
import com.example.defensync.data.repository.UserRepository

/**
 * Tiny manual dependency container. Initialized once from [DefenSyncApp.onCreate];
 * ViewModels read the repositories they need from here.
 */
object ServiceLocator {

    lateinit var tokenStore: TokenStore
        private set
    lateinit var serverConfig: ServerConfig
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var userRepository: UserRepository
        private set
    lateinit var defenseRepository: DefenseRepository
        private set
    lateinit var directMessageRepository: DirectMessageRepository
        private set

    fun init(context: Context) {
        tokenStore = TokenStore(context.applicationContext)
        serverConfig = ServerConfig(context.applicationContext)
        ApiConfig.baseUrl = serverConfig.baseUrl   // restore a persisted LAN address before any request
        val http = createHttpClient(tokenStore)
        authRepository = AuthRepository(AuthApi(http), tokenStore)
        userRepository = UserRepository(UserApi(http))
        defenseRepository = DefenseRepository(DefenseApi(http))
        directMessageRepository = DirectMessageRepository(DirectMessageApi(http))
    }
}
