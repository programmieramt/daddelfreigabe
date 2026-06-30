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
import java.time.LocalDate
import java.time.LocalDateTime

private val Context.taskDataStore by preferencesDataStore(name = "tasks")

/** Matches the nightly cron lock time, so the task day rolls over with it. */
private const val DAY_CUTOFF_HOUR = 3

@JsonClass(generateAdapter = true)
data class Task(
    val id: String,
    val title: String,
    val completed: Boolean = false
)

class TaskRepository(private val context: Context) {

    private companion object {
        val TASKS_JSON = stringPreferencesKey("tasks_json")
        val TASK_DAY = stringPreferencesKey("task_day")
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
            prefs[TASK_DAY] = currentTaskDay().toString()
        }
    }

    /** Resets tasks once per "task day" (rolls over at [DAY_CUTOFF_HOUR]), matching the nightly cron lock. */
    suspend fun resetIfNewDay() {
        context.taskDataStore.edit { prefs ->
            val today = currentTaskDay().toString()
            if (prefs[TASK_DAY] != today) {
                val json = prefs[TASKS_JSON]
                val current = if (json != null) adapter.fromJson(json) ?: defaultTasks() else defaultTasks()
                prefs[TASKS_JSON] = adapter.toJson(current.map { it.copy(completed = false) })
                prefs[TASK_DAY] = today
            }
        }
    }

    private fun currentTaskDay(): LocalDate {
        val now = LocalDateTime.now()
        return if (now.hour < DAY_CUTOFF_HOUR) now.toLocalDate().minusDays(1) else now.toLocalDate()
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
