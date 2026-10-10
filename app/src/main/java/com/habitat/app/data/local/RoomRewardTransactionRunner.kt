package com.habitat.app.data.local

import androidx.room.withTransaction
import com.habitat.app.domain.RewardCompletionTransactionPort
import com.habitat.app.domain.RewardDropRecord
import com.habitat.app.domain.RewardDropTransactionPort
import com.habitat.app.domain.RewardHabitTransactionPort
import com.habitat.app.domain.RewardLedgerEntry
import com.habitat.app.domain.RewardLedgerTransactionPort
import com.habitat.app.domain.RewardTransactionRunner
import com.habitat.app.domain.RewardTransactionScope
import com.habitat.app.domain.logic.DropRarity
import com.habitat.app.domain.logic.RewardCurrency
import com.habitat.app.domain.logic.RewardStage
import com.habitat.app.domain.model.Difficulty
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.LoggedCompletion
import java.time.LocalDate
import javax.inject.Inject

class RoomRewardTransactionRunner @Inject constructor(
    private val database: AppDatabase
) : RewardTransactionRunner {

    override suspend fun <T> inTransaction(block: suspend RewardTransactionScope.() -> T): T =
        database.withTransaction {
            RoomRewardTransactionScope(
                habitDao = database.habitDao(),
                completionDao = database.completionDao(),
                ledgerEntryDao = database.ledgerEntryDao(),
                dropRecordDao = database.dropRecordDao()
            ).block()
        }
}

private class RoomRewardTransactionScope(
    habitDao: HabitDao,
    completionDao: CompletionDao,
    ledgerEntryDao: LedgerEntryDao,
    dropRecordDao: DropRecordDao
) : RewardTransactionScope {
    override val habits: RewardHabitTransactionPort = RoomRewardHabitPort(habitDao)
    override val completions: RewardCompletionTransactionPort = RoomRewardCompletionPort(completionDao)
    override val ledger: RewardLedgerTransactionPort = RoomRewardLedgerPort(ledgerEntryDao)
    override val drops: RewardDropTransactionPort = RoomRewardDropPort(dropRecordDao)
}

private class RoomRewardHabitPort(private val dao: HabitDao) : RewardHabitTransactionPort {
    override suspend fun getById(habitId: String): Habit? = dao.getHabitById(habitId)?.toDomainModel()
}

private class RoomRewardCompletionPort(private val dao: CompletionDao) : RewardCompletionTransactionPort {
    override suspend fun get(habitId: String, logicalDate: LocalDate): LoggedCompletion? =
        dao.getCompletion(habitId, logicalDate)?.toDomainModel()

    override suspend fun forHabit(habitId: String): List<LoggedCompletion> =
        dao.getCompletionsForHabitOnce(habitId).map(CompletionEntity::toDomainModel)

    override suspend fun put(completion: LoggedCompletion) {
        dao.insertOrUpdate(completion.toEntity())
    }

    override suspend fun remove(habitId: String, logicalDate: LocalDate) {
        dao.deleteCompletion(habitId, logicalDate)
    }
}

private class RoomRewardLedgerPort(private val dao: LedgerEntryDao) : RewardLedgerTransactionPort {
    override suspend fun insert(entry: RewardLedgerEntry): Long = dao.insert(entry.toEntity())

    override suspend fun sumPositiveHabitSeeds(logicalDate: LocalDate): Long =
        dao.sumPositiveHabitSeeds(logicalDate)

    override suspend fun highestStage(habitId: String, logicalDate: LocalDate): RewardStage? =
        dao.highestStageForHabitDate(habitId, logicalDate)?.let(RewardStage::valueOf)

    override suspend fun stageRowsForSavedBasis(habitId: String, logicalDate: LocalDate): List<RewardLedgerEntry> =
        dao.stageRowsForSavedBasis(habitId, logicalDate).map(LedgerEntryEntity::toDomainModel)

    override suspend fun latestFirstDoneRowForPity(): RewardLedgerEntry? =
        dao.latestFirstDoneRowForPity()?.toDomainModel()

    override suspend fun distinctPaidDatesInRangeExcludingDate(
        habitId: String,
        startDate: LocalDate,
        endDate: LocalDate,
        excludedDate: LocalDate
    ): List<LocalDate> = dao.distinctPaidDatesForHabitInRangeExcludingDate(
        habitId = habitId,
        startDate = startDate,
        endDate = endDate,
        excludedDate = excludedDate
    )

    override suspend fun balance(currency: RewardCurrency): Long = dao.balanceForCurrency(currency.name)

    override suspend fun totalXp(): Long = dao.totalXp()
}

private class RoomRewardDropPort(private val dao: DropRecordDao) : RewardDropTransactionPort {
    override suspend fun insert(record: RewardDropRecord) {
        dao.insert(record.toEntity())
    }
}

private fun LedgerEntryEntity.toDomainModel() = RewardLedgerEntry(
    id = id,
    currency = RewardCurrency.valueOf(currency),
    delta = delta,
    reason = reason,
    habitId = habitId,
    logicalDate = logicalDate,
    createdAt = createdAt,
    idempotencyKey = idempotencyKey,
    rewardStage = rewardStage?.let(RewardStage::valueOf),
    difficulty = difficulty?.let(Difficulty::valueOf),
    streakLength = streakLength,
    comebackApplied = comebackApplied,
    stageEntitlementAmount = stageEntitlementAmount,
    pityCounterAfter = pityCounterAfter
)

private fun RewardLedgerEntry.toEntity() = LedgerEntryEntity(
    id = id,
    currency = currency.name,
    delta = delta,
    reason = reason,
    habitId = habitId,
    logicalDate = logicalDate,
    createdAt = createdAt,
    idempotencyKey = idempotencyKey,
    rewardStage = rewardStage?.name,
    difficulty = difficulty?.name,
    streakLength = streakLength,
    comebackApplied = comebackApplied,
    stageEntitlementAmount = stageEntitlementAmount,
    pityCounterAfter = pityCounterAfter
)

private fun RewardDropRecord.toEntity() = DropRecordEntity(
    id = id,
    rarity = rarity.name,
    habitId = habitId,
    logicalDate = logicalDate,
    createdAt = createdAt
)
