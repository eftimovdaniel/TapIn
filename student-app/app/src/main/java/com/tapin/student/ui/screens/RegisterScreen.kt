package com.tapin.student.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tapin.student.ui.Ink
import com.tapin.student.ui.Ink40
import com.tapin.student.ui.Paper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegister: (email: String, password: String, fullName: String, studentNumber: String) -> Unit,
    onBack: () -> Unit,
    error: String?,
    busy: Boolean
) {
    var fullName by remember { mutableStateOf("") }
    var studentNumber by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val canSubmit = !busy &&
        fullName.length >= 3 &&
        studentNumber.length >= 3 &&
        email.contains("@") &&
        password.length >= 6

    Scaffold(
        containerColor = Paper,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Registracija — Student",
                         style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Nazad", tint = Ink)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Paper)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Kreiraj studentski akaunt.",
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "Brojot na student e tvojot identifikator za atendansa.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink40
            )

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Ime i prezime") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            )
            OutlinedTextField(
                value = studentNumber,
                onValueChange = {
                    studentNumber = it.filter { c -> c.isLetterOrDigit() || c == '-' }
                },
                label = { Text("Broj na student (npr. 193001)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !busy
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Lozinka (min. 6)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !busy
            )

            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error,
                     style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onRegister(email, password, fullName, studentNumber) },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper)
            ) {
                if (busy) CircularProgressIndicator(
                    Modifier.size(20.dp), color = Paper, strokeWidth = 2.dp
                )
                else Text("Kreiraj akaunt", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
