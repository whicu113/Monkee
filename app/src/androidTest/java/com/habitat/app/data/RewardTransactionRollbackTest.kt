package com.habitat.app.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habitat.app.data.local.AppDatabase
import com.habitat.app.data.local.DropRecordEntity
import com.habitat.app.data.local.RoomRewardTransactionRunner
import com.habitat.app.domain.RewardDropRecord
import com.habitat.app.domain.RewardLedgerEntry
import com.habitat.app.domain.logic.DropRarity
import com.habitat.app.domain.logic.RewardCurrency
import com.habitat.app.domain.logic.RewardStage
import com.habitat.app.domain.logic.rewardIdempotencyKey
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.Difficulty
import com.habitat.app.domain.model.LoggedCompletion
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RewardTransactionRollbackTest {

    private lateinit var database: AppDatabase
    private lateinit var runner: RoomRewardTransactionRunner

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        runner = RoomRewardTransactionRunner(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun completionAndRewardsRollbackWhenDropRecordInsertFails() = runBlocking {
        database.dropRecordDao().insert(
            DropRecordEntity(
                id = "existing-drop",
                rarity = DropRarity.COMMON.name,
                habitId = HABIT_ID,
                logicalDate = LOGICAL_DATE,
                createdAt = CREATED_AT
            )
        )

        var constraintFailureObserved = false
        try {
            runner.inTransaction {
                completions.put(
                    LoggedCompletion(
                        id = "completion-attempt",
                        habitId = HABIT_ID,
                        logicalDate = LOGICAL_DATE,
                        state = CompletionState.DONE,
                        completedAt = CREATED_AT
                    )
                )
                ledger.insert(
                    RewardLedgerEntry(
                        currency = RewardCurrency.SEEDS,
                        delta = 10,
                        reason = "HABIT_REWARD",
                        habitId = HABIT_ID,
                        logicalDate = LOGICAL_DATE,
                        createdAt = CREATED_AT,
                        idempotencyKey = rewardIdempotencyKey(
                            HABIT_ID,
                            LOGICAL_DATE,
                            RewardStage.DONE,
                            RewardCurrency.SEEDS
                        ),
                        rewardStage = RewardStage.DONE,
                        difficulty = Difficulty.EASY,
                        streakLength = 1,
                        comebackApplied = false,
                        stageEntitlementAmount = 10,
                        pityCounterAfter = 1
                    )
                )
                drops.insert(
                    RewardDropRecord(
                        id = "attempted-drop",
                        rarity = DropRarity.RARE,
                        habitId = HABIT_ID,
                        logicalDate = LOGICAL_DATE,
                        createdAt = CREATED_AT
                    )
                )
            }
        } catch (_: SQLiteConstraintException) {
            constraintFailureObserved = true
        }

        assertEquals("duplicate drop row caused the expected insert failure", true, constraintFailureObserved)
        assertNull("completion insert rolled back", database.completionDao().getCompletion(HABIT_ID, LOGICAL_DATE))
        assertTrue(
            "ledger insert rolled back",
            database.ledgerEntryDao().stageRowsForSavedBasis(HABIT_ID, LOGICAL_DATE).isEmpty()
        )
        database.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM drop_records WHERE habitId = '$HABIT_ID' AND logicalDate = '$LOGICAL_DATE'"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("only the pre-existing drop remains", 1, cursor.getInt(0))
        }
    }

    private companion object {
        const val HABIT_ID = "habit-rollback"
        val LOGICAL_DATE: LocalDate = LocalDate.parse("2025-05-12")
        val CREATED_AT: Instant = Instant.parse("2025-05-12T12:00:00Z")
    }
}
