package com.habitat.app.domain.model

/**
 * User-logged completion states.
 * SPEC Section 4.4.
 */
enum class CompletionState(val weight: Double) {
    DONE(1.0),
    MINIMUM(0.7),
    SKIPPED(0.0)
}

/**
 * Derived occurrence state during replay evaluation.
 * SPEC Section 4.4.
 */
enum class OccurrenceState(val countsForConsistency: Boolean, val weight: Double) {
    DONE(true, 1.0),
    MINIMUM(true, 0.7),
    SKIPPED(false, 0.0),
    MISSED(true, 0.0),
    PROTECTED(false, 0.0),
    PENDING(false, 0.0)
}
