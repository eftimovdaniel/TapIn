package com.tapin.student.data.api

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val fullName: String,
    val role: String = "STUDENT",
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
