package com.habitat.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.model.ScheduleType
import com.habitat.app.domain.repository.CompletionRepository
import com.habitat.app.domain.repository.HabitRepository
import com.habitat.app.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BroadcastReceiver triggered by AlarmManager when a habit reminder fires.
 * Uses goAsync() for background DB checks and notification posting.
 * Re-arms the next scheduled occurrence automatically after firing.
 * SPEC Section 8.1.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var habitRepository: HabitRepository

    @Inject
    lateinit var completionRepository: CompletionRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var clock: AppClock

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getStringExtra(NotificationHelper.EXTRA_HABIT_ID) ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val habit = habitRepository.getHabitById(habitId) ?: return@launch
                if (habit.archived || habit.schedule is ScheduleType.Unknown) return@launch

                val settings = settingsRepository.getSettings().first()
                val today = clock.logicalDate()
                val completions = completionRepository.getCompletionsForHabit(habitId).first()

                // Check if already logged for today
                val alreadyLogged = completions.any { it.logicalDate == today }
                if (!alreadyLogged) {
                    NotificationHelper.postHabitNotification(
                        context = context,
                        habit = habit,
                        logicalDate = today,
                        snoozeMinutes = settings.snoozeLengthMinutes
                    )
                }

                // Re-arm next scheduled reminder occurrence
                val nowInstant = clock.instant().plusSeconds(60) // look forward past current fire time
                ReminderScheduler.scheduleNextForHabit(
                    context = context,
                    habit = habit,
                    fromInstant = nowInstant,
                    zoneId = clock.zoneId(),
                    appSettings = settings
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
