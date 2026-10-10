package com.habitat.app.domain.logic

import com.habitat.app.domain.model.AppSettings
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.LoggedCompletion
import com.habitat.app.domain.model.ScheduleType
import java.time.LocalDate

/**
 * Widget row for a single habit shown in the Glance widget.
 *
 * @param habitId   Stable identifier for tap actions.
 * @param name      Display name.
 * @param emoji     Emoji icon.
 * @param isCompleted True if DONE or MINIMUM logged today.
 * @param isSkipped   True if SKIPPED logged today.
 */
data class WidgetHabitRow(
    val habitId: String,
    val name: String,
    val emoji: String,
    val isCompleted: Boolean,
    val isSkipped: Boolean
)

/**
 * Sealed display state returned by [WidgetStateBuilder.build].
 * SPEC Section 8.7.
 */
sealed class WidgetDisplayState {
    /** No habits exist in the repository. */
    object NoHabits : WidgetDisplayState()

    /** Habits exist but none are due today (all excluded by schedule, quota, archive, or Unknown). */
    object NothingDueToday : WidgetDisplayState()

    /**
     * All due habits were skipped today.
     * Distinct from NothingDueToday so the widget can show a different message.
     */
    object AllSkipped : WidgetDisplayState()

    /**
     * One or more habits are due today (skipped habits remain in the list).
     *
     * @param rows           Up to [ROW_CAP] rows to render (skipped and completed habits included).
     * @param remainingCount Number of due habits beyond the cap (shown as "+N more").
     * @param completedCount Number of due habits completed (DONE or MINIMUM) today.
     * @param totalDueCount  Total number of due habits (skipped habits NOT counted in denominator).
     * @param progressFraction [completedCount] / [totalDueCount], or 0 if [totalDueCount] == 0.
     * @param allCompleted   True when every non-skipped due habit has been completed.
     */
    data class Habits(
        val rows: List<WidgetHabitRow>,
        val remainingCount: Int,
        val completedCount: Int,
        val totalDueCount: Int,
        val progressFraction: Float,
        val allCompleted: Boolean
    ) : WidgetDisplayState()

    companion object {
        const val ROW_CAP = 5
    }
}

/**
 * Pure-Kotlin builder that converts raw domain data into a [WidgetDisplayState].
 *
 * Rules (SPEC 8.7):
 * - Archived habits and Unknown schedules are excluded entirely.
 * - A habit is **due** if it is scheduled today (via [ScheduleEvaluator.isDueOn]) AND, for
 *   NPerWeek, the weekly quota has not yet been met (via [ScheduleEvaluator.isWeeklyQuotaMet]).
 *   Non-NPerWeek habits that are scheduled today are always due.
 * - Due habits stay in rows even after they are completed or skipped.
 * - SKIPPED habits are NOT counted in [WidgetDisplayState.Habits.totalDueCount] or
 *   [WidgetDisplayState.Habits.completedCount] so they don't inflate the denominator.
 * - Rows are capped at [WidgetDisplayState.ROW_CAP]; excess is reported as remainingCount.
 * - The builder is stateless and has no Android imports; it accepts a pre-computed logical date
 *   from the caller (who obtains it via [com.habitat.app.domain.AppClock.logicalDate]).
 */
object WidgetStateBuilder {

    /**
     * @param today       Logical date from the injected clock (04:00 boundary already applied).
     * @param habits      All active and archived habits from the repository.
     * @param completions All logged completions (any date, any habit).
     * @param settings    App settings containing [AppSettings.startOfWeekDay].
     */
    fun build(
        today: LocalDate,
        habits: List<Habit>,
        completions: List<LoggedCompletion>,
        settings: AppSettings
    ): WidgetDisplayState {
        // Filter out archived and Unknown schedules
        val activeHabits = habits.filter { !it.archived && it.schedule !is ScheduleType.Unknown }

        if (activeHabits.isEmpty()) {
            return if (habits.isEmpty()) WidgetDisplayState.NoHabits
            else WidgetDisplayState.NothingDueToday
        }

        // Pre-index completions by habitId for O(1) lookup
        val completionsByHabit = completions.groupBy { it.habitId }

        // Determine which habits are due today
        val dueHabits = activeHabits.filter { habit ->
            val habitCompletions = completionsByHabit[habit.id] ?: emptyList()
            val completionsThisWeek = weeklyDoneMinimumCount(
                habitCompletions, today, settings
            )
            ScheduleEvaluator.isDueOn(habit.schedule, today, completionsThisWeek)
        }

        if (dueHabits.isEmpty()) return WidgetDisplayState.NothingDueToday

        // Build rows; due habits stay even when completed or skipped
        val rows = dueHabits.take(WidgetDisplayState.ROW_CAP).map { habit ->
            val todayComp = completionsByHabit[habit.id]
                ?.firstOrNull { it.logicalDate == today }
            WidgetHabitRow(
                habitId = habit.id,
                name = habit.name,
                emoji = habit.emoji,
                isCompleted = todayComp?.state == CompletionState.DONE ||
                    todayComp?.state == CompletionState.MINIMUM,
                isSkipped = todayComp?.state == CompletionState.SKIPPED
            )
        }
        val remainingCount = (dueHabits.size - WidgetDisplayState.ROW_CAP).coerceAtLeast(0)

        // totalDueCount and completedCount exclude SKIPPED
        val completedCount = dueHabits.count { habit ->
            val todayComp = completionsByHabit[habit.id]
                ?.firstOrNull { it.logicalDate == today }
            todayComp?.state == CompletionState.DONE || todayComp?.state == CompletionState.MINIMUM
        }
        val skippedCount = dueHabits.count { habit ->
            completionsByHabit[habit.id]
                ?.firstOrNull { it.logicalDate == today }?.state == CompletionState.SKIPPED
        }
        val totalDueCount = dueHabits.size - skippedCount
        val allSkipped = skippedCount == dueHabits.size

        if (allSkipped) return WidgetDisplayState.AllSkipped

        val progressFraction = if (totalDueCount > 0) completedCount.toFloat() / totalDueCount else 0f
        val allCompleted = totalDueCount > 0 && completedCount == totalDueCount

        return WidgetDisplayState.Habits(
            rows = rows,
            remainingCount = remainingCount,
            completedCount = completedCount,
            totalDueCount = totalDueCount,
            progressFraction = progressFraction,
            allCompleted = allCompleted
        )
    }

    /**
     * Counts DONE + MINIMUM completions for [habit] within the current week
     * (needed to pass into [ScheduleEvaluator.isDueOn] for NPerWeek habits).
     */
    private fun weeklyDoneMinimumCount(
        habitCompletions: List<LoggedCompletion>,
        today: LocalDate,
        settings: AppSettings
    ): Int {
        val weekStart = ScheduleEvaluator.startOfWeek(today, settings.startOfWeekDay)
        val weekEnd = weekStart.plusDays(6)
        return habitCompletions.count { comp ->
            comp.logicalDate in weekStart..weekEnd &&
                (comp.state == CompletionState.DONE || comp.state == CompletionState.MINIMUM)
        }
    }
}
