package com.tapin.teacher.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tapin.teacher.data.AttendanceRepository
import com.tapin.teacher.data.api.ApiClient
import com.tapin.teacher.data.api.ApiException
import com.tapin.teacher.data.api.AttendanceView
import com.tapin.teacher.data.api.CourseView
import com.tapin.teacher.data.api.SessionView
import com.tapin.teacher.nfc.NfcReader
import com.tapin.teacher.util.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drzi state za aktivnata sesija + lista atendansi.
 * Konstruira se pri vleguvanje vo SessionScreen za daden CourseView.
 *
 * Sega koristi offline-first pattern preku [AttendanceRepository]:
 *   tap → Room (lokalno) → uploadAttendance API → mark synced
 * Ako mreza ne raboti, zapisot ostanuva PENDING dur ne se klikne "Sync".
 */
class SessionViewModel(
    app: Application,
    private val course: CourseView,
) : AndroidViewModel(app) {

    private val repo = AttendanceRepository(app)

    sealed interface TapEvent {
        data class Recorded(val name: String, val number: String?) : TapEvent
        data class Duplicate(val name: String) : TapEvent
        data class StudentNotFound(val number: String) : TapEvent
        data class QueuedOffline(val number: String) : TapEvent
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
        val isSyncing: Boolean = false,
        val syncProgress: Pair<Int, Int> = 0 to 0, // (done, total)
        val syncMessage: String? = null,
    ) {
        val isActive: Boolean get() = session?.active == true
    }

    private val _state = MutableStateFlow(UiState(course = course))
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Vreme na posleden tap po student-broj — za debounce na brzi povtoreni tap-i. */
    private val lastTapAtMs = mutableMapOf<String, Long>()
    private val tapDebounceMs = 3000L

    /** Pending broj — koristi vo UI badge. */
    val pendingCount: StateFlow<Int> = repo.pendingCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        observeNetworkForAutoSync()
    }

    /**
     * Avtomatska sinhronizacija: koga mrezata ke se vrati i ima PENDING
     * zapisi, avtomatski gi kachuva (spec 3.1 — upload when connection
     * is re-established). Korisnikot moze i rachno preku "Sync now".
     */
    private fun observeNetworkForAutoSync() {
        viewModelScope.launch {
            var wasOnline = true
            NetworkMonitor.isOnlineFlow(getApplication()).collect { online ->
                val cameBackOnline = online && !wasOnline
                wasOnline = online
                if (cameBackOnline && pendingCount.value > 0 && !_state.value.isSyncing) {
                    syncPending(auto = true)
                }
            }
        }
    }

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
                _state.update { it.copy(isStarting = false, error = "Мрежен проблем: ${e.message ?: "?"}") }
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
                _state.update { it.copy(isClosing = false, error = "Мрежен проблем: ${e.message ?: "?"}") }
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
            is NfcReader.Result.Tapped -> recordByStudentNumber(
                studentNumber = result.studentNumber,
                signedPayload = result.signedPayload,
                studentName = result.studentName,
            )
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
        recordByStudentNumber(studentNumber.trim(), signedPayload = null, studentName = null)
    }

    private fun recordByStudentNumber(
        studentNumber: String,
        signedPayload: String?,
        studentName: String?,
    ) {
        val s = _state.value.session ?: return
        if (!s.active) return
        if (_state.value.tapBusy) return

        // Time-based debounce: ignoriraj brzi povtoreni tap-i od ist student
        // (npr. telefonot sluchajno dopre dvapati za kuso vreme).
        val now = System.currentTimeMillis()
        val prev = lastTapAtMs[studentNumber]
        if (prev != null && now - prev < tapDebounceMs) return
        lastTapAtMs[studentNumber] = now

        _state.update { it.copy(tapBusy = true) }

        viewModelScope.launch {
            val ev = when (val outcome = repo.recordTap(s.id, studentNumber, signedPayload, studentName)) {
                is AttendanceRepository.RecordOutcome.Recorded ->
                    TapEvent.Recorded(outcome.name, outcome.number)
                is AttendanceRepository.RecordOutcome.Duplicate ->
                    TapEvent.Duplicate(outcome.name)
                is AttendanceRepository.RecordOutcome.StudentNotFound ->
                    TapEvent.StudentNotFound(outcome.number)
                is AttendanceRepository.RecordOutcome.QueuedOffline ->
                    TapEvent.QueuedOffline(outcome.number)
                is AttendanceRepository.RecordOutcome.Failed ->
                    TapEvent.Failed(outcome.message)
            }
            _state.update { it.copy(tapBusy = false, lastTap = ev) }
            // ako uspeshno e zapishano online, osvezi listata
            if (ev is TapEvent.Recorded) refreshAttendance()
        }
    }

    /**
     * "Sync now" — proba pak da gi kachi site PENDING zapisi.
     * [auto] = true koga e povikano avtomatski po vrakanje na mreza.
     */
    fun syncPending(auto: Boolean = false) {
        if (_state.value.isSyncing) return
        _state.update { it.copy(isSyncing = true, syncMessage = null, syncProgress = 0 to 0) }
        viewModelScope.launch {
            try {
                val result = repo.syncPending { done, total ->
                    _state.update { it.copy(syncProgress = done to total) }
                }
                val prefix = if (auto) "Авто-синхронизација · " else ""
                val msg = prefix + buildString {
                    if (result.synced > 0) append("Синхронизирани: ${result.synced}")
                    if (result.rejected > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("Отфрлени: ${result.rejected}")
                    }
                    if (result.failed > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("Грешка: ${result.failed}")
                    }
                    if (isEmpty()) append("Нема записи за синхронизација.")
                }
                _state.update { it.copy(isSyncing = false, syncMessage = msg) }
                if (result.synced > 0) refreshAttendance()
            } catch (e: Exception) {
                _state.update {
                    it.copy(isSyncing = false, syncMessage = "Грешка: ${e.message ?: "?"}")
                }
            }
        }
    }

    fun clearLastTap() {
        _state.update { it.copy(lastTap = null) }
    }

    fun clearSyncMessage() {
        _state.update { it.copy(syncMessage = null) }
    }

    private fun nowIso(): String = java.time.OffsetDateTime.now().toString()
}
