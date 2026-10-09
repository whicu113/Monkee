package com.habitat.app.domain.logic

import com.habitat.app.domain.model.ScheduleType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object ScheduleEvaluator {

    fun isScheduledOn(schedule: ScheduleType, date: LocalDate): Boolean {
        return when (schedule) {
            is ScheduleType.Daily -> true
            is ScheduleType.Weekdays -> date.dayOfWeek in schedule.days
            is ScheduleType.EveryNDays -> {
                date >= schedule.startDate && ((ChronoUnit.DAYS.between(schedule.startDate, date) % schedule.n) == 0L)
            }
            is ScheduleType.NPerWeek -> true
        }
    }
}
