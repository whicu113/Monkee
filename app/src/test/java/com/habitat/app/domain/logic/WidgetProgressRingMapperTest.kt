package com.habitat.app.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Table-driven tests for [WidgetProgressRingMapper.progressRingIndex].
 * SPEC Section 8.7.
 */
class WidgetProgressRingMapperTest {

    data class TestCase(
        val fraction: Float,
        val expectedIndex: Int,
        val description: String
    )

    private val testCases = listOf(
        TestCase(0.0f, 0, "0.0 fraction maps to index 0"),
        TestCase(0.04f, 0, "0.04 fraction rounds down to index 0"),
        TestCase(0.05f, 1, "0.05 fraction rounds up to index 1"),
        TestCase(0.14f, 1, "0.14 fraction rounds down to index 1"),
        TestCase(0.15f, 2, "0.15 fraction rounds up to index 2"),
        TestCase(0.50f, 5, "0.50 fraction maps to index 5"),
        TestCase(0.94f, 9, "0.94 fraction rounds down to index 9"),
        TestCase(0.95f, 10, "0.95 fraction rounds up to index 10"),
        TestCase(1.00f, 10, "1.00 fraction maps to index 10"),
        // Clamping edge cases
        TestCase(-0.01f, 0, "Negative fraction clamps to 0"),
        TestCase(-1.00f, 0, "Large negative fraction clamps to 0"),
        TestCase(1.01f, 10, "Fraction > 1.0 clamps to 10"),
        TestCase(2.50f, 10, "Large positive fraction clamps to 10")
    )

    @Test
    fun `table-driven progressRingIndex tests verify all required fraction points and clamping`() {
        for (case in testCases) {
            val actual = WidgetProgressRingMapper.progressRingIndex(case.fraction)
            assertEquals(case.description, case.expectedIndex, actual)
        }
    }
}
