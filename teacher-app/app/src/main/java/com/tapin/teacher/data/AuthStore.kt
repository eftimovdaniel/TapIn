package com.tapin.teacher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tapin.teacher.data.api.UserView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "tapin_auth")

class AuthStore(private val context: Context) {

    private val tokenKey   = stringPreferencesKey("token")
    private val userIdKey  = stringPreferencesKey("user_id")
    private val emailKey   = stringPreferencesKey("email")
    private val nameKey    = stringPreferencesKey("name")
    private val roleKey    = stringPreferencesKey("role")

    data class Session(val token: String, val user: UserView)

    val sessionFlow: Flow<Session?> = context.dataStore.data.map { prefs ->
        val token = prefs[tokenKey] ?: return@map null
        val id    = prefs[userIdKey]?.toLongOrNull() ?: return@map null
        val email = prefs[emailKey] ?: return@map null
        val name  = prefs[nameKey] ?: return@map null
        val role  = prefs[roleKey] ?: return@map null
        Session(token, UserView(id, email, name, role))
    }

    suspend fun current(): Session? = sessionFlow.first()

    suspend fun save(token: String, user: UserView) {
        context.dataStore.edit {
            it[tokenKey] = token
            it[userIdKey] = user.id.toString()
            it[emailKey] = user.email
            it[nameKey] = user.fullName
            it[roleKey] = user.role
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
