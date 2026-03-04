// SettingsScreen - ustawienia jakości obrazu i zarządzanie połączeniem | 2026-03-04
package com.clarion.scanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.clarion.scanner.data.local.ImageQualitySetting
import com.clarion.scanner.data.local.PreferencesManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val scope = rememberCoroutineScope()

    val serverUrl by prefs.serverUrl.collectAsState(initial = "")
    val userEmail by prefs.userEmail.collectAsState(initial = "")
    val userName by prefs.userName.collectAsState(initial = "")
    val qualityName by prefs.imageQuality.collectAsState(initial = ImageQualitySetting.STANDARD.name)
    val selectedQuality = remember(qualityName) {
        runCatching { ImageQualitySetting.valueOf(qualityName) }
            .getOrDefault(ImageQualitySetting.STANDARD)
    }

    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.Default.Logout, contentDescription = null) },
            title = { Text("Wyloguj się") },
            text = { Text("Klucz API i adres serwera zostaną usunięte z urządzenia. Czy kontynuować?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            prefs.clear()
                            onLogout()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Wyloguj") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Anuluj") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                actions = {
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(
                            Icons.Default.Logout,
                            contentDescription = "Wyloguj",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Jakość obrazu",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Wyższa jakość = lepsze skanowanie, ale większy plik i wolniejszy upload",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.selectableGroup()) {
                    ImageQualitySetting.entries.forEachIndexed { index, quality ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedQuality == quality,
                                    onClick = {
                                        scope.launch { prefs.saveImageQuality(quality) }
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedQuality == quality,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        quality.label,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (quality == ImageQualitySetting.STANDARD) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text(
                                                "zalecana",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(
                                                    horizontal = 6.dp,
                                                    vertical = 2.dp
                                                ),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                                Text(
                                    quality.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (index < ImageQualitySetting.entries.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Konto",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = userName.ifBlank { "Użytkownik" },
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = userEmail.ifBlank { "–" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = serverUrl.ifBlank { "Brak serwera" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Zmień serwer / wyloguj")
            }
        }
    }
}
