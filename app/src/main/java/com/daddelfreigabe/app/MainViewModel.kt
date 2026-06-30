package com.daddelfreigabe.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daddelfreigabe.app.data.AdGuardApi
import com.daddelfreigabe.app.data.ClientConfig
import com.daddelfreigabe.app.data.KNOWN_SERVICES
import com.daddelfreigabe.app.data.Settings
import com.daddelfreigabe.app.data.SettingsStore
import com.daddelfreigabe.app.data.Task
import com.daddelfreigabe.app.data.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ClientStatus(
    val config: ClientConfig,
    val blockedServices: List<String> = emptyList()
) {
    val servicesUnlocked: Boolean
        get() = config.services.isNotEmpty() &&
                config.services.none { it in blockedServices }

    val blockedServiceLabels: List<String>
        get() {
            val labelMap = KNOWN_SERVICES.toMap()
            return config.services
                .filter { it in blockedServices }
                .map { labelMap[it] ?: it }
        }

    val unlockedServiceLabels: List<String>
        get() {
            val labelMap = KNOWN_SERVICES.toMap()
            return config.services
                .filter { it !in blockedServices }
                .map { labelMap[it] ?: it }
        }
}

data class UiState(
    val tasks: List<Task> = emptyList(),
    val settings: Settings = Settings(),
    val clientStatuses: List<ClientStatus> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
    val allTasksCompleted: Boolean = false
) {
    val allClientsUnlocked: Boolean
        get() = clientStatuses.isNotEmpty() && clientStatuses.all { it.servicesUnlocked }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val taskRepository = TaskRepository(application)

    private val _isLoading = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)
    private val _clientStatuses = MutableStateFlow<List<ClientStatus>>(emptyList())

    val uiState: StateFlow<UiState> = combine(
        taskRepository.tasks,
        settingsStore.settings,
        _isLoading,
        _message,
        _clientStatuses
    ) { tasks, settings, loading, message, statuses ->
        UiState(
            tasks = tasks,
            settings = settings,
            clientStatuses = statuses,
            isLoading = loading,
            message = message,
            allTasksCompleted = tasks.isNotEmpty() && tasks.all { it.completed }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    init {
        viewModelScope.launch { taskRepository.resetIfNewDay() }
        refreshStatus()
    }

    fun toggleTask(taskId: String) {
        viewModelScope.launch { taskRepository.toggleTask(taskId) }
    }

    fun addTask(title: String) {
        viewModelScope.launch { taskRepository.addTask(title) }
    }

    fun removeTask(taskId: String) {
        viewModelScope.launch { taskRepository.removeTask(taskId) }
    }

    fun resetTasks() {
        viewModelScope.launch { taskRepository.resetTasks() }
    }

    fun saveServer(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            settingsStore.saveServer(serverUrl, username, password)
            refreshStatus()
        }
    }

    fun addClient(client: ClientConfig) {
        viewModelScope.launch {
            settingsStore.addClient(client)
            refreshStatus()
        }
    }

    fun updateClient(client: ClientConfig) {
        viewModelScope.launch {
            settingsStore.updateClient(client)
            refreshStatus()
        }
    }

    fun removeClient(clientId: String) {
        viewModelScope.launch {
            settingsStore.removeClient(clientId)
            _clientStatuses.value = _clientStatuses.value.filter { it.config.id != clientId }
        }
    }

    fun testConnection(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val api = AdGuardApi(serverUrl, username, password)
            api.testConnection()
                .onSuccess { _message.value = it }
                .onFailure { _message.value = "Fehler: ${it.message}" }
            _isLoading.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun refreshStatus() {
        viewModelScope.launch {
            val settings = uiState.value.settings
            if (settings.serverUrl.isBlank() || settings.clients.isEmpty()) return@launch

            _isLoading.value = true
            val api = createApi(settings)

            val statuses = settings.clients.map { client ->
                val blocked = api.getBlockedServices(client.ip)
                    .getOrElse {
                        _message.value = "Fehler bei ${client.name}: ${it.message}"
                        emptyList()
                    }
                ClientStatus(config = client, blockedServices = blocked)
            }
            _clientStatuses.value = statuses
            _isLoading.value = false
        }
    }

    fun unlockServices() {
        viewModelScope.launch {
            val settings = uiState.value.settings
            if (settings.clients.isEmpty()) {
                _message.value = "Keine Clients konfiguriert"
                return@launch
            }

            _isLoading.value = true
            val api = createApi(settings)
            val results = mutableListOf<String>()

            for (client in settings.clients) {
                api.unblockServices(client.ip, client.services)
                    .onSuccess { results.add(client.name) }
                    .onFailure { _message.value = "Fehler bei ${client.name}: ${it.message}" }
            }

            if (results.isNotEmpty()) {
                _message.value = "Freigeschaltet: ${results.joinToString(", ")}"
            }
            refreshStatus()
            _isLoading.value = false
        }
    }

    private fun createApi(settings: Settings) = AdGuardApi(
        serverUrl = settings.serverUrl,
        username = settings.username,
        password = settings.password
    )
}
