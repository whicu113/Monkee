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
}
