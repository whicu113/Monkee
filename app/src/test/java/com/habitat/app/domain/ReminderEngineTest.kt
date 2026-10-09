package com.habitat.app.domain

import com.habitat.app.domain.logic.ReminderAllocation
import com.habitat.app.domain.logic.ReminderEngine
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class ReminderEngineTest {

    private val zoneUtc = ZoneId.of("UTC")
    private val quietStart = LocalTime.of(22, 0)
    private val quietEnd = LocalTime.of(7, 0)

    @Test
    fun `nextTrigger defers to quiet hours end when reminder time is inside quiet hours`() {
        val habit = Habit(
            name = "Late Night Habit",
            schedule = ScheduleType.Daily,
            reminderTime = LocalTime.of(23, 0)
        )

        val fromInstant = Instant.parse("2025-05-01T10:00:00Z")
        val trigger = ReminderEngine.nextTrigger(
            habit = habit,
            fromInstant = fromInstant,
            zoneId = zoneUtc,
            quietHoursStart = quietStart,
            quietHoursEnd = quietEnd
        )

        assertNotNull(trigger)
        val triggerZdt = trigger!!.atZone(zoneUtc)

        assertEquals(LocalTime.of(7, 0), triggerZdt.toLocalTime())
        assertEquals(2, triggerZdt.dayOfMonth)
    }

    @Test
    fun `nextTrigger returns exact time when outside quiet hours`() {
        val habit = Habit(
            name = "Morning Habit",
            schedule = ScheduleType.Daily,
            reminderTime = LocalTime.of(9, 0)
        )

        val fromInstant = Instant.parse("2025-05-01T08:00:00Z")
        val trigger = ReminderEngine.nextTrigger(
            habit = habit,
            fromInstant = fromInstant,
            zoneId = zoneUtc,
            quietHoursStart = quietStart,
            quietHoursEnd = quietEnd
        )

        assertNotNull(trigger)
        assertEquals(Instant.parse("2025-05-01T09:00:00Z"), trigger)
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

        // Already posted 2 initial reminders today, cap = 4 -> 2 slots left
        // With 3 due habits and 2 slots available, 1 slot is reserved for summary -> 1 individual + 1 summary (2 habits)
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
        assertEquals("h4", summary.habits[0].id)
        assertEquals("h5", summary.habits[1].id)
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

    @Test
    fun `DST spring forward transition adjusts trigger cleanly`() {
        val nyZone = ZoneId.of("America/New_York")
        val habit = Habit(
            name = "DST Habit",
            schedule = ScheduleType.Daily,
            reminderTime = LocalTime.of(2, 30)
        )

        val fromInstant = Instant.parse("2025-03-08T20:00:00Z")
        val trigger = ReminderEngine.nextTrigger(
            habit = habit,
            fromInstant = fromInstant,
            zoneId = nyZone,
            quietHoursStart = quietStart,
            quietHoursEnd = quietEnd
        )

        assertNotNull(trigger)
        val triggerZdt = trigger!!.atZone(nyZone)
        assertTrue(triggerZdt.toLocalTime() >= LocalTime.of(3, 0))
    }

    @Test
    fun `DST fall back overlap fires once at next valid instant`() {
        val nyZone = ZoneId.of("America/New_York")
        val habit = Habit(
            name = "Fall Back Habit",
            schedule = ScheduleType.Daily,
            reminderTime = LocalTime.of(1, 30) // November 2, 2025 has two 01:30s
        )

        // Evaluate on Nov 1, 2025
        val fromInstant = Instant.parse("2025-11-01T20:00:00Z")
        val trigger1 = ReminderEngine.nextTrigger(
            habit = habit,
            fromInstant = fromInstant,
            zoneId = nyZone,
            quietHoursStart = quietStart,
            quietHoursEnd = quietEnd
        )

        assertNotNull(trigger1)

        // Evaluate immediately after trigger 1 fires
        val trigger2 = ReminderEngine.nextTrigger(
            habit = habit,
            fromInstant = trigger1!!.plusSeconds(60),
            zoneId = nyZone,
            quietHoursStart = quietStart,
            quietHoursEnd = quietEnd
        )

        assertNotNull(trigger2)
        // Next trigger moves forward to Nov 3 (does not loop infinitely during fall back)
        assertTrue(trigger2!! > trigger1)
    }

    @Test
    fun `consecutive triggers re-arm across 3 consecutive days without opening app`() {
        val habit = Habit(
            id = "h1",
            name = "Daily Habit",
            schedule = ScheduleType.Daily,
            reminderTime = LocalTime.of(9, 0)
        )

        var currentInstant = Instant.parse("2025-05-01T08:00:00Z")

        // Day 1 trigger
        val trigger1 = ReminderEngine.nextTrigger(habit, currentInstant, zoneUtc, quietHoursStart = quietStart, quietHoursEnd = quietEnd)
        assertEquals(Instant.parse("2025-05-01T09:00:00Z"), trigger1)

        // Day 2 trigger re-armed after Day 1 fires
        currentInstant = trigger1!!.plusSeconds(60)
        val trigger2 = ReminderEngine.nextTrigger(habit, currentInstant, zoneUtc, quietHoursStart = quietStart, quietHoursEnd = quietEnd)
        assertEquals(Instant.parse("2025-05-02T09:00:00Z"), trigger2)

        // Day 3 trigger re-armed after Day 2 fires
        currentInstant = trigger2!!.plusSeconds(60)
        val trigger3 = ReminderEngine.nextTrigger(habit, currentInstant, zoneUtc, quietHoursStart = quietStart, quietHoursEnd = quietEnd)
        assertEquals(Instant.parse("2025-05-03T09:00:00Z"), trigger3)
    }
}
