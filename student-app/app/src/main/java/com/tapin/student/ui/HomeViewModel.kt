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
 * Sluzi gi [TapInHceService.tapEvents] od HCE servisot i gi pretvora vo
 * prikazliv feedback state (uspeh/neuspeh), so avtomatsko skrivanje po 2.5s.
 * Ovaa logika porano zhiveese vo Composable-ot; sega e vo ViewModel.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    enum class TapFeedbackState { NONE, SUCCESS, FAILURE }

    private val _feedback = MutableStateFlow(TapFeedbackState.NONE)
    val feedback: StateFlow<TapFeedbackState> = _feedback.asStateFlow()

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
                delay(2500)
                _feedback.value = TapFeedbackState.NONE
            }
        }
    }
}
