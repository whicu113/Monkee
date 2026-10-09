package com.habitat.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()

    var showPickerForStart by remember { mutableStateOf(false) }
    var showPickerForEnd by remember { mutableStateOf(false) }

    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable Quiet Hours")
                Switch(
                    checked = settings.quietHoursEnabled,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings(settings.copy(quietHoursEnabled = checked))
                    }
                )
            }

            if (settings.quietHoursEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showPickerForStart = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Start: ${settings.quietHoursStart.format(timeFormatter)}")
                    }

                    OutlinedButton(
                        onClick = { showPickerForEnd = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("End: ${settings.quietHoursEnd.format(timeFormatter)}")
                    }
                }

                Text(
                    text = "Reminders in this window arrive when it ends.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
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

        if (showPickerForStart) {
            val pickerState = rememberTimePickerState(
                initialHour = settings.quietHoursStart.hour,
                initialMinute = settings.quietHoursStart.minute,
                is24Hour = true
            )
            AlertDialog(
                onDismissRequest = { showPickerForStart = false },
                title = { Text("Select Quiet Hours Start") },
                text = { TimePicker(state = pickerState) },
                confirmButton = {
                    TextButton(onClick = {
                        showPickerForStart = false
                        val newTime = LocalTime.of(pickerState.hour, pickerState.minute)
                        viewModel.updateSettings(settings.copy(quietHoursStart = newTime))
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPickerForStart = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showPickerForEnd) {
            val pickerState = rememberTimePickerState(
                initialHour = settings.quietHoursEnd.hour,
                initialMinute = settings.quietHoursEnd.minute,
                is24Hour = true
            )
            AlertDialog(
                onDismissRequest = { showPickerForEnd = false },
                title = { Text("Select Quiet Hours End") },
                text = { TimePicker(state = pickerState) },
                confirmButton = {
                    TextButton(onClick = {
                        showPickerForEnd = false
                        val newTime = LocalTime.of(pickerState.hour, pickerState.minute)
                        viewModel.updateSettings(settings.copy(quietHoursEnd = newTime))
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPickerForEnd = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
