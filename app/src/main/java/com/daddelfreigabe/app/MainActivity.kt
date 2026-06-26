package com.daddelfreigabe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.daddelfreigabe.app.ui.screens.ClientEditScreen
import com.daddelfreigabe.app.ui.screens.SettingsScreen
import com.daddelfreigabe.app.ui.screens.TaskListScreen
import com.daddelfreigabe.app.ui.theme.DaddelfreigabeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DaddelfreigabeTheme {
                val viewModel: MainViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "tasks") {
                    composable("tasks") {
                        TaskListScreen(
                            uiState = uiState,
                            onToggleTask = viewModel::toggleTask,
                            onAddTask = viewModel::addTask,
                            onRemoveTask = viewModel::removeTask,
                            onUnlock = viewModel::unlockServices,
                            onRefresh = viewModel::refreshStatus,
                            onSettingsClick = { navController.navigate("settings") },
                            onMessageShown = viewModel::clearMessage
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            currentSettings = uiState.settings,
                            onSaveServer = viewModel::saveServer,
                            onTestConnection = viewModel::testConnection,
                            onRemoveClient = viewModel::removeClient,
                            onEditClient = { clientId -> navController.navigate("client/$clientId") },
                            onAddClient = { navController.navigate("client/new") },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("client/{clientId}") { backStackEntry ->
                        val clientId = backStackEntry.arguments?.getString("clientId")
                        val existingClient = if (clientId == "new") null
                            else uiState.settings.clients.find { it.id == clientId }

                        ClientEditScreen(
                            client = existingClient,
                            onSave = { client ->
                                if (clientId == "new") viewModel.addClient(client)
                                else viewModel.updateClient(client)
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
