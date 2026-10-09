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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

@OptIn(ExperimentalLayoutApi::class)
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
    var reminderTimeStr by remember { mutableStateOf(habit?.reminderTime?.toString() ?: "09:00") }

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
    var timeError by remember { mutableStateOf<String?>(null) }
    var timeWarning by remember { mutableStateOf<String?>(null) }

    fun validateTime(timeStr: String): LocalTime? {
        val parsed = try { LocalTime.parse(timeStr) } catch (_: Exception) { null }
        if (parsed == null) {
            timeError = "Invalid time format (use HH:mm)"
            timeWarning = null
            return null
        }

        // Block reminder times before 04:00 boundary (00:00 to 04:00)
        if (parsed >= LocalTime.MIDNIGHT && parsed < LocalTime.of(4, 0)) {
            timeError = "Reminder times before 04:00 day boundary are not allowed"
            timeWarning = null
            return null
        }

        timeError = null

        // Check if inside quiet hours (22:00 to 07:00)
        if (ReminderEngine.isInsideQuietHours(parsed, LocalTime.of(22, 0), LocalTime.of(7, 0))) {
            timeWarning = "⚠️ Time falls in quiet hours (22:00-07:00). Reminder will deliver at 07:00."
        } else {
            timeWarning = null
        }

        return parsed
    }

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

                OutlinedTextField(
                    value = reminderTimeStr,
                    onValueChange = {
                        reminderTimeStr = it
                        validateTime(it)
                    },
                    label = { Text("Reminder Time (HH:mm)") },
                    isError = timeError != null,
                    supportingText = {
                        when {
                            timeError != null -> Text(timeError!!, color = MaterialTheme.colorScheme.error)
                            timeWarning != null -> Text(timeWarning!!, color = MaterialTheme.colorScheme.tertiary)
                            else -> Text("Note: Reminder times are approximate to preserve battery life.", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

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

                    val validatedTime = validateTime(reminderTimeStr)
                    if (timeError != null) return@Button

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
                        reminderTime = validatedTime,
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
}
