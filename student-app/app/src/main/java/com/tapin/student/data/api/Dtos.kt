package com.tapin.student.data.api
import kotlinx.serialization.Serializable

// telo za najava
@Serializable
data class LoginRequest(val email: String, val password: String)

// telo za registracija (role e STUDENT po default)
@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val fullName: String,
    val role: String = "STUDENT",
    val studentNumber: String? = null
)

// podatoci za korisnik vrateni od backend
@Serializable
data class UserView(
    val id: Long,
    val email: String,
    val fullName: String,
    val role: String,
    val studentNumber: String? = null
)

// odgovor po najava/registracija — token + korisnik + rok na traene
@Serializable
data class AuthResponse(
    val token: String,
    val user: UserView,
    val expiresAt: String
)

// greshka od backend; poliwata se opcionalni zavisno od izvorot
@Serializable
data class ApiError(
    val status: Int? = null,
    val title: String? = null,
    val detail: String? = null,
    val message: String? = null,
    val error: String? = null
) {
    // izberi prva dostapna poraka za prikaz na korisnikot
    fun friendly(): String = detail ?: message ?: title ?: error ?: "Unknown error"
}
