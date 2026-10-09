package com.example.monkee.domain

/**
 * Game configuration holding default starting values and tunable constants.
 * SPEC Section 1 & 4.3.
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
}
