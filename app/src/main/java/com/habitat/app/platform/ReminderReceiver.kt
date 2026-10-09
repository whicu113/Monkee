package com.habitat.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.logic.FireDecision
import com.habitat.app.domain.logic.ReminderEngine
import com.habitat.app.domain.model.ScheduleType
import com.habitat.app.domain.repository.CompletionRepository
import com.habitat.app.domain.repository.HabitRepository
import com.habitat.app.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * BroadcastReceiver triggered by AlarmManager when a habit reminder fires.
 * Uses goAsync() for background DB checks and notification posting.
 *
 * NOTE: The next trigger is re-armed BEFORE posting the notification so that a failure
 * or error during notification posting can NEVER break the re-arming chain.
 *
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
        val intentDateStr = intent.getStringExtra(NotificationHelper.EXTRA_LOGICAL_DATE)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val habit = habitRepository.getHabitById(habitId) ?: return@launch
                if (habit.archived || habit.schedule is ScheduleType.Unknown) return@launch

                val settings = settingsRepository.getSettings().first()
                val today = clock.logicalDate()

                // Snooze 04:00 Day Boundary check: drop silently if intent date has passed
                if (intentDateStr != null) {
                    val intentDate = LocalDate.parse(intentDateStr)
                    if (ReminderEngine.isSnoozeExpired(intentDate, today)) {
                        return@launch
                    }
                }

                // RE-ARM FIRST: Arm the next trigger BEFORE posting notification
                val nowInstant = clock.instant().plusSeconds(60)
                ReminderScheduler.scheduleNextForHabit(
                    context = context,
                    habit = habit,
                    fromInstant = nowInstant,
                    zoneId = clock.zoneId(),
                    appSettings = settings
                )

                val completions = completionRepository.getCompletionsForHabit(habitId).first()
                val isLoggedToday = completions.any { it.logicalDate == today }
                val postedIds = settingsRepository.getPostedHabitIds(today.toString())

                val scheduledTime = habit.reminderTime ?: clock.zonedDateTime().toLocalTime()
                val scheduledInstant = LocalDateTime.of(today, scheduledTime).atZone(clock.zoneId()).toInstant()

                val decision = ReminderEngine.evaluateFireTimeDecision(
                    habit = habit,
                    scheduledInstant = scheduledInstant,
                    nowInstant = clock.instant(),
                    zoneId = clock.zoneId(),
                    quietHoursStart = settings.quietHoursStart,
                    quietHoursEnd = settings.quietHoursEnd,
                    isLoggedToday = isLoggedToday,
                    isWeeklyQuotaMet = false,
                    postedIdsToday = postedIds,
                    currentLogicalDate = today
                )

                when (decision) {
                    is FireDecision.Post -> {
                        settingsRepository.addPostedHabitId(today.toString(), habitId)
                        NotificationHelper.postHabitNotification(
                            context = context,
                            habit = habit,
                            logicalDate = today,
                            snoozeMinutes = settings.snoozeLengthMinutes
                        )
                    }
                    is FireDecision.Summary -> {
                        val habits = habitRepository.getActiveHabits().first()
                        val overflow = habits.filter { it.id !in postedIds }
                        NotificationHelper.postSummaryNotification(
                            context = context,
                            overflowHabits = overflow,
                            logicalDate = today
                        )
                    }
                    is FireDecision.Drop -> {
                        // Suppressed (archived, unknown schedule, duplicate, quiet hours, or lateness)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
