package com.habitat.app.domain.logic

import com.habitat.app.domain.model.LoggedCompletion
import com.habitat.app.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalDate

enum class WidgetCompletionDecision {
    LOG_AND_REFRESH,
    REFRESH_ONLY
}

/** Pure gate for completion actions carrying the date the widget displayed. */
fun widgetCompletionDecision(
    renderedDate: LocalDate,
    currentLogicalDate: LocalDate,
    alreadyLogged: Boolean,
    habitExists: Boolean,
    habitArchived: Boolean,
    schedule: ScheduleType?,
    startOfWeekDay: DayOfWeek,
    habitCompletions: List<LoggedCompletion>
): WidgetCompletionDecision {
    if (
        renderedDate != currentLogicalDate || alreadyLogged || !habitExists || habitArchived ||
        schedule == null || schedule is ScheduleType.Unknown
    ) {
        return WidgetCompletionDecision.REFRESH_ONLY
    }
    if (!ScheduleEvaluator.isScheduledOn(schedule, currentLogicalDate)) {
        return WidgetCompletionDecision.REFRESH_ONLY
    }
    if (
        ScheduleEvaluator.isWeeklyQuotaMet(
            schedule = schedule,
            date = currentLogicalDate,
            startOfWeekDay = startOfWeekDay,
            completions = habitCompletions
        )
    ) {
        return WidgetCompletionDecision.REFRESH_ONLY
    }
    return WidgetCompletionDecision.LOG_AND_REFRESH
}
