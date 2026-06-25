package com.daddelfreigabe.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daddelfreigabe.app.data.AdGuardApi
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

data class UiState(
    val tasks: List<Task> = emptyList(),
    val settings: Settings = Settings(),
    val blockedServices: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
    val allTasksCompleted: Boolean = false
) {
    val servicesUnlocked: Boolean
        get() = settings.services.isNotEmpty() &&
                settings.services.none { it in blockedServices }

    val blockedServiceLabels: List<String>
        get() {
            val labelMap = KNOWN_SERVICES.toMap()
            return settings.services
                .filter { it in blockedServices }
                .map { labelMap[it] ?: it }
        }

    val unlockedServiceLabels: List<String>
        get() {
            val labelMap = KNOWN_SERVICES.toMap()
            return settings.services
                .filter { it !in blockedServices }
                .map { labelMap[it] ?: it }
        }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val taskRepository = TaskRepository(application)

    private val _isLoading = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)
    private val _blockedServices = MutableStateFlow<List<String>>(emptyList())

    val uiState: StateFlow<UiState> = combine(
        taskRepository.tasks,
        settingsStore.settings,
        _isLoading,
        _message,
        _blockedServices
    ) { tasks, settings, loading, message, blocked ->
        UiState(
            tasks = tasks,
            settings = settings,
            blockedServices = blocked,
            isLoading = loading,
            message = message,
            allTasksCompleted = tasks.isNotEmpty() && tasks.all { it.completed }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    init {
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

    fun saveSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.save(settings)
            refreshStatus()
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun testConnection(settings: Settings) {
        viewModelScope.launch {
            _isLoading.value = true
            val api = createApi(settings)
            api.testConnection()
                .onSuccess { _message.value = it }
                .onFailure { _message.value = "Fehler: ${it.message}" }
            _isLoading.value = false
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            val settings = uiState.value.settings
            if (settings.serverUrl.isBlank() || settings.clientIp.isBlank()) return@launch

            _isLoading.value = true
            val api = createApi(settings)
            api.getBlockedServices(settings.clientIp)
                .onSuccess { _blockedServices.value = it }
                .onFailure { _message.value = "Verbindungsfehler: ${it.message}" }
            _isLoading.value = false
        }
    }

    fun unlockServices() {
        viewModelScope.launch {
            val settings = uiState.value.settings
            if (settings.clientIp.isBlank()) {
                _message.value = "Keine Client-IP konfiguriert"
                return@launch
            }

            _isLoading.value = true
            val api = createApi(settings)
            api.unblockServices(settings.clientIp, settings.services)
                .onSuccess {
                    _blockedServices.value = _blockedServices.value - settings.services.toSet()
                    val labels = KNOWN_SERVICES.toMap()
                    val names = settings.services.map { labels[it] ?: it }
                    _message.value = "${names.joinToString(", ")} freigeschaltet!"
                }
                .onFailure { _message.value = "Fehler: ${it.message}" }
            _isLoading.value = false
        }
    }

    fun lockServices() {
        viewModelScope.launch {
            val settings = uiState.value.settings
            if (settings.clientIp.isBlank()) {
                _message.value = "Keine Client-IP konfiguriert"
                return@launch
            }

            _isLoading.value = true
            val api = createApi(settings)
            api.blockServices(settings.clientIp, settings.services)
                .onSuccess {
                    _blockedServices.value = (_blockedServices.value + settings.services).distinct()
                    val labels = KNOWN_SERVICES.toMap()
                    val names = settings.services.map { labels[it] ?: it }
                    _message.value = "${names.joinToString(", ")} gesperrt."
                    taskRepository.resetTasks()
                }
                .onFailure { _message.value = "Fehler: ${it.message}" }
            _isLoading.value = false
        }
    }

    private fun createApi(settings: Settings) = AdGuardApi(
        serverUrl = settings.serverUrl,
        username = settings.username,
        password = settings.password
    )
}
