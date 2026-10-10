package com.habitat.app.platform

import android.content.Context
import android.util.Log
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.logic.DropReason
import com.habitat.app.domain.logic.FireDecision
import com.habitat.app.domain.logic.ReminderEngine
import com.habitat.app.domain.logic.ScheduleEvaluator
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.ScheduleType
import com.habitat.app.domain.repository.CompletionRepository
import com.habitat.app.domain.repository.HabitRepository
import com.habitat.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles the fulfillment of a reminder alarm:
 * 1. Re-arms next occurrence first (guaranteeing alarms survive any downstream failures)
 * 2. Evaluates eligibility (checking weekly quota for NPerWeek using ScheduleEvaluator)
 * 3. Dispatches notification posting or summary
 *
 * SPEC Section 8.1.
 */
@Singleton
class ReminderFulfillmentHandler @Inject constructor(
    private val habitRepository: HabitRepository,
    private val completionRepository: CompletionRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: AppClock,
    private val notifier: NotificationNotifier = SystemNotificationNotifier()
) {

    private val tag = "HabitatDebug"

    suspend fun execute(
        context: Context,
        habitId: String,
        intentDateStr: String?,
        forcePost: Boolean = false
    ): FireDecision? {
        val habit = habitRepository.getHabitById(habitId) ?: return null
        if (habit.archived || habit.schedule is ScheduleType.Unknown) return null

        val settings = settingsRepository.getSettings().first()
        val today = clock.logicalDate()

        // Snooze 04:00 Day Boundary check: drop silently if intent date has passed
        if (intentDateStr != null) {
            val intentDate = LocalDate.parse(intentDateStr)
            if (ReminderEngine.isSnoozeExpired(intentDate, today)) {
                return null
            }
        }

        // 1. RE-ARM FIRST: Arm the next trigger BEFORE posting notification
        val nowInstant = clock.instant().plusSeconds(60)
        ReminderScheduler.scheduleNextForHabit(
            context = context,
            habit = habit,
            fromInstant = nowInstant,
            zoneId = clock.zoneId(),
            appSettings = settings
        )

        // 2. Compute isWeeklyQuotaMet and completions for habit
        val completions = completionRepository.getCompletionsForHabit(habitId).first()
        val isLoggedToday = completions.any { it.logicalDate == today }
        val postedIds = settingsRepository.getPostedHabitIds(today.toString())

        val isWeeklyQuotaMet = if (habit.schedule is ScheduleType.NPerWeek) {
            val weekStart = ScheduleEvaluator.startOfWeek(today, settings.startOfWeekDay)
            val weekEnd = weekStart.plusDays(6)
            val completionsThisWeek = completions.count { comp ->
                comp.logicalDate in weekStart..weekEnd &&
                        (comp.state == CompletionState.DONE || comp.state == CompletionState.MINIMUM)
            }
            !ScheduleEvaluator.isDueOn(habit.schedule, today, completionsThisWeek)
        } else {
            false
        }

        val scheduledTime = habit.reminderTime ?: clock.zonedDateTime().toLocalTime()
        val scheduledInstant = LocalDateTime.of(today, scheduledTime).atZone(clock.zoneId()).toInstant()

        val decision = if (forcePost) {
            Log.d(tag, "Habit ${habit.name} (${habit.id}): Force post mode active, bypassing eligibility checks")
            FireDecision.Post
        } else {
            val eval = ReminderEngine.evaluateFireTimeDecision(
                habit = habit,
                scheduledInstant = scheduledInstant,
                nowInstant = clock.instant(),
                zoneId = clock.zoneId(),
                quietHoursEnabled = settings.quietHoursEnabled,
                quietHoursStart = settings.quietHoursStart,
                quietHoursEnd = settings.quietHoursEnd,
                isLoggedToday = isLoggedToday,
                isWeeklyQuotaMet = isWeeklyQuotaMet,
                postedIdsToday = postedIds,
                currentLogicalDate = today
            )
            Log.d(tag, "Habit ${habit.name} (${habit.id}): Decision = $eval")
            eval
        }

        when (decision) {
            is FireDecision.Post -> {
                if (!forcePost) {
                    settingsRepository.addPostedHabitId(today.toString(), habitId)
                }
                notifier.postHabitNotification(
                    context = context,
                    habit = habit,
                    logicalDate = today,
                    snoozeMinutes = settings.snoozeLengthMinutes
                )
            }
            is FireDecision.Summary -> {
                settingsRepository.addPostedHabitId(today.toString(), habitId)
                val habits = habitRepository.getActiveHabits().first()
                val overflow = habits.filter { it.id !in postedIds }
                notifier.postSummaryNotification(
                    context = context,
                    overflowHabits = overflow,
                    logicalDate = today
                )
            }
            is FireDecision.Drop -> {
                // Drop: do not post notification and do not add to postedIds
            }
        }

        return decision
    }
}
