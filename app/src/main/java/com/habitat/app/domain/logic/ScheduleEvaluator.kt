package com.habitat.app.domain.logic

import com.habitat.app.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Evaluates whether a habit schedule applies or is due on a given logical date.
 * SPEC Section 4.2.
 */
object ScheduleEvaluator {

    /**
     * Determines if a habit with [schedule] is scheduled on [date].
     *
     * - [ScheduleType.Daily]: always true.
     * - [ScheduleType.Weekdays]: true if [date]'s dayOfWeek is in the set.
     * - [ScheduleType.EveryNDays]: true if [date] is on or after [ScheduleType.EveryNDays.startDate]
     *   and is a multiple of n days from that fixed anchor date.
     * - [ScheduleType.NPerWeek]: true (available all week).
     * - [ScheduleType.Unknown]: false (excluded from scheduling).
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

            is ScheduleType.Unknown -> false
        }
    }

    /**
     * Determines if a habit is due on [date] taking completed occurrences this week into account.
     *
     * For [ScheduleType.NPerWeek]: available all week until [ScheduleType.NPerWeek.n] completions have occurred.
     * If [completionsThisWeek] >= [ScheduleType.NPerWeek.n], it is no longer due this week.
     *
     * SPEC Section 4.2.
     */
    fun isDueOn(
        schedule: ScheduleType,
        date: LocalDate,
        completionsThisWeek: Int = 0
    ): Boolean {
        if (!isScheduledOn(schedule, date)) return false

        return when (schedule) {
            is ScheduleType.NPerWeek -> completionsThisWeek < schedule.n
            else -> true
        }
    }

    /**
     * Helper to find the start date of the week containing [date] given a configurable [startOfWeekDay].
     * SPEC Section 4.2.
     */
    fun startOfWeek(
        date: LocalDate,
        startOfWeekDay: DayOfWeek = DayOfWeek.MONDAY
    ): LocalDate {
        var d = date
        while (d.dayOfWeek != startOfWeekDay) {
            d = d.minusDays(1)
        }
        return d
    }
}
