package com.habitat.app.domain

import com.habitat.app.domain.logic.DropRarity
import com.habitat.app.domain.logic.RewardCurrency
import com.habitat.app.domain.logic.RewardStage
import com.habitat.app.domain.model.Difficulty
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.LoggedCompletion
import java.time.Instant
import java.time.LocalDate

/** Runs reward-related reads and writes in one storage transaction. */
interface RewardTransactionRunner {
    suspend fun <T> inTransaction(block: suspend RewardTransactionScope.() -> T): T
}

/** Domain-only ports available while the reward transaction is active. */
interface RewardTransactionScope {
    val habits: RewardHabitTransactionPort
    val completions: RewardCompletionTransactionPort
    val ledger: RewardLedgerTransactionPort
    val drops: RewardDropTransactionPort
}

interface RewardHabitTransactionPort {
    suspend fun getById(habitId: String): Habit?
}

interface RewardCompletionTransactionPort {
    suspend fun get(habitId: String, logicalDate: LocalDate): LoggedCompletion?
    suspend fun forHabit(habitId: String): List<LoggedCompletion>
    suspend fun put(completion: LoggedCompletion)
    suspend fun remove(habitId: String, logicalDate: LocalDate)
}

interface RewardLedgerTransactionPort {
    suspend fun insert(entry: RewardLedgerEntry): Long
    suspend fun sumPositiveHabitSeeds(logicalDate: LocalDate): Long
    suspend fun highestStage(habitId: String, logicalDate: LocalDate): RewardStage?
    suspend fun stageRowsForSavedBasis(habitId: String, logicalDate: LocalDate): List<RewardLedgerEntry>
    suspend fun latestFirstDoneRowForPity(): RewardLedgerEntry?
    suspend fun distinctPaidDatesInRangeExcludingDate(
        habitId: String,
        startDate: LocalDate,
        endDate: LocalDate,
        excludedDate: LocalDate
    ): List<LocalDate>
    suspend fun balance(currency: RewardCurrency): Long
    suspend fun totalXp(): Long
}

interface RewardDropTransactionPort {
    suspend fun insert(record: RewardDropRecord)
}

data class RewardLedgerEntry(
    val id: Long = 0,
    val currency: RewardCurrency,
    val delta: Long,
    val reason: String,
    val habitId: String?,
    val logicalDate: LocalDate?,
    val createdAt: Instant,
    val idempotencyKey: String,
    val rewardStage: RewardStage?,
    val difficulty: Difficulty?,
    val streakLength: Int?,
    val comebackApplied: Boolean?,
    val stageEntitlementAmount: Long?,
    val pityCounterAfter: Int?
)

data class RewardDropRecord(
    val id: String,
    val rarity: DropRarity,
    val habitId: String,
    val logicalDate: LocalDate,
    val createdAt: Instant
)
