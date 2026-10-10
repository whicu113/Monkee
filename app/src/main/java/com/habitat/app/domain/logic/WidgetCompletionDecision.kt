package com.habitat.app.domain.logic

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
    scheduleUnknown: Boolean
): WidgetCompletionDecision = if (
    renderedDate == currentLogicalDate &&
    !alreadyLogged &&
    habitExists &&
    !habitArchived &&
    !scheduleUnknown
) {
    WidgetCompletionDecision.LOG_AND_REFRESH
} else {
    WidgetCompletionDecision.REFRESH_ONLY
}
