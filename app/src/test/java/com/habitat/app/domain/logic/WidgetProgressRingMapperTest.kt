package com.habitat.app.domain.logic

import com.habitat.app.domain.model.CompletionState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Table-driven tests for [WidgetProgressRingMapper.progressRingIndex].
 * SPEC Section 8.7.
 */
class WidgetProgressRingMapperTest {

    private data class TestCase(
        val state: WidgetDisplayState,
        val expectedIndex: Int?,
        val description: String
    )

    private val testCases = listOf(
        TestCase(habitsState(0.0f), 0, "0.0 fraction maps to index 0"),
        TestCase(habitsState(Math.nextUp(0.0f)), 0, "fraction just above zero maps to index 0"),
        TestCase(habitsState(0.5f), 5, "0.5 fraction maps to index 5"),
        TestCase(habitsState(Math.nextDown(1.0f)), 10, "fraction just below one maps to index 10"),
        TestCase(habitsState(1.0f), 10, "1.0 fraction maps to index 10"),
        TestCase(habitsState(-0.01f), 0, "negative fraction clamps to index 0"),
        TestCase(habitsState(1.01f), 10, "fraction above one clamps to index 10"),
        TestCase(WidgetDisplayState.NothingDueToday, null, "nothing due uses the neutral ring"),
        TestCase(
            WidgetDisplayState.AllSkipped(listOf(WidgetHabitRow("habit-1", "Read", "📖", CompletionState.SKIPPED))),
            null,
            "all skipped uses the neutral ring"
        ),
        TestCase(WidgetDisplayState.NoHabits, null, "no habits has no progress ring")
    )

    @Test
    fun `table driven ring selection covers progress boundaries and neutral states`() {
        for (case in testCases) {
            val actual = WidgetProgressRingMapper.ringIndex(case.state)
            assertEquals(case.description, case.expectedIndex, actual)
        }
    }

    private fun habitsState(fraction: Float) = WidgetDisplayState.Habits(
        rows = listOf(WidgetHabitRow("habit-1", "Read", "📖", CompletionState.DONE)),
        completedCount = 1,
        totalDueCount = 1,
        progressFraction = fraction,
        allCompleted = fraction >= 1f
    )
}
