package com.tapin.student
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Build
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapin.student.ui.AppViewModel
import com.tapin.student.ui.AuthState
import com.tapin.student.ui.Paper
import com.tapin.student.ui.TapInTheme
import com.tapin.student.ui.screens.HomeScreen
import com.tapin.student.ui.screens.LoginScreen
import com.tapin.student.ui.screens.RegisterScreen
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels(factoryProducer = { AppViewModel.Factory })

    private val nfcEnabledFlow = MutableStateFlow(false)
    private var nfcSupported = false

    /** Live osvezhuvanje na NFC statusot koga korisnikot go vkluchi/iskluchi NFC. */
    private val nfcStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) {
                val state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF)
                nfcEnabledFlow.value = state == NfcAdapter.STATE_ON
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val adapter = NfcAdapter.getDefaultAdapter(this)
        nfcSupported = adapter != null
        nfcEnabledFlow.value = adapter?.isEnabled == true

        setContent { TapInTheme { App(vm, nfcSupported, nfcEnabledFlow) } }
    }

    override fun onResume() {
        super.onResume()
        val adapter = NfcAdapter.getDefaultAdapter(this)
        nfcEnabledFlow.value = adapter?.isEnabled == true

        val filter = IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(nfcStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(nfcStateReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(nfcStateReceiver) }
    }
}

@Composable
private fun App(
    vm: AppViewModel,
    nfcSupported: Boolean,
    nfcEnabledFlow: MutableStateFlow<Boolean>,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nfcEnabled by nfcEnabledFlow.collectAsState()
    var showRegister by remember { mutableStateOf(false) }

    when (val s = state) {
        AuthState.Loading -> Box(
            Modifier.fillMaxSize().background(Paper),
            Alignment.Center
        ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

        is AuthState.LoggedOut -> {
            if (showRegister) {
                RegisterScreen(
                    onRegister = { email, pw, name, num ->
                        vm.register(email, pw, name, num)
                    },
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

        is AuthState.LoggedIn -> HomeScreen(
            user = s.user,
            nfcSupported = nfcSupported,
            nfcEnabled = nfcEnabled,
            onLogout = vm::logout,
        )
    }
}
