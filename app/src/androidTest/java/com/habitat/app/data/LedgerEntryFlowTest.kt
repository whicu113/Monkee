package com.habitat.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habitat.app.data.local.AppDatabase
import com.habitat.app.data.local.LedgerEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class LedgerEntryFlowTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun economyFlowsEmitZeroThenNewLedgerSumsAfterInsert() = runBlocking {
        val dao = database.ledgerEntryDao()
        val seedEmissions = Channel<Long>(Channel.UNLIMITED)
        val xpEmissions = Channel<Long>(Channel.UNLIMITED)
        val seedCollector = launch(Dispatchers.IO) {
            dao.observeBalanceForCurrency("SEEDS").collect { seedEmissions.send(it) }
        }
        val xpCollector = launch(Dispatchers.IO) {
            dao.observeTotalXp().collect { xpEmissions.send(it) }
        }

        try {
            assertEquals("empty Seeds ledger emits zero", 0L, withTimeout(5_000) { seedEmissions.receive() })
            assertEquals("empty XP ledger emits zero", 0L, withTimeout(5_000) { xpEmissions.receive() })

            database.withTransaction {
                dao.insert(ledgerRow(currency = "SEEDS", delta = 42L, key = "flow-seeds"))
                dao.insert(ledgerRow(currency = "XP", delta = 10L, key = "flow-xp"))
            }

            assertEquals("Seeds Flow emits inserted sum", 42L, withTimeout(5_000) { seedEmissions.receive() })
            assertEquals("XP Flow emits inserted sum", 10L, withTimeout(5_000) { xpEmissions.receive() })
        } finally {
            seedCollector.cancel()
            xpCollector.cancel()
            seedEmissions.close()
            xpEmissions.close()
        }
    }

    private fun ledgerRow(currency: String, delta: Long, key: String) = LedgerEntryEntity(
        currency = currency,
        delta = delta,
        reason = "HABIT_REWARD",
        habitId = "flow-habit",
        logicalDate = LocalDate.parse("2025-05-12"),
        createdAt = Instant.parse("2025-05-12T12:00:00Z"),
        idempotencyKey = key,
        rewardStage = "DONE",
        difficulty = "EASY",
        streakLength = 1,
        comebackApplied = false,
        stageEntitlementAmount = delta,
        pityCounterAfter = 0
    )
}
