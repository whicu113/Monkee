package com.habitat.app.platform

import android.content.Context
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.logic.DayBoundaryEngine
import com.habitat.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Reconciles and schedules the day boundary alarm.
 * Arms an inexact alarm via [AlarmGateway] at the user's configured [AppSettings.dayBoundaryHour].
 *
 * SPEC Section 4.3 & 8.7.
 */
object DayBoundaryScheduler {

    var alarmGateway: AlarmGateway = SystemAlarmGateway()

    /**
     * Schedules the next day boundary alarm using the configured dayBoundaryHour from [settingsRepository].
     */
    suspend fun scheduleNext(
        context: Context,
        settingsRepository: SettingsRepository,
        clock: AppClock
    ) {
        val settings = settingsRepository.getSettings().first()
        val nextBoundary = DayBoundaryEngine.nextBoundaryInstant(
            now = clock.instant(),
            zone = clock.zoneId(),
            dayBoundaryHour = settings.dayBoundaryHour
        )
        alarmGateway.scheduleDayBoundaryAlarm(context, nextBoundary.toEpochMilli())
    }

    /**
     * Fire-and-forget wrapper for application startup or UI callers.
     */
    fun scheduleNextAsync(
        context: Context,
        settingsRepository: SettingsRepository,
        clock: AppClock,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ) {
        scope.launch {
            scheduleNext(context, settingsRepository, clock)
        }
    }

    fun cancel(context: Context) {
        alarmGateway.cancelDayBoundaryAlarm(context)
    }

    fun isScheduled(context: Context): Boolean {
        return alarmGateway.isDayBoundaryAlarmScheduled(context)
    }
}
