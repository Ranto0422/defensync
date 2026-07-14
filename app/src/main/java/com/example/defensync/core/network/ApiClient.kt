package com.example.defensync.core.network

import android.content.Context
import com.example.defensync.data.dto.ErrorDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Where the PHP API lives. [DEFAULT_BASE_URL]'s 10.0.2.2 is the host machine as seen from the Android
 * emulator; on a physical phone it's overridden at runtime with the XAMPP machine's LAN IP (edited on
 * the sign-in screen, persisted by [ServerConfig], restored at startup by ServiceLocator).
 */
object ApiConfig {
    const val DEFAULT_BASE_URL = "http://10.0.2.2/defensync/"

    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL
}

/** Thrown when the API returns a non-2xx status; message is the server's error text. */
class ApiException(message: String) : Exception(message)

/**
 * Persists the API base URL across launches, so a physical device can point at the LAN address of the
 * machine running XAMPP without rebuilding. Writing also updates the live [ApiConfig.baseUrl].
 */
class ServerConfig(context: Context) {
    private val prefs = context.getSharedPreferences("defensync_prefs", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, ApiConfig.DEFAULT_BASE_URL) ?: ApiConfig.DEFAULT_BASE_URL
        set(value) {
            val normalized = normalize(value)
            prefs.edit().putString(KEY_BASE_URL, normalized).apply()
            ApiConfig.baseUrl = normalized
        }

    private companion object {
        const val KEY_BASE_URL = "server_base_url"

        /** Tidy user input: blank → default; add http:// if no scheme; ensure a single trailing slash. */
        fun normalize(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ApiConfig.DEFAULT_BASE_URL
            val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
            return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
        }
    }
}

/** Persists the auth token across app launches. */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("defensync_prefs", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
            }.apply()
        }

    fun clear() { token = null }

    private companion object {
        const val KEY_TOKEN = "auth_token"
    }
}

private val appJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/** Builds the shared Ktor client. The token is read fresh on every request. */
fun createHttpClient(tokenStore: TokenStore): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    install(ContentNegotiation) { json(appJson) }
    defaultRequest {
        contentType(ContentType.Application.Json)
        tokenStore.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }
}

/** Absolute URL for an API path (paths are given without a leading slash). */
fun apiUrl(path: String): String = ApiConfig.baseUrl + path

/** Returns the response if 2xx, otherwise throws [ApiException] with the server's message. */
suspend fun HttpResponse.orThrow(): HttpResponse {
    if (status.isSuccess()) return this
    val message = runCatching {
        appJson.decodeFromString(ErrorDto.serializer(), bodyAsText()).error
    }.getOrDefault("Request failed (${status.value})")
    throw ApiException(message)
}
