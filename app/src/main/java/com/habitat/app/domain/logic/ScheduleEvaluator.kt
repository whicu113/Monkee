package com.habitat.app.domain.logic

import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.LoggedCompletion
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

    /** Returns the N_PER_WEEK target for a week, capped by remaining dates in a creation week. */
    fun effectiveNPerWeekTarget(
        schedule: ScheduleType.NPerWeek,
        habitStartDate: LocalDate,
        weekStart: LocalDate
    ): Int {
        val effectiveDays = (0..6L).count { weekStart.plusDays(it) >= habitStartDate }
        return minOf(schedule.n, effectiveDays)
    }

    /**
     * Returns true if the NPerWeek weekly quota has been met for [schedule] on [date],
     * using only DONE and MINIMUM completions (SKIPPED does not count toward the quota).
     *
     * For non-NPerWeek schedules always returns false (quota concept does not apply).
     *
     * This is the single canonical quota implementation shared by ReminderFulfillmentHandler,
     * HabitViewModel and WidgetStateBuilder. SPEC Section 4.2.
     *
     * @param schedule          The habit's schedule type.
     * @param date              The logical date to evaluate quota for.
     * @param startOfWeekDay    The configured week start day (from AppSettings).
     * @param completions       All logged completions for the habit.
     *                          Only DONE and MINIMUM count; SKIPPED is excluded.
     */
    fun isWeeklyQuotaMet(
        schedule: ScheduleType,
        date: LocalDate,
        startOfWeekDay: DayOfWeek,
        completions: List<LoggedCompletion>
    ): Boolean {
        if (schedule !is ScheduleType.NPerWeek) return false
        val weekStart = startOfWeek(date, startOfWeekDay)
        val weekEnd = weekStart.plusDays(6)
        val completionsThisWeek = completions.count { comp ->
            comp.logicalDate in weekStart..weekEnd &&
                (comp.state == CompletionState.DONE || comp.state == CompletionState.MINIMUM)
        }
        return !isDueOn(schedule, date, completionsThisWeek)
    }
}
