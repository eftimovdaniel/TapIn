package com.tapin.student.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tapin.student.data.AuthStore
import com.tapin.student.data.api.ApiClient
import com.tapin.student.data.api.ApiException
import com.tapin.student.data.api.LoginRequest
import com.tapin.student.data.api.RegisterRequest
import com.tapin.student.data.api.UserView
import com.tapin.student.nfc.StudentNumberStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// sostojba na najavata — vchituvanje, odjaven ili najaven
sealed interface AuthState {
    data object Loading : AuthState
    data class LoggedOut(val isBusy: Boolean = false, val error: String? = null) : AuthState
    data class LoggedIn(val user: UserView) : AuthState
}

// glaven viewmodel — drzhi sostojba na najava i logika za login/register/logout
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = AuthStore(app)

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    // pri start proveri dali ima zachuvana sesija na student
    init {
        viewModelScope.launch {
            val s = store.current()
            // prifati ja sesijata samo ako e na student
            if (s != null && s.user.role == "STUDENT") {
                ApiClient.setToken(s.token)
                StudentNumberStore.set(getApplication(), s.user.studentNumber, s.user.fullName)
                _state.value = AuthState.LoggedIn(s.user)
            } else {
                StudentNumberStore.set(getApplication(), null)
                _state.value = AuthState.LoggedOut()
            }
        }
    }

    // najava; dozvoleno samo za studentski profili
    fun login(email: String, password: String) {
        _state.value = AuthState.LoggedOut(isBusy = true)
        viewModelScope.launch {
            try {
                val resp = ApiClient.login(LoginRequest(email.trim(), password))
                // odbij profesori/admini — ovoj app e samo za studenti
                if (resp.user.role != "STUDENT") {
                    _state.value = AuthState.LoggedOut(
                        error = "Оваа апликација е само за студенти."
                    )
                    return@launch
                }
                ApiClient.setToken(resp.token)
                store.save(resp.token, resp.user)
                StudentNumberStore.set(getApplication(), resp.user.studentNumber, resp.user.fullName)
                _state.value = AuthState.LoggedIn(resp.user)
            } catch (e: ApiException) {
                _state.value = AuthState.LoggedOut(error = e.friendlyMessage)
            } catch (e: Exception) {
                _state.value = AuthState.LoggedOut(error = "Мрежен проблем: ${e.message ?: "?"}")
            }
        }
    }

    // registracija na nov student i avtomatska najava po uspeh
    fun register(email: String, password: String, fullName: String, studentNumber: String) {
        _state.value = AuthState.LoggedOut(isBusy = true)
        viewModelScope.launch {
            try {
                val resp = ApiClient.register(
                    RegisterRequest(
                        email = email.trim(),
                        password = password,
                        fullName = fullName.trim(),
                        role = "STUDENT",
                        studentNumber = studentNumber.trim()
                    )
                )
                ApiClient.setToken(resp.token)
                store.save(resp.token, resp.user)
                StudentNumberStore.set(getApplication(), resp.user.studentNumber, resp.user.fullName)
                _state.value = AuthState.LoggedIn(resp.user)
            } catch (e: ApiException) {
                _state.value = AuthState.LoggedOut(error = e.friendlyMessage)
            } catch (e: Exception) {
                _state.value = AuthState.LoggedOut(error = "Мрежен проблем: ${e.message ?: "?"}")
            }
        }
    }

    // odjava — ischisti sesija, token i zachuvan studentski broj
    fun logout() {
        viewModelScope.launch {
            store.clear()
            ApiClient.setToken(null)
            StudentNumberStore.set(getApplication(), null)
            _state.value = AuthState.LoggedOut()
        }
    }

    companion object {
        // factory za kreiranje na viewmodelot so Application kontekst
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: androidx.lifecycle.viewmodel.CreationExtras
            ): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                return AppViewModel(app) as T
            }
        }
    }
}
