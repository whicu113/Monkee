package com.habitat.app.ui.habit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitat.app.domain.logic.ReminderEngine
import com.habitat.app.domain.model.Difficulty
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HabitEditorDialog(
    habit: Habit? = null,
    onDismiss: () -> Unit,
    onSave: (Habit) -> Unit
) {
    var name by remember { mutableStateOf(habit?.name ?: "") }
    var emoji by remember { mutableStateOf(habit?.emoji ?: "🌱") }
    var cue by remember { mutableStateOf(habit?.cue ?: "") }
    var fullVersion by remember { mutableStateOf(habit?.fullVersion ?: "") }
    var minVersion by remember { mutableStateOf(habit?.minVersion ?: "") }
    var identity by remember { mutableStateOf(habit?.identity ?: "") }
    var difficulty by remember { mutableStateOf(habit?.difficulty ?: Difficulty.EASY) }
    var reminderTime by remember { mutableStateOf<LocalTime?>(habit?.reminderTime) }
    var showTimePicker by remember { mutableStateOf(false) }

    var scheduleTypeOption by remember {
        mutableStateOf(
            when (habit?.schedule) {
                is ScheduleType.Weekdays -> "WEEKDAYS"
                is ScheduleType.EveryNDays -> "EVERY_N"
                is ScheduleType.NPerWeek -> "N_PER_WEEK"
                else -> "DAILY"
            }
        )
    }

    var selectedWeekdays by remember {
        mutableStateOf(
            (habit?.schedule as? ScheduleType.Weekdays)?.days ?: setOf(
                DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY
            )
        )
    }

    var everyNDaysVal by remember {
        mutableStateOf(
            ((habit?.schedule as? ScheduleType.EveryNDays)?.n ?: 2).toString()
        )
    }

    var nPerWeekVal by remember {
        mutableStateOf(
            ((habit?.schedule as? ScheduleType.NPerWeek)?.n ?: 3).toString()
        )
    }

    var nameError by remember { mutableStateOf(value = false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (habit == null) "Create Habit" else "Edit Habit") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) nameError = false
                    },
                    label = { Text("Name *") },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("Habit name is required") }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it },
                        label = { Text("Emoji") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Reminder Time Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reminder Time", style = MaterialTheme.typography.bodyMedium)
                        val timeDisplay = reminderTime?.let {
                            "${String.format("%02d:%02d", it.hour, it.minute)} (approximate)"
                        } ?: "None (no reminder)"
                        Text(
                            text = timeDisplay,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (reminderTime != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                        if (reminderTime != null && ReminderEngine.isInsideQuietHours(reminderTime!!, LocalTime.of(22, 0), LocalTime.of(7, 0))) {
                            Text(
                                text = "⚠️ Falls in quiet hours (22:00-07:00). Reminder will deliver at 07:00.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        } else {
                            Text(
                                text = "Note: Reminder times are approximate to preserve battery life.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (reminderTime != null) {
                            OutlinedButton(onClick = { reminderTime = null }) {
                                Text("Clear")
                            }
                        }
                        Button(onClick = { showTimePicker = true }) {
                            Text(if (reminderTime == null) "Set Time" else "Change")
                        }
                    }
                }

                OutlinedTextField(
                    value = cue,
                    onValueChange = { cue = it },
                    label = { Text("Cue (Prompt)") },
                    placeholder = { Text("After I ...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = minVersion,
                    onValueChange = { minVersion = it },
                    label = { Text("Minimum Version (Prompted)") },
                    placeholder = { Text("e.g. Put on running shoes") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = fullVersion,
                    onValueChange = { fullVersion = it },
                    label = { Text("Full Version") },
                    placeholder = { Text("e.g. Run 30 minutes") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = identity,
                    onValueChange = { identity = it },
                    label = { Text("Identity Statement") },
                    placeholder = { Text("e.g. I am an athlete") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = "Difficulty",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Difficulty.entries.forEach { diff ->
                        FilterChip(
                            selected = (difficulty == diff),
                            onClick = { difficulty = diff },
                            label = { Text(diff.name) }
                        )
                    }
                }

                Text(
                    text = "Schedule",
                    style = MaterialTheme.typography.titleSmall
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = scheduleTypeOption == "DAILY",
                        onClick = { scheduleTypeOption = "DAILY" },
                        label = { Text("Daily") }
                    )
                    FilterChip(
                        selected = scheduleTypeOption == "WEEKDAYS",
                        onClick = { scheduleTypeOption = "WEEKDAYS" },
                        label = { Text("Weekdays") }
                    )
                    FilterChip(
                        selected = scheduleTypeOption == "EVERY_N",
                        onClick = { scheduleTypeOption = "EVERY_N" },
                        label = { Text("Every N Days") }
                    )
                    FilterChip(
                        selected = scheduleTypeOption == "N_PER_WEEK",
                        onClick = { scheduleTypeOption = "N_PER_WEEK" },
                        label = { Text("N Per Week") }
                    )
                }

                when (scheduleTypeOption) {
                    "WEEKDAYS" -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            DayOfWeek.entries.forEach { day ->
                                val isSelected = day in selectedWeekdays
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedWeekdays = if (isSelected) {
                                            selectedWeekdays - day
                                        } else {
                                            selectedWeekdays + day
                                        }
                                    },
                                    label = { Text(day.name.take(3)) }
                                )
                            }
                        }
                    }
                    "EVERY_N" -> {
                        OutlinedTextField(
                            value = everyNDaysVal,
                            onValueChange = { everyNDaysVal = it },
                            label = { Text("Every N Days (Interval)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    "N_PER_WEEK" -> {
                        OutlinedTextField(
                            value = nPerWeekVal,
                            onValueChange = { nPerWeekVal = it },
                            label = { Text("Times Per Week (1..7)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                        return@Button
                    }

                    val finalSchedule: ScheduleType = when (scheduleTypeOption) {
                        "WEEKDAYS" -> ScheduleType.Weekdays(selectedWeekdays)
                        "EVERY_N" -> ScheduleType.EveryNDays(
                            n = everyNDaysVal.toIntOrNull()?.coerceAtLeast(1) ?: 2,
                            startDate = LocalDate.now()
                        )
                        "N_PER_WEEK" -> ScheduleType.NPerWeek(
                            n = nPerWeekVal.toIntOrNull()?.coerceIn(1, 7) ?: 3
                        )
                        else -> ScheduleType.Daily
                    }

                    val updatedHabit = (habit ?: Habit(name = name)).copy(
                        name = name.trim(),
                        emoji = emoji.ifBlank { "🌱" },
                        cue = cue.trim(),
                        fullVersion = fullVersion.trim(),
                        minVersion = minVersion.trim(),
                        identity = identity.trim(),
                        reminderTime = reminderTime,
                        difficulty = difficulty,
                        schedule = finalSchedule
                    )

                    onSave(updatedHabit)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showTimePicker) {
        val initialTime = reminderTime ?: LocalTime.of(9, 0)
        val pickerState = rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Reminder Time") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    reminderTime = LocalTime.of(pickerState.hour, pickerState.minute)
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
