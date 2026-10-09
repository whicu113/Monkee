package com.example.monkee.domain.model

/**
 * Difficulty tier for a habit, impacting reward multipliers.
 * SPEC Section 4.1 & 6.2.
 */
enum class Difficulty(val multiplier: Double) {
    EASY(1.0),
    MEDIUM(1.5),
    HARD(2.0)
}
