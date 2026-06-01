package com.tapin.teacher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tapin.teacher.data.api.CourseView
import com.tapin.teacher.data.api.UserView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "tapin_auth")

class AuthStore(private val context: Context) {

    private val tokenKey   = stringPreferencesKey("token")
    private val userIdKey  = stringPreferencesKey("user_id")
    private val emailKey   = stringPreferencesKey("email")
    private val nameKey    = stringPreferencesKey("name")
    private val roleKey    = stringPreferencesKey("role")
    private val lastCourseJsonKey = stringPreferencesKey("last_course_json")

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    data class Session(val token: String, val user: UserView)

    val sessionFlow: Flow<Session?> = context.dataStore.data.map { prefs ->
        val token = prefs[tokenKey] ?: return@map null
        val id    = prefs[userIdKey]?.toLongOrNull() ?: return@map null
        val email = prefs[emailKey] ?: return@map null
        val name  = prefs[nameKey] ?: return@map null
        val role  = prefs[roleKey] ?: return@map null
        Session(token, UserView(id, email, name, role))
    }

    /** Ako vekje e izbran predmet vo prethodna sesija, pri sledno login go vrakame
     *  pravo na SessionScreen — spec 3.1.2: avtomatska aktivacija na sesija. */
    val lastCourseFlow: Flow<CourseView?> = context.dataStore.data.map { prefs ->
        val raw = prefs[lastCourseJsonKey] ?: return@map null
        runCatching { json.decodeFromString<CourseView>(raw) }.getOrNull()
    }

    suspend fun current(): Session? = sessionFlow.first()
    suspend fun lastCourse(): CourseView? = lastCourseFlow.first()

    suspend fun save(token: String, user: UserView) {
        context.dataStore.edit {
            it[tokenKey] = token
            it[userIdKey] = user.id.toString()
            it[emailKey] = user.email
            it[nameKey] = user.fullName
            it[roleKey] = user.role
        }
    }

    suspend fun saveLastCourse(course: CourseView) {
        context.dataStore.edit { it[lastCourseJsonKey] = json.encodeToString(course) }
    }

    suspend fun clearLastCourse() {
        context.dataStore.edit { it.remove(lastCourseJsonKey) }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
