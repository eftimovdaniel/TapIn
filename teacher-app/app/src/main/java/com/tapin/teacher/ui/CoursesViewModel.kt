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

data class CoursesUiState(
    val isLoading: Boolean = true,
    val items: List<CourseView> = emptyList(),
    val error: String? = null,
    val createBusy: Boolean = false,
    val createError: String? = null,
)

class CoursesViewModel : ViewModel() {

    private val _state = MutableStateFlow(CoursesUiState())
    val state: StateFlow<CoursesUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val list = ApiClient.listCourses()
                _state.update { it.copy(isLoading = false, items = list, error = null) }
            } catch (e: ApiException) {
                _state.update { it.copy(isLoading = false, error = e.friendlyMessage) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Mrezni problem: ${e.message ?: "?"}") }
            }
        }
    }

    fun createCourse(code: String, name: String, onCreated: (CourseView) -> Unit) {
        if (code.isBlank() || name.isBlank()) {
            _state.update { it.copy(createError = "Polnete kod i ime") }
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
                _state.update { it.copy(createBusy = false, createError = "Mrezni problem: ${e.message ?: "?"}") }
            }
        }
    }

    fun clearCreateError() {
        _state.update { it.copy(createError = null) }
    }
}
