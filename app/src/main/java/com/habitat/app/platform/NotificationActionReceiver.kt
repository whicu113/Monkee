package com.habitat.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.usecase.LogCompletionUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Background BroadcastReceiver handling notification actions without opening the app UI.
 * Uses goAsync() for background DB writes via LogCompletionUseCase.
 * SPEC Section 8.1.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var logCompletionUseCase: LogCompletionUseCase

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getStringExtra(NotificationHelper.EXTRA_HABIT_ID) ?: return
        val dateString = intent.getStringExtra(NotificationHelper.EXTRA_LOGICAL_DATE) ?: return
        val logicalDate = LocalDate.parse(dateString)

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    NotificationHelper.ACTION_DONE -> {
                        logCompletionUseCase.executeLog(habitId, CompletionState.DONE, logicalDate)
                    }
                    NotificationHelper.ACTION_MINIMUM -> {
                        logCompletionUseCase.executeLog(habitId, CompletionState.MINIMUM, logicalDate)
                    }
                    NotificationHelper.ACTION_SNOOZE -> {
                        NotificationHelper.cancelNotification(context, habitId)
                        ReminderScheduler.snoozeReminder(context, habitId, logicalDate, 30)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
