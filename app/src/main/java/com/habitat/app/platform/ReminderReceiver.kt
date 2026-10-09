package com.habitat.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    lateinit var fulfillmentHandler: ReminderFulfillmentHandler

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getStringExtra(NotificationHelper.EXTRA_HABIT_ID) ?: return
        val intentDateStr = intent.getStringExtra(NotificationHelper.EXTRA_LOGICAL_DATE)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                fulfillmentHandler.execute(context, habitId, intentDateStr)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
