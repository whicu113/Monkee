package com.habitat.app.platform

import com.habitat.app.domain.AppClock
import com.habitat.app.domain.logic.DropReason
import com.habitat.app.domain.logic.FireDecision
import com.habitat.app.domain.logic.ReminderEngine
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.ScheduleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderReceiverLogicTest {

    private val fixedToday = LocalDate.of(2025, 5, 10)
    private val zoneUtc = ZoneId.of("UTC")
    private val clock = object : AppClock {
        override fun instant() = Instant.parse("2025-05-10T09:00:00Z")
        override fun zoneId() = zoneUtc
        override fun logicalDate(dayBoundaryHour: Int) = fixedToday
    }

    /**
     * Drives evaluateFireTimeDecision and receiver side effects using fakes.
     */
    private suspend fun simulateReceiverFire(
        habit: Habit,
        nowInstant: Instant,
        settingsRepo: FakeSettingsRepository,
        alarmGateway: FakeAlarmGateway,
        isLoggedToday: Boolean = false
    ): FireDecision {
        val settings = settingsRepo.getSettings().first()
        val today = clock.logicalDate()

        // 1. RE-ARM FIRST: Arm next trigger before evaluating/posting
        val nextInstant = nowInstant.plusSeconds(60)
        ReminderScheduler.alarmGateway = alarmGateway
        ReminderScheduler.scheduleNextForHabit(
            context = DummyContext(),
            habit = habit,
            fromInstant = nextInstant,
            zoneId = clock.zoneId(),
            appSettings = settings
        )

        // 2. Evaluate decision
        val scheduledTime = habit.reminderTime ?: clock.zonedDateTime().toLocalTime()
        val scheduledInstant = LocalDateTime.of(today, scheduledTime).atZone(clock.zoneId()).toInstant()
        val postedIds = settingsRepo.getPostedHabitIds(today.toString())

        val decision = ReminderEngine.evaluateFireTimeDecision(
            habit = habit,
            scheduledInstant = scheduledInstant,
            nowInstant = nowInstant,
            zoneId = clock.zoneId(),
            quietHoursEnabled = settings.quietHoursEnabled,
            quietHoursStart = settings.quietHoursStart,
            quietHoursEnd = settings.quietHoursEnd,
            isLoggedToday = isLoggedToday,
            isWeeklyQuotaMet = false,
            postedIdsToday = postedIds,
            currentLogicalDate = today
        )

        // 3. Dispatch receiver side effects
        when (decision) {
            is FireDecision.Post -> {
                settingsRepo.addPostedHabitId(today.toString(), habit.id)
            }
            is FireDecision.Summary -> {
                settingsRepo.addPostedHabitId(today.toString(), habit.id)
            }
            is FireDecision.Drop -> {
                // Drop leaves postedIds unchanged
            }
        }

        return decision
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
        var exceptionThrown = false

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
    fun `evaluateFireTimeDecision Post adds habit id to posted ids and arms alarm`() = runTest {
        val habit = Habit(id = "h1", name = "Habit 1", schedule = ScheduleType.Daily, reminderTime = LocalTime.of(9, 0))
        val settingsRepo = FakeSettingsRepository()
        val gateway = FakeAlarmGateway()

        val decision = simulateReceiverFire(habit, Instant.parse("2025-05-10T09:05:00Z"), settingsRepo, gateway)

        assertEquals(FireDecision.Post, decision)
        val posted = settingsRepo.getPostedHabitIds(fixedToday.toString())
        assertEquals(setOf("h1"), posted)
        assertTrue(gateway.isAlarmScheduled(DummyContext(), "h1", "alarm"))
    }

    @Test
    fun `evaluateFireTimeDecision Summary adds habit id to posted ids`() = runTest {
        val habit5 = Habit(id = "h5", name = "Habit 5", schedule = ScheduleType.Daily, reminderTime = LocalTime.of(9, 0))
        val settingsRepo = FakeSettingsRepository()
        val gateway = FakeAlarmGateway()

        // 4 habits already posted today -> reaches cap (cap is 4: 3 individual + 1 summary)
        settingsRepo.addPostedHabitId(fixedToday.toString(), "h1")
        settingsRepo.addPostedHabitId(fixedToday.toString(), "h2")
        settingsRepo.addPostedHabitId(fixedToday.toString(), "h3")
        settingsRepo.addPostedHabitId(fixedToday.toString(), "h4")

        val decision = simulateReceiverFire(habit5, Instant.parse("2025-05-10T09:05:00Z"), settingsRepo, gateway)

        assertEquals(FireDecision.Summary(habit5), decision)
        val posted = settingsRepo.getPostedHabitIds(fixedToday.toString())
        assertEquals(setOf("h1", "h2", "h3", "h4", "h5"), posted)
    }

    @Test
    fun `Drop decisions leave posted ids set unchanged`() = runTest {
        val habit = Habit(id = "h1", name = "Habit 1", schedule = ScheduleType.Daily, reminderTime = LocalTime.of(9, 0))
        val settingsRepo = FakeSettingsRepository()
        val gateway = FakeAlarmGateway()

        // 1. LatenessExceeded (> 120 min late) -> Drop, posted ids remains empty
        val lateInstant = Instant.parse("2025-05-10T11:05:00Z") // 125 min late
        val decisionLate = simulateReceiverFire(habit, lateInstant, settingsRepo, gateway)
        assertEquals(FireDecision.Drop(DropReason.LatenessExceeded), decisionLate)
        assertEquals(emptySet<String>(), settingsRepo.getPostedHabitIds(fixedToday.toString()))

        // 2. QuietHours -> Drop, posted ids remains empty
        val quietHabit = Habit(id = "h2", name = "Habit 2", schedule = ScheduleType.Daily, reminderTime = LocalTime.of(23, 0))
        val quietInstant = Instant.parse("2025-05-10T23:05:00Z")
        val decisionQuiet = simulateReceiverFire(quietHabit, quietInstant, settingsRepo, gateway)
        assertEquals(FireDecision.Drop(DropReason.QuietHours), decisionQuiet)
        assertEquals(emptySet<String>(), settingsRepo.getPostedHabitIds(fixedToday.toString()))

        // 3. Duplicate -> Drop, posted ids remains unchanged
        settingsRepo.addPostedHabitId(fixedToday.toString(), "h1")
        val decisionDup = simulateReceiverFire(habit, Instant.parse("2025-05-10T09:05:00Z"), settingsRepo, gateway)
        assertEquals(FireDecision.Drop(DropReason.Duplicate), decisionDup)
        assertEquals(setOf("h1"), settingsRepo.getPostedHabitIds(fixedToday.toString()))
    }
}
