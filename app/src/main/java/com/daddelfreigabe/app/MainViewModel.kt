package com.daddelfreigabe.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daddelfreigabe.app.data.AdGuardApi
import com.daddelfreigabe.app.data.Settings
import com.daddelfreigabe.app.data.SettingsStore
import com.daddelfreigabe.app.data.Task
import com.daddelfreigabe.app.data.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val tasks: List<Task> = emptyList(),
    val settings: Settings = Settings(),
    val isClientBlocked: Boolean? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
    val allTasksCompleted: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val taskRepository = TaskRepository(application)

    private val _isLoading = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)
    private val _isClientBlocked = MutableStateFlow<Boolean?>(null)

    val uiState: StateFlow<UiState> = combine(
        taskRepository.tasks,
        settingsStore.settings,
        _isLoading,
        _message,
        _isClientBlocked
    ) { tasks, settings, loading, message, blocked ->
        UiState(
            tasks = tasks,
            settings = settings,
            isClientBlocked = blocked,
            isLoading = loading,
            message = message,
            allTasksCompleted = tasks.isNotEmpty() && tasks.all { it.completed }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    init {
        refreshStatus()
    }

    fun toggleTask(taskId: String) {
        viewModelScope.launch {
            taskRepository.toggleTask(taskId)
        }
    }

    fun addTask(title: String) {
        viewModelScope.launch {
            taskRepository.addTask(title)
        }
    }

    fun removeTask(taskId: String) {
        viewModelScope.launch {
            taskRepository.removeTask(taskId)
        }
    }

    fun resetTasks() {
        viewModelScope.launch {
            taskRepository.resetTasks()
        }
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

    fun refreshStatus() {
        viewModelScope.launch {
            val settings = uiState.value.settings
            if (settings.serverUrl.isBlank() || settings.clientId.isBlank()) return@launch

            _isLoading.value = true
            val api = createApi(settings)
            api.isClientBlocked(settings.clientId)
                .onSuccess { _isClientBlocked.value = it }
                .onFailure { _message.value = "Verbindungsfehler: ${it.message}" }
            _isLoading.value = false
        }
    }

    fun unlockClient() {
        viewModelScope.launch {
            val settings = uiState.value.settings
            if (settings.clientId.isBlank()) {
                _message.value = "Keine Client-ID konfiguriert"
                return@launch
            }

            _isLoading.value = true
            val api = createApi(settings)
            api.unblockClient(settings.clientId)
                .onSuccess {
                    _isClientBlocked.value = false
                    _message.value = "Freigeschaltet!"
                }
                .onFailure { _message.value = "Fehler: ${it.message}" }
            _isLoading.value = false
        }
    }

    fun lockClient() {
        viewModelScope.launch {
            val settings = uiState.value.settings
            if (settings.clientId.isBlank()) {
                _message.value = "Keine Client-ID konfiguriert"
                return@launch
            }

            _isLoading.value = true
            val api = createApi(settings)
            api.blockClient(settings.clientId)
                .onSuccess {
                    _isClientBlocked.value = true
                    _message.value = "Gesperrt."
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
