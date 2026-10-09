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
import java.time.ZonedDateTime

class ReminderEngineTest {

    private val zoneUtc = ZoneId.of("UTC")
    private val quietStart = LocalTime.of(22, 0)
    private val quietEnd = LocalTime.of(7, 0)

    @Test
    fun `nextTrigger defers to quiet hours end when reminder time is inside quiet hours`() {
        val habit = Habit(
            name = "Late Night Habit",
            schedule = ScheduleType.Daily,
            reminderTime = LocalTime.of(23, 0) // Inside 22:00 - 07:00 quiet hours
        )

        // Eval at May 1 2025 10:00 UTC
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

        // Deferred to 07:00 next morning (May 2)
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

        // Total 4 allocations: 3 Individual + 1 Summary
        assertEquals(4, allocations.size)

        assertTrue(allocations[0] is ReminderAllocation.Individual)
        assertTrue(allocations[1] is ReminderAllocation.Individual)
        assertTrue(allocations[2] is ReminderAllocation.Individual)

        val summary = allocations[3] as ReminderAllocation.Summary
        assertEquals(2, summary.habits.size) // Habits 4 and 5 merged
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
            reminderTime = LocalTime.of(2, 30) // March 9, 2025 spring forward skips 02:00 - 03:00
        )

        // Evaluate on March 8, 2025 at 20:00 UTC
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
}
