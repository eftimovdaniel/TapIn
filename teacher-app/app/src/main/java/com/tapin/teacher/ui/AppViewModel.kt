package com.tapin.teacher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tapin.teacher.data.AuthStore
import com.tapin.teacher.data.api.ApiClient
import com.tapin.teacher.data.api.ApiException
import com.tapin.teacher.data.api.CourseView
import com.tapin.teacher.data.api.LoginRequest
import com.tapin.teacher.data.api.RegisterRequest
import com.tapin.teacher.data.api.UserView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object Loading : AuthState
    data class LoggedOut(val isBusy: Boolean = false, val error: String? = null) : AuthState
    data class LoggedIn(val user: UserView) : AuthState
}

/** Kade da odi profesorot posle login — spec 3.1.2 avtomatska sesija. */
sealed interface PostLoginTarget {
    data object Loading : PostLoginTarget
    data object Home : PostLoginTarget
    data class AttendanceSession(val course: CourseView) : PostLoginTarget
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = AuthStore(app)

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _postLoginTarget = MutableStateFlow<PostLoginTarget>(PostLoginTarget.Loading)
    val postLoginTarget: StateFlow<PostLoginTarget> = _postLoginTarget.asStateFlow()

    fun rememberCourse(course: CourseView) {
        viewModelScope.launch { store.saveLastCourse(course) }
    }

    fun forgetLastCourse() {
        viewModelScope.launch { store.clearLastCourse() }
    }

    init {
        viewModelScope.launch {
            val s = store.current()
            if (s != null) {
                ApiClient.setToken(s.token)
                _state.value = AuthState.LoggedIn(s.user)
                resolvePostLoginTarget()
            } else {
                _state.value = AuthState.LoggedOut()
                _postLoginTarget.value = PostLoginTarget.Home
            }
        }
    }

    /** Spec 3.1.2 — po login otvori sesija (posleden predmet ili edinstven predmet). */
    private suspend fun resolvePostLoginTarget() {
        _postLoginTarget.value = PostLoginTarget.Loading
        store.lastCourse()?.let { saved ->
            _postLoginTarget.value = PostLoginTarget.AttendanceSession(saved)
            return
        }
        try {
            val courses = ApiClient.listCourses()
            if (courses.size == 1) {
                store.saveLastCourse(courses.first())
                _postLoginTarget.value = PostLoginTarget.AttendanceSession(courses.first())
            } else {
                _postLoginTarget.value = PostLoginTarget.Home
            }
        } catch (_: Exception) {
            _postLoginTarget.value = PostLoginTarget.Home
        }
    }

    fun login(email: String, password: String) {
        _state.value = AuthState.LoggedOut(isBusy = true)
        viewModelScope.launch {
            try {
                val resp = ApiClient.login(LoginRequest(email.trim(), password))
                if (resp.user.role !in setOf("TEACHER", "ADMIN")) {
                    _state.value = AuthState.LoggedOut(error = "Овој профил не е професорски.")
                    return@launch
                }
                ApiClient.setToken(resp.token)
                store.save(resp.token, resp.user)
                _state.value = AuthState.LoggedIn(resp.user)
                resolvePostLoginTarget()
            } catch (e: ApiException) {
                _state.value = AuthState.LoggedOut(error = e.friendlyMessage)
            } catch (e: Exception) {
                _state.value = AuthState.LoggedOut(error = "Мрежен проблем: ${e.message ?: "?"}")
            }
        }
    }

    fun register(email: String, password: String, fullName: String) {
        _state.value = AuthState.LoggedOut(isBusy = true)
        viewModelScope.launch {
            try {
                val resp = ApiClient.register(
                    RegisterRequest(
                        email = email.trim(),
                        password = password,
                        fullName = fullName.trim(),
                        role = "TEACHER"
                    )
                )
                ApiClient.setToken(resp.token)
                store.save(resp.token, resp.user)
                _state.value = AuthState.LoggedIn(resp.user)
                resolvePostLoginTarget()
            } catch (e: ApiException) {
                _state.value = AuthState.LoggedOut(error = e.friendlyMessage)
            } catch (e: Exception) {
                _state.value = AuthState.LoggedOut(error = "Мрежен проблем: ${e.message ?: "?"}")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            store.clear()
            ApiClient.setToken(null)
            _state.value = AuthState.LoggedOut()
            _postLoginTarget.value = PostLoginTarget.Home
        }
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                return AppViewModel(app) as T
            }
        }
    }
}
