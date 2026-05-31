package com.tapin.teacher.data.api

import kotlinx.serialization.Serializable

// ─────────────────────── Auth ───────────────────────

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val fullName: String,
    val role: String = "TEACHER",
    val studentNumber: String? = null
)

@Serializable
data class UserView(
    val id: Long,
    val email: String,
    val fullName: String,
    val role: String,
    val studentNumber: String? = null
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserView,
    val expiresAt: String
)

@Serializable
data class ApiError(
    val status: Int? = null,
    val title: String? = null,
    val detail: String? = null,
    val message: String? = null,
    val error: String? = null
) {
    fun friendly(): String = detail ?: message ?: title ?: error ?: "Unknown error"
}

// ─────────────────────── Courses ───────────────────────

@Serializable
data class CourseRequest(
    val code: String,
    val name: String,
    val teacherId: Long? = null
)

@Serializable
data class CourseView(
    val id: Long,
    val code: String,
    val name: String,
    val teacherId: Long,
    val teacherName: String
)

// ─────────────────────── Sessions ───────────────────────

@Serializable
data class StartSessionRequest(val courseId: Long)

@Serializable
data class SessionView(
    val id: Long,
    val courseId: Long,
    val courseName: String,
    val teacherId: Long,
    val teacherName: String,
    val startedAt: String,
    val endedAt: String? = null,
    val active: Boolean,
    val attendanceCount: Int = 0
)

// ─────────────────────── Attendance ───────────────────────

@Serializable
data class TapRecord(
    val studentId: Long,
    val tappedAt: String? = null
)

@Serializable
data class BulkAttendanceRequest(
    val sessionId: Long,
    val records: List<TapRecord>
)

@Serializable
data class BulkAttendanceResponse(
    val accepted: Int,
    val duplicates: Int,
    val rejected: Int,
    val ids: List<Long> = emptyList()
)

@Serializable
data class AttendanceView(
    val id: Long,
    val sessionId: Long,
    val studentId: Long,
    val studentName: String,
    val studentNumber: String? = null,
    val courseName: String,
    val teacherName: String,
    val tappedAt: String
)

@Serializable
data class AttendancePage(
    val items: List<AttendanceView>,
    val totalElements: Int,
    val page: Int,
    val size: Int
)
