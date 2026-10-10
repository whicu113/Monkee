package com.habitat.app.domain

import com.habitat.app.domain.logic.ScheduleEvaluator
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.LoggedCompletion
import com.habitat.app.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ScheduleEvaluatorTest {

    @Test
    fun `daily schedule is scheduled every day`() {
        val schedule = ScheduleType.Daily
        val date = LocalDate.of(2025, 5, 12)

        assertTrue(ScheduleEvaluator.isScheduledOn(schedule, date))
        assertTrue(ScheduleEvaluator.isScheduledOn(schedule, date.plusDays(1)))
        assertTrue(ScheduleEvaluator.isDueOn(schedule, date))
    }

    @Test
    fun `weekdays schedule matches configured days`() {
        val schedule = ScheduleType.Weekdays(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        )

        val monday = LocalDate.of(2025, 5, 12)
        val tuesday = LocalDate.of(2025, 5, 13)
        val wednesday = LocalDate.of(2025, 5, 14)

        assertTrue(ScheduleEvaluator.isScheduledOn(schedule, monday))
        assertFalse(ScheduleEvaluator.isScheduledOn(schedule, tuesday))
        assertTrue(ScheduleEvaluator.isScheduledOn(schedule, wednesday))
    }

    @Test
    fun `every n days schedule calculates correctly from fixed anchor start date`() {
        val startDate = LocalDate.of(2025, 5, 1)
        val schedule = ScheduleType.EveryNDays(n = 3, startDate = startDate)

        val beforeStart = LocalDate.of(2025, 4, 30)
        val day0 = LocalDate.of(2025, 5, 1)
        val day1 = LocalDate.of(2025, 5, 2)
        val day3 = LocalDate.of(2025, 5, 4)
        val day6 = LocalDate.of(2025, 5, 7)

        assertFalse(ScheduleEvaluator.isScheduledOn(schedule, beforeStart))
        assertTrue(ScheduleEvaluator.isScheduledOn(schedule, day0))
        assertFalse(ScheduleEvaluator.isScheduledOn(schedule, day1))
        assertTrue(ScheduleEvaluator.isScheduledOn(schedule, day3))
        assertTrue(ScheduleEvaluator.isScheduledOn(schedule, day6))
    }

    @Test
    fun `n per week schedule availability resolves based on completions this week`() {
        val schedule = ScheduleType.NPerWeek(n = 3)
        val anyDate = LocalDate.of(2025, 5, 12)

        assertTrue(ScheduleEvaluator.isScheduledOn(schedule, anyDate))

        assertTrue(ScheduleEvaluator.isDueOn(schedule, anyDate, completionsThisWeek = 0))
        assertTrue(ScheduleEvaluator.isDueOn(schedule, anyDate, completionsThisWeek = 2))

        assertFalse(ScheduleEvaluator.isDueOn(schedule, anyDate, completionsThisWeek = 3))
        assertFalse(ScheduleEvaluator.isDueOn(schedule, anyDate, completionsThisWeek = 4))
    }

    @Test
    fun `start of week is configurable`() {
        val wednesday = LocalDate.of(2025, 5, 14)

        val mondayStart = ScheduleEvaluator.startOfWeek(wednesday, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2025, 5, 12), mondayStart)

        val sundayStart = ScheduleEvaluator.startOfWeek(wednesday, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2025, 5, 11), sundayStart)
    }

    @Test
    fun `completion at 0330 AM belongs to previous logical date in Europe Berlin timezone`() {
        val berlinZone = ZoneId.of("Europe/Berlin")
        val clock0330 = object : AppClock {
            // 03:30 CEST on May 12, 2025 = 01:30 UTC
            override fun instant() = Instant.parse("2025-05-12T01:30:00Z")
            override fun zoneId() = berlinZone
        }

        assertEquals(LocalDate.of(2025, 5, 11), clock0330.logicalDate(4))
    }

    @Test
    fun `DST transitions in Europe Berlin timezone preserve 0400 boundary logical date`() {
        val berlinZone = ZoneId.of("Europe/Berlin")

        // Spring forward day in Europe/Berlin: March 30, 2025 (clocks jump 02:00 -> 03:00)
        // 03:30 CEST (01:30 UTC) -> logically belongs to March 29 because 03:30 < 04:00 boundary
        val springClock0330 = object : AppClock {
            override fun instant() = Instant.parse("2025-03-30T01:30:00Z")
            override fun zoneId() = berlinZone
        }
        assertEquals(LocalDate.of(2025, 3, 29), springClock0330.logicalDate(4))

        // 04:15 CEST (02:15 UTC) -> logically belongs to March 30 because 04:15 >= 04:00
        val springClock0415 = object : AppClock {
            override fun instant() = Instant.parse("2025-03-30T02:15:00Z")
            override fun zoneId() = berlinZone
        }
        assertEquals(LocalDate.of(2025, 3, 30), springClock0415.logicalDate(4))

        // Fall back day in Europe/Berlin: October 26, 2025 (clocks fall back 03:00 -> 02:00)
        // 03:30 CET (02:30 UTC) -> logically belongs to Oct 25
        val fallClock0330 = object : AppClock {
            override fun instant() = Instant.parse("2025-10-26T02:30:00Z")
            override fun zoneId() = berlinZone
        }
        assertEquals(LocalDate.of(2025, 10, 25), fallClock0330.logicalDate(4))
    }

    // ─── isWeeklyQuotaMet tests ───────────────────────────────────────────────

    private val monday = LocalDate.of(2025, 5, 12)  // Monday
    private val tuesday = LocalDate.of(2025, 5, 13)
    private val sunday = LocalDate.of(2025, 5, 11)  // Sunday (preceding week end for Mon-start)

    /** Convenience builder for LoggedCompletion. */
    private fun comp(habitId: String = "h1", date: LocalDate, state: CompletionState) =
        LoggedCompletion(habitId = habitId, logicalDate = date, state = state)

    @Test
    fun `isWeeklyQuotaMet returns false for non-NPerWeek schedule`() {
        assertFalse(
            ScheduleEvaluator.isWeeklyQuotaMet(
                schedule = ScheduleType.Daily,
                date = monday,
                startOfWeekDay = DayOfWeek.MONDAY,
                completions = listOf(comp(date = monday, state = CompletionState.DONE))
            )
        )
    }

    @Test
    fun `isWeeklyQuotaMet false when no completions this week`() {
        assertFalse(
            ScheduleEvaluator.isWeeklyQuotaMet(
                schedule = ScheduleType.NPerWeek(n = 3),
                date = monday,
                startOfWeekDay = DayOfWeek.MONDAY,
                completions = emptyList()
            )
        )
    }

    @Test
    fun `isWeeklyQuotaMet false when under quota with DONE completions`() {
        assertFalse(
            ScheduleEvaluator.isWeeklyQuotaMet(
                schedule = ScheduleType.NPerWeek(n = 3),
                date = tuesday,
                startOfWeekDay = DayOfWeek.MONDAY,
                completions = listOf(
                    comp(date = monday, state = CompletionState.DONE),
                    comp(date = tuesday, state = CompletionState.DONE)
                )
            )
        )
    }

    @Test
    fun `isWeeklyQuotaMet true when quota met with DONE completions`() {
        assertTrue(
            ScheduleEvaluator.isWeeklyQuotaMet(
                schedule = ScheduleType.NPerWeek(n = 3),
                date = LocalDate.of(2025, 5, 14),
                startOfWeekDay = DayOfWeek.MONDAY,
                completions = listOf(
                    comp(date = monday, state = CompletionState.DONE),
                    comp(date = tuesday, state = CompletionState.DONE),
                    comp(date = LocalDate.of(2025, 5, 14), state = CompletionState.DONE)
                )
            )
        )
    }

    @Test
    fun `isWeeklyQuotaMet MINIMUM counts toward quota`() {
        assertTrue(
            ScheduleEvaluator.isWeeklyQuotaMet(
                schedule = ScheduleType.NPerWeek(n = 2),
                date = tuesday,
                startOfWeekDay = DayOfWeek.MONDAY,
                completions = listOf(
                    comp(date = monday, state = CompletionState.MINIMUM),
                    comp(date = tuesday, state = CompletionState.MINIMUM)
                )
            )
        )
    }

    @Test
    fun `isWeeklyQuotaMet SKIPPED does not count toward quota`() {
        // 2 DONE + 1 SKIPPED for n=3: quota NOT met because SKIPPED doesn't count
        assertFalse(
            ScheduleEvaluator.isWeeklyQuotaMet(
                schedule = ScheduleType.NPerWeek(n = 3),
                date = LocalDate.of(2025, 5, 14),
                startOfWeekDay = DayOfWeek.MONDAY,
                completions = listOf(
                    comp(date = monday, state = CompletionState.DONE),
                    comp(date = tuesday, state = CompletionState.DONE),
                    comp(date = LocalDate.of(2025, 5, 14), state = CompletionState.SKIPPED)
                )
            )
        )
    }

    @Test
    fun `isWeeklyQuotaMet Monday-start week boundary excludes previous week completions`() {
        // Completion from previous Sunday is outside the Mon-start week containing monday(May 12)
        assertFalse(
            ScheduleEvaluator.isWeeklyQuotaMet(
                schedule = ScheduleType.NPerWeek(n = 1),
                date = monday,
                startOfWeekDay = DayOfWeek.MONDAY,
                completions = listOf(
                    comp(date = sunday, state = CompletionState.DONE) // May 11 = prev week
                )
            )
        )
    }

    @Test
    fun `isWeeklyQuotaMet Sunday-start week includes Sunday completion`() {
        // With Sunday start, May 11 IS in the same week as May 12
        assertTrue(
            ScheduleEvaluator.isWeeklyQuotaMet(
                schedule = ScheduleType.NPerWeek(n = 1),
                date = monday,
                startOfWeekDay = DayOfWeek.SUNDAY,
                completions = listOf(
                    comp(date = sunday, state = CompletionState.DONE) // May 11 = same week
                )
            )
        )
    }

    @Test
    fun `effective NPerWeek target caps the habit creation week only`() {
        data class Row(
            val name: String,
            val n: Int,
            val weekStart: LocalDate,
            val habitStartDate: LocalDate,
            val expected: Int
        )
        val rows = listOf(
            Row("full week", 3, LocalDate.of(2025, 5, 12), LocalDate.of(2025, 5, 12), 3),
            Row("partial week caps to remaining days", 6, LocalDate.of(2025, 5, 12), LocalDate.of(2025, 5, 15), 4),
            Row("partial week retains smaller n", 2, LocalDate.of(2025, 5, 12), LocalDate.of(2025, 5, 15), 2),
            Row("later week uses configured n", 6, LocalDate.of(2025, 5, 19), LocalDate.of(2025, 5, 15), 6)
        )

        rows.forEach { row ->
            assertEquals(
                "${row.name}: effective weekly target",
                row.expected,
                ScheduleEvaluator.effectiveNPerWeekTarget(
                    schedule = ScheduleType.NPerWeek(row.n),
                    habitStartDate = row.habitStartDate,
                    weekStart = row.weekStart
                )
            )
        }
    }
}
