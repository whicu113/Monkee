package com.habitat.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.repository.CompletionRepository
import com.habitat.app.domain.repository.HabitRepository
import com.habitat.app.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BroadcastReceiver restoring reminder alarms on reboot, package update, and time/timezone changes.
 * Uses goAsync() to ensure reconciliation completes before process death.
 * SPEC Section 8.1.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var habitRepository: HabitRepository

    @Inject
    lateinit var completionRepository: CompletionRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var clock: AppClock

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    reconcileAll(context)
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }

    suspend fun handleAction(context: Context, action: String?) {
        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            reconcileAll(context)
        }
    }

    suspend fun reconcileAll(context: Context) {
        reconcileAll(
            reminderReconcile = {
                ReminderScheduler.reconcile(
                    context = context,
                    habitRepository = habitRepository,
                    completionRepository = completionRepository,
                    settingsRepository = settingsRepository,
                    clock = clock
                )
            },
            dayBoundaryRearm = {
                DayBoundaryScheduler.scheduleNext(
                    context = context,
                    settingsRepository = settingsRepository,
                    clock = clock
                )
            }
        )
    }

    internal suspend fun reconcileAll(
        reminderReconcile: suspend () -> Unit,
        dayBoundaryRearm: suspend () -> Unit
    ) {
        try {
            reminderReconcile()
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to reconcile reminder alarms", exception)
        }

        try {
            dayBoundaryRearm()
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to re-arm day-boundary alarm", exception)
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
