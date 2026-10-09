package com.habitat.app.domain

/**
 * Game configuration holding default starting values and tunable constants.
 * SPEC Section 1 & 4.3 & 8.1.
 */
object GameConfig {
    /** Default logical day boundary hour (04:00 local time). SPEC 4.3 */
    const val DEFAULT_DAY_BOUNDARY_HOUR: Int = 4

    /** Maximum allowed habit seeds per logical day. SPEC 6.3 */
    const val DAILY_HABIT_SEEDS_CAP: Int = 300

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
