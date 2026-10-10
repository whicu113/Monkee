package com.habitat.app.platform

import androidx.lifecycle.ViewModelStore
import com.habitat.app.data.FakeCompletionDao
import com.habitat.app.data.repository.CompletionRepositoryImpl
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.FakeCompletionReminderHandler
import com.habitat.app.domain.WidgetUpdater
import com.habitat.app.domain.createLogCompletionUseCaseFixture
import com.habitat.app.domain.repository.PlayerEconomyRepository
import com.habitat.app.ui.habit.HabitListUiState
import com.habitat.app.ui.habit.HabitViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HabitViewModelEconomyTest {

    @Test
    fun economyFlowsUpdateSeedsAndLevelInViewModelState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val viewModelStore = ViewModelStore()
        var collection: kotlinx.coroutines.Job? = null

        try {
            val habits = FakeHabitRepository()
            val completions = CompletionRepositoryImpl(FakeCompletionDao())
            val settings = FakeSettingsRepository()
            val economy = FakePlayerEconomyRepository()
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
                logCompletionUseCase = createLogCompletionUseCaseFixture(
                    completions,
                    clock,
                    FakeCompletionReminderHandler()
                ),
                reminderFulfillmentHandler = ReminderFulfillmentHandler(habits, completions, settings, clock),
                clock = clock,
                widgetUpdater = WidgetUpdater { },
                playerEconomyRepository = economy
            )
            viewModelStore.put("habit-economy", viewModel)
            val states = mutableListOf<HabitListUiState>()
            collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect { states += it }
            }

            advanceUntilIdle()
            val initial = states.last()
            assertEquals("zero Seeds balance", 0L, initial.seedsBalance)
            assertEquals("zero XP starts at level one", 1L, initial.level)

            economy.xpTotal.value = 100L
            economy.seedsBalance.value = 42L
            advanceUntilIdle()

            val updated = states.last()
            assertEquals("100 XP reaches level two", 2L, updated.level)
            assertEquals("level two starts at zero XP", 0L, updated.xpIntoLevel)
            assertEquals("level two requires 125 XP", 125L, updated.xpRequiredForNextLevel)
            assertEquals("Seeds balance follows repository emission", 42L, updated.seedsBalance)
        } finally {
            collection?.cancel()
            viewModelStore.clear()
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }
}

private class FakePlayerEconomyRepository : PlayerEconomyRepository {
    val seedsBalance = MutableStateFlow(0L)
    val xpTotal = MutableStateFlow(0L)

    override fun observeSeedsBalance() = seedsBalance
    override fun observeXpTotal() = xpTotal
}
