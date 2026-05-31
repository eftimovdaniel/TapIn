package com.tapin.teacher.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
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
     * Android emulator: 10.0.2.2 maps to host's localhost.
     * Real phone: replace with your laptop's LAN IP, e.g. http://192.168.1.42:8080
     */
    const val BASE_URL = "http://10.0.2.2:8080"
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

    // ─────────────────────── Auth ───────────────────────

    suspend fun register(req: RegisterRequest): AuthResponse =
        handle(http.post("/api/auth/register") { setBody(req) })

    suspend fun login(req: LoginRequest): AuthResponse =
        handle(http.post("/api/auth/login") { setBody(req) })

    suspend fun me(): UserView = handle(http.get("/api/auth/me"))

    suspend fun findStudentByNumber(studentNumber: String): UserView =
        handle(http.get("/api/users/by-number/$studentNumber"))

    // ─────────────────────── Courses ───────────────────────

    suspend fun listCourses(): List<CourseView> = handle(http.get("/api/courses"))

    suspend fun createCourse(req: CourseRequest): CourseView =
        handle(http.post("/api/courses") { setBody(req) })

    // ─────────────────────── Sessions ───────────────────────

    suspend fun startSession(courseId: Long): SessionView =
        handle(http.post("/api/sessions") { setBody(StartSessionRequest(courseId)) })

    suspend fun closeSession(sessionId: Long) {
        val resp = http.post("/api/sessions/$sessionId/close")
        if (!resp.status.isSuccess()) throwFromResponse(resp)
    }

    suspend fun listSessions(courseId: Long? = null): List<SessionView> = handle(
        http.get("/api/sessions") {
            courseId?.let { parameter("courseId", it) }
        }
    )

    // ─────────────────────── Attendance ───────────────────────

    suspend fun uploadAttendance(req: BulkAttendanceRequest): BulkAttendanceResponse =
        handle(http.post("/api/attendance") { setBody(req) })

    suspend fun listAttendance(
        sessionId: Long? = null,
        courseId: Long? = null,
        page: Int = 0,
        size: Int = 100
    ): AttendancePage = handle(
        http.get("/api/attendance") {
            sessionId?.let { parameter("sessionId", it) }
            courseId?.let { parameter("courseId", it) }
            parameter("page", page)
            parameter("size", size)
        }
    )

    // ─────────────────────── helpers ───────────────────────

    private suspend inline fun <reified T> handle(resp: HttpResponse): T {
        if (resp.status.isSuccess()) return resp.body()
        throwFromResponse(resp)
    }

    private suspend fun throwFromResponse(resp: HttpResponse): Nothing {
        val msg = runCatching { resp.body<ApiError>().friendly() }
            .getOrElse { resp.status.description }
        throw ApiException(resp.status.value, msg)
    }

    private fun HttpStatusCode.isSuccess() = value in 200..299
}
