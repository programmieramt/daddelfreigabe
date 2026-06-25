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
    val clientId: String = ""
)

class SettingsStore(private val context: Context) {

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val CLIENT_ID = stringPreferencesKey("client_id")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            serverUrl = prefs[SERVER_URL] ?: "",
            username = prefs[USERNAME] ?: "",
            password = prefs[PASSWORD] ?: "",
            clientId = prefs[CLIENT_ID] ?: ""
        )
    }

    suspend fun save(settings: Settings) {
        context.dataStore.edit { prefs ->
            prefs[SERVER_URL] = settings.serverUrl
            prefs[USERNAME] = settings.username
            prefs[PASSWORD] = settings.password
            prefs[CLIENT_ID] = settings.clientId
        }
    }
}
