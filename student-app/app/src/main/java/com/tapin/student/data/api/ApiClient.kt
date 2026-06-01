package com.tapin.student.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object ApiConfig {
    /**
     * BASE_URL — kade da se zakaci klientot.
     *
     *  Produkcija (Render):             https://tapin.onrender.com
     *  Real telefon (Mac LAN IP):       http://192.168.0.106:8080
     *  Android emulator na host Mac:    http://10.0.2.2:8080
     */
    const val BASE_URL = "https://tapin.onrender.com"
}

class ApiException(val statusCode: Int, val friendlyMessage: String) : Exception(friendlyMessage)

object ApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Volatile private var token: String? = null
    fun setToken(value: String?) { token = value }

    val http: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        defaultRequest {
            url(ApiConfig.BASE_URL)
            contentType(ContentType.Application.Json)
            token?.let { header("Authorization", "Bearer $it") }
        }
        expectSuccess = false
    }

    suspend fun register(req: RegisterRequest): AuthResponse =
        handle(http.post("/api/auth/register") { setBody(req) })

    suspend fun login(req: LoginRequest): AuthResponse =
        handle(http.post("/api/auth/login") { setBody(req) })

    suspend fun me(): UserView = handle(http.get("/api/auth/me"))

    private suspend inline fun <reified T> handle(resp: HttpResponse): T {
        if (resp.status.isSuccess()) return resp.body()
        val msg = runCatching { resp.body<ApiError>().friendly() }
            .getOrElse { resp.status.description }
        throw ApiException(resp.status.value, msg)
    }

    private fun HttpStatusCode.isSuccess() = value in 200..299
}
