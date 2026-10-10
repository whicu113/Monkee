package com.habitat.app.platform

import com.habitat.app.data.FakeCompletionDao
import com.habitat.app.data.repository.CompletionRepositoryImpl
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.WidgetUpdater
import com.habitat.app.domain.createLogCompletionUseCaseFixture
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.repository.PlayerEconomyRepository
import com.habitat.app.domain.usecase.LogCompletionUseCase
import com.habitat.app.domain.FakeCompletionReminderHandler
import com.habitat.app.ui.habit.HabitViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HabitWidgetUpdaterIntegrationTest {
    private lateinit var originalAlarmGateway: AlarmGateway

    @Before
    fun saveAlarmGateway() {
        originalAlarmGateway = ReminderScheduler.alarmGateway
    }

    @After
    fun restoreAlarmGateway() {
        ReminderScheduler.alarmGateway = originalAlarmGateway
    }

    @Test
    fun `habit save remains successful when widget refresh throws for row`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        val habits = FakeHabitRepository()
        val completions = CompletionRepositoryImpl(FakeCompletionDao())
        val settings = FakeSettingsRepository()
        val clock = object : AppClock {
            override fun instant() = Instant.parse("2025-05-10T12:00:00Z")
            override fun zoneId() = ZoneId.of("UTC")
        }
        ReminderScheduler.alarmGateway = FakeAlarmGateway()
        val viewModel = HabitViewModel(
            context = DummyContext(),
            habitRepository = habits,
            completionRepository = completions,
            settingsRepository = settings,
            logCompletionUseCase = createLogCompletionUseCaseFixture(completions, clock, FakeCompletionReminderHandler()),
            reminderFulfillmentHandler = ReminderFulfillmentHandler(habits, completions, settings, clock),
            clock = clock,
            widgetUpdater = WidgetUpdater { throw IllegalStateException("provider unavailable") },
            playerEconomyRepository = object : PlayerEconomyRepository {
                override fun observeSeedsBalance() = flowOf(0L)
                override fun observeXpTotal() = flowOf(0L)
            }
        )
        val habit = Habit(id = "walk-id", name = "Morning walk")

        try {
            viewModel.saveHabit(habit)
            advanceUntilIdle()

            assertEquals("row 'Morning walk' is saved", habit, habits.getHabitById(habit.id))
        } finally {
            Dispatchers.resetMain()
        }
    }
}
