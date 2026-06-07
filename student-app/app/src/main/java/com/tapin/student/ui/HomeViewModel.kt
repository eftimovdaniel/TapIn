package com.tapin.student.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tapin.student.nfc.TapInHceService
import com.tapin.student.util.TapFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State holder za HomeScreen (MVVM).
 *
 * Gi sluzi [TapInHceService.tapEvents] od HCE servisot i gi pretvora vo
 * prikazliv feedback state (uspeh/neuspeh), so avtomatsko skrivanje po 2.5s.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    // sostojba na vizuelniot feedback po tap
    enum class TapFeedbackState { NONE, SUCCESS, FAILURE }

    private val _feedback = MutableStateFlow(TapFeedbackState.NONE)
    val feedback: StateFlow<TapFeedbackState> = _feedback.asStateFlow()

    // slushaj gi tap nastanite od hce servisot i pretvori gi vo feedback
    init {
        viewModelScope.launch {
            TapInHceService.tapEvents.collect { event ->
                when (event) {
                    is TapInHceService.TapEvent.Success -> {
                        TapFeedback.success(getApplication())
                        _feedback.value = TapFeedbackState.SUCCESS
                    }
                    is TapInHceService.TapEvent.Failure -> {
                        TapFeedback.failure(getApplication())
                        _feedback.value = TapFeedbackState.FAILURE
                    }
                }
                // sokrij go feedbackot avtomatski po 2.5 sekundi
                delay(2500)
                _feedback.value = TapFeedbackState.NONE
            }
        }
    }
}
