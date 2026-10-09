package com.habitat.app.domain

import com.habitat.app.domain.logic.ScheduleEvaluator
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
            override fun instant() = Instant.parse("2025-05-12T03:59:00Z")
            override fun zoneId() = ZoneId.of("UTC")
        }
        val clock0400 = object : AppClock {
            override fun instant() = Instant.parse("2025-05-12T04:00:00Z")
            override fun zoneId() = ZoneId.of("UTC")
        }

        assertEquals(LocalDate.of(2025, 5, 11), clock0359.logicalDate(4))
        assertEquals(LocalDate.of(2025, 5, 12), clock0400.logicalDate(4))
    }
}
