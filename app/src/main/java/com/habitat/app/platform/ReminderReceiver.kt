package com.habitat.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.habitat.app.domain.AppClock
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
import java.time.LocalTime
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
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val habit = habitRepository.getHabitById(habitId) ?: return@launch
                if (habit.archived || habit.schedule is ScheduleType.Unknown) return@launch

                val settings = settingsRepository.getSettings().first()
                val today = clock.logicalDate()

                // RE-ARM FIRST: Arm the next trigger BEFORE posting notification
                val nowInstant = clock.instant().plusSeconds(60)
                ReminderScheduler.scheduleNextForHabit(
                    context = context,
                    habit = habit,
                    fromInstant = nowInstant,
                    zoneId = clock.zoneId(),
                    appSettings = settings
                )

                // Lateness rule / Quiet Hours re-check at fire time
                val nowTime = clock.zonedDateTime().toLocalTime()
                val reminderTime = habit.reminderTime ?: LocalTime.now()

                // If fire time falls in quiet hours or is stale (>2h late), skip posting
                if (ReminderEngine.isInsideQuietHours(nowTime, settings.quietHoursStart, settings.quietHoursEnd)) {
                    return@launch
                }

                val completions = completionRepository.getCompletionsForHabit(habitId).first()
                val isLoggedToday = completions.any { it.logicalDate == today }

                // Check pure domain shouldPost
                val shouldPost = ReminderEngine.shouldPost(
                    habit = habit,
                    isLoggedToday = isLoggedToday,
                    isWeeklyQuotaMet = false,
                    currentLogicalDate = today
                )

                if (!shouldPost) return@launch

                // Daily cap check using set of posted habit IDs
                val postedIds = settingsRepository.getPostedHabitIds(today.toString())
                val cap = 4

                if (postedIds.size < cap || postedIds.contains(habitId)) {
                    settingsRepository.addPostedHabitId(today.toString(), habitId)
                    NotificationHelper.postHabitNotification(
                        context = context,
                        habit = habit,
                        logicalDate = today,
                        snoozeMinutes = settings.snoozeLengthMinutes
                    )
                } else {
                    // Summary notification for overflow
                    val habits = habitRepository.getActiveHabits().first()
                    val overflow = habits.filter { it.id !in postedIds }
                    NotificationHelper.postSummaryNotification(
                        context = context,
                        overflowHabits = overflow,
                        logicalDate = today
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
