package com.tapin.student.ui.screens
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tapin.student.R
import com.tapin.student.ui.Ink
import com.tapin.student.ui.Ink20
import com.tapin.student.ui.Ink40
import com.tapin.student.ui.Ink60
import com.tapin.student.ui.Paper

// ekran za najava — polinja za e-poshta i lozinka plus link kon registracija
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLogin: (String, String) -> Unit,
    onGoToRegister: () -> Unit,
    error: String?,
    busy: Boolean
) {
    // lokalna sostojba za vnesenite polinja
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Scaffold(containerColor = Paper) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.5f))

            Image(
                painter = painterResource(R.drawable.tapin_logo),
                contentDescription = "TapIn",
                modifier = Modifier.height(128.dp)
            )

            Spacer(Modifier.height(28.dp))

            Text("Најави се",
                 style = MaterialTheme.typography.titleLarge,
                 color = Ink)
            Spacer(Modifier.height(6.dp))
            Text("Допри телефон до професоровиот за присуство.",
                 style = MaterialTheme.typography.bodySmall,
                 color = Ink40,
                 textAlign = TextAlign.Center)

            Spacer(Modifier.height(28.dp))

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
                placeholder = "",
                keyboardType = KeyboardType.Password,
                isPassword = true,
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

            // kopche aktivno samo koga dvete polinja se popolneti i ne sme zafateni
            Button(
                onClick = { onLogin(email, password) },
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
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
                else Text("Најави се",
                          style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(28.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Немаш профил? ",
                     style = MaterialTheme.typography.bodySmall,
                     color = Ink40)
                TextButton(
                    onClick = onGoToRegister,
                    enabled = !busy,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text("Регистрирај се",
                         style = MaterialTheme.typography.bodySmall,
                         color = Ink)
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

// mala etiketa nad poleto za vnesuvanje
@Composable
internal fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Ink60,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    )
}

// edinstven stil na tekst pole (spodeleno megju login i register), so opcija za lozinka
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebStyleField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    enabled: Boolean = true,
) {
    // dali lozinkata e momentalno vidliva (oko kopche)
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = if (placeholder.isNotBlank()) {
            { Text(placeholder, color = Ink40,
                   style = MaterialTheme.typography.bodySmall) }
        } else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword && passwordVisible) KeyboardType.Text else keyboardType
        ),
        enabled = enabled,
        trailingIcon = if (isPassword) {
            {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    enabled = enabled,
                ) {
                    Icon(
                        painter = painterResource(
                            if (passwordVisible) R.drawable.ic_password_visible
                            else R.drawable.ic_password_hidden
                        ),
                        contentDescription = if (passwordVisible) "Сокриј лозинка" else "Покажи лозинка",
                        modifier = Modifier.size(20.dp),
                        tint = Ink40,
                    )
                }
            }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ink,
            unfocusedBorderColor = Ink20,
            disabledBorderColor = Ink20,
            cursorColor = Ink,
            focusedTextColor = Ink,
            unfocusedTextColor = Ink,
            focusedContainerColor = Paper,
            unfocusedContainerColor = Paper,
        )
    )
}
