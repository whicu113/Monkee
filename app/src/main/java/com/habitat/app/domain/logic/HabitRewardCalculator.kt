package com.habitat.app.domain.logic

import com.habitat.app.domain.GameConfig
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.Difficulty
import java.time.LocalDate

/** Reward stage whose entitlement has already been applied for a habit and logical date. */
enum class RewardStage {
    MINIMUM,
    DONE
}

enum class RewardCurrency {
    SEEDS,
    XP
}

/** Builds the stable per-habit, logical-date, reward-stage idempotency key. */
fun rewardIdempotencyKey(
    habitId: String,
    logicalDate: LocalDate,
    stage: RewardStage,
    currency: RewardCurrency
): String = "$habitId|$logicalDate|${stage.name}|${currency.name}"

/** Reward inputs fixed when the first stage for a habit/date was granted. */
data class RewardBasis(val difficulty: Difficulty, val streakLength: Int, val comeback: Boolean)

/** Actual ledger amounts and immutable calculation basis already saved for a reward stage. */
data class RewardAmounts(val seeds: Int, val xp: Int, val basis: RewardBasis)

/** Bounded random source injected so drop decisions can be deterministic in tests. */
fun interface RewardRandom {
    /** Returns an integer in [0, boundExclusive). */
    fun nextInt(boundExclusive: Int): Int
}

/** Drop rarity selected by the SPEC 6.5 weighted table. */
enum class DropRarity {
    COMMON,
    UNCOMMON,
    RARE,
    LEGENDARY
}

/** Computed reward amounts and updated global pity state. */
data class RewardCalculation(
    val seedsGranted: Int,
    val xpGranted: Int,
    val drop: DropRarity?,
    val pityCounterAfter: Int,
    val grantedAmounts: RewardAmounts?
)

/**
 * Computes the unspent entitlement delta for a logged stage and applies daily habit caps.
 * Inputs for streak and comeback must be those stored with the first grant for this habit/date.
 * SPEC Sections 5.4 and 6.2 to 6.5.
 */
fun calculateHabitReward(
    state: CompletionState,
    difficulty: Difficulty,
    streakLength: Int,
    comeback: Boolean,
    grantedByStage: Map<RewardStage, RewardAmounts>,
    seedsEarnedToday: Int,
    rng: RewardRandom,
    pityCounter: Int
): RewardCalculation {
    if (state == CompletionState.SKIPPED) {
        return RewardCalculation(0, 0, null, pityCounter.coerceAtLeast(0), null)
    }

    val stage = if (state == CompletionState.DONE) RewardStage.DONE else RewardStage.MINIMUM
    val normalizedPity = pityCounter.coerceAtLeast(0)
    if (stage in grantedByStage) {
        return RewardCalculation(0, 0, null, normalizedPity, null)
    }

    val priorGrant = grantedByStage[RewardStage.MINIMUM] ?: grantedByStage[RewardStage.DONE]
    val basis = priorGrant?.basis ?: RewardBasis(difficulty, streakLength, comeback)
    val currentEntitlement = entitlement(state, basis.difficulty, basis.streakLength, basis.comeback)
    val previousEntitlement = when {
        RewardStage.DONE in grantedByStage -> entitlement(CompletionState.DONE, basis.difficulty, basis.streakLength, basis.comeback)
        RewardStage.MINIMUM in grantedByStage -> entitlement(CompletionState.MINIMUM, basis.difficulty, basis.streakLength, basis.comeback)
        else -> RewardAmounts(0, 0, basis)
    }

    val seedDelta = (currentEntitlement.seeds - previousEntitlement.seeds).coerceAtLeast(0)
    val xpDelta = (currentEntitlement.xp - previousEntitlement.xp).coerceAtLeast(0)
    val seedsRoom = (GameConfig.DAILY_HABIT_SEEDS_CAP - seedsEarnedToday).coerceAtLeast(0)

    val drop = if (state == CompletionState.DONE) rollDrop(rng, normalizedPity) else null
    val pityAfter = if (drop == null && state == CompletionState.DONE) normalizedPity + 1
    else if (drop != null) 0 else normalizedPity

    val seedsGranted = minOf(seedDelta, seedsRoom)
    val xpGranted = xpDelta
    return RewardCalculation(
        seedsGranted = seedsGranted,
        xpGranted = xpGranted,
        drop = drop,
        pityCounterAfter = pityAfter,
        grantedAmounts = RewardAmounts(seedsGranted, xpGranted, basis)
    )
}

private fun entitlement(
    state: CompletionState,
    difficulty: Difficulty,
    streakLength: Int,
    comeback: Boolean
): RewardAmounts {
    val stateFactor = if (state == CompletionState.DONE) GameConfig.DONE_SEEDS_FACTOR else GameConfig.MINIMUM_SEEDS_FACTOR
    val baseRounded = roundHalfUp(GameConfig.HABIT_SEEDS_BASE * difficulty.multiplier * stateFactor)
    val streakBonus = streakLength.coerceAtLeast(0).coerceAtMost(GameConfig.MAX_STREAK_SEEDS_BONUS)
    val beforeComeback = baseRounded + streakBonus
    val seeds = if (comeback) roundHalfUp(beforeComeback * GameConfig.COMEBACK_SEEDS_MULTIPLIER) else beforeComeback
    return RewardAmounts(seeds, GameConfig.HABIT_XP_PER_STAGE, RewardBasis(difficulty, streakLength, comeback))
}

private fun rollDrop(rng: RewardRandom, pityCounter: Int): DropRarity? {
    if (pityCounter >= GameConfig.HABIT_DROP_PITY_COUNT || rng.nextInt(100) < GameConfig.HABIT_DROP_CHANCE_PERCENT) {
        val total = GameConfig.DROP_RARITY_COMMON_WEIGHT + GameConfig.DROP_RARITY_UNCOMMON_WEIGHT +
            GameConfig.DROP_RARITY_RARE_WEIGHT + GameConfig.DROP_RARITY_LEGENDARY_WEIGHT
        val roll = rng.nextInt(total)
        return when {
            roll < GameConfig.DROP_RARITY_COMMON_WEIGHT -> DropRarity.COMMON
            roll < GameConfig.DROP_RARITY_COMMON_WEIGHT + GameConfig.DROP_RARITY_UNCOMMON_WEIGHT -> DropRarity.UNCOMMON
            roll < GameConfig.DROP_RARITY_COMMON_WEIGHT + GameConfig.DROP_RARITY_UNCOMMON_WEIGHT +
                GameConfig.DROP_RARITY_RARE_WEIGHT -> DropRarity.RARE
            else -> DropRarity.LEGENDARY
        }
    }
    return null
}

private fun roundHalfUp(value: Double): Int = kotlin.math.floor(value + 0.5).toInt()
