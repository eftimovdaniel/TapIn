package com.tapin.student.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tapin.student.ui.Ink
import com.tapin.student.ui.Ink40
import com.tapin.student.ui.Paper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLogin: (String, String) -> Unit,
    onGoToRegister: () -> Unit,
    error: String?,
    busy: Boolean
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Scaffold(containerColor = Paper) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TAPIN — СТУДЕНТ", style = MaterialTheme.typography.labelSmall, color = Ink40)
                Text("v1.0", style = MaterialTheme.typography.labelSmall, color = Ink40)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Најави се.", style = MaterialTheme.typography.headlineLarge, color = Ink)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Допри телефон до професоровиот.\nПрисуството е запишано.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink40,
                    textAlign = TextAlign.Center
                )
            }

            Column {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Е-пошта") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !busy
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Лозинка") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !busy
                )

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(error, color = MaterialTheme.colorScheme.error,
                         style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { onLogin(email, password) },
                    enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper)
                ) {
                    if (busy) CircularProgressIndicator(
                        Modifier.size(20.dp), color = Paper, strokeWidth = 2.dp
                    )
                    else Text("Најави се", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = onGoToRegister,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy
                ) {
                    Text("Немам профил — Регистрирај се", color = Ink)
                }
            }
        }
    }
}
