package com.habitat.app.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reconciles and schedules habit reminders using inexact alarms for battery efficiency.
 * Always fetches the latest persisted AppSettings from SettingsRepository.
 * SPEC Section 8.1.
 */
object ReminderScheduler {

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
    }

    /**
     * Schedules the next trigger for a single [habit] starting after [fromInstant].
     */
    fun scheduleNextForHabit(
        context: Context,
        habit: Habit,
        fromInstant: java.time.Instant,
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
            scheduleAlarm(context, habit.id, "alarm", nextTrigger.toEpochMilli())
        } else {
            cancelAlarm(context, habit.id, "alarm")
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
        scheduleAlarm(context, habitId, "snooze_alarm", triggerAtMillis, intentExtraDate)
    }

    fun isAlarmScheduled(context: Context, habitId: String, actionPath: String = "alarm"): Boolean {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = Uri.parse("habitat://reminder/$habitId/$actionPath")
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) != null
    }

    private fun scheduleAlarm(
        context: Context,
        habitId: String,
        actionPath: String,
        triggerAtMillis: Long,
        logicalDateExtra: String? = null
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = Uri.parse("habitat://reminder/$habitId/$actionPath")
            putExtra(NotificationHelper.EXTRA_HABIT_ID, habitId)
            if (logicalDateExtra != null) {
                putExtra(NotificationHelper.EXTRA_LOGICAL_DATE, logicalDateExtra)
            }
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    fun cancelAlarm(context: Context, habitId: String, actionPath: String = "alarm") {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = Uri.parse("habitat://reminder/$habitId/$actionPath")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )?.also {
            alarmManager.cancel(it)
            it.cancel()
        }
    }
}
