package com.tapin.teacher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tapin.teacher.ui.Ink
import com.tapin.teacher.ui.Ink40
import com.tapin.teacher.ui.Paper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegister: (email: String, password: String, fullName: String) -> Unit,
    onBack: () -> Unit,
    error: String?,
    busy: Boolean
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val canSubmit = !busy && fullName.length >= 3 && email.contains("@") && password.length >= 6

    Scaffold(
        containerColor = Paper,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Регистрација — Професор", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = Ink)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Paper)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Креирај професорски профил.",
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "Податоците ќе се зачуваат во базата.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink40
            )

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Име и презиме") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Е-пошта") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !busy
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Лозинка (мин. 6)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !busy
            )

            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onRegister(email, password, fullName) },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Paper, strokeWidth = 2.dp)
                else Text("Креирај профил", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
