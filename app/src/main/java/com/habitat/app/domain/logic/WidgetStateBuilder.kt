package com.habitat.app.domain.logic

import com.habitat.app.domain.AppClock
import com.habitat.app.domain.model.AppSettings
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.LoggedCompletion
import com.habitat.app.domain.model.ScheduleType

/**
 * Widget row for a single habit shown in the Glance widget.
 *
 * @param habitId         Stable identifier for tap actions.
 * @param name            Display name.
 * @param emoji           Emoji icon.
 * @param completionState The completion state logged today (DONE, MINIMUM, SKIPPED), or null if pending.
 */
data class WidgetHabitRow(
    val habitId: String,
    val name: String,
    val emoji: String,
    val completionState: CompletionState? = null
) {
    val isCompleted: Boolean
        get() = completionState == CompletionState.DONE || completionState == CompletionState.MINIMUM

    val isSkipped: Boolean
        get() = completionState == CompletionState.SKIPPED
}

/**
 * Sealed display state returned by [WidgetStateBuilder.build].
 * SPEC Section 8.7.
 */
sealed class WidgetDisplayState {
    /** No active habits exist in the repository (empty, all archived, or all Unknown). */
    object NoHabits : WidgetDisplayState()

    /** Active habits exist but none are due today (all excluded by schedule or quota). */
    object NothingDueToday : WidgetDisplayState()

    /**
     * All due habits were skipped today.
     * Distinct from NothingDueToday so the widget can show a different message.
     */
    data class AllSkipped(val rows: List<WidgetHabitRow>) : WidgetDisplayState()

    /**
     * One or more habits are due today (skipped habits remain in the list).
     *
     * @param rows           All due rows (skipped and completed habits included).
     * @param completedCount Number of due habits completed (DONE or MINIMUM) today.
     * @param totalDueCount  Total number of due habits (skipped habits NOT counted in denominator).
     * @param progressFraction [completedCount] / [totalDueCount], or 0 if [totalDueCount] == 0.
     * @param allCompleted   True when every non-skipped due habit has been completed.
     */
    data class Habits(
        val rows: List<WidgetHabitRow>,
        val completedCount: Int,
        val totalDueCount: Int,
        val progressFraction: Float,
        val allCompleted: Boolean
    ) : WidgetDisplayState()

}

/**
 * Pure-Kotlin builder that converts raw domain data into a [WidgetDisplayState].
 *
 * Rules (SPEC 8.7):
 * - Archived habits and Unknown schedules are excluded entirely; if no active habits remain,
 *   returns [WidgetDisplayState.NoHabits].
 * - "Today" is derived directly from [clock.logicalDate(settings.dayBoundaryHour)].
 * - A habit is **due** if it is scheduled today ([ScheduleEvaluator.isScheduledOn]) AND its weekly quota
 *   has not yet been met (![ScheduleEvaluator.isWeeklyQuotaMet]). Non-NPerWeek habits scheduled today are always due.
 * - Due habits stay in rows even after they are completed or skipped.
 * - Pending rows appear first, then DONE, MINIMUM, and SKIPPED rows; each status follows app-list order
 *   by group ([Habit.groupId] nulls-first), then by [Habit.sortOrder].
 * - Every due habit is returned; the scrollable widget list has no row cap.
 * - SKIPPED habits are NOT counted in [WidgetDisplayState.Habits.totalDueCount] or
 *   [WidgetDisplayState.Habits.completedCount] so they don't inflate the denominator.
 * - The builder is pure Kotlin with no Android dependencies.
 */
object WidgetStateBuilder {

    /**
     * @param clock       Injected clock to derive the logical date via [AppClock.logicalDate].
     * @param habits      All habits from the repository.
     * @param completions All logged completions.
     * @param settings    App settings containing [AppSettings.dayBoundaryHour] and [AppSettings.startOfWeekDay].
     */
    fun build(
        clock: AppClock,
        habits: List<Habit>,
        completions: List<LoggedCompletion>,
        settings: AppSettings
    ): WidgetDisplayState {
        val activeHabits = habits.filter { !it.archived && it.schedule !is ScheduleType.Unknown }
        if (activeHabits.isEmpty()) {
            return WidgetDisplayState.NoHabits
        }

        val today = clock.logicalDate(settings.dayBoundaryHour)
        val completionsByHabit = completions.groupBy { it.habitId }

        val dueHabits = activeHabits.filter { habit ->
            val habitCompletions = completionsByHabit[habit.id] ?: emptyList()
            ScheduleEvaluator.isScheduledOn(habit.schedule, today) &&
                !ScheduleEvaluator.isWeeklyQuotaMet(
                    schedule = habit.schedule,
                    date = today,
                    startOfWeekDay = settings.startOfWeekDay,
                    completions = habitCompletions
                )
        }

        if (dueHabits.isEmpty()) return WidgetDisplayState.NothingDueToday

        val completionForToday: (Habit) -> CompletionState? = { habit ->
            completionsByHabit[habit.id]?.firstOrNull { it.logicalDate == today }?.state
        }
        val sortedDueHabits = dueHabits.sortedWith(
            compareBy<Habit> {
                when (completionForToday(it)) {
                    null -> 0
                    CompletionState.DONE -> 1
                    CompletionState.MINIMUM -> 2
                    CompletionState.SKIPPED -> 3
                }
            }
                .thenBy { it.groupId ?: "" }
                .thenBy { it.sortOrder }
        )

        val rows = sortedDueHabits.map { habit ->
            WidgetHabitRow(
                habitId = habit.id,
                name = habit.name,
                emoji = habit.emoji,
                completionState = completionForToday(habit)
            )
        }

        val completedCount = sortedDueHabits.count { habit ->
            val state = completionsByHabit[habit.id]
                ?.firstOrNull { it.logicalDate == today }?.state
            state == CompletionState.DONE || state == CompletionState.MINIMUM
        }
        val skippedCount = sortedDueHabits.count { habit ->
            completionsByHabit[habit.id]
                ?.firstOrNull { it.logicalDate == today }?.state == CompletionState.SKIPPED
        }
        val totalDueCount = sortedDueHabits.size - skippedCount
        val allSkipped = skippedCount == sortedDueHabits.size

        if (allSkipped) return WidgetDisplayState.AllSkipped(rows)

        val progressFraction = if (totalDueCount > 0) completedCount.toFloat() / totalDueCount else 0f
        val allCompleted = totalDueCount > 0 && completedCount == totalDueCount

        return WidgetDisplayState.Habits(
            rows = rows,
            completedCount = completedCount,
            totalDueCount = totalDueCount,
            progressFraction = progressFraction,
            allCompleted = allCompleted
        )
    }
}
