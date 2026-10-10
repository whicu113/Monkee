package com.habitat.app.domain

import com.habitat.app.domain.logic.RewardCurrency
import com.habitat.app.domain.logic.RewardRandom
import com.habitat.app.domain.logic.RewardStage
import com.habitat.app.domain.model.AppSettings
import com.habitat.app.domain.model.Difficulty
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.LoggedCompletion
import com.habitat.app.domain.repository.CompletionRepository
import com.habitat.app.domain.repository.SettingsRepository
import com.habitat.app.domain.usecase.CompletionReminderHandler
import com.habitat.app.domain.usecase.LogCompletionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/** Shared factory for use-case tests so constructor changes stay localized to this fixture. */
internal fun createLogCompletionUseCaseFixture(
    completionRepository: CompletionRepository,
    clock: AppClock,
    reminderHandler: CompletionReminderHandler,
    widgetUpdater: WidgetUpdater = WidgetUpdater { }
): LogCompletionUseCase = LogCompletionUseCase(
    completionRepository = completionRepository,
    clock = clock,
    reminderHandler = reminderHandler,
    widgetUpdater = widgetUpdater,
    settingsRepository = FixtureSettingsRepository,
    transactionRunner = FixtureRewardTransactionRunner(completionRepository),
    rewardRandom = RewardRandom { 99 }
)

private object FixtureSettingsRepository : SettingsRepository {
    override fun getSettings(): Flow<AppSettings> = flowOf(AppSettings())
    override suspend fun updateSettings(settings: AppSettings) = Unit
    override suspend fun getPostedHabitIds(logicalDateString: String) = emptySet<String>()
    override suspend fun addPostedHabitId(logicalDateString: String, habitId: String) = Unit
    override suspend fun clearPostedHabitIds(logicalDateString: String) = Unit
    override suspend fun pruneOldPostedHabitIds(cutoffDateString: String) = Unit
}

private class FixtureRewardTransactionRunner(
    private val completionRepository: CompletionRepository
) : RewardTransactionRunner {
    private val habit = Habit(id = "fixture-habit", name = "Fixture", createdAt = java.time.Instant.EPOCH)

    override suspend fun <T> inTransaction(block: suspend RewardTransactionScope.() -> T): T {
        val scope = object : RewardTransactionScope {
            override val habits = object : RewardHabitTransactionPort {
                override suspend fun getById(habitId: String): Habit = habit.copy(id = habitId)
            }
            override val completions = object : RewardCompletionTransactionPort {
                override suspend fun get(habitId: String, logicalDate: LocalDate) = completionRepository.getCompletion(habitId, logicalDate)
                override suspend fun forHabit(habitId: String) = completionRepository.getCompletionsForHabit(habitId).first()
                override suspend fun put(completion: LoggedCompletion) = completionRepository.logCompletion(completion)
                override suspend fun remove(habitId: String, logicalDate: LocalDate) = completionRepository.removeCompletion(habitId, logicalDate)
            }
            override val ledger = object : RewardLedgerTransactionPort {
                private val rows = mutableListOf<RewardLedgerEntry>()
                override suspend fun insert(entry: RewardLedgerEntry): Long { rows += entry; return rows.size.toLong() }
                override suspend fun sumPositiveHabitSeeds(logicalDate: LocalDate) = rows.filter { it.currency == RewardCurrency.SEEDS && it.logicalDate == logicalDate && it.delta > 0 }.sumOf { it.delta }
                override suspend fun highestStage(habitId: String, logicalDate: LocalDate) = rows.filter { it.habitId == habitId && it.logicalDate == logicalDate }.mapNotNull { it.rewardStage }.maxByOrNull { it.ordinal }
                override suspend fun stageRowsForSavedBasis(habitId: String, logicalDate: LocalDate) = rows.filter { it.habitId == habitId && it.logicalDate == logicalDate }
                override suspend fun latestFirstDoneRowForPity() = rows.lastOrNull { it.currency == RewardCurrency.SEEDS && it.rewardStage == RewardStage.DONE }
                override suspend fun distinctPaidDatesInRangeExcludingDate(habitId: String, startDate: LocalDate, endDate: LocalDate, excludedDate: LocalDate) = rows.filter { it.habitId == habitId && it.currency == RewardCurrency.SEEDS && it.rewardStage in setOf(RewardStage.MINIMUM, RewardStage.DONE) }.mapNotNull { it.logicalDate?.takeIf { date -> date in startDate..endDate && date != excludedDate } }.distinct()
                override suspend fun balance(currency: RewardCurrency) = rows.filter { it.currency == currency }.sumOf { it.delta }
                override suspend fun totalXp() = balance(RewardCurrency.XP)
            }
            override val drops = object : RewardDropTransactionPort {
                override suspend fun insert(record: RewardDropRecord) = Unit
            }
        }
        return scope.block()
    }
}
