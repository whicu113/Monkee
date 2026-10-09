package com.habitat.app.domain

import com.habitat.app.domain.GameConfig
import com.habitat.app.domain.logic.ReminderAllocation
import com.habitat.app.domain.logic.ReminderEngine
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ReminderEngineTest {

    private val zoneUtc = ZoneId.of("UTC")
    private val zoneBerlin = ZoneId.of("Europe/Berlin")
    private val quietStart = LocalTime.of(22, 0)
    private val quietEnd = LocalTime.of(7, 0)

    @Test
    fun `shouldPost table driven test`() {
        val date = LocalDate.of(2025, 5, 12)
        val dailyHabit = Habit(name = "Daily", schedule = ScheduleType.Daily)
        val archivedHabit = Habit(name = "Archived", schedule = ScheduleType.Daily, archived = true)
        val unknownHabit = Habit(name = "Unknown", schedule = ScheduleType.Unknown("v2:FUTURE"))
        val tuesdayHabit = Habit(name = "Tuesday Only", schedule = ScheduleType.Weekdays(setOf(DayOfWeek.TUESDAY)))

        assertTrue(ReminderEngine.shouldPost(dailyHabit, isLoggedToday = false, isWeeklyQuotaMet = false, currentLogicalDate = date))
        assertFalse(ReminderEngine.shouldPost(archivedHabit, isLoggedToday = false, isWeeklyQuotaMet = false, currentLogicalDate = date))
        assertFalse(ReminderEngine.shouldPost(unknownHabit, isLoggedToday = false, isWeeklyQuotaMet = false, currentLogicalDate = date))
        assertFalse(ReminderEngine.shouldPost(tuesdayHabit, isLoggedToday = false, isWeeklyQuotaMet = false, currentLogicalDate = date))
        assertFalse(ReminderEngine.shouldPost(dailyHabit, isLoggedToday = true, isWeeklyQuotaMet = false, currentLogicalDate = date))
        assertFalse(ReminderEngine.shouldPost(dailyHabit, isLoggedToday = false, isWeeklyQuotaMet = true, currentLogicalDate = date))
    }

    @Test
    fun `isLatenessExceeded at 119m, 120m, and 121m boundary`() {
        val scheduled = Instant.parse("2025-05-10T10:00:00Z")

        val at119m = scheduled.plusSeconds(119 * 60)
        val at120m = scheduled.plusSeconds(120 * 60)
        val at121m = scheduled.plusSeconds(121 * 60)

        assertFalse(ReminderEngine.isLatenessExceeded(scheduled, at119m, maxLatenessMinutes = 120))
        assertFalse(ReminderEngine.isLatenessExceeded(scheduled, at120m, maxLatenessMinutes = 120))
        assertTrue(ReminderEngine.isLatenessExceeded(scheduled, at121m, maxLatenessMinutes = 120))
    }

    @Test
    fun `isSnoozeExpired returns true when current logical date is past intent logical date`() {
        val intentDate = LocalDate.of(2025, 5, 10)

        val sameDate = LocalDate.of(2025, 5, 10)
        val nextDate = LocalDate.of(2025, 5, 11)

        assertFalse(ReminderEngine.isSnoozeExpired(intentDate, sameDate))
        assertTrue(ReminderEngine.isSnoozeExpired(intentDate, nextDate))
    }

    @Test
    fun `isInsideQuietHours evaluates exact boundaries`() {
        val start = LocalTime.of(22, 0)
        val end = LocalTime.of(7, 0)

        assertTrue(ReminderEngine.isInsideQuietHours(LocalTime.of(22, 0), start, end))
        assertTrue(ReminderEngine.isInsideQuietHours(LocalTime.of(23, 30), start, end))
        assertTrue(ReminderEngine.isInsideQuietHours(LocalTime.of(3, 0), start, end))
        assertFalse(ReminderEngine.isInsideQuietHours(LocalTime.of(7, 0), start, end))
        assertFalse(ReminderEngine.isInsideQuietHours(LocalTime.of(12, 0), start, end))
    }

    @Test
    fun `nextTrigger deferral in Europe Berlin timezone 2230 to 0700 next logical date`() {
        val habit = Habit(
            name = "Berlin Habit",
            schedule = ScheduleType.Daily,
            reminderTime = LocalTime.of(22, 30)
        )

        val fromInstant = Instant.parse("2025-05-01T18:00:00Z")
        val trigger = ReminderEngine.nextTrigger(
            habit = habit,
            fromInstant = fromInstant,
            zoneId = zoneBerlin,
            dayBoundaryHour = 4,
            quietHoursStart = quietStart,
            quietHoursEnd = quietEnd
        )

        assertNotNull(trigger)
        val triggerZdt = trigger!!.atZone(zoneBerlin)

        assertEquals(LocalTime.of(7, 0), triggerZdt.toLocalTime())
        assertEquals(LocalDate.of(2025, 5, 2), triggerZdt.toLocalDate())
    }

    @Test
    fun `nextTrigger in Europe Berlin during DST spring forward and fall back`() {
        val habitSpring = Habit(
            name = "Spring Habit",
            schedule = ScheduleType.Daily,
            reminderTime = LocalTime.of(2, 30)
        )

        val springFrom = Instant.parse("2025-03-29T20:00:00Z")
        val springTrigger = ReminderEngine.nextTrigger(
            habit = habitSpring,
            fromInstant = springFrom,
            zoneId = zoneBerlin,
            quietHoursStart = quietStart,
            quietHoursEnd = quietEnd
        )

        assertNotNull(springTrigger)
        val springZdt = springTrigger!!.atZone(zoneBerlin)
        assertTrue(springZdt.toLocalTime() >= LocalTime.of(3, 0))

        val habitFall = Habit(
            name = "Fall Habit",
            schedule = ScheduleType.Daily,
            reminderTime = LocalTime.of(1, 30)
        )

        val fallFrom1 = Instant.parse("2025-10-25T20:00:00Z")
        val fallTrigger1 = ReminderEngine.nextTrigger(habitFall, fallFrom1, zoneBerlin, quietHoursStart = quietStart, quietHoursEnd = quietEnd)
        assertNotNull(fallTrigger1)

        val fallFrom2 = fallTrigger1!!.plusSeconds(60)
        val fallTrigger2 = ReminderEngine.nextTrigger(habitFall, fallFrom2, zoneBerlin, quietHoursStart = quietStart, quietHoursEnd = quietEnd)
        assertNotNull(fallTrigger2)
        assertTrue(fallTrigger2!! > fallTrigger1)
    }

    @Test
    fun `nextTrigger at trigger instant and 1s after does not re-arm same trigger`() {
        val habit = Habit(
            name = "Daily",
            schedule = ScheduleType.Daily,
            reminderTime = LocalTime.of(9, 0)
        )

        val t1 = Instant.parse("2025-05-01T09:00:00Z")
        val triggerAtT1 = ReminderEngine.nextTrigger(habit, t1, zoneUtc, quietHoursStart = quietStart, quietHoursEnd = quietEnd)

        val t1Plus1s = t1.plusSeconds(1)
        val triggerAfterT1 = ReminderEngine.nextTrigger(habit, t1Plus1s, zoneUtc, quietHoursStart = quietStart, quietHoursEnd = quietEnd)

        assertEquals(Instant.parse("2025-05-02T09:00:00Z"), triggerAtT1)
        assertEquals(Instant.parse("2025-05-02T09:00:00Z"), triggerAfterT1)
    }

    @Test
    fun `allocateDay with alreadyPostedCount greater than zero respects cap`() {
        val habits = (1..3).map { index ->
            Habit(
                id = "h$index",
                name = "Habit $index",
                reminderTime = LocalTime.of(8 + index, 0),
                sortOrder = index
            )
        }

        val allocations = ReminderEngine.allocateDay(dueHabits = habits, alreadyPostedCount = 2, cap = 4)

        assertEquals(2, allocations.size)
        assertTrue(allocations[0] is ReminderAllocation.Individual)
        val summary = allocations[1] as ReminderAllocation.Summary
        assertEquals(2, summary.habits.size)
    }

    @Test
    fun `allocateDay posts 3 individual and 1 summary when due habits exceed cap`() {
        val habits = (1..5).map { index ->
            Habit(
                id = "h$index",
                name = "Habit $index",
                reminderTime = LocalTime.of(8 + index, 0),
                sortOrder = index
            )
        }

        val allocations = ReminderEngine.allocateDay(dueHabits = habits, alreadyPostedCount = 0, cap = 4)

        assertEquals(4, allocations.size)
        assertTrue(allocations[0] is ReminderAllocation.Individual)
        assertTrue(allocations[1] is ReminderAllocation.Individual)
        assertTrue(allocations[2] is ReminderAllocation.Individual)

        val summary = allocations[3] as ReminderAllocation.Summary
        assertEquals(2, summary.habits.size)
    }

    @Test
    fun `allocateDay orders by reminder time then sort order`() {
        val habitA = Habit(id = "a", name = "A", reminderTime = LocalTime.of(10, 0), sortOrder = 2)
        val habitB = Habit(id = "b", name = "B", reminderTime = LocalTime.of(9, 0), sortOrder = 1)
        val habitC = Habit(id = "c", name = "C", reminderTime = LocalTime.of(9, 0), sortOrder = 0)

        val allocations = ReminderEngine.allocateDay(dueHabits = listOf(habitA, habitB, habitC), cap = 4)

        assertEquals("c", (allocations[0] as ReminderAllocation.Individual).habit.id)
        assertEquals("b", (allocations[1] as ReminderAllocation.Individual).habit.id)
        assertEquals("a", (allocations[2] as ReminderAllocation.Individual).habit.id)
    }
}
