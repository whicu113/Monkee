package com.habitat.app.platform

import android.content.Context
import com.habitat.app.data.FakeCompletionDao
import com.habitat.app.data.repository.CompletionRepositoryImpl
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.logic.DropReason
import com.habitat.app.domain.logic.FireDecision
import com.habitat.app.domain.logic.ReminderEngine
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.LoggedCompletion
import com.habitat.app.domain.model.ScheduleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ReminderReceiverLogicTest {

    private val fixedToday = LocalDate.of(2025, 5, 10) // Saturday
    private val zoneUtc = ZoneId.of("UTC")
    private val clock = object : AppClock {
        override fun instant() = Instant.parse("2025-05-10T09:00:00Z")
        override fun zoneId() = zoneUtc
        override fun logicalDate(dayBoundaryHour: Int) = fixedToday
    }

    class FakeNotificationNotifier : NotificationNotifier {
        val postedHabits = mutableListOf<Habit>()
        val summaryCalls = mutableListOf<List<Habit>>()
        var throwOnPost = false

        override fun postHabitNotification(context: Context, habit: Habit, logicalDate: LocalDate, snoozeMinutes: Int) {
            if (throwOnPost) throw RuntimeException("Simulated notification posting failure")
            postedHabits.add(habit)
        }

        override fun postSummaryNotification(context: Context, overflowHabits: List<Habit>, logicalDate: LocalDate) {
            summaryCalls.add(overflowHabits)
        }
    }

    @Test
    fun `re-arm occurs even when notification posting throws exception`() = runTest {
        val habit = Habit(
            id = "h1",
            name = "Test",
            schedule = ScheduleType.Daily,
            reminderTime = LocalTime.of(9, 0)
        )
        val habitRepo = FakeHabitRepository()
        habitRepo.saveHabit(habit)

        val completionRepo = CompletionRepositoryImpl(FakeCompletionDao())
        val settingsRepo = FakeSettingsRepository()
        val alarmGateway = FakeAlarmGateway()
        ReminderScheduler.alarmGateway = alarmGateway

        val notifier = FakeNotificationNotifier().apply { throwOnPost = true }

        val handler = ReminderFulfillmentHandler(
            habitRepository = habitRepo,
            completionRepository = completionRepo,
            settingsRepository = settingsRepo,
            clock = clock,
            notifier = notifier
        )

        var exceptionThrown = false
        try {
            handler.execute(DummyContext(), "h1", null)
        } catch (_: Exception) {
            exceptionThrown = true
        }

        assertTrue(exceptionThrown)
        // Alarm was re-armed despite the downstream notification throw
        assertTrue(alarmGateway.isAlarmScheduled(DummyContext(), "h1", "alarm"))
    }

    @Test
    fun `evaluateFireTimeDecision Post adds habit id to posted ids and arms alarm`() = runTest {
        val habit = Habit(id = "h1", name = "Habit 1", schedule = ScheduleType.Daily, reminderTime = LocalTime.of(9, 0))
        val habitRepo = FakeHabitRepository()
        habitRepo.saveHabit(habit)
        val completionRepo = CompletionRepositoryImpl(FakeCompletionDao())
        val settingsRepo = FakeSettingsRepository()
        val gateway = FakeAlarmGateway()
        ReminderScheduler.alarmGateway = gateway
        val notifier = FakeNotificationNotifier()

        val handler = ReminderFulfillmentHandler(
            habitRepository = habitRepo,
            completionRepository = completionRepo,
            settingsRepository = settingsRepo,
            clock = clock,
            notifier = notifier
        )

        val decision = handler.execute(DummyContext(), "h1", null)

        assertEquals(FireDecision.Post, decision)
        val posted = settingsRepo.getPostedHabitIds(fixedToday.toString())
        assertEquals(setOf("h1"), posted)
        assertTrue(gateway.isAlarmScheduled(DummyContext(), "h1", "alarm"))
        assertEquals(1, notifier.postedHabits.size)
        assertEquals("h1", notifier.postedHabits[0].id)
    }

    @Test
    fun `evaluateFireTimeDecision Summary adds habit id to posted ids`() = runTest {
        val habit5 = Habit(id = "h5", name = "Habit 5", schedule = ScheduleType.Daily, reminderTime = LocalTime.of(9, 0))
        val habitRepo = FakeHabitRepository()
        habitRepo.saveHabit(habit5)
        val completionRepo = CompletionRepositoryImpl(FakeCompletionDao())
        val settingsRepo = FakeSettingsRepository()
        val gateway = FakeAlarmGateway()
        ReminderScheduler.alarmGateway = gateway
        val notifier = FakeNotificationNotifier()

        // 4 habits already posted today -> reaches cap (cap is 4: 3 individual + 1 summary)
        settingsRepo.addPostedHabitId(fixedToday.toString(), "h1")
        settingsRepo.addPostedHabitId(fixedToday.toString(), "h2")
        settingsRepo.addPostedHabitId(fixedToday.toString(), "h3")
        settingsRepo.addPostedHabitId(fixedToday.toString(), "h4")

        val handler = ReminderFulfillmentHandler(
            habitRepository = habitRepo,
            completionRepository = completionRepo,
            settingsRepository = settingsRepo,
            clock = clock,
            notifier = notifier
        )

        val decision = handler.execute(DummyContext(), "h5", null)

        assertEquals(FireDecision.Summary(habit5), decision)
        assertEquals(1, notifier.summaryCalls.size)
    }

    @Test
    fun `Drop decisions leave posted ids set unchanged`() = runTest {
        val habit = Habit(id = "h1", name = "Habit 1", schedule = ScheduleType.Daily, reminderTime = LocalTime.of(9, 0))
        val habitRepo = FakeHabitRepository()
        habitRepo.saveHabit(habit)
        val completionRepo = CompletionRepositoryImpl(FakeCompletionDao())
        val settingsRepo = FakeSettingsRepository()
        val gateway = FakeAlarmGateway()
        ReminderScheduler.alarmGateway = gateway
        val notifier = FakeNotificationNotifier()

        // 1. QuietHours -> Drop, posted ids remains empty
        val quietHabit = Habit(id = "h2", name = "Habit 2", schedule = ScheduleType.Daily, reminderTime = LocalTime.of(23, 0))
        habitRepo.saveHabit(quietHabit)
        val lateClock = object : AppClock {
            override fun instant() = Instant.parse("2025-05-10T23:05:00Z")
            override fun zoneId() = zoneUtc
            override fun logicalDate(dayBoundaryHour: Int) = fixedToday
        }
        val quietHandler = ReminderFulfillmentHandler(
            habitRepository = habitRepo,
            completionRepository = completionRepo,
            settingsRepository = settingsRepo,
            clock = lateClock,
            notifier = notifier
        )
        val decisionQuiet = quietHandler.execute(DummyContext(), "h2", null)
        assertEquals(FireDecision.Drop(DropReason.QuietHours), decisionQuiet)
        assertEquals(emptySet<String>(), settingsRepo.getPostedHabitIds(fixedToday.toString()))

        // 2. Duplicate -> Drop, posted ids remains unchanged
        settingsRepo.addPostedHabitId(fixedToday.toString(), "h1")
        val normalHandler = ReminderFulfillmentHandler(
            habitRepository = habitRepo,
            completionRepository = completionRepo,
            settingsRepository = settingsRepo,
            clock = clock,
            notifier = notifier
        )
        val decisionDup = normalHandler.execute(DummyContext(), "h1", null)
        assertEquals(FireDecision.Drop(DropReason.Duplicate), decisionDup)
        assertEquals(setOf("h1"), settingsRepo.getPostedHabitIds(fixedToday.toString()))
    }

    @Test
    fun `NPerWeek habit drops reminder when weekly quota is met through production path`() = runTest {
        // 2 per week habit, created Mon May 5
        val habit = Habit(
            id = "hw",
            name = "Weekly Habit",
            schedule = ScheduleType.NPerWeek(n = 2),
            reminderTime = LocalTime.of(9, 0)
        )
        val habitRepo = FakeHabitRepository()
        habitRepo.saveHabit(habit)

        val completionRepo = CompletionRepositoryImpl(FakeCompletionDao())
        val settingsRepo = FakeSettingsRepository()
        val gateway = FakeAlarmGateway()
        ReminderScheduler.alarmGateway = gateway
        val notifier = FakeNotificationNotifier()

        // Log 2 completions earlier in the week (e.g. Wed May 7, Thu May 8)
        completionRepo.logCompletion(
            LoggedCompletion(habitId = "hw", logicalDate = LocalDate.of(2025, 5, 7), state = CompletionState.DONE)
        )
        completionRepo.logCompletion(
            LoggedCompletion(habitId = "hw", logicalDate = LocalDate.of(2025, 5, 8), state = CompletionState.DONE)
        )

        val handler = ReminderFulfillmentHandler(
            habitRepository = habitRepo,
            completionRepository = completionRepo,
            settingsRepository = settingsRepo,
            clock = clock,
            notifier = notifier
        )

        val decision = handler.execute(DummyContext(), "hw", null)

        // Quota is met (2/2 done this week), so reminder is dropped with WeeklyQuotaMet
        assertEquals(FireDecision.Drop(DropReason.WeeklyQuotaMet), decision)
        assertEquals(emptySet<String>(), settingsRepo.getPostedHabitIds(fixedToday.toString()))
        assertEquals(0, notifier.postedHabits.size)
        // Alarm was still re-armed for the next occurrence
        assertTrue(gateway.isAlarmScheduled(DummyContext(), "hw", "alarm"))
    }
}
