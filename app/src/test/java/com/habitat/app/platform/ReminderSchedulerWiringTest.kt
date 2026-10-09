package com.habitat.app.platform

import com.habitat.app.domain.AppClock
import com.habitat.app.domain.logic.ReminderEngine
import com.habitat.app.domain.model.AppSettings
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class ReminderSchedulerWiringTest {

    private val zoneUtc = ZoneId.of("UTC")
    private val clock = object : AppClock {
        override fun instant() = Instant.parse("2025-05-10T20:00:00Z") // 20:00
        override fun zoneId() = zoneUtc
    }

    @Test
    fun `reconcile with quiet hours enabled defers 2230 reminder to 0700`() {
        val habit = Habit(
            id = "h1",
            name = "Late Habit",
            schedule = ScheduleType.Daily,
            reminderTime = LocalTime.of(22, 30)
        )
        val settings = AppSettings(
            quietHoursEnabled = true,
            quietHoursStart = LocalTime.of(22, 0),
            quietHoursEnd = LocalTime.of(7, 0)
        )

        val trigger = ReminderEngine.nextTrigger(
            habit = habit,
            fromInstant = clock.instant(),
            zoneId = clock.zoneId(),
            quietHoursEnabled = settings.quietHoursEnabled,
            quietHoursStart = settings.quietHoursStart,
            quietHoursEnd = settings.quietHoursEnd
        )

        assertNotNull(trigger)
        assertEquals(Instant.parse("2025-05-11T07:00:00Z"), trigger)
    }

    @Test
    fun `reconcile with quiet hours disabled re-arms 2230 reminder at 2230 same day`() {
        val habit = Habit(
            id = "h1",
            name = "Late Habit",
            schedule = ScheduleType.Daily,
            reminderTime = LocalTime.of(22, 30)
        )
        val settings = AppSettings(
            quietHoursEnabled = false,
            quietHoursStart = LocalTime.of(22, 0),
            quietHoursEnd = LocalTime.of(7, 0)
        )

        val trigger = ReminderEngine.nextTrigger(
            habit = habit,
            fromInstant = clock.instant(),
            zoneId = clock.zoneId(),
            quietHoursEnabled = settings.quietHoursEnabled,
            quietHoursStart = settings.quietHoursStart,
            quietHoursEnd = settings.quietHoursEnd
        )

        assertNotNull(trigger)
        assertEquals(Instant.parse("2025-05-10T22:30:00Z"), trigger)
    }
}
