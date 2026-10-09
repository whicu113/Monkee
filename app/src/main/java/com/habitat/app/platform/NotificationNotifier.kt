package com.habitat.app.platform

import android.content.Context
import com.habitat.app.domain.model.Habit
import java.time.LocalDate

/**
 * Port interface for posting notifications.
 * Allows ReminderFulfillmentUseCase to be tested cleanly with fakes.
 * SPEC Section 8.1.
 */
interface NotificationNotifier {
    fun postHabitNotification(context: Context, habit: Habit, logicalDate: LocalDate, snoozeMinutes: Int)
    fun postSummaryNotification(context: Context, overflowHabits: List<Habit>, logicalDate: LocalDate)
}

class SystemNotificationNotifier : NotificationNotifier {
    override fun postHabitNotification(context: Context, habit: Habit, logicalDate: LocalDate, snoozeMinutes: Int) {
        NotificationHelper.postHabitNotification(context, habit, logicalDate, snoozeMinutes)
    }

    override fun postSummaryNotification(context: Context, overflowHabits: List<Habit>, logicalDate: LocalDate) {
        NotificationHelper.postSummaryNotification(context, overflowHabits, logicalDate)
    }
}
