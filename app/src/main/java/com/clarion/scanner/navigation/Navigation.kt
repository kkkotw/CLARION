// Navigation - definicje tras nawigacji | 2026-03-04
package com.clarion.scanner.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Camera : Screen("camera")
    object Queue : Screen("queue")
    object Settings : Screen("settings")
}
