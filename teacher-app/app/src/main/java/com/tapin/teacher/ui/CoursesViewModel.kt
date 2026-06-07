package com.tapin.teacher.ui
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapin.teacher.data.api.ApiClient
import com.tapin.teacher.data.api.ApiException
import com.tapin.teacher.data.api.CourseRequest
import com.tapin.teacher.data.api.CourseView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// sostojba na ekranot za predmeti — lista + statusi za vchituvanje i kreiranje
data class CoursesUiState(
    val isLoading: Boolean = true,
    val items: List<CourseView> = emptyList(),
    val error: String? = null,
    val createBusy: Boolean = false,
    val createError: String? = null,
)

// viewmodel za listata na predmeti i kreiranje nov predmet
class CoursesViewModel : ViewModel() {

    private val _state = MutableStateFlow(CoursesUiState())
    val state: StateFlow<CoursesUiState> = _state.asStateFlow()

    // refresh() se vika od CoursesScreen so forUserId — ne pri kreiranje na VM

    // povlechi ja listata na predmeti od backend (samo svoi — API + lokalna proverka)
    fun refresh(forUserId: Long? = null) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val list = ApiClient.listCourses()
                val owned = if (forUserId != null) {
                    list.filter { it.teacherId == forUserId }
                } else {
                    list
                }
                _state.update { it.copy(isLoading = false, items = owned, error = null) }
            } catch (e: ApiException) {
                _state.update { it.copy(isLoading = false, error = e.friendlyMessage) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Мрежен проблем: ${e.message ?: "?"}") }
            }
        }
    }

    // kreiraj nov predmet; po uspeh go dodavame vo listata i vikame onCreated
    fun createCourse(code: String, name: String, onCreated: (CourseView) -> Unit) {
        // prosta validacija — i shifra i ime mora da se popolneti
        if (code.isBlank() || name.isBlank()) {
            _state.update { it.copy(createError = "Пополни шифра и име") }
            return
        }
        _state.update { it.copy(createBusy = true, createError = null) }
        viewModelScope.launch {
            try {
                val created = ApiClient.createCourse(CourseRequest(code.trim(), name.trim()))
                _state.update {
                    it.copy(
                        createBusy = false,
                        createError = null,
                        items = it.items + created
                    )
                }
                onCreated(created)
            } catch (e: ApiException) {
                _state.update { it.copy(createBusy = false, createError = e.friendlyMessage) }
            } catch (e: Exception) {
                _state.update { it.copy(createBusy = false, createError = "Мрежен проблем: ${e.message ?: "?"}") }
            }
        }
    }

    // ischisti ja greshkata od formata za kreiranje
    fun clearCreateError() {
        _state.update { it.copy(createError = null) }
    }
}
