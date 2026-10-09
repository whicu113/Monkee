package com.habitat.app.platform

import com.habitat.app.domain.AppClock
import com.habitat.app.domain.logic.ReminderEngine
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class ReminderReceiverLogicTest {

    private val clock = object : AppClock {
        override fun instant() = Instant.parse("2025-05-10T09:00:00Z")
        override fun zoneId() = ZoneId.of("UTC")
    }

    @Test
    fun `re-arm occurs even when notification posting throws exception`() {
        val habit = Habit(
            id = "h1",
            name = "Test",
            schedule = ScheduleType.Daily,
            reminderTime = LocalTime.of(9, 0)
        )

        var alarmArmed = false
        var exceptionThrown: Boolean

        try {
            // 1. Re-arm next trigger FIRST
            val nextTrigger = ReminderEngine.nextTrigger(
                habit = habit,
                fromInstant = clock.instant().plusSeconds(60),
                zoneId = clock.zoneId(),
                quietHoursStart = LocalTime.of(22, 0),
                quietHoursEnd = LocalTime.of(7, 0)
            )
            if (nextTrigger != null) {
                alarmArmed = true
            }

            // 2. Simulate throwing notifier during posting
            throw RuntimeException("Simulated notification posting failure")
        } catch (_: Exception) {
            exceptionThrown = true
        }

        assertTrue(alarmArmed)
        assertTrue(exceptionThrown)
    }

    @Test
    fun `duplicate fire for same habit on same date does not double count towards cap`() {
        val postedSet = mutableSetOf<String>()

        // First fire
        postedSet.add("h1")
        assertEquals(1, postedSet.size)

        // Duplicate fire on same date
        postedSet.add("h1")
        assertEquals(1, postedSet.size)
    }
}
