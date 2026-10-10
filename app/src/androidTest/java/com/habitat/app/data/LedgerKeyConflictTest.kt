package com.habitat.app.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habitat.app.data.local.AppDatabase
import com.habitat.app.data.local.DropRecordEntity
import com.habitat.app.data.local.RoomRewardTransactionRunner
import com.habitat.app.domain.LedgerKeyConflictException
import com.habitat.app.domain.LedgerReason
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class LedgerKeyConflictTest {

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
    fun duplicateIdempotencyKeyBecomesDomainConflict() = runBlocking {
        var conflict: LedgerKeyConflictException? = null
        try {
            runner.inTransaction {
                ledger.insert(rewardEntry())
                ledger.insert(rewardEntry())
            }
        } catch (failure: LedgerKeyConflictException) {
            conflict = failure
        }

        assertNotNull("duplicate ledger key becomes a domain conflict", conflict)
    }

    @Test
    fun duplicateDropConstraintRemainsSqliteConstraintException() = runBlocking {
        database.dropRecordDao().insert(dropEntity("existing-drop"))
        var rawConstraintFailureObserved = false

        try {
            runner.inTransaction {
                drops.insert(dropRecord("duplicate-drop"))
            }
        } catch (_: SQLiteConstraintException) {
            rawConstraintFailureObserved = true
        }

        assertTrue("drop unique failure is not translated as a ledger conflict", rawConstraintFailureObserved)
    }

    @Test
    fun translatedLedgerConflictRollsBackEarlierCompletionLedgerAndDropWrites() = runBlocking {
        var conflictObserved = false
        try {
            runner.inTransaction {
                completions.put(
                    LoggedCompletion(
                        id = "completion-before-conflict",
                        habitId = HABIT_ID,
                        logicalDate = DAY,
                        state = CompletionState.DONE,
                        completedAt = NOW
                    )
                )
                ledger.insert(rewardEntry())
                drops.insert(dropRecord("drop-before-conflict"))
                ledger.insert(rewardEntry())
            }
        } catch (_: LedgerKeyConflictException) {
            conflictObserved = true
        }

        assertTrue("duplicate key caused the translated conflict", conflictObserved)
        assertNull("completion written before conflict rolled back", database.completionDao().getCompletion(HABIT_ID, DAY))
        assertTrue(
            "ledger row written before conflict rolled back",
            database.ledgerEntryDao().stageRowsForSavedBasis(HABIT_ID, DAY).isEmpty()
        )
        database.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM drop_records WHERE habitId = '$HABIT_ID' AND logicalDate = '$DAY'"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("drop written before conflict rolled back", 0, cursor.getInt(0))
        }
    }

    private fun rewardEntry() = RewardLedgerEntry(
        currency = RewardCurrency.SEEDS,
        delta = 10,
        reason = LedgerReason.HABIT_REWARD,
        habitId = HABIT_ID,
        logicalDate = DAY,
        createdAt = NOW,
        idempotencyKey = rewardIdempotencyKey(HABIT_ID, DAY, RewardStage.DONE, RewardCurrency.SEEDS),
        rewardStage = RewardStage.DONE,
        difficulty = Difficulty.EASY,
        streakLength = 1,
        comebackApplied = false,
        stageEntitlementAmount = 10,
        pityCounterAfter = 1
    )

    private fun dropRecord(id: String) = RewardDropRecord(
        id = id,
        rarity = DropRarity.COMMON,
        habitId = HABIT_ID,
        logicalDate = DAY,
        createdAt = NOW
    )

    private fun dropEntity(id: String) = DropRecordEntity(
        id = id,
        rarity = DropRarity.COMMON.name,
        habitId = HABIT_ID,
        logicalDate = DAY,
        createdAt = NOW
    )

    private companion object {
        const val HABIT_ID = "habit-key-conflict"
        val DAY: LocalDate = LocalDate.parse("2025-05-12")
        val NOW: Instant = Instant.parse("2025-05-12T12:00:00Z")
    }
}
