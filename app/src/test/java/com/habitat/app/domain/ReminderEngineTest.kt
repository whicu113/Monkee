package com.habitat.app.domain

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
    private val quietStart = LocalTime.of(22, 0)
    private val quietEnd = LocalTime.of(7, 0)

    @Test
    fun `shouldPost table driven test`() {
        val date = LocalDate.of(2025, 5, 12) // Monday
        val dailyHabit = Habit(name = "Daily", schedule = ScheduleType.Daily)
        val archivedHabit = Habit(name = "Archived", schedule = ScheduleType.Daily, archived = true)
        val unknownHabit = Habit(name = "Unknown", schedule = ScheduleType.Unknown("v2:FUTURE"))
        val tuesdayHabit = Habit(name = "Tuesday Only", schedule = ScheduleType.Weekdays(setOf(DayOfWeek.TUESDAY)))

        // 1. Positive case
        assertTrue(ReminderEngine.shouldPost(dailyHabit, isLoggedToday = false, isWeeklyQuotaMet = false, currentLogicalDate = date))

        // 2. Archived habit -> false
        assertFalse(ReminderEngine.shouldPost(archivedHabit, isLoggedToday = false, isWeeklyQuotaMet = false, currentLogicalDate = date))

        // 3. Unknown schedule -> false
        assertFalse(ReminderEngine.shouldPost(unknownHabit, isLoggedToday = false, isWeeklyQuotaMet = false, currentLogicalDate = date))

        // 4. Not scheduled today -> false
        assertFalse(ReminderEngine.shouldPost(tuesdayHabit, isLoggedToday = false, isWeeklyQuotaMet = false, currentLogicalDate = date))

        // 5. Already logged today -> false
        assertFalse(ReminderEngine.shouldPost(dailyHabit, isLoggedToday = true, isWeeklyQuotaMet = false, currentLogicalDate = date))

        // 6. Weekly quota met -> false
        assertFalse(ReminderEngine.shouldPost(dailyHabit, isLoggedToday = false, isWeeklyQuotaMet = true, currentLogicalDate = date))
    }

    @Test
    fun `nextTrigger defers 2230 reminder to 0700 across 0400 boundary and derives logical date from fire instant`() {
        val habit = Habit(
            name = "Late Night Habit",
            schedule = ScheduleType.Daily,
            reminderTime = LocalTime.of(22, 30)
        )

        // Evaluate on May 1 at 20:00 UTC
        val fromInstant = Instant.parse("2025-05-01T20:00:00Z")
        val trigger = ReminderEngine.nextTrigger(
            habit = habit,
            fromInstant = fromInstant,
            zoneId = zoneUtc,
            dayBoundaryHour = 4,
            quietHoursStart = quietStart,
            quietHoursEnd = quietEnd
        )

        assertNotNull(trigger)
        val triggerZdt = trigger!!.atZone(zoneUtc)

        // Fire instant is May 2 at 07:00 UTC
        assertEquals(LocalTime.of(7, 0), triggerZdt.toLocalTime())
        assertEquals(LocalDate.of(2025, 5, 2), triggerZdt.toLocalDate())

        // Calculate logical date at fire instant (07:00 on May 2 >= 04:00 boundary -> logical date May 2)
        val clockAtFire = object : AppClock {
            override fun instant() = trigger
            override fun zoneId() = zoneUtc
        }
        assertEquals(LocalDate.of(2025, 5, 2), clockAtFire.logicalDate(4))
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
            reminderTime = LocalTime.of(1, 30)
        )

        val fromInstant = Instant.parse("2025-11-01T20:00:00Z")
        val trigger1 = ReminderEngine.nextTrigger(
            habit = habit,
            fromInstant = fromInstant,
            zoneId = nyZone,
            quietHoursStart = quietStart,
            quietHoursEnd = quietEnd
        )

        assertNotNull(trigger1)

        val trigger2 = ReminderEngine.nextTrigger(
            habit = habit,
            fromInstant = trigger1!!.plusSeconds(60),
            zoneId = nyZone,
            quietHoursStart = quietStart,
            quietHoursEnd = quietEnd
        )

        assertNotNull(trigger2)
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

        val trigger1 = ReminderEngine.nextTrigger(habit, currentInstant, zoneUtc, quietHoursStart = quietStart, quietHoursEnd = quietEnd)
        assertEquals(Instant.parse("2025-05-01T09:00:00Z"), trigger1)

        currentInstant = trigger1!!.plusSeconds(60)
        val trigger2 = ReminderEngine.nextTrigger(habit, currentInstant, zoneUtc, quietHoursStart = quietStart, quietHoursEnd = quietEnd)
        assertEquals(Instant.parse("2025-05-02T09:00:00Z"), trigger2)

        currentInstant = trigger2!!.plusSeconds(60)
        val trigger3 = ReminderEngine.nextTrigger(habit, currentInstant, zoneUtc, quietHoursStart = quietStart, quietHoursEnd = quietEnd)
        assertEquals(Instant.parse("2025-05-03T09:00:00Z"), trigger3)
    }
}
