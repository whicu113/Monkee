package com.habitat.app

import android.app.Application
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.repository.CompletionRepository
import com.habitat.app.domain.repository.HabitRepository
import com.habitat.app.platform.NotificationHelper
import com.habitat.app.platform.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HabitatApplication : Application() {

    @Inject
    lateinit var habitRepository: HabitRepository

    @Inject
    lateinit var completionRepository: CompletionRepository

    @Inject
    lateinit var clock: AppClock

    override fun onCreate() {
        super.onCreate()

        // Create notification channels idempotently at application startup
        NotificationHelper.createNotificationChannel(this)

        // Single startup reconciliation: calculates & schedules future alarms without posting notifications
        ReminderScheduler.reconcile(
            context = this,
            habitRepository = habitRepository,
            completionRepository = completionRepository,
            clock = clock
        )
    }
}
