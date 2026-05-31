package com.tapin.teacher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapin.teacher.data.api.CourseView
import com.tapin.teacher.nfc.NfcReader
import com.tapin.teacher.ui.AppViewModel
import com.tapin.teacher.ui.AuthState
import com.tapin.teacher.ui.Paper
import com.tapin.teacher.ui.TapInTheme
import com.tapin.teacher.ui.screens.CoursesScreen
import com.tapin.teacher.ui.screens.HomeScreen
import com.tapin.teacher.ui.screens.LoginScreen
import com.tapin.teacher.ui.screens.RegisterScreen
import com.tapin.teacher.ui.screens.SessionScreen
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels(factoryProducer = { AppViewModel.Factory })

    private lateinit var nfc: NfcReader

    /** Set by SessionScreen via DisposableEffect. */
    private var nfcDesired = false
    private val nfcEnabledFlow = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfc = NfcReader(this)
        nfcEnabledFlow.value = nfc.isEnabled

        setContent { TapInTheme { App(vm, nfc, nfcEnabledFlow) { wanted ->
            nfcDesired = wanted
            applyNfcState()
        } } }
    }

    override fun onResume() {
        super.onResume()
        nfcEnabledFlow.value = nfc.isEnabled
        applyNfcState()
    }

    override fun onPause() {
        super.onPause()
        nfc.stop() // mandatory: enableReaderMode requires active Activity
    }

    private fun applyNfcState() {
        if (nfcDesired) nfc.start() else nfc.stop()
    }
}

@Composable
private fun App(
    vm: AppViewModel,
    nfc: NfcReader,
    nfcEnabledFlow: MutableStateFlow<Boolean>,
    setNfcDesired: (Boolean) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showRegister by remember { mutableStateOf(false) }

    when (val s = state) {
        AuthState.Loading -> Box(
            Modifier.fillMaxSize().background(Paper), Alignment.Center
        ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

        is AuthState.LoggedOut -> {
            if (showRegister) {
                RegisterScreen(
                    onRegister = { email, pw, name -> vm.register(email, pw, name) },
                    onBack = { showRegister = false },
                    error = s.error,
                    busy = s.isBusy
                )
            } else {
                LoginScreen(
                    onLogin = { email, pw -> vm.login(email, pw) },
                    onGoToRegister = { showRegister = true },
                    error = s.error,
                    busy = s.isBusy
                )
            }
        }

        is AuthState.LoggedIn -> AuthedNavigation(
            user = s.user,
            nfc = nfc,
            nfcEnabledFlow = nfcEnabledFlow,
            setNfcDesired = setNfcDesired,
            onLogout = vm::logout,
        )
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Courses : Screen
    data class Session(val course: CourseView) : Screen
}

@Composable
private fun AuthedNavigation(
    user: com.tapin.teacher.data.api.UserView,
    nfc: NfcReader,
    nfcEnabledFlow: MutableStateFlow<Boolean>,
    setNfcDesired: (Boolean) -> Unit,
    onLogout: () -> Unit,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val nfcEnabled by nfcEnabledFlow.collectAsState()
    val nfcSupported = nfc.isSupported

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            user = user,
            nfcSupported = nfcSupported,
            nfcEnabled = nfcEnabled,
            onOpenCourses = { screen = Screen.Courses },
            onLogout = onLogout,
        )
        Screen.Courses -> CoursesScreen(
            onBack = { screen = Screen.Home },
            onCourseSelected = { course -> screen = Screen.Session(course) },
        )
        is Screen.Session -> {
            // NFC reader stays on while this screen is shown
            DisposableEffect(s.course.id) {
                setNfcDesired(true)
                onDispose { setNfcDesired(false) }
            }
            SessionScreen(
                course = s.course,
                nfcEvents = nfc.events,
                nfcSupported = nfcSupported,
                nfcEnabled = nfcEnabled,
                onBack = { screen = Screen.Courses },
            )
        }
    }
}
