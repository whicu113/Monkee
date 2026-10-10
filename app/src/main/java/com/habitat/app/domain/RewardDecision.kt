package com.habitat.app.domain

import com.habitat.app.domain.logic.DropRarity
import com.habitat.app.domain.logic.ScheduleEvaluator
import com.habitat.app.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalDate

enum class RewardNoRewardReason {
    NOT_SCHEDULED,
    BACKFILL,
    UNKNOWN_SCHEDULE,
    WEEKLY_GOAL_REACHED,
    ALREADY_REWARDED
}

sealed interface HabitRewardResult {
    data class Granted(
        val seeds: Int,
        val xp: Int,
        val drop: DropRarity?,
        val comebackApplied: Boolean,
        val capApplied: Int
    ) : HabitRewardResult {
        init {
            require(seeds >= 0)
            require(xp >= 0)
            require(capApplied >= 0)
        }
    }

    data class NoReward(val reason: RewardNoRewardReason) : HabitRewardResult
}

/** Returns the enum reason when a first grant is ineligible, or null when it may be granted. */
fun evaluateRewardEligibility(
    schedule: ScheduleType,
    logicalDate: LocalDate,
    today: LocalDate,
    habitCreatedDate: LocalDate,
    startOfWeekDay: DayOfWeek,
    paidLogicalDatesInWeek: Set<LocalDate>,
    eligibilityAlreadyGranted: Boolean = false
): RewardNoRewardReason? {
    if (logicalDate < today) return RewardNoRewardReason.BACKFILL
    require(logicalDate == today) { "Future logical dates are rejected before reward eligibility" }

    // A later stage for this date keeps the first grant's eligibility even if settings changed.
    if (eligibilityAlreadyGranted) return null
    if (schedule is ScheduleType.Unknown) return RewardNoRewardReason.UNKNOWN_SCHEDULE
    if (!ScheduleEvaluator.isScheduledOn(schedule, logicalDate)) {
        return RewardNoRewardReason.NOT_SCHEDULED
    }

    if (schedule is ScheduleType.NPerWeek) {
        val weekStart = ScheduleEvaluator.startOfWeek(logicalDate, startOfWeekDay)
        val weekEnd = weekStart.plusDays(6)
        val paidDates = paidLogicalDatesInWeek
            .asSequence()
            .filter { it != logicalDate && it in weekStart..weekEnd }
            .distinct()
            .count()
        val effectiveTarget = ScheduleEvaluator.effectiveNPerWeekTarget(
            schedule = schedule,
            habitStartDate = habitCreatedDate,
            weekStart = weekStart
        )
        if (paidDates >= effectiveTarget) return RewardNoRewardReason.WEEKLY_GOAL_REACHED
    }
    return null
}

/** Builds a copy-free result from the reward and eligibility decisions. */
fun rewardResult(
    noRewardReason: RewardNoRewardReason?,
    alreadyRewarded: Boolean,
    seeds: Int,
    xp: Int,
    drop: DropRarity?,
    comebackApplied: Boolean,
    capApplied: Int
): HabitRewardResult {
    if (alreadyRewarded) return HabitRewardResult.NoReward(RewardNoRewardReason.ALREADY_REWARDED)
    if (noRewardReason != null) return HabitRewardResult.NoReward(noRewardReason)
    return HabitRewardResult.Granted(
        seeds = seeds,
        xp = xp,
        drop = drop,
        comebackApplied = comebackApplied,
        capApplied = capApplied
    )
}
