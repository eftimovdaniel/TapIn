package com.tapin.student.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tapin.student.data.api.UserView
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
) {
    val nfcOk = nfcSupported && nfcEnabled

    Scaffold(
        containerColor = Paper,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("TapIn — Student",
                         style = MaterialTheme.typography.titleMedium, color = Ink)
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Outlined.Logout, contentDescription = "Odjavi se", tint = Ink)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Paper)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            UserCard(user)

            Spacer(Modifier.weight(1f))

            NfcRing(active = nfcOk)

            Text(
                when {
                    !nfcSupported -> "Telefonot ne podderzhuva NFC"
                    !nfcEnabled -> "NFC e isklucen"
                    else -> "Spremen za tap"
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
                    !nfcSupported -> "Atendansata mora rachno da se zapishe."
                    !nfcEnabled -> "Vklji NFC od poshtenstvenoto meni."
                    else -> "Dopri zadnata strana na profesoroviot telefon."
                },
                color = Ink40,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            HceHint()

            Spacer(Modifier.height(16.dp))
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
            Text("STUDENT", style = MaterialTheme.typography.labelSmall, color = Ink40)
            Text(user.fullName,
                 style = MaterialTheme.typography.headlineMedium, color = Ink)
            Text(user.email, color = Ink40,
                 style = MaterialTheme.typography.bodyMedium)
            user.studentNumber?.let {
                Text("Broj: $it",
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
            Text("KAKO RABOTI",
                 color = Ink40, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Pri tap, telefonot prakja samo tvojot broj na student do profesoroviot telefon. Ne treba da otvorash aplikacija — raboti i koga e zaklucen.",
                color = Ink60, style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
