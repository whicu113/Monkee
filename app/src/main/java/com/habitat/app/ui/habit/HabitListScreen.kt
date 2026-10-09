package com.habitat.app.ui.habit

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitat.app.BuildConfig
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.ScheduleType
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitListScreen(
    viewModel: HabitViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var editingHabit by remember { mutableStateOf<Habit?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Habitat") },
                actions = {
                    uiState.overallConsistency?.let { score ->
                        Text(
                            text = "Consistency: ${(score * 100).roundToInt()}%",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create Habit")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Backfill Date Selection Strip (Past 7 Days -> Today; Future dates blocked)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
                uiState.availableDates.forEach { date ->
                    val isToday = date == uiState.currentLogicalDate
                    val isSelected = date == uiState.selectedDate
                    val labelText = if (isToday) "Today" else date.format(dateFormatter)

                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setSelectedDate(date) },
                        label = { Text(labelText) }
                    )
                }
            }

            // Debug-build-only time shift controls
            if (BuildConfig.DEBUG) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🛠️ Debug Date: ${uiState.currentLogicalDate}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.shiftDebugTime(days = 1, hours = 0) }
                            ) {
                                Text("+1d", fontSize = 10.sp)
                            }
                            OutlinedButton(
                                onClick = { viewModel.shiftDebugTime(days = 0, hours = 1) }
                            ) {
                                Text("+1h", fontSize = 10.sp)
                            }
                            Button(onClick = { viewModel.resetDebugTime() }) {
                                Text("Reset", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            if (uiState.habitItems.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🌱 No active habits yet",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap the + button to create your first habit!",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    items(uiState.habitItems, key = { it.habit.id }) { item ->
                        HabitCard(
                            itemState = item,
                            onCheckOff = { state ->
                                if (item.selectedDateCompletion?.state == state) {
                                    viewModel.removeCompletion(item.habit.id, uiState.selectedDate)
                                } else {
                                    viewModel.toggleCheckOff(item.habit.id, state, uiState.selectedDate)
                                }
                            },
                            onEdit = { editingHabit = item.habit },
                            onArchive = { viewModel.archiveHabit(item.habit.id) }
                        )
                    }
                }
            }
        }

        if (showCreateDialog) {
            HabitEditorDialog(
                habit = null,
                onDismiss = { showCreateDialog = false },
                onSave = {
                    viewModel.saveHabit(it)
                    showCreateDialog = false
                }
            )
        }

        editingHabit?.let { habitToEdit ->
            HabitEditorDialog(
                habit = habitToEdit,
                onDismiss = { editingHabit = null },
                onSave = {
                    viewModel.saveHabit(it)
                    editingHabit = null
                }
            )
        }
    }
}

@Composable
fun HabitCard(
    itemState: HabitItemUiState,
    onCheckOff: (CompletionState) -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit
) {
    val habit = itemState.habit
    val replay = itemState.replayResult
    val isUnknown = habit.schedule is ScheduleType.Unknown

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = habit.emoji, fontSize = 28.sp)
                Spacer(modifier = Modifier.padding(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = habit.name,
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (isUnknown) {
                        Text(
                            text = "⚠️ Needs app update",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        if (habit.minVersion.isNotBlank()) {
                            Text(
                                text = "Min: ${habit.minVersion}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        if (habit.cue.isNotBlank()) {
                            Text(
                                text = "Cue: After I ${habit.cue}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Habit")
                }

                IconButton(onClick = onArchive) {
                    Icon(Icons.Default.Archive, contentDescription = "Archive Habit")
                }
            }

            if (!isUnknown) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        replay.consistencyScore?.let { score ->
                            Text(
                                text = "Score: ${(score * 100).roundToInt()}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = "🔥 ${replay.currentStreak}d streak",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (replay.availableRestTokens > 0) {
                            Text(
                                text = "🛡️ ${replay.availableRestTokens}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = itemState.selectedDateCompletion?.state == CompletionState.DONE,
                            onClick = { onCheckOff(CompletionState.DONE) },
                            label = { Text("Done") }
                        )
                        FilterChip(
                            selected = itemState.selectedDateCompletion?.state == CompletionState.MINIMUM,
                            onClick = { onCheckOff(CompletionState.MINIMUM) },
                            label = { Text("Min") }
                        )
                        FilterChip(
                            selected = itemState.selectedDateCompletion?.state == CompletionState.SKIPPED,
                            onClick = { onCheckOff(CompletionState.SKIPPED) },
                            label = { Text("Skip") }
                        )
                    }
                }
            }
        }
    }
}
