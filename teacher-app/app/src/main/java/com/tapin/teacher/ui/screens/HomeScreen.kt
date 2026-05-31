package com.tapin.teacher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tapin.teacher.data.api.UserView
import com.tapin.teacher.ui.Danger
import com.tapin.teacher.ui.Ink
import com.tapin.teacher.ui.Ink10
import com.tapin.teacher.ui.Ink40
import com.tapin.teacher.ui.Ink60
import com.tapin.teacher.ui.Paper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    user: UserView,
    nfcSupported: Boolean,
    nfcEnabled: Boolean,
    onOpenCourses: () -> Unit,
    onLogout: () -> Unit,
) {
    Scaffold(
        containerColor = Paper,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("TapIn — Teacher",
                              style = MaterialTheme.typography.titleMedium, color = Ink) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Paper)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(color = Ink10, shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ULOGIRAN", style = MaterialTheme.typography.labelSmall, color = Ink40)
                    Text(user.fullName, style = MaterialTheme.typography.headlineMedium, color = Ink)
                    Text(user.email, style = MaterialTheme.typography.bodyMedium, color = Ink40)
                    Text("Rola: ${user.role}",
                         style = MaterialTheme.typography.bodyMedium, color = Ink40)
                }
            }

            NfcStatusBadge(supported = nfcSupported, enabled = nfcEnabled)

            Surface(
                color = Ink,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenCourses
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = Paper)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Predmeti i sesii",
                             color = Paper,
                             style = MaterialTheme.typography.titleMedium)
                        Text("Pochne sesija i registriraj atendansa preku NFC.",
                             color = Color(0xFFCFCFD2),
                             style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Paper)
                }
            }

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.Outlined.Logout, contentDescription = null,
                     modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Odjavi se", color = Ink)
            }
        }
    }
}

@Composable
private fun NfcStatusBadge(supported: Boolean, enabled: Boolean) {
    val (label, sub, labelColor) = when {
        !supported -> Triple(
            "NFC ne e podderzhan",
            "Atendansata raboti, ama bez tap.",
            Danger
        )
        !enabled -> Triple(
            "NFC e isklucen",
            "Vklji NFC vo poshtenstvenoto meni.",
            Danger
        )
        else -> Triple(
            "NFC podgotven",
            "Sesija + tap = avtomatska atendansa.",
            Ink60
        )
    }
    Surface(color = Ink10, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = labelColor, style = MaterialTheme.typography.titleMedium)
            Text(sub, color = Ink40, style = MaterialTheme.typography.bodySmall)
        }
    }
}
