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

        // Available when completions < n
        assertTrue(ScheduleEvaluator.isDueOn(schedule, anyDate, completionsThisWeek = 0))
        assertTrue(ScheduleEvaluator.isDueOn(schedule, anyDate, completionsThisWeek = 2))

        // No longer due once target completions (n) reached
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
    fun `completion at 0330 AM belongs to previous logical date`() {
        val clock0330 = object : AppClock {
            override fun instant() = Instant.parse("2025-05-12T03:30:00Z")
            override fun zoneId() = ZoneId.of("UTC")
        }

        assertEquals(LocalDate.of(2025, 5, 11), clock0330.logicalDate(4))
    }

    @Test
    fun `DST transitions preserve 0400 boundary logical date correctly`() {
        val nyZone = ZoneId.of("America/New_York")

        // Spring forward day: March 9, 2025
        val springClock0330 = object : AppClock {
            override fun instant() = Instant.parse("2025-03-09T07:30:00Z")
            override fun zoneId() = nyZone
        }
        assertEquals(LocalDate.of(2025, 3, 8), springClock0330.logicalDate(4))

        val springClock0415 = object : AppClock {
            override fun instant() = Instant.parse("2025-03-09T08:15:00Z")
            override fun zoneId() = nyZone
        }
        assertEquals(LocalDate.of(2025, 3, 9), springClock0415.logicalDate(4))

        // Fall back day: November 2, 2025
        val fallClock0330 = object : AppClock {
            override fun instant() = Instant.parse("2025-11-02T08:30:00Z")
            override fun zoneId() = nyZone
        }
        assertEquals(LocalDate.of(2025, 11, 1), fallClock0330.logicalDate(4))
    }
}
