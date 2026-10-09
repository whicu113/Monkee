package com.habitat.app.domain.logic

import com.habitat.app.domain.GameConfig
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.LoggedCompletion
import com.habitat.app.domain.model.OccurrenceState
import com.habitat.app.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min

/**
 * Derived replay calculation result.
 * SPEC Section 4 & 5.
 */
data class ReplayResult(
    val habitId: String,
    val currentStreak: Int,
    val consistencyScore: Double?,
    val availableRestTokens: Int,
    val restTokenProgress: Int,
    val occurrenceStates: Map<LocalDate, OccurrenceState>
)

/**
 * Pure domain replay engine that dynamically evaluates past completions,
 * rest tokens, streaks, and consistency scores without writing derived state to DB.
 * SPEC Section 4 & 5.
 */
object ReplayEngine {

    /**
     * Replays historical completions for [habit] up to [currentLogicalDate].
     *
     * @param habit The habit to evaluate.
     * @param loggedCompletions User-logged completion entries for this habit.
     * @param currentLogicalDate The current logical date.
     * @param strictStreaks Whether any MISSED resets streaks immediately.
     * @param autoUseRestTokens Whether available rest tokens auto-protect MISSED days.
     * @param startOfWeekDay Global setting for start of week day.
     * @return Calculated [ReplayResult].
     *
     * SPEC Section 4.4, 5.1, 5.2, 5.3.
     */
    fun evaluate(
        habit: Habit,
        loggedCompletions: List<LoggedCompletion>,
        currentLogicalDate: LocalDate,
        strictStreaks: Boolean = false,
        autoUseRestTokens: Boolean = true,
        startOfWeekDay: DayOfWeek = DayOfWeek.MONDAY
    ): ReplayResult {
        if (habit.schedule is ScheduleType.Unknown) {
            return ReplayResult(
                habitId = habit.id,
                currentStreak = 0,
                consistencyScore = null,
                availableRestTokens = 0,
                restTokenProgress = 0,
                occurrenceStates = emptyMap()
            )
        }

        val completionsByDate = loggedCompletions.associateBy { it.logicalDate }
        val startDate = habit.createdAt.atZone(ZoneId.systemDefault()).toLocalDate()

        val rawStates = buildRawStates(
            habit = habit,
            startDate = startDate,
            currentLogicalDate = currentLogicalDate,
            completionsByDate = completionsByDate,
            startOfWeekDay = startOfWeekDay
        )

        var tokens = 0
        var progress = 0
        val resolvedStates = mutableMapOf<LocalDate, OccurrenceState>()

        // Chronologically evaluate rest token accumulation and spending on MISSED
        val sortedDates = rawStates.keys.sorted()
        for (date in sortedDates) {
            val state = rawStates[date] ?: continue
            when (state) {
                OccurrenceState.DONE, OccurrenceState.MINIMUM -> {
                    resolvedStates[date] = state
                    if (tokens < GameConfig.MAX_REST_TOKENS_PER_HABIT) {
                        progress += 1
                        if (progress == GameConfig.COMPLETIONS_PER_REST_TOKEN) {
                            tokens += 1
                            progress = 0
                        }
                    }
                }
                OccurrenceState.MISSED -> {
                    if (autoUseRestTokens && tokens > 0) {
                        tokens -= 1
                        resolvedStates[date] = OccurrenceState.PROTECTED
                    } else {
                        resolvedStates[date] = OccurrenceState.MISSED
                    }
                }
                else -> {
                    resolvedStates[date] = state
                }
            }
        }

        // Streak calculation with two-miss rule (SPEC 5.2)
        var streak = 0
        var consecutiveMisses = 0

        for (date in sortedDates) {
            val state = resolvedStates[date] ?: continue
            when (state) {
                OccurrenceState.DONE, OccurrenceState.MINIMUM, OccurrenceState.PROTECTED -> {
                    streak += 1
                    consecutiveMisses = 0
                }
                OccurrenceState.SKIPPED -> {
                    // Neutral: no streak length added, no reset, no miss increment
                }
                OccurrenceState.MISSED -> {
                    consecutiveMisses += 1
                    if (strictStreaks || consecutiveMisses >= 2) {
                        streak = 0
                    }
                }
                OccurrenceState.PENDING -> {
                    // Pending does not break or add to streak
                }
            }
        }

        // Consistency score over trailing 30 scheduled occurrences (SPEC 5.1)
        val nonPendingOccurrences = sortedDates
            .mapNotNull { resolvedStates[it] }
            .filter { it != OccurrenceState.PENDING }

        val trailing30 = nonPendingOccurrences.takeLast(30)
        val denominatorOccurrences = trailing30.filter {
            it != OccurrenceState.SKIPPED && it != OccurrenceState.PROTECTED
        }

        val consistencyScore: Double? = if (denominatorOccurrences.isEmpty()) {
            null
        } else {
            val sumWeights = denominatorOccurrences.sumOf { it.weight }
            sumWeights / denominatorOccurrences.size
        }

        return ReplayResult(
            habitId = habit.id,
            currentStreak = streak,
            consistencyScore = consistencyScore,
            availableRestTokens = tokens,
            restTokenProgress = progress,
            occurrenceStates = resolvedStates
        )
    }

    private fun buildRawStates(
        habit: Habit,
        startDate: LocalDate,
        currentLogicalDate: LocalDate,
        completionsByDate: Map<LocalDate, LoggedCompletion>,
        startOfWeekDay: DayOfWeek
    ): Map<LocalDate, OccurrenceState> {
        val rawStates = mutableMapOf<LocalDate, OccurrenceState>()

        when (val schedule = habit.schedule) {
            is ScheduleType.NPerWeek -> {
                var weekStart = ScheduleEvaluator.startOfWeek(startDate, startOfWeekDay)
                val endWeek = ScheduleEvaluator.startOfWeek(currentLogicalDate, startOfWeekDay)

                while (weekStart <= endWeek) {
                    val daysInWeek = (0..6L).map { weekStart.plusDays(it) }

                    // Creation partial week cap
                    val effectiveDays = daysInWeek.filter { it >= startDate }
                    val targetN = min(schedule.n, effectiveDays.size)

                    val loggedInWeek = daysInWeek.mapNotNull { completionsByDate[it] }
                    val completedCount = loggedInWeek.count {
                        it.state == CompletionState.DONE || it.state == CompletionState.MINIMUM
                    }

                    if (weekStart < endWeek) {
                        // Resolved past week: logged entries map to their state
                        for (date in effectiveDays) {
                            val completion = completionsByDate[date]
                            if (completion != null) {
                                rawStates[date] = when (completion.state) {
                                    CompletionState.DONE -> OccurrenceState.DONE
                                    CompletionState.MINIMUM -> OccurrenceState.MINIMUM
                                    CompletionState.SKIPPED -> OccurrenceState.SKIPPED
                                }
                            }
                        }
                        // Unlogged missed count = max(0, targetN - completedCount)
                        val missedCount = (targetN - completedCount).coerceAtLeast(0)
                        val unloggedDays = effectiveDays.filter { completionsByDate[it] == null }
                        for (i in 0 until min(missedCount, unloggedDays.size)) {
                            rawStates[unloggedDays[i]] = OccurrenceState.MISSED
                        }
                    } else {
                        // Current week: logged days are resolved; unlogged are PENDING
                        for (date in effectiveDays) {
                            val completion = completionsByDate[date]
                            if (completion != null) {
                                rawStates[date] = when (completion.state) {
                                    CompletionState.DONE -> OccurrenceState.DONE
                                    CompletionState.MINIMUM -> OccurrenceState.MINIMUM
                                    CompletionState.SKIPPED -> OccurrenceState.SKIPPED
                                }
                            } else {
                                rawStates[date] = OccurrenceState.PENDING
                            }
                        }
                    }

                    weekStart = weekStart.plusWeeks(1)
                }
            }
            else -> {
                var curr = startDate
                while (curr <= currentLogicalDate) {
                    if (ScheduleEvaluator.isScheduledOn(schedule, curr)) {
                        val completion = completionsByDate[curr]
                        if (completion != null) {
                            rawStates[curr] = when (completion.state) {
                                CompletionState.DONE -> OccurrenceState.DONE
                                CompletionState.MINIMUM -> OccurrenceState.MINIMUM
                                CompletionState.SKIPPED -> OccurrenceState.SKIPPED
                            }
                        } else {
                            if (curr == currentLogicalDate) {
                                rawStates[curr] = OccurrenceState.PENDING
                            } else {
                                rawStates[curr] = OccurrenceState.MISSED
                            }
                        }
                    }
                    curr = curr.plusDays(1)
                }
            }
        }

        return rawStates
    }

    /**
     * Computes whether a newly logged completion qualifies for a Comeback Bonus.
     * SPEC Section 5.4.
     * Computed at log time; PROTECTED does not trigger comeback bonus.
     */
    fun isComebackBonus(
        resolvedStates: Map<LocalDate, OccurrenceState>,
        loggedDate: LocalDate
    ): Boolean {
        val previousDates = resolvedStates.keys
            .filter { it < loggedDate && resolvedStates[it] != OccurrenceState.PENDING }
            .sortedDescending()

        val last1Or2 = previousDates.take(2)
        return last1Or2.any { resolvedStates[it] == OccurrenceState.MISSED }
    }
}
