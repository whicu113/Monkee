package com.habitat.app.domain.logic

import kotlin.math.roundToInt

/**
 * Pure domain logic mapping completion progress fraction (0.0f..1.0f) to a ring drawable index (0..10).
 * SPEC Section 8.7.
 */
object WidgetProgressRingMapper {

    /**
     * Converts a completion progress [fraction] to an index in 0..10 corresponding
     * to ring drawables 0% to 100% in 10% steps.
     *
     * Values < 0.0f are clamped to 0. Values > 1.0f are clamped to 10.
     * Standard rounding is applied:
     * - 0.00f -> 0
     * - 0.04f -> 0
     * - 0.05f -> 1
     * - 0.50f -> 5
     * - 0.95f -> 10
     * - 1.00f -> 10
     */
    fun progressRingIndex(fraction: Float): Int {
        val clamped = fraction.coerceIn(0f, 1f)
        return (clamped * 10f).roundToInt().coerceIn(0, 10)
    }
}
