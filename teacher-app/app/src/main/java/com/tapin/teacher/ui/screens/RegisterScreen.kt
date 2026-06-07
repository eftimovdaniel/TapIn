package com.tapin.teacher.ui.screens
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tapin.teacher.R
import com.tapin.teacher.ui.Ink
import com.tapin.teacher.ui.Ink40
import com.tapin.teacher.ui.Paper

// ekran za registracija na nov profesorski profil
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegister: (email: String, password: String, fullName: String) -> Unit,
    onBack: () -> Unit,
    error: String?,
    busy: Boolean
) {
    // lokalna sostojba za polinjata vo formata
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // prosta validacija: ime min 3 znaci, e-poshta so @, lozinka min 6 znaci
    val canSubmit = !busy && fullName.length >= 3 &&
        email.contains("@") && password.length >= 6

    Scaffold(
        containerColor = Paper,
        topBar = {
            CenterAlignedTopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(Icons.Outlined.ArrowBack,
                             contentDescription = "Назад",
                             tint = Ink)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Paper
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.tapin_logo),
                contentDescription = "TapIn",
                modifier = Modifier.height(112.dp)
            )

            Spacer(Modifier.height(20.dp))

            Text("Регистрирај се",
                 style = MaterialTheme.typography.titleLarge,
                 color = Ink)
            Spacer(Modifier.height(6.dp))
            Text("Професорски профил за пристап кон контролната табла.",
                 style = MaterialTheme.typography.bodySmall,
                 color = Ink40,
                 textAlign = TextAlign.Center)

            Spacer(Modifier.height(24.dp))

            FieldLabel("Име и презиме")
            WebStyleField(
                value = fullName,
                onValueChange = { fullName = it },
                placeholder = "Име Презиме",
                enabled = !busy,
            )

            Spacer(Modifier.height(14.dp))

            FieldLabel("Е-пошта")
            WebStyleField(
                value = email,
                onValueChange = { email = it },
                placeholder = "ime.prezime@ugd.edu.mk",
                keyboardType = KeyboardType.Email,
                enabled = !busy,
            )

            Spacer(Modifier.height(14.dp))

            FieldLabel("Лозинка")
            WebStyleField(
                value = password,
                onValueChange = { password = it },
                placeholder = "Минимум 6 знаци",
                isPassword = true,
                keyboardType = KeyboardType.Password,
                enabled = !busy,
            )

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error,
                     color = MaterialTheme.colorScheme.error,
                     style = MaterialTheme.typography.bodySmall,
                     modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { onRegister(email, password, fullName) },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ink,
                    contentColor = Paper,
                    disabledContainerColor = Ink.copy(alpha = 0.4f),
                )
            ) {
                if (busy) CircularProgressIndicator(
                    Modifier.size(16.dp),
                    color = Paper,
                    strokeWidth = 2.dp
                )
                else Text("Креирај профил",
                          style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
