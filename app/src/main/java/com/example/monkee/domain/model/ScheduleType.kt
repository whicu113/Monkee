package com.example.monkee.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Habit recurrence schedule types.
 * SPEC Section 4.2.
 */
sealed interface ScheduleType {
    /** Repeat every day. */
    data object Daily : ScheduleType

    /** Repeat on specific days of the week. */
    data class Weekdays(val days: Set<DayOfWeek>) : ScheduleType

    /** Repeat every [n] days starting from [startDate]. */
    data class EveryNDays(val n: Int, val startDate: LocalDate) : ScheduleType {
        init {
            require(n > 0) { "Interval 'n' must be greater than 0" }
        }
    }

    /** Repeat [n] times per week starting on [startOfWeekDay]. */
    data class NPerWeek(
        val n: Int,
        val startOfWeekDay: DayOfWeek = DayOfWeek.MONDAY
    ) : ScheduleType {
        init {
            require(n in 1..7) { "Target count 'n' must be between 1 and 7" }
        }
    }
}
