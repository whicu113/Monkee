package com.habitat.app.domain

/**
 * Game configuration holding default starting values and tunable constants.
 * SPEC Section 1 & 4.3 & 8.1.
 */
object GameConfig {
    /** Maximum successful build edits retained in a single session. */
    const val BUILD_MODE_HISTORY_LIMIT: Int = 100

    /** Default first island dimensions. SPEC 7.1. */
    const val STARTER_ISLAND_ID: String = "starter"
    const val STARTER_ISLAND_WIDTH: Int = 8
    const val STARTER_ISLAND_HEIGHT: Int = 8

    /** Default logical day boundary hour (04:00 local time). SPEC 4.3 */
    const val DEFAULT_DAY_BOUNDARY_HOUR: Int = 4

    /** Maximum allowed habit seeds per logical day. SPEC 6.3 */
    const val DAILY_HABIT_SEEDS_CAP: Int = 300

    const val HABIT_SEEDS_BASE: Int = 10
    const val MINIMUM_SEEDS_FACTOR: Double = 0.6
    const val DONE_SEEDS_FACTOR: Double = 1.0
    const val COMEBACK_SEEDS_MULTIPLIER: Double = 1.5
    const val MAX_STREAK_SEEDS_BONUS: Int = 10
    const val HABIT_XP_PER_STAGE: Int = 10
    const val XP_TO_NEXT_LEVEL_BASE: Long = 100L
    const val XP_TO_NEXT_LEVEL_STEP: Long = 25L
    const val HABIT_DROP_CHANCE_PERCENT: Int = 5
    const val HABIT_DROP_PITY_COUNT: Int = 30
    const val DROP_RARITY_COMMON_WEIGHT: Int = 70
    const val DROP_RARITY_UNCOMMON_WEIGHT: Int = 22
    const val DROP_RARITY_RARE_WEIGHT: Int = 7
    const val DROP_RARITY_LEGENDARY_WEIGHT: Int = 1

    /** Maximum allowed focus seeds per logical day. SPEC 6.3 */
    const val DAILY_FOCUS_SEEDS_CAP: Int = 120

    /** Maximum allowed focus XP per logical day. SPEC 6.3 */
    const val DAILY_FOCUS_XP_CAP: Int = 120

    /** Cap for max rest tokens per habit. SPEC 5.3 */
    const val MAX_REST_TOKENS_PER_HABIT: Int = 3

    /** Number of completions required to earn 1 rest token. SPEC 5.3 */
    const val COMPLETIONS_PER_REST_TOKEN: Int = 7

    /** Maximum allowed lateness (in minutes) for delivering a reminder before dropping it as stale. SPEC 8.1 */
    const val MAX_REMINDER_LATENESS_MINUTES: Long = 120L

    /** Default reminder snooze length in minutes. SPEC 8.1 */
    const val DEFAULT_SNOOZE_LENGTH_MINUTES: Int = 30
}
