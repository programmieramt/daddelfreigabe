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

private val Context.taskDataStore by preferencesDataStore(name = "tasks")

@JsonClass(generateAdapter = true)
data class Task(
    val id: String,
    val title: String,
    val completed: Boolean = false
)

class TaskRepository(private val context: Context) {

    private companion object {
        val TASKS_JSON = stringPreferencesKey("tasks_json")
    }

    private val moshi = Moshi.Builder().build()
    private val taskListType = Types.newParameterizedType(List::class.java, Task::class.java)
    private val adapter = moshi.adapter<List<Task>>(taskListType)

    val tasks: Flow<List<Task>> = context.taskDataStore.data.map { prefs ->
        val json = prefs[TASKS_JSON] ?: return@map defaultTasks()
        adapter.fromJson(json) ?: defaultTasks()
    }

    suspend fun saveTasks(tasks: List<Task>) {
        context.taskDataStore.edit { prefs ->
            prefs[TASKS_JSON] = adapter.toJson(tasks)
        }
    }

    suspend fun toggleTask(taskId: String) {
        context.taskDataStore.edit { prefs ->
            val json = prefs[TASKS_JSON]
            val current = if (json != null) adapter.fromJson(json) ?: defaultTasks() else defaultTasks()
            val updated = current.map { task ->
                if (task.id == taskId) task.copy(completed = !task.completed) else task
            }
            prefs[TASKS_JSON] = adapter.toJson(updated)
        }
    }

    suspend fun resetTasks() {
        context.taskDataStore.edit { prefs ->
            val json = prefs[TASKS_JSON]
            val current = if (json != null) adapter.fromJson(json) ?: defaultTasks() else defaultTasks()
            val reset = current.map { it.copy(completed = false) }
            prefs[TASKS_JSON] = adapter.toJson(reset)
        }
    }

    suspend fun addTask(title: String) {
        context.taskDataStore.edit { prefs ->
            val json = prefs[TASKS_JSON]
            val current = if (json != null) adapter.fromJson(json) ?: defaultTasks() else defaultTasks()
            val newTask = Task(
                id = System.currentTimeMillis().toString(),
                title = title
            )
            prefs[TASKS_JSON] = adapter.toJson(current + newTask)
        }
    }

    suspend fun removeTask(taskId: String) {
        context.taskDataStore.edit { prefs ->
            val json = prefs[TASKS_JSON]
            val current = if (json != null) adapter.fromJson(json) ?: defaultTasks() else defaultTasks()
            prefs[TASKS_JSON] = adapter.toJson(current.filter { it.id != taskId })
        }
    }

    private fun defaultTasks(): List<Task> = listOf(
        Task(id = "1", title = "Zimmer aufräumen"),
        Task(id = "2", title = "Hausaufgaben machen"),
        Task(id = "3", title = "Müll rausbringen")
    )
}
