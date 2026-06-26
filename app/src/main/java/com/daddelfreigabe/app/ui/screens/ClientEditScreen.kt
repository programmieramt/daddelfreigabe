package com.daddelfreigabe.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daddelfreigabe.app.data.ClientConfig
import com.daddelfreigabe.app.data.KNOWN_SERVICES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientEditScreen(
    client: ClientConfig?,
    onSave: (ClientConfig) -> Unit,
    onBack: () -> Unit
) {
    val isNew = client == null
    var name by remember { mutableStateOf(client?.name ?: "") }
    var ip by remember { mutableStateOf(client?.ip ?: "") }
    var selectedServices by remember { mutableStateOf(client?.services?.toSet() ?: setOf("youtube")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Neuer Client" else "Client bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("Sebastians Laptop") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("IP-Adresse") },
                placeholder = { Text("192.168.68.53") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text("Dienste zum Freischalten", style = MaterialTheme.typography.titleSmall)

            KNOWN_SERVICES.forEach { (id, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = id in selectedServices,
                        onCheckedChange = { checked ->
                            selectedServices = if (checked) selectedServices + id else selectedServices - id
                        }
                    )
                    Text(label, modifier = Modifier.padding(start = 4.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    onSave(
                        ClientConfig(
                            id = client?.id ?: System.currentTimeMillis().toString(),
                            name = name.trim(),
                            ip = ip.trim(),
                            services = selectedServices.toList()
                        )
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && ip.isNotBlank() && selectedServices.isNotEmpty()
            ) {
                Text(if (isNew) "Hinzufügen" else "Speichern")
            }
        }
    }
}
