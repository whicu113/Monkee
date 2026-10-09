package com.habitat.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.repository.CompletionRepository
import com.habitat.app.domain.repository.HabitRepository
import com.habitat.app.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * BroadcastReceiver restoring reminder alarms on reboot, package update, and time/timezone changes.
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
            ReminderScheduler.reconcile(
                context = context,
                habitRepository = habitRepository,
                completionRepository = completionRepository,
                settingsRepository = settingsRepository,
                clock = clock
            )
        }
    }
}
