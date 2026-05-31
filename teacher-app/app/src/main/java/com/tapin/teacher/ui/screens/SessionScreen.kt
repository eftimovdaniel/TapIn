package com.tapin.teacher.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tapin.teacher.data.api.AttendanceView
import com.tapin.teacher.data.api.CourseView
import com.tapin.teacher.nfc.NfcReader
import com.tapin.teacher.ui.Danger
import com.tapin.teacher.ui.Ink
import com.tapin.teacher.ui.Ink10
import com.tapin.teacher.ui.Ink20
import com.tapin.teacher.ui.Ink40
import com.tapin.teacher.ui.Ink60
import com.tapin.teacher.ui.Paper
import com.tapin.teacher.ui.SessionViewModel
import com.tapin.teacher.ui.Success
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    course: CourseView,
    nfcEvents: SharedFlow<NfcReader.Result>,
    nfcSupported: Boolean,
    nfcEnabled: Boolean,
    onBack: () -> Unit,
) {
    val vm: SessionViewModel = viewModel(
        key = "session-${course.id}",
        factory = viewModelFactory { initializer { SessionViewModel(course) } }
    )
    val state by vm.state.collectAsStateWithLifecycle()
    var showManual by remember { mutableStateOf(false) }

    LaunchedEffect(nfcEvents) {
        nfcEvents.collect { vm.onNfcResult(it) }
    }

    LaunchedEffect(state.lastTap) {
        if (state.lastTap != null) {
            delay(2200)
            vm.clearLastTap()
        }
    }

    Scaffold(
        containerColor = Paper,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(course.code, color = Ink40,
                             style = MaterialTheme.typography.labelSmall)
                        Text(course.name, color = Ink,
                             style = MaterialTheme.typography.titleMedium,
                             maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Nazad", tint = Ink)
                    }
                },
                actions = {
                    if (state.session != null) {
                        IconButton(onClick = vm::refreshAttendance) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Osvezi", tint = Ink)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Paper)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            when {
                state.session == null -> StartCard(
                    isStarting = state.isStarting,
                    onStart = vm::startSession
                )
                state.isActive -> ActiveCard(
                    nfcSupported = nfcSupported,
                    nfcEnabled = nfcEnabled,
                    tapBusy = state.tapBusy,
                    lastTap = state.lastTap,
                    attendanceCount = state.attendance.size,
                    isClosing = state.isClosing,
                    onManual = { showManual = true },
                    onClose = vm::closeSession,
                )
                else -> ClosedCard(count = state.attendance.size)
            }

            if (state.error != null) {
                Text(state.error ?: "?",
                     color = MaterialTheme.colorScheme.error,
                     style = MaterialTheme.typography.bodySmall)
            }

            if (state.session != null) {
                AttendanceList(items = state.attendance)
            }
        }
    }

    if (showManual) {
        ManualEntryDialog(
            onDismiss = { showManual = false },
            onSubmit = { number ->
                vm.submitManualNumber(number)
                showManual = false
            }
        )
    }
}

@Composable
private fun StartCard(isStarting: Boolean, onStart: () -> Unit) {
    Surface(color = Ink10, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Sesijata uste ne e zapochnata",
                 style = MaterialTheme.typography.headlineMedium, color = Ink)
            Text("Pritisni za da pochnesh sesija. Studentite ke mozhat da tapnat za atendansa.",
                 color = Ink40, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onStart,
                enabled = !isStarting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper)
            ) {
                if (isStarting) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Paper, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Pochne sesija", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun ActiveCard(
    nfcSupported: Boolean,
    nfcEnabled: Boolean,
    tapBusy: Boolean,
    lastTap: SessionViewModel.TapEvent?,
    attendanceCount: Int,
    isClosing: Boolean,
    onManual: () -> Unit,
    onClose: () -> Unit,
) {
    val nfcOk = nfcSupported && nfcEnabled

    val statusText = when {
        !nfcSupported -> "Telefonot ne podderzhuva NFC"
        !nfcEnabled -> "Vklji NFC od poshtenstvenoto meni"
        tapBusy -> "Procesiranje..."
        else -> "Spremen za tap"
    }

    val statusColor = when {
        !nfcOk -> Danger
        tapBusy -> Ink60
        else -> Success
    }

    Surface(color = Ink, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(Success)
                )
                Spacer(Modifier.width(8.dp))
                Text("AKTIVNA SESIJA",
                     color = Color(0xFFCFCFD2),
                     style = MaterialTheme.typography.labelSmall)
            }

            NfcRing(active = nfcOk && !tapBusy)

            Text(statusText,
                 color = statusColor,
                 style = MaterialTheme.typography.bodyMedium)

            Text("$attendanceCount ${if (attendanceCount == 1) "tap" else "tap-ovi"}",
                 color = Paper,
                 style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))

            TapFeedbackBanner(lastTap)

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onManual,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Paper, containerColor = Color.Transparent
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3F3F44))
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null,
                         modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Rachno")
                }
                Button(
                    onClick = onClose,
                    enabled = !isClosing,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Paper, contentColor = Ink
                    )
                ) {
                    if (isClosing) CircularProgressIndicator(
                        Modifier.size(16.dp), color = Ink, strokeWidth = 2.dp
                    ) else {
                        Icon(Icons.Outlined.Close, contentDescription = null,
                             modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Zatvori")
                    }
                }
            }
        }
    }
}

@Composable
private fun ClosedCard(count: Int) {
    Surface(color = Ink10, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Sesijata e zatvorena",
                 style = MaterialTheme.typography.headlineMedium, color = Ink)
            Text("$count studenti tapnaa atendansa.",
                 color = Ink40, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NfcRing(active: Boolean) {
    val infinite = rememberInfiniteTransition(label = "nfc")
    val pulse by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100), repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val scale by animateFloatAsState(
        targetValue = if (active) pulse else 1f, label = "scale"
    )
    val ringColor by animateColorAsState(
        targetValue = if (active) Success else Color(0xFF3F3F44),
        animationSpec = tween(400), label = "ring"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(120.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(ringColor.copy(alpha = 0.18f))
        )
        Box(
            Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(ringColor.copy(alpha = 0.32f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Wifi, contentDescription = null,
                tint = Paper, modifier = Modifier.size(40.dp)
            )
        }
    }
}

@Composable
private fun TapFeedbackBanner(lastTap: SessionViewModel.TapEvent?) {
    if (lastTap == null) {
        Spacer(Modifier.height(48.dp))
        return
    }
    val (text, sub, ok) = when (lastTap) {
        is SessionViewModel.TapEvent.Recorded ->
            Triple("Zapishano", lastTap.name + (lastTap.number?.let { " · $it" } ?: ""), true)
        is SessionViewModel.TapEvent.Duplicate ->
            Triple("Vekje zapishan", lastTap.name, false)
        is SessionViewModel.TapEvent.StudentNotFound ->
            Triple("Student ne e najden", "Broj: ${lastTap.number}", false)
        is SessionViewModel.TapEvent.RawUid ->
            Triple("Nepoznat tag", "UID: ${lastTap.uid}", false)
        is SessionViewModel.TapEvent.Failed ->
            Triple("Greshka", lastTap.message, false)
    }

    Surface(
        color = if (ok) Success.copy(alpha = 0.18f) else Color(0xFF3F3F44),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = Paper,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(text, color = Paper,
                     style = MaterialTheme.typography.titleMedium)
                Text(sub, color = Color(0xFFCFCFD2),
                     style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AttendanceList(items: List<AttendanceView>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ATENDANSA",
             color = Ink40,
             style = MaterialTheme.typography.labelSmall)
        if (items.isEmpty()) {
            Text("Nema tap-ovi uste. Studentite go dopiraat telefonot.",
                 color = Ink40,
                 style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(items, key = { it.id }) { a ->
                    AttendanceRow(a)
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun AttendanceRow(a: AttendanceView) {
    Surface(color = Ink10, shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(a.studentName, color = Ink,
                     style = MaterialTheme.typography.titleMedium)
                Text(a.studentNumber ?: "—",
                     color = Ink40,
                     style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            }
            Text(a.tappedAt.takeLast(8).take(5),
                 color = Ink60,
                 style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
    }
}

@Composable
private fun ManualEntryDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var num by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text("Vnes broj na student") },
        text = {
            OutlinedTextField(
                value = num,
                onValueChange = { num = it.filter { c -> c.isLetterOrDigit() || c == '-' } },
                label = { Text("Broj na student") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Ink, unfocusedBorderColor = Ink20,
                    focusedLabelColor = Ink, cursorColor = Ink
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(num) },
                enabled = num.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper)
            ) { Text("Zapishi") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Otkazhi", color = Ink60) }
        }
    )
}
