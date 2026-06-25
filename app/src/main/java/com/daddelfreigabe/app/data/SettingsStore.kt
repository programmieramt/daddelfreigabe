package com.daddelfreigabe.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val clientIp: String = "",
    val services: List<String> = listOf("youtube")
)

val KNOWN_SERVICES = listOf(
    "youtube" to "YouTube",
    "tiktok" to "TikTok",
    "discord" to "Discord",
    "twitter" to "X (Twitter)",
    "facebook" to "Facebook",
    "instagram" to "Instagram",
    "snapchat" to "Snapchat",
    "twitch" to "Twitch",
    "steam" to "Steam",
    "epic_games" to "Epic Games",
    "reddit" to "Reddit",
    "spotify" to "Spotify",
    "netflix" to "Netflix",
    "amazon" to "Amazon",
    "whatsapp" to "WhatsApp",
    "telegram" to "Telegram",
    "pinterest" to "Pinterest"
)

class SettingsStore(private val context: Context) {

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val CLIENT_IP = stringPreferencesKey("client_ip")
        val SERVICES = stringPreferencesKey("services")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            serverUrl = prefs[SERVER_URL] ?: "",
            username = prefs[USERNAME] ?: "",
            password = prefs[PASSWORD] ?: "",
            clientIp = prefs[CLIENT_IP] ?: "",
            services = prefs[SERVICES]?.split(",")?.filter { it.isNotBlank() }
                ?: listOf("youtube")
        )
    }

    suspend fun save(settings: Settings) {
        context.dataStore.edit { prefs ->
            prefs[SERVER_URL] = settings.serverUrl
            prefs[USERNAME] = settings.username
            prefs[PASSWORD] = settings.password
            prefs[CLIENT_IP] = settings.clientIp
            prefs[SERVICES] = settings.services.joinToString(",")
        }
    }
}
