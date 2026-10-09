package com.habitat.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Reminders & Quiet Hours", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = settings.quietHoursStart.toString(),
                    onValueChange = { str ->
                        try {
                            val time = LocalTime.parse(str)
                            viewModel.updateSettings(settings.copy(quietHoursStart = time))
                        } catch (_: Exception) {}
                    },
                    label = { Text("Quiet Hours Start") },
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = settings.quietHoursEnd.toString(),
                    onValueChange = { str ->
                        try {
                            val time = LocalTime.parse(str)
                            viewModel.updateSettings(settings.copy(quietHoursEnd = time))
                        } catch (_: Exception) {}
                    },
                    label = { Text("Quiet Hours End") },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = settings.snoozeLengthMinutes.toString(),
                onValueChange = { str ->
                    val mins = str.toIntOrNull()?.coerceIn(5, 120) ?: 30
                    viewModel.updateSettings(settings.copy(snoozeLengthMinutes = mins))
                },
                label = { Text("Snooze Duration (Minutes)") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Streak & Tokens", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Strict Streaks (Reset on single miss)")
                Switch(
                    checked = settings.strictStreaks,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings(settings.copy(strictStreaks = checked))
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-use Rest Tokens on missed days")
                Switch(
                    checked = settings.autoUseRestTokens,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings(settings.copy(autoUseRestTokens = checked))
                    }
                )
            }
        }
    }
}
