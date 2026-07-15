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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
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

/** An account remembered on this device, so the user can switch without re-typing a password. */
@Serializable
data class SavedAccount(
    val uid: String,
    val name: String,
    val email: String,
    val role: String,
    val token: String
)

/**
 * Persists the active auth token plus the list of accounts that have signed in on this device
 * (for one-tap account switching). [token] is always the active account's token.
 */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("defensync_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val accountsSerializer = ListSerializer(SavedAccount.serializer())

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
            }.apply()
        }

    /** All accounts remembered on this device. */
    var accounts: List<SavedAccount>
        get() = prefs.getString(KEY_ACCOUNTS, null)
            ?.let { runCatching { json.decodeFromString(accountsSerializer, it) }.getOrNull() }
            ?: emptyList()
        private set(value) {
            prefs.edit().putString(KEY_ACCOUNTS, json.encodeToString(accountsSerializer, value)).apply()
        }

    /** Add or refresh an account (matched by uid) and make it the active one.
     *  Keeps existing accounts in their original position so the switcher list stays stable. */
    fun upsertActive(account: SavedAccount) {
        val current = accounts
        accounts = if (current.any { it.uid == account.uid }) {
            current.map { if (it.uid == account.uid) account else it }
        } else {
            current + account
        }
        token = account.token
    }

    /** Make an already-saved account the active one. */
    fun setActive(account: SavedAccount) {
        token = account.token
    }

    /** Forget the current account; return the next remembered account (now active) or null. */
    fun removeActive(currentUid: String): SavedAccount? {
        accounts = accounts.filterNot { it.uid == currentUid }
        val next = accounts.firstOrNull()
        token = next?.token
        return next
    }

    fun clear() {
        token = null
    }

    private companion object {
        const val KEY_TOKEN = "auth_token"
        const val KEY_ACCOUNTS = "saved_accounts"
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
