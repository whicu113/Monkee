package com.habitat.app.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.logic.ReminderEngine
import com.habitat.app.domain.model.AppSettings
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.repository.CompletionRepository
import com.habitat.app.domain.repository.HabitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reconciles and schedules habit reminders with exact-alarm permission check and quiet hours.
 * SPEC Section 8.1.
 */
object ReminderScheduler {

    /**
     * Idempotently reconciles alarms for active habits.
     * Called on app start, boot, app update, habit/settings edit, and time/timezone change.
     */
    fun reconcile(
        context: Context,
        habitRepository: HabitRepository,
        completionRepository: CompletionRepository,
        clock: AppClock,
        appSettings: AppSettings = AppSettings()
    ) {
        CoroutineScope(Dispatchers.IO).launch {
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
        appSettings: AppSettings = AppSettings()
    ) {
        val nextTrigger = ReminderEngine.nextTrigger(
            habit = habit,
            fromInstant = fromInstant,
            zoneId = zoneId,
            quietHoursStart = appSettings.quietHoursStart,
            quietHoursEnd = appSettings.quietHoursEnd
        )

        if (nextTrigger != null) {
            scheduleAlarm(context, habit.id, nextTrigger.toEpochMilli())
        } else {
            cancelAlarm(context, habit.id)
        }
    }

    fun snoozeReminder(
        context: Context,
        habitId: String,
        logicalDate: LocalDate,
        snoozeMinutes: Int
    ) {
        val triggerAtMillis = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
        scheduleAlarm(context, habitId, triggerAtMillis)
    }

    private fun scheduleAlarm(context: Context, habitId: String, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(NotificationHelper.EXTRA_HABIT_ID, habitId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            habitId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Scheduling is inexact by default for battery efficiency;
        // exact alarms (setExactAndAllowWhileIdle) are used only if SCHEDULE_EXACT_ALARM permission is granted on Android 12+ (SDK 31+)
        val canUseExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canUseExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun cancelAlarm(context: Context, habitId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            habitId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
