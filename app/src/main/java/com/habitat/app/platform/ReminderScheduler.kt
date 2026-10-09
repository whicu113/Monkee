package com.habitat.app.platform

import android.content.Context
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.logic.ReminderEngine
import com.habitat.app.domain.model.AppSettings
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.repository.CompletionRepository
import com.habitat.app.domain.repository.HabitRepository
import com.habitat.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reconciles and schedules habit reminders using inexact alarms for battery efficiency.
 * Always fetches the latest persisted AppSettings from SettingsRepository.
 * SPEC Section 8.1.
 */
object ReminderScheduler {

    var alarmGateway: AlarmGateway = SystemAlarmGateway()

    /**
     * Idempotently reconciles alarms for active habits.
     * Fetches current [AppSettings] directly from [settingsRepository].
     * Called on app startup, boot, app update, habit/settings edit, and time/timezone change.
     */
    fun reconcile(
        context: Context,
        habitRepository: HabitRepository,
        completionRepository: CompletionRepository,
        settingsRepository: SettingsRepository,
        clock: AppClock
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            reconcileDirect(context, habitRepository, completionRepository, settingsRepository, clock)
        }
    }

    suspend fun reconcileDirect(
        context: Context,
        habitRepository: HabitRepository,
        completionRepository: CompletionRepository,
        settingsRepository: SettingsRepository,
        clock: AppClock
    ) {
        val appSettings = settingsRepository.getSettings().first()
        val habits = habitRepository.getActiveHabits().first()
        val nowInstant = clock.instant()
        val zoneId = clock.zoneId()

        habits.forEach { habit ->
            scheduleNextForHabit(
                context = context,
                habit = habit,
                fromInstant = nowInstant,
                zoneId = zoneId,
                appSettings = appSettings
            )
        }
    }

    /**
     * Schedules the next trigger for a single [habit] starting after [fromInstant].
     */
    fun scheduleNextForHabit(
        context: Context,
        habit: Habit,
        fromInstant: Instant,
        zoneId: ZoneId,
        appSettings: AppSettings
    ) {
        val nextTrigger = ReminderEngine.nextTrigger(
            habit = habit,
            fromInstant = fromInstant,
            zoneId = zoneId,
            quietHoursEnabled = appSettings.quietHoursEnabled,
            quietHoursStart = appSettings.quietHoursStart,
            quietHoursEnd = appSettings.quietHoursEnd
        )

        if (nextTrigger != null) {
            alarmGateway.scheduleAlarm(context, habit.id, "alarm", nextTrigger.toEpochMilli())
        } else {
            alarmGateway.cancelAlarm(context, habit.id, "alarm")
        }
    }

    fun snoozeReminder(
        context: Context,
        habitId: String,
        logicalDate: LocalDate,
        snoozeMinutes: Int
    ) {
        val triggerAtMillis = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
        val intentExtraDate = logicalDate.toString()
        alarmGateway.scheduleAlarm(context, habitId, "snooze_alarm", triggerAtMillis, intentExtraDate)
    }

    fun isAlarmScheduled(context: Context, habitId: String, actionPath: String = "alarm"): Boolean {
        return alarmGateway.isAlarmScheduled(context, habitId, actionPath)
    }

    fun cancelAlarm(context: Context, habitId: String, actionPath: String = "alarm") {
        alarmGateway.cancelAlarm(context, habitId, actionPath)
    }
}
