// PreferencesManager - DataStore dla ustawień, tokenu i danych użytkownika | 2026-03-04
package com.clarion.scanner.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scanner_prefs")

enum class ImageQualitySetting(
    val label: String,
    val description: String,
    val jpegQuality: Int,
    val maxDimension: Int
) {
    FAST("Szybka", "75% jakości, max 1600px – najszybszy upload", 75, 1600),
    STANDARD("Standardowa", "85% jakości, max 2400px – zalecana", 85, 2400),
    FULL("Pełna", "90% jakości, oryginalna rozdzielczość", 90, 0)
}

class PreferencesManager(private val context: Context) {

    companion object {
        val SERVER_URL   = stringPreferencesKey("server_url")
        val AUTH_TOKEN   = stringPreferencesKey("auth_token")
        val USER_EMAIL   = stringPreferencesKey("user_email")
        val USER_NAME    = stringPreferencesKey("user_name")
        val IMAGE_QUALITY = stringPreferencesKey("image_quality")
    }

    val serverUrl: Flow<String> =
        context.dataStore.data.map { it[SERVER_URL] ?: "" }

    val authToken: Flow<String> =
        context.dataStore.data.map { it[AUTH_TOKEN] ?: "" }

    val userEmail: Flow<String> =
        context.dataStore.data.map { it[USER_EMAIL] ?: "" }

    val userName: Flow<String> =
        context.dataStore.data.map { it[USER_NAME] ?: "" }

    val imageQuality: Flow<String> =
        context.dataStore.data.map { it[IMAGE_QUALITY] ?: ImageQualitySetting.STANDARD.name }

    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { it[SERVER_URL] = url.trim().trimEnd('/') }
    }

    suspend fun saveLoginData(token: String, email: String, name: String) {
        context.dataStore.edit {
            it[AUTH_TOKEN]  = token
            it[USER_EMAIL]  = email
            it[USER_NAME]   = name
        }
    }

    suspend fun saveImageQuality(quality: ImageQualitySetting) {
        context.dataStore.edit { it[IMAGE_QUALITY] = quality.name }
    }

    suspend fun isConfigured(): Boolean {
        val prefs = context.dataStore.data.first()
        return !prefs[SERVER_URL].isNullOrBlank() && !prefs[AUTH_TOKEN].isNullOrBlank()
    }

    suspend fun clear() {
        context.dataStore.edit {
            it.remove(AUTH_TOKEN)
            it.remove(USER_EMAIL)
            it.remove(USER_NAME)
            it.remove(SERVER_URL)
        }
    }
}
