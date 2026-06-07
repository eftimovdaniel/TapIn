package com.tapin.student.data
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tapin.student.data.api.UserView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// datastore za trajno chuvanje na najavata (token + podatoci za korisnikot)
private val Context.dataStore by preferencesDataStore(name = "tapin_student_auth")

// chuva i vchituva sesija na student megju startuvanja na aplikacijata
class AuthStore(private val context: Context) {

    // klucevi pod koi se chuva sekoe pole vo datastore
    private val tokenKey       = stringPreferencesKey("token")
    private val userIdKey      = stringPreferencesKey("user_id")
    private val emailKey       = stringPreferencesKey("email")
    private val nameKey        = stringPreferencesKey("name")
    private val roleKey        = stringPreferencesKey("role")
    private val studentNumKey  = stringPreferencesKey("student_number")

    // edna zachuvana sesija — token i podatoci za korisnikot
    data class Session(val token: String, val user: UserView)

    // live stream na sesijata; null ako nema kompletna zachuvana najava
    val sessionFlow: Flow<Session?> = context.dataStore.data.map { prefs ->
        val token = prefs[tokenKey] ?: return@map null
        val id = prefs[userIdKey]?.toLongOrNull() ?: return@map null
        val email = prefs[emailKey] ?: return@map null
        val name = prefs[nameKey] ?: return@map null
        val role = prefs[roleKey] ?: return@map null
        val number = prefs[studentNumKey]
        Session(token, UserView(id, email, name, role, number))
    }

    // edno-kratno chitanje na momentalnata sesija
    suspend fun current(): Session? = sessionFlow.first()

    // zapishi nova sesija po uspeshna najava/registracija
    suspend fun save(token: String, user: UserView) {
        context.dataStore.edit {
            it[tokenKey] = token
            it[userIdKey] = user.id.toString()
            it[emailKey] = user.email
            it[nameKey] = user.fullName
            it[roleKey] = user.role
            user.studentNumber?.let { num -> it[studentNumKey] = num }
        }
    }

    // izbrishi ja sesijata (odjava)
    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
