package com.habitat.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.LocalDate

@Dao
interface LedgerEntryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: LedgerEntryEntity): Long

    @Query(
        "SELECT COALESCE(SUM(delta), 0) FROM ledger_entries " +
            "WHERE reason = 'HABIT_REWARD' AND currency = 'SEEDS' AND logicalDate = :logicalDate AND delta > 0"
    )
    suspend fun sumPositiveHabitSeeds(logicalDate: LocalDate): Long

    @Query(
        "SELECT rewardStage FROM ledger_entries " +
            "WHERE reason = 'HABIT_REWARD' AND currency = 'SEEDS' AND habitId = :habitId AND logicalDate = :logicalDate " +
            "AND rewardStage IS NOT NULL " +
            "ORDER BY CASE rewardStage WHEN 'DONE' THEN 2 WHEN 'MINIMUM' THEN 1 ELSE 0 END DESC LIMIT 1"
    )
    suspend fun highestStageForHabitDate(habitId: String, logicalDate: LocalDate): String?

    @Query(
        "SELECT * FROM ledger_entries WHERE reason = 'HABIT_REWARD' AND habitId = :habitId " +
            "AND logicalDate = :logicalDate AND rewardStage IS NOT NULL ORDER BY id ASC"
    )
    suspend fun stageRowsForSavedBasis(habitId: String, logicalDate: LocalDate): List<LedgerEntryEntity>

    @Query(
        "SELECT * FROM ledger_entries WHERE reason = 'HABIT_REWARD' AND currency = 'SEEDS' " +
            "AND rewardStage = 'DONE' ORDER BY id DESC LIMIT 1"
    )
    suspend fun latestFirstDoneRowForPity(): LedgerEntryEntity?

    @Query(
        "SELECT DISTINCT logicalDate FROM ledger_entries WHERE reason = 'HABIT_REWARD' AND currency = 'SEEDS' " +
            "AND habitId = :habitId AND logicalDate BETWEEN :startDate AND :endDate " +
            "AND logicalDate != :excludedDate AND rewardStage IN ('MINIMUM', 'DONE') ORDER BY logicalDate ASC"
    )
    suspend fun distinctPaidDatesForHabitInRangeExcludingDate(
        habitId: String,
        startDate: LocalDate,
        endDate: LocalDate,
        excludedDate: LocalDate
    ): List<LocalDate>

    @Query("SELECT COALESCE(SUM(delta), 0) FROM ledger_entries WHERE currency = :currency")
    suspend fun balanceForCurrency(currency: String): Long

    @Query("SELECT COALESCE(SUM(delta), 0) FROM ledger_entries WHERE currency = 'XP'")
    suspend fun totalXp(): Long
}
