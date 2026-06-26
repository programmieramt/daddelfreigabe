package com.daddelfreigabe.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

@JsonClass(generateAdapter = true)
data class ClientConfig(
    val id: String = System.currentTimeMillis().toString(),
    val name: String = "",
    val ip: String = "",
    val services: List<String> = listOf("youtube")
)

data class Settings(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val clients: List<ClientConfig> = emptyList()
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
        val CLIENTS_JSON = stringPreferencesKey("clients_json")
    }

    private val moshi = Moshi.Builder().build()
    private val clientListType = Types.newParameterizedType(List::class.java, ClientConfig::class.java)
    private val clientListAdapter = moshi.adapter<List<ClientConfig>>(clientListType)

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        val clientsJson = prefs[CLIENTS_JSON]
        Settings(
            serverUrl = prefs[SERVER_URL] ?: "",
            username = prefs[USERNAME] ?: "",
            password = prefs[PASSWORD] ?: "",
            clients = if (clientsJson != null) clientListAdapter.fromJson(clientsJson) ?: emptyList() else emptyList()
        )
    }

    suspend fun saveServer(serverUrl: String, username: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[SERVER_URL] = serverUrl
            prefs[USERNAME] = username
            prefs[PASSWORD] = password
        }
    }

    suspend fun saveClients(clients: List<ClientConfig>) {
        context.dataStore.edit { prefs ->
            prefs[CLIENTS_JSON] = clientListAdapter.toJson(clients)
        }
    }

    suspend fun addClient(client: ClientConfig) {
        context.dataStore.edit { prefs ->
            val current = prefs[CLIENTS_JSON]?.let { clientListAdapter.fromJson(it) } ?: emptyList()
            prefs[CLIENTS_JSON] = clientListAdapter.toJson(current + client)
        }
    }

    suspend fun updateClient(client: ClientConfig) {
        context.dataStore.edit { prefs ->
            val current = prefs[CLIENTS_JSON]?.let { clientListAdapter.fromJson(it) } ?: emptyList()
            val updated = current.map { if (it.id == client.id) client else it }
            prefs[CLIENTS_JSON] = clientListAdapter.toJson(updated)
        }
    }

    suspend fun removeClient(clientId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[CLIENTS_JSON]?.let { clientListAdapter.fromJson(it) } ?: emptyList()
            prefs[CLIENTS_JSON] = clientListAdapter.toJson(current.filter { it.id != clientId })
        }
    }
}
