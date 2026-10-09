package com.habitat.app.domain.model

import com.habitat.app.domain.GameConfig
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * App settings domain model.
 * SPEC Section 8.1 & 8.6.
 */
data class AppSettings(
    val dayBoundaryHour: Int = 4,
    val quietHoursEnabled: Boolean = true,
    val quietHoursStart: LocalTime = LocalTime.of(22, 0),
    val quietHoursEnd: LocalTime = LocalTime.of(7, 0),
    val snoozeLengthMinutes: Int = GameConfig.DEFAULT_SNOOZE_LENGTH_MINUTES,
    val strictStreaks: Boolean = false,
    val autoUseRestTokens: Boolean = true,
    val startOfWeekDay: DayOfWeek = DayOfWeek.MONDAY
)
