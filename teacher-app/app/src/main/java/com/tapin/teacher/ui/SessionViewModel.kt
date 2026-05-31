package com.tapin.teacher.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapin.teacher.data.api.ApiClient
import com.tapin.teacher.data.api.ApiException
import com.tapin.teacher.data.api.AttendanceView
import com.tapin.teacher.data.api.BulkAttendanceRequest
import com.tapin.teacher.data.api.CourseView
import com.tapin.teacher.data.api.SessionView
import com.tapin.teacher.data.api.TapRecord
import com.tapin.teacher.nfc.NfcReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drzi state za aktivnata sesija + lista atendansi.
 * Konstruira se pri vleguvanje vo SessionScreen za daden CourseView.
 */
class SessionViewModel(private val course: CourseView) : ViewModel() {

    sealed interface TapEvent {
        data class Recorded(val name: String, val number: String?) : TapEvent
        data class Duplicate(val name: String) : TapEvent
        data class StudentNotFound(val number: String) : TapEvent
        data class RawUid(val uid: String) : TapEvent
        data class Failed(val message: String) : TapEvent
    }

    data class UiState(
        val course: CourseView,
        val isStarting: Boolean = false,
        val isClosing: Boolean = false,
        val session: SessionView? = null,
        val attendance: List<AttendanceView> = emptyList(),
        val error: String? = null,
        val tapBusy: Boolean = false,
        val lastTap: TapEvent? = null,
    ) {
        val isActive: Boolean get() = session?.active == true
    }

    private val _state = MutableStateFlow(UiState(course = course))
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun startSession() {
        if (_state.value.session != null) return
        _state.update { it.copy(isStarting = true, error = null) }
        viewModelScope.launch {
            try {
                val s = ApiClient.startSession(course.id)
                _state.update { it.copy(isStarting = false, session = s, attendance = emptyList()) }
            } catch (e: ApiException) {
                _state.update { it.copy(isStarting = false, error = e.friendlyMessage) }
            } catch (e: Exception) {
                _state.update { it.copy(isStarting = false, error = "Mrezni problem: ${e.message ?: "?"}") }
            }
        }
    }

    fun closeSession(onClosed: () -> Unit = {}) {
        val s = _state.value.session ?: return
        _state.update { it.copy(isClosing = true, error = null) }
        viewModelScope.launch {
            try {
                ApiClient.closeSession(s.id)
                _state.update {
                    it.copy(
                        isClosing = false,
                        session = s.copy(active = false, endedAt = nowIso()),
                    )
                }
                onClosed()
            } catch (e: ApiException) {
                _state.update { it.copy(isClosing = false, error = e.friendlyMessage) }
            } catch (e: Exception) {
                _state.update { it.copy(isClosing = false, error = "Mrezni problem: ${e.message ?: "?"}") }
            }
        }
    }

    fun refreshAttendance() {
        val s = _state.value.session ?: return
        viewModelScope.launch {
            try {
                val page = ApiClient.listAttendance(sessionId = s.id, size = 200)
                _state.update { it.copy(attendance = page.items) }
            } catch (_: Exception) { /* nepostojano — molchime */ }
        }
    }

    fun onNfcResult(result: NfcReader.Result) {
        when (result) {
            is NfcReader.Result.Tapped -> recordByStudentNumber(result.studentNumber)
            is NfcReader.Result.RawUid -> _state.update {
                it.copy(lastTap = TapEvent.RawUid(result.uid))
            }
            is NfcReader.Result.Error -> _state.update {
                it.copy(lastTap = TapEvent.Failed(result.message))
            }
        }
    }

    /** Rachno vnesuvanje na student broj (bez NFC, za testiranje). */
    fun submitManualNumber(studentNumber: String) {
        if (studentNumber.isBlank()) return
        recordByStudentNumber(studentNumber.trim())
    }

    private fun recordByStudentNumber(studentNumber: String) {
        val s = _state.value.session ?: return
        if (!s.active) return
        if (_state.value.tapBusy) return
        _state.update { it.copy(tapBusy = true) }

        viewModelScope.launch {
            try {
                val student = ApiClient.findStudentByNumber(studentNumber)

                val resp = ApiClient.uploadAttendance(
                    BulkAttendanceRequest(
                        sessionId = s.id,
                        records = listOf(TapRecord(studentId = student.id))
                    )
                )

                val event = when {
                    resp.accepted > 0 -> TapEvent.Recorded(student.fullName, student.studentNumber)
                    resp.duplicates > 0 -> TapEvent.Duplicate(student.fullName)
                    else -> TapEvent.Failed("Otfrleno (${resp.rejected})")
                }
                _state.update { it.copy(tapBusy = false, lastTap = event) }
                refreshAttendance()
            } catch (e: ApiException) {
                val ev = if (e.statusCode == 404)
                    TapEvent.StudentNotFound(studentNumber)
                else
                    TapEvent.Failed(e.friendlyMessage)
                _state.update { it.copy(tapBusy = false, lastTap = ev) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(tapBusy = false, lastTap = TapEvent.Failed(e.message ?: "?"))
                }
            }
        }
    }

    fun clearLastTap() {
        _state.update { it.copy(lastTap = null) }
    }

    private fun nowIso(): String = java.time.OffsetDateTime.now().toString()
}
