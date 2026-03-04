// MainActivity - punkt wejścia aplikacji, konfiguracja nawigacji | 2026-03-04
package com.clarion.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clarion.scanner.data.local.PreferencesManager
import com.clarion.scanner.navigation.Screen
import com.clarion.scanner.ui.screens.*
import com.clarion.scanner.ui.theme.ClarionScannerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = PreferencesManager(this)
        val isConfigured = runBlocking { prefs.isConfigured() }

        setContent {
            ClarionScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val startDestination =
                        if (isConfigured) Screen.Camera.route else Screen.Login.route

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable(Screen.Login.route) {
                            LoginScreen(
                                onLoginSuccess = {
                                    navController.navigate(Screen.Camera.route) {
                                        popUpTo(Screen.Login.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(Screen.Camera.route) {
                            CameraScreen(
                                onOpenQueue = {
                                    navController.navigate(Screen.Queue.route)
                                },
                                onOpenSettings = {
                                    navController.navigate(Screen.Settings.route)
                                }
                            )
                        }

                        composable(Screen.Queue.route) {
                            QueueScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onLogout = {
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
