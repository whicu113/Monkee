package com.example.monkee.domain.logic

import com.example.monkee.domain.model.ScheduleType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Evaluates whether a habit schedule applies on a given logical date.
 * SPEC Section 4.2.
 */
object ScheduleEvaluator {

    /**
     * Determines if a habit with [schedule] is scheduled on [date].
     *
     * @param schedule The habit recurrence schedule.
     * @param date The logical date to evaluate.
     * @return true if the habit is scheduled on [date]; false otherwise.
     *
     * SPEC Section 4.2.
     */
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
