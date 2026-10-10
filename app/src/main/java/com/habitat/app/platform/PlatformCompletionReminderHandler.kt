package com.habitat.app.platform

import android.content.Context
import android.util.Log
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.WidgetUpdater
import com.habitat.app.domain.updateSafely
import com.habitat.app.domain.repository.CompletionRepository
import com.habitat.app.domain.repository.HabitRepository
import com.habitat.app.domain.repository.SettingsRepository
import com.habitat.app.domain.usecase.CompletionReminderHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android implementation of [CompletionReminderHandler].
 * Cancels active notifications, cancels pending snooze alarms, and reconciles future occurrences.
 * SPEC Section 8.1.
 */
@Singleton
class PlatformCompletionReminderHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val habitRepository: HabitRepository,
    private val completionRepository: CompletionRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: AppClock,
    private val widgetUpdater: WidgetUpdater
) : CompletionReminderHandler {

    override suspend fun onCompletionLogged(habitId: String, logicalDate: LocalDate) {
        try {
            NotificationHelper.cancelNotification(context, habitId)
            ReminderScheduler.cancelAlarm(context, habitId, "snooze_alarm")
            ReminderScheduler.reconcile(context, habitRepository, completionRepository, settingsRepository, clock)
        } finally {
            widgetUpdater.updateSafely { exception ->
                Log.e(TAG, "Widget refresh failed after completion of habit '$habitId'", exception)
            }
        }
    }

    private companion object {
        const val TAG = "PlatformCompletionReminderHandler"
    }
}
