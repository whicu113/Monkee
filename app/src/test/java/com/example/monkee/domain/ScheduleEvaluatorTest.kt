package com.example.monkee.domain

import com.example.monkee.domain.logic.ScheduleEvaluator
import com.example.monkee.domain.model.ScheduleType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Unit tests for [ScheduleEvaluator].
 * SPEC Section 4.2 & 12 (M1 acceptance criteria).
 */
class ScheduleEvaluatorTest {

    @Test
    fun `daily schedule is scheduled every day`() {
        val schedule = ScheduleType.Daily
        val date = LocalDate.of(2025, 5, 12) // Monday

        assertTrue(ScheduleEvaluator.isScheduledOn(schedule, date))
        assertTrue(ScheduleEvaluator.isScheduledOn(schedule, date.plusDays(1)))
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
    fun `every n days schedule calculates correctly from start date`() {
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
    fun `n per week schedule returns true for all days`() {
        val schedule = ScheduleType.NPerWeek(n = 3, startOfWeekDay = DayOfWeek.MONDAY)
        val anyDate = LocalDate.of(2025, 5, 12)

        assertTrue(ScheduleEvaluator.isScheduledOn(schedule, anyDate))
    }

    @Test
    fun `logical date calculation around 0400 day boundary`() {
        val clock0359 = object : AppClock {
            override fun instant() = java.time.Instant.parse("2025-05-12T03:59:00Z")
            override fun zoneId() = java.time.ZoneId.of("UTC")
        }
        val clock0400 = object : AppClock {
            override fun instant() = java.time.Instant.parse("2025-05-12T04:00:00Z")
            override fun zoneId() = java.time.ZoneId.of("UTC")
        }

        // Before 04:00 boundary -> belongs to previous logical date (May 11)
        java.lang.System.out.println("0359 logical date: " + clock0359.logicalDate(4))
        org.junit.Assert.assertEquals(LocalDate.of(2025, 5, 11), clock0359.logicalDate(4))

        // At or after 04:00 boundary -> belongs to May 12
        org.junit.Assert.assertEquals(LocalDate.of(2025, 5, 12), clock0400.logicalDate(4))
    }
}
