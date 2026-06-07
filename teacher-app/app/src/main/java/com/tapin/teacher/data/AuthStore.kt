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
    private val lastCourseUserIdKey = stringPreferencesKey("last_course_user_id")

    private fun lastCourseKey(userId: Long) = stringPreferencesKey("last_course_$userId")

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

    /** Posleden izbran predmet — po userId, za da ne se mesaat profili na ist telefon. */
    suspend fun lastCourse(userId: Long): CourseView? {
        val prefs = context.dataStore.data.first()
        // Nov kluch po korisnik
        prefs[lastCourseKey(userId)]?.let { raw ->
            return runCatching { json.decodeFromString<CourseView>(raw) }.getOrNull()
        }
        // Legacy: stariot globalen kluch — samo ako pripagja na ovoj korisnik
        val legacyOwner = prefs[lastCourseUserIdKey]?.toLongOrNull()
        val legacyRaw = prefs[lastCourseJsonKey]
        if (legacyRaw != null && legacyOwner == userId) {
            return runCatching { json.decodeFromString<CourseView>(legacyRaw) }.getOrNull()
        }
        return null
    }

    /** @deprecated koristi lastCourse(userId) */
    val lastCourseFlow: Flow<CourseView?> = context.dataStore.data.map { prefs ->
        val raw = prefs[lastCourseJsonKey] ?: return@map null
        runCatching { json.decodeFromString<CourseView>(raw) }.getOrNull()
    }

    suspend fun current(): Session? = sessionFlow.first()

    /** @deprecated koristi lastCourse(userId) */
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

    suspend fun saveLastCourse(course: CourseView, userId: Long) {
        context.dataStore.edit {
            it[lastCourseKey(userId)] = json.encodeToString(course)
            // Ischisti legacy globalen zapis — povtorno mesanje na profili
            it.remove(lastCourseJsonKey)
            it.remove(lastCourseUserIdKey)
        }
    }

    suspend fun clearLastCourse(userId: Long? = null) {
        context.dataStore.edit { prefs ->
            if (userId != null) {
                prefs.remove(lastCourseKey(userId))
            } else {
                prefs.remove(lastCourseJsonKey)
                prefs.remove(lastCourseUserIdKey)
            }
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
