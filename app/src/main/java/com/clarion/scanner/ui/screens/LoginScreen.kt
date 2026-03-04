// LoginScreen - logowanie email + hasło przez /api/auth/mobile-login | 2026-03-04
package com.clarion.scanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clarion.scanner.data.api.ApiClient
import com.clarion.scanner.data.api.MobileLoginRequest
import com.clarion.scanner.data.local.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var serverUrl by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        serverUrl = prefs.serverUrl.first()
        email = prefs.userEmail.first()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.DocumentScanner,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Clarion Scanner",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Skaner dokumentów medycznych",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(40.dp))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it; errorMessage = "" },
            label = { Text("Adres serwera") },
            placeholder = { Text("https://myapp.replit.app") },
            leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; errorMessage = "" },
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = "" },
            label = { Text("Hasło") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Ukryj hasło" else "Pokaż hasło"
                    )
                }
            },
            singleLine = true
        )

        if (errorMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (serverUrl.isBlank()) { errorMessage = "Podaj adres serwera"; return@Button }
                if (!serverUrl.startsWith("http")) {
                    errorMessage = "Adres musi zaczynać się od http:// lub https://"
                    return@Button
                }
                if (email.isBlank()) { errorMessage = "Podaj adres email"; return@Button }
                if (password.isBlank()) { errorMessage = "Podaj hasło"; return@Button }

                isLoading = true
                errorMessage = ""

                scope.launch {
                    try {
                        prefs.saveServerUrl(serverUrl)
                        val response = ApiClient.getService(serverUrl).mobileLogin(
                            MobileLoginRequest(email = email.trim(), password = password)
                        )
                        if (response.isSuccessful) {
                            val body = response.body()
                            if (body != null) {
                                prefs.saveLoginData(
                                    token = body.token,
                                    email = body.user.email,
                                    name = body.user.name
                                )
                                onLoginSuccess()
                            } else {
                                errorMessage = "Serwer zwrócił pustą odpowiedź"
                            }
                        } else {
                            errorMessage = when (response.code()) {
                                401 -> "Nieprawidłowy email lub hasło"
                                404 -> "Nie znaleziono serwera – sprawdź adres URL"
                                500 -> "Błąd serwera (500) – skontaktuj się z administratorem"
                                else -> "Błąd logowania: HTTP ${response.code()}"
                            }
                        }
                    } catch (e: Exception) {
                        errorMessage = when {
                            e.message?.contains("Unable to resolve host") == true ->
                                "Nie można połączyć z serwerem – sprawdź adres URL i internet"
                            e.message?.contains("timeout") == true ->
                                "Przekroczono czas połączenia – spróbuj ponownie"
                            else -> "Błąd połączenia: ${e.message}"
                        }
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Logowanie...")
            } else {
                Icon(Icons.Default.Login, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Zaloguj się", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Użyj tych samych danych co do logowania w aplikacji webowej Clarion",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
