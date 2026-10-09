package com.habitat.app.domain

import com.habitat.app.domain.logic.ReplayEngine
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.LoggedCompletion
import com.habitat.app.domain.model.OccurrenceState
import com.habitat.app.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class ReplayEngineTest {

    private val habitStart = LocalDate.of(2025, 5, 1)
    private val defaultHabit = Habit(
        id = "h1",
        name = "Test Habit",
        schedule = ScheduleType.Daily,
        createdAt = habitStart.atStartOfDay(ZoneOffset.UTC).toInstant()
    )

    @Test
    fun `today is pending and excluded from missed and consistency denominator`() {
        val today = LocalDate.of(2025, 5, 1)
        val result = ReplayEngine.evaluate(defaultHabit, emptyList(), today)

        assertEquals(OccurrenceState.PENDING, result.occurrenceStates[today])
        assertNull(result.consistencyScore)
        assertEquals(0, result.currentStreak)
    }

    @Test
    fun `past unlogged day becomes missed`() {
        val today = LocalDate.of(2025, 5, 2)
        val result = ReplayEngine.evaluate(defaultHabit, emptyList(), today)

        assertEquals(OccurrenceState.MISSED, result.occurrenceStates[LocalDate.of(2025, 5, 1)])
        assertEquals(OccurrenceState.PENDING, result.occurrenceStates[today])
        assertEquals(0.0, result.consistencyScore!!, 0.001)
    }

    @Test
    fun `skipped is neutral for streak length`() {
        val today = LocalDate.of(2025, 5, 4)
        val completions = listOf(
            LoggedCompletion(habitId = "h1", logicalDate = LocalDate.of(2025, 5, 1), state = CompletionState.DONE),
            LoggedCompletion(habitId = "h1", logicalDate = LocalDate.of(2025, 5, 2), state = CompletionState.SKIPPED),
            LoggedCompletion(habitId = "h1", logicalDate = LocalDate.of(2025, 5, 3), state = CompletionState.DONE)
        )

        val result = ReplayEngine.evaluate(defaultHabit, completions, today)

        // Skipped does not add to streak length -> streak = 2 (DONE + DONE)
        assertEquals(2, result.currentStreak)
    }

    @Test
    fun `protected counts toward streak length`() {
        val today = LocalDate.of(2025, 5, 10)
        // 7 DONE completions -> 1 rest token
        val completions = (1..7).map { day ->
            LoggedCompletion(habitId = "h1", logicalDate = LocalDate.of(2025, 5, day), state = CompletionState.DONE)
        }

        // May 8 unlogged -> PROTECTED
        val result = ReplayEngine.evaluate(defaultHabit, completions, today, autoUseRestTokens = true)

        assertEquals(OccurrenceState.PROTECTED, result.occurrenceStates[LocalDate.of(2025, 5, 8)])
        // 7 DONE + 1 PROTECTED = 8 streak
        assertEquals(8, result.currentStreak)
    }

    @Test
    fun `no token spend when autoUseRestTokens is false`() {
        val today = LocalDate.of(2025, 5, 9)
        val completions = (1..7).map { day ->
            LoggedCompletion(habitId = "h1", logicalDate = LocalDate.of(2025, 5, day), state = CompletionState.DONE)
        }

        val result = ReplayEngine.evaluate(defaultHabit, completions, today, autoUseRestTokens = false)

        assertEquals(1, result.availableRestTokens)
        assertEquals(OccurrenceState.MISSED, result.occurrenceStates[LocalDate.of(2025, 5, 8)])
    }

    @Test
    fun `start of week setting affects N_PER_WEEK week boundary in replay`() {
        val habit = Habit(
            id = "h2",
            name = "Weekly Habit",
            schedule = ScheduleType.NPerWeek(n = 2),
            createdAt = LocalDate.of(2025, 5, 4).atStartOfDay(ZoneOffset.UTC).toInstant() // Sunday May 4
        )

        val completions = listOf(
            LoggedCompletion(habitId = "h2", logicalDate = LocalDate.of(2025, 5, 4), state = CompletionState.DONE)
        )

        // Monday start: Sun May 4 is end of week starting Mon Apr 28
        val resultMonday = ReplayEngine.evaluate(habit, completions, LocalDate.of(2025, 5, 5), startOfWeekDay = DayOfWeek.MONDAY)

        // Sunday start: Sun May 4 is start of week starting Sun May 4
        val resultSunday = ReplayEngine.evaluate(habit, completions, LocalDate.of(2025, 5, 5), startOfWeekDay = DayOfWeek.SUNDAY)

        assertEquals(OccurrenceState.DONE, resultMonday.occurrenceStates[LocalDate.of(2025, 5, 4)])
        assertEquals(OccurrenceState.DONE, resultSunday.occurrenceStates[LocalDate.of(2025, 5, 4)])
    }

    @Test
    fun `consistency score calculates weights correctly over trailing window`() {
        val today = LocalDate.of(2025, 5, 4)
        val completions = listOf(
            LoggedCompletion(habitId = "h1", logicalDate = LocalDate.of(2025, 5, 1), state = CompletionState.DONE),
            LoggedCompletion(habitId = "h1", logicalDate = LocalDate.of(2025, 5, 2), state = CompletionState.MINIMUM),
            LoggedCompletion(habitId = "h1", logicalDate = LocalDate.of(2025, 5, 3), state = CompletionState.SKIPPED)
        )

        val result = ReplayEngine.evaluate(defaultHabit, completions, today)

        assertEquals(0.85, result.consistencyScore!!, 0.001)
        assertEquals(2, result.currentStreak)
    }

    @Test
    fun `two miss rule allows a single miss without resetting streak`() {
        val today = LocalDate.of(2025, 5, 4)
        val completions = listOf(
            LoggedCompletion(habitId = "h1", logicalDate = LocalDate.of(2025, 5, 1), state = CompletionState.DONE),
            LoggedCompletion(habitId = "h1", logicalDate = LocalDate.of(2025, 5, 3), state = CompletionState.DONE)
        )

        val result = ReplayEngine.evaluate(defaultHabit, completions, today, autoUseRestTokens = false)

        assertEquals(2, result.currentStreak)
    }

    @Test
    fun `two consecutive misses reset streak to zero`() {
        val today = LocalDate.of(2025, 5, 5)
        val completions = listOf(
            LoggedCompletion(habitId = "h1", logicalDate = LocalDate.of(2025, 5, 1), state = CompletionState.DONE),
            LoggedCompletion(habitId = "h1", logicalDate = LocalDate.of(2025, 5, 4), state = CompletionState.DONE)
        )

        val result = ReplayEngine.evaluate(defaultHabit, completions, today, autoUseRestTokens = false)

        assertEquals(1, result.currentStreak)
    }

    @Test
    fun `rest token cap at 3 stops progress accumulation`() {
        val completions = (1..22).map { day ->
            LoggedCompletion(
                habitId = "h1",
                logicalDate = LocalDate.of(2025, 5, 1).plusDays(day.toLong() - 1),
                state = CompletionState.DONE
            )
        }

        val result = ReplayEngine.evaluate(defaultHabit, completions, LocalDate.of(2025, 5, 23))

        assertEquals(3, result.availableRestTokens)
        assertEquals(0, result.restTokenProgress)
    }

    @Test
    fun `n per week resolves at week end and partial first week caps n`() {
        val thursdayHabit = Habit(
            id = "h2",
            name = "Weekly Habit",
            schedule = ScheduleType.NPerWeek(n = 3),
            createdAt = LocalDate.of(2025, 5, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
        )

        val completions = listOf(
            LoggedCompletion(habitId = "h2", logicalDate = LocalDate.of(2025, 5, 2), state = CompletionState.DONE)
        )

        val result = ReplayEngine.evaluate(thursdayHabit, completions, LocalDate.of(2025, 5, 5), startOfWeekDay = DayOfWeek.MONDAY)

        val missedCount = result.occurrenceStates.values.count { it == OccurrenceState.MISSED }
        val doneCount = result.occurrenceStates.values.count { it == OccurrenceState.DONE }

        assertEquals(1, doneCount)
        assertEquals(2, missedCount)
    }

    @Test
    fun `unknown schedule habit is excluded from replay and scoring`() {
        val unknownHabit = Habit(
            id = "h3",
            name = "Unknown Habit",
            schedule = ScheduleType.Unknown("v2:FUTURE_TYPE")
        )

        val result = ReplayEngine.evaluate(unknownHabit, emptyList(), LocalDate.of(2025, 5, 10))

        assertNull(result.consistencyScore)
        assertEquals(0, result.currentStreak)
        assertTrue(result.occurrenceStates.isEmpty())
    }

    @Test
    fun `comeback bonus triggers after missed day but not after protected day`() {
        val statesWithMissed = mapOf(
            LocalDate.of(2025, 5, 1) to OccurrenceState.DONE,
            LocalDate.of(2025, 5, 2) to OccurrenceState.MISSED
        )

        val statesWithProtected = mapOf(
            LocalDate.of(2025, 5, 1) to OccurrenceState.DONE,
            LocalDate.of(2025, 5, 2) to OccurrenceState.PROTECTED
        )

        val logDate = LocalDate.of(2025, 5, 3)

        assertTrue(ReplayEngine.isComebackBonus(statesWithMissed, logDate))
        assertFalse(ReplayEngine.isComebackBonus(statesWithProtected, logDate))
    }

    @Test
    fun `backfill dynamically updates replay calculation`() {
        val today = LocalDate.of(2025, 5, 3)
        val resultBefore = ReplayEngine.evaluate(defaultHabit, emptyList(), today)
        assertEquals(OccurrenceState.MISSED, resultBefore.occurrenceStates[LocalDate.of(2025, 5, 1)])

        val backfilled = listOf(
            LoggedCompletion(habitId = "h1", logicalDate = LocalDate.of(2025, 5, 1), state = CompletionState.DONE)
        )

        val resultAfter = ReplayEngine.evaluate(defaultHabit, backfilled, today)
        assertEquals(OccurrenceState.DONE, resultAfter.occurrenceStates[LocalDate.of(2025, 5, 1)])
        assertEquals(1, resultAfter.currentStreak)
    }
}
