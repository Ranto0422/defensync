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

/** Where the PHP API lives. 10.0.2.2 is the host machine as seen from the Android emulator. */
object ApiConfig {
    // Physical device on the same Wi-Fi: replace with your Mac's LAN IP, e.g. "http://192.168.1.20/defensync/"
    const val BASE_URL = "http://10.0.2.2/defensync/"
}

/** Thrown when the API returns a non-2xx status; message is the server's error text. */
class ApiException(message: String) : Exception(message)

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
fun apiUrl(path: String): String = ApiConfig.BASE_URL + path

/** Returns the response if 2xx, otherwise throws [ApiException] with the server's message. */
suspend fun HttpResponse.orThrow(): HttpResponse {
    if (status.isSuccess()) return this
    val message = runCatching {
        appJson.decodeFromString(ErrorDto.serializer(), bodyAsText()).error
    }.getOrDefault("Request failed (${status.value})")
    throw ApiException(message)
}
