package com.habitat.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.WidgetUpdater
import com.habitat.app.domain.updateSafely
import com.habitat.app.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BroadcastReceiver triggered when the day boundary alarm fires.
 * Uses goAsync() and re-arms the next day boundary alarm.
 * (Widget refresh call to be wired in commit 8).
 *
 * SPEC Section 4.3 & 8.7.
 */
@AndroidEntryPoint
class DayBoundaryReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var clock: AppClock

    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                executeBoundaryTransition(context)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    suspend fun executeBoundaryTransition(context: Context) {
        try {
            DayBoundaryScheduler.scheduleNext(context, settingsRepository, clock)
        } finally {
            widgetUpdater.updateSafely { exception ->
                Log.e(TAG, "Widget refresh failed at day boundary", exception)
            }
        }
    }

    companion object {
        const val ACTION_DAY_BOUNDARY = "com.habitat.app.action.DAY_BOUNDARY"
        private const val TAG = "DayBoundaryReceiver"
    }
}
