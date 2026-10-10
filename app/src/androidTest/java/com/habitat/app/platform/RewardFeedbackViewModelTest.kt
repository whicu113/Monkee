package com.habitat.app.platform

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.habitat.app.data.local.AppDatabase
import com.habitat.app.data.repository.CompletionRepositoryImpl
import com.habitat.app.data.repository.HabitRepositoryImpl
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.HabitRewardResult
import com.habitat.app.domain.WidgetUpdater
import com.habitat.app.domain.logic.RewardRandom
import com.habitat.app.domain.model.AppSettings
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.repository.SettingsRepository
import com.habitat.app.domain.usecase.CompletionReminderHandler
import com.habitat.app.domain.usecase.LogCompletionResultUseCase
import com.habitat.app.domain.usecase.LogCompletionUseCase
import com.habitat.app.data.local.RoomRewardTransactionRunner
import com.habitat.app.ui.habit.HabitViewModel
import com.habitat.app.ui.reward.RewardMessageKey
import com.habitat.app.ui.reward.RewardMessageSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class RewardFeedbackViewModelTest {

    private lateinit var database: AppDatabase
    private lateinit var reminderGatewayBeforeTest: AlarmGateway

    @Before
    fun setUp() {
        reminderGatewayBeforeTest = ReminderScheduler.alarmGateway
        ReminderScheduler.alarmGateway = RecordingAlarmGateway()
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
        ReminderScheduler.alarmGateway = reminderGatewayBeforeTest
    }

    @Test
    fun rewardFeedbackRowsUseTheResultStubWithoutChangingLoggingCallbacks() = runBlocking {
        val viewModelStore = ViewModelStore()
        val clock = object : AppClock {
            override fun instant() = Instant.parse("2025-05-12T12:00:00Z")
            override fun zoneId() = ZoneId.of("UTC")
        }
        val today = LocalDate.parse("2025-05-12")
        val habits = HabitRepositoryImpl(database.habitDao())
        val completions = CompletionRepositoryImpl(database.completionDao())
        val settings = FeedbackSettingsRepository()
        val reminderCalls = CopyOnWriteArrayList<Pair<String, LocalDate>>()
        val widgetUpdates = AtomicInteger()
        val useCase = LogCompletionUseCase(
            completionRepository = completions,
            clock = clock,
            reminderHandler = object : CompletionReminderHandler {
                override suspend fun onCompletionLogged(habitId: String, logicalDate: LocalDate) {
                    reminderCalls += habitId to logicalDate
                }
            },
            widgetUpdater = WidgetUpdater { widgetUpdates.incrementAndGet() },
            settingsRepository = settings,
            transactionRunner = RoomRewardTransactionRunner(database),
            rewardRandom = RewardRandom { boundExclusive -> boundExclusive - 1 }
        )
        val resultApi: LogCompletionResultUseCase = useCase
        val viewModel = HabitViewModel(
            context = ApplicationProvider.getApplicationContext<Context>(),
            habitRepository = habits,
            completionRepository = completions,
            settingsRepository = settings,
            logCompletionUseCase = useCase,
            reminderFulfillmentHandler = ReminderFulfillmentHandler(habits, completions, settings, clock),
            clock = clock,
            widgetUpdater = WidgetUpdater { widgetUpdates.incrementAndGet() },
            playerEconomyRepository = object : com.habitat.app.domain.repository.PlayerEconomyRepository {
                override fun observeSeedsBalance(): Flow<Long> = kotlinx.coroutines.flow.flowOf(0L)
                override fun observeXpTotal(): Flow<Long> = kotlinx.coroutines.flow.flowOf(0L)
            }
        )
        viewModelStore.put("reward-feedback", viewModel)
        val failures = mutableListOf<String>()
        fun <T> row(name: String, expected: T, actual: T) {
            if (expected != actual) failures += "$name expected <$expected> but was <$actual>"
        }
        suspend fun nextEvent() = withTimeoutOrNull(10L) { viewModel.rewardMessageEvents.first() }
        suspend fun toggleAndWaitForReminder(habitId: String, state: CompletionState, date: LocalDate) {
            val before = reminderCalls.size
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModel.toggleCheckOff(habitId, state, date)
            }
            withTimeout(5_000L) {
                while (reminderCalls.size <= before) delay(10L)
            }
            delay(50L)
        }
        suspend fun toggleWithoutWaitingForReminder(habitId: String, state: CompletionState, date: LocalDate) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModel.toggleCheckOff(habitId, state, date)
            }
            delay(100L)
        }

        try {
            val firstHabit = Habit(id = "feedback-first", name = "First", createdAt = clock.instant())
            habits.saveHabit(firstHabit)
            toggleAndWaitForReminder(firstHabit.id, CompletionState.DONE, today)

            row(
                "result interface call uses habit, state, and logical date",
                CompletionState.DONE to today,
                completions.getCompletion(firstHabit.id, today)?.let { it.state to it.logicalDate }
            )
            row("first grant writes its completion", true, completions.getCompletion(firstHabit.id, today) != null)
            row("first grant calls reminder handler once", listOf(firstHabit.id to today), reminderCalls.toList())
            row("first grant calls widget updater once", 1, widgetUpdates.get())
            row(
                "first grant publishes ordered Seeds and XP feedback",
                listOf(RewardMessageSpec(RewardMessageKey.SEEDS_EARNED, listOf(11)), RewardMessageSpec(RewardMessageKey.XP_EARNED, listOf(10))),
                nextEvent()
            )
            row("first grant event is consumed once", null, nextEvent())

            val duplicate = Habit(id = "feedback-duplicate", name = "Duplicate", createdAt = clock.instant())
            habits.saveHabit(duplicate)
            val reminderCountBeforeDuplicate = reminderCalls.size
            val widgetCountBeforeDuplicate = widgetUpdates.get()
            toggleAndWaitForReminder(duplicate.id, CompletionState.DONE, today)
            toggleWithoutWaitingForReminder(duplicate.id, CompletionState.DONE, today)
            val duplicateResult = resultApi.executeLogWithResult(duplicate.id, CompletionState.DONE, today)
            row(
                "rapid duplicate second result is already rewarded",
                HabitRewardResult.NoReward(com.habitat.app.domain.RewardNoRewardReason.ALREADY_REWARDED),
                duplicateResult
            )
            row("rapid duplicate feedback is one first-grant message", listOf(RewardMessageSpec(RewardMessageKey.SEEDS_EARNED, listOf(11)), RewardMessageSpec(RewardMessageKey.XP_EARNED, listOf(10))), nextEvent())
            row("rapid duplicate has no second message", null, nextEvent())
            row("rapid duplicate reminder callback count is one", 1, reminderCalls.drop(reminderCountBeforeDuplicate).count { it.first == duplicate.id })
            row("rapid duplicate widget callback count is one", 1, widgetUpdates.get() - widgetCountBeforeDuplicate)

            val doneMinimum = Habit(id = "feedback-done-minimum", name = "Done then Minimum", createdAt = clock.instant())
            habits.saveHabit(doneMinimum)
            toggleAndWaitForReminder(doneMinimum.id, CompletionState.DONE, today)
            toggleAndWaitForReminder(doneMinimum.id, CompletionState.MINIMUM, today)
            row("Done then Minimum keeps one first-grant message", listOf(RewardMessageSpec(RewardMessageKey.SEEDS_EARNED, listOf(11)), RewardMessageSpec(RewardMessageKey.XP_EARNED, listOf(10))), nextEvent())
            row("Done then Minimum has no second message", null, nextEvent())

            val backfill = Habit(id = "feedback-backfill", name = "Backfill", createdAt = clock.instant())
            habits.saveHabit(backfill)
            toggleAndWaitForReminder(backfill.id, CompletionState.DONE, today.minusDays(1))
            row("backfill result has no reward", HabitRewardResult.NoReward(com.habitat.app.domain.RewardNoRewardReason.BACKFILL), resultApi.executeLogWithResult(backfill.id, CompletionState.DONE, today.minusDays(1)))
            row("backfill completion is retained", true, completions.getCompletion(backfill.id, today.minusDays(1)) != null)
            row("backfill publishes no message", null, nextEvent())

            val rejected = Habit(id = "feedback-rejected", name = "Rejected", createdAt = clock.instant())
            habits.saveHabit(rejected)
            for ((rowName, rejectedDate) in listOf("future date" to today.plusDays(1), "older than seven days" to today.minusDays(8))) {
                toggleWithoutWaitingForReminder(rejected.id, CompletionState.DONE, rejectedDate)
                row("$rowName result is BACKFILL", HabitRewardResult.NoReward(com.habitat.app.domain.RewardNoRewardReason.BACKFILL), resultApi.executeLogWithResult(rejected.id, CompletionState.DONE, rejectedDate))
                row("$rowName writes no completion", null, completions.getCompletion(rejected.id, rejectedDate))
                row("$rowName publishes no message", null, nextEvent())
            }

            assertTrue("reward feedback assertion rows:\n${failures.joinToString("\n")}", failures.isEmpty())
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { viewModelStore.clear() }
        }
    }
}

private class FeedbackSettingsRepository : SettingsRepository {
    override fun getSettings(): Flow<AppSettings> = kotlinx.coroutines.flow.flowOf(AppSettings())
    override suspend fun updateSettings(settings: AppSettings) = Unit
    override suspend fun getPostedHabitIds(logicalDateString: String) = emptySet<String>()
    override suspend fun addPostedHabitId(logicalDateString: String, habitId: String) = Unit
    override suspend fun clearPostedHabitIds(logicalDateString: String) = Unit
    override suspend fun pruneOldPostedHabitIds(cutoffDateString: String) = Unit
}

private class RecordingAlarmGateway : AlarmGateway {
    override fun scheduleAlarm(context: Context, habitId: String, actionPath: String, triggerAtMillis: Long, logicalDateExtra: String?) = Unit
    override fun cancelAlarm(context: Context, habitId: String, actionPath: String) = Unit
    override fun isAlarmScheduled(context: Context, habitId: String, actionPath: String) = false
    override fun scheduleDayBoundaryAlarm(context: Context, triggerAtMillis: Long) = Unit
    override fun cancelDayBoundaryAlarm(context: Context) = Unit
    override fun isDayBoundaryAlarmScheduled(context: Context) = false
}
