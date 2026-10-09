package com.habitat.app.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

sealed interface ScheduleType {
    data object Daily : ScheduleType

    data class Weekdays(val days: Set<DayOfWeek>) : ScheduleType

    data class EveryNDays(val n: Int, val startDate: LocalDate) : ScheduleType {
        init {
            require(n > 0) { "Interval 'n' must be greater than 0" }
        }
    }

    data class NPerWeek(
        val n: Int,
        val startOfWeekDay: DayOfWeek = DayOfWeek.MONDAY
    ) : ScheduleType {
        init {
            require(n in 1..7) { "Target count 'n' must be between 1 and 7" }
        }
    }
}
