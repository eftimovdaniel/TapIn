package com.tapin.teacher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tapin.teacher.data.AuthStore
import com.tapin.teacher.data.api.ApiClient
import com.tapin.teacher.data.api.ApiException
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

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = AuthStore(app)

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = store.current()
            if (s != null) {
                ApiClient.setToken(s.token)
                _state.value = AuthState.LoggedIn(s.user)
            } else {
                _state.value = AuthState.LoggedOut()
            }
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
