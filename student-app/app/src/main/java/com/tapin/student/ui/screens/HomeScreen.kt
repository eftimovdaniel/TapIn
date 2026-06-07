package com.tapin.student.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tapin.student.data.api.UserView
import com.tapin.student.ui.HomeViewModel
import com.tapin.student.ui.Danger
import com.tapin.student.ui.Ink
import com.tapin.student.ui.Ink10
import com.tapin.student.ui.Ink40
import com.tapin.student.ui.Ink60
import com.tapin.student.ui.Paper
import com.tapin.student.ui.Success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    user: UserView,
    nfcSupported: Boolean,
    nfcEnabled: Boolean,
    onLogout: () -> Unit,
    homeVm: HomeViewModel = viewModel(),
) {
    val nfcOk = nfcSupported && nfcEnabled
    val ctx = LocalContext.current
    val feedback by homeVm.feedback.collectAsStateWithLifecycle()
    val showSuccess = feedback == HomeViewModel.TapFeedbackState.SUCCESS
    val showFailure = feedback == HomeViewModel.TapFeedbackState.FAILURE

    Scaffold(
        containerColor = Paper,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("TapIn — Студент",
                         style = MaterialTheme.typography.titleMedium, color = Ink)
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Outlined.Logout, contentDescription = "Одјави се", tint = Ink)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Paper)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                UserCard(user)

                Spacer(Modifier.weight(1f))

                NfcRing(active = nfcOk)

                Text(
                    when {
                        !nfcSupported -> "Телефонот не поддржува NFC"
                        !nfcEnabled -> "NFC е исклучен"
                        else -> "Подготвен за тап"
                    },
                    color = when {
                        !nfcOk -> Danger
                        else -> Ink
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )

                Text(
                    when {
                        !nfcSupported -> "Присуството мора рачно да се запише."
                        !nfcEnabled -> "Вклучи NFC од поставките."
                        else -> "Допри ја задната страна на професоровиот телефон."
                    },
                    color = Ink40,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )

                // Direkten short-cut do NFC poставките koga e iskluchen (spec 9-10 NFC lifecycle)
                if (nfcSupported && !nfcEnabled) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            runCatching {
                                ctx.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                            }.onFailure {
                                ctx.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                            }
                        }
                    ) {
                        Text("Отвори NFC поставки")
                    }
                }

                Spacer(Modifier.weight(1f))

                HceHint()

                Spacer(Modifier.height(16.dp))
            }

            // Full-screen overlay koga uspeshno e ispraten potpis
            AnimatedVisibility(
                visible = showSuccess,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
                modifier = Modifier.fillMaxSize()
            ) {
                SuccessOverlay()
            }

            // Full-screen overlay koga tapоt ne uspea (spec 3.2.4)
            AnimatedVisibility(
                visible = showFailure,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
                modifier = Modifier.fillMaxSize()
            ) {
                FailureOverlay()
            }
        }
    }
}

@Composable
private fun SuccessOverlay() {
    Box(
        Modifier.fillMaxSize().background(Success.copy(alpha = 0.96f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = Paper,
                modifier = Modifier.size(140.dp)
            )
            Text(
                "Запишано!",
                color = Paper,
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                "Присуството е успешно регистрирано",
                color = Paper.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FailureOverlay() {
    Box(
        Modifier.fillMaxSize().background(Danger.copy(alpha = 0.96f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = Paper,
                modifier = Modifier.size(140.dp)
            )
            Text(
                "Тап не успеа",
                color = Paper,
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                "Најави се и обиди се повторно.",
                color = Paper.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun UserCard(user: UserView) {
    Surface(color = Ink10, shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("СТУДЕНТ", style = MaterialTheme.typography.labelSmall, color = Ink40)
            Text(user.fullName,
                 style = MaterialTheme.typography.headlineMedium, color = Ink)
            Text(user.email, color = Ink40,
                 style = MaterialTheme.typography.bodyMedium)
            user.studentNumber?.let {
                Text("Број: $it",
                     color = Ink60,
                     style = MaterialTheme.typography.bodyMedium
                         .copy(fontFamily = FontFamily.Monospace))
            }
        }
    }
}

@Composable
private fun NfcRing(active: Boolean) {
    val infinite = rememberInfiniteTransition(label = "nfc")
    val pulse by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300), repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val pulse2 by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700), repeatMode = RepeatMode.Reverse
        ),
        label = "pulse2"
    )
    val scale1 by animateFloatAsState(
        targetValue = if (active) pulse else 1f, label = "s1"
    )
    val scale2 by animateFloatAsState(
        targetValue = if (active) pulse2 else 1f, label = "s2"
    )
    val ringColor by animateColorAsState(
        targetValue = if (active) Success else Color(0xFF8A8A92),
        animationSpec = tween(400), label = "ring"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        Box(
            Modifier
                .size(200.dp)
                .scale(scale2)
                .clip(CircleShape)
                .background(ringColor.copy(alpha = 0.10f))
        )
        Box(
            Modifier
                .size(150.dp)
                .scale(scale1)
                .clip(CircleShape)
                .background(ringColor.copy(alpha = 0.18f))
        )
        Box(
            Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(ringColor.copy(alpha = 0.32f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Wifi, contentDescription = null,
                tint = if (active) ringColor else Color(0xFF8A8A92),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
private fun HceHint() {
    Surface(color = Ink10, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("КАКО РАБОТИ",
                 color = Ink40, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "При тап, телефонот праќа само твојот број на студент до професоровиот телефон. Не треба да отвораш апликација — работи и кога е заклучен.",
                color = Ink60, style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
