package com.habitat.app.platform

import com.habitat.app.data.FakeCompletionDao
import com.habitat.app.data.repository.CompletionRepositoryImpl
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.ScheduleType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class PlatformCompletionReminderHandlerTest {

    private val clock = object : AppClock {
        override fun instant() = Instant.parse("2025-05-10T12:00:00Z")
        override fun zoneId() = ZoneId.of("UTC")
    }

    @Test
    fun `onCompletionLogged cancels snooze alarm and reconciles next occurrence`() = runTest {
        val fakeSettingsRepo = FakeSettingsRepository()
        val fakeHabitRepo = FakeHabitRepository()
        val fakeGateway = FakeAlarmGateway()
        ReminderScheduler.alarmGateway = fakeGateway

        val habit = Habit(
            id = "h1",
            name = "Test Habit",
            schedule = ScheduleType.Daily,
            reminderTime = LocalTime.of(15, 0)
        )
        fakeHabitRepo.saveHabit(habit)
        val completionRepo = CompletionRepositoryImpl(FakeCompletionDao())

        // Set up active snooze alarm
        fakeGateway.scheduleAlarm(DummyContext(), "h1", "snooze_alarm", 123456L)
        assertTrue(fakeGateway.isAlarmScheduled(DummyContext(), "h1", "snooze_alarm"))

        val handler = PlatformCompletionReminderHandler(
            context = DummyContext(),
            habitRepository = fakeHabitRepo,
            completionRepository = completionRepo,
            settingsRepository = fakeSettingsRepo,
            clock = clock
        )

        handler.onCompletionLogged("h1", LocalDate.of(2025, 5, 10))

        // Snooze alarm cancelled
        assertFalse(fakeGateway.isAlarmScheduled(DummyContext(), "h1", "snooze_alarm"))
        // Main reminder alarm reconciled and scheduled
        assertTrue(fakeGateway.isAlarmScheduled(DummyContext(), "h1", "alarm"))
    }
}
