package com.habitat.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habitat.app.data.local.AppDatabase
import com.habitat.app.data.local.LedgerEntryDao
import com.habitat.app.data.local.LedgerEntryEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class LedgerEntryDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: LedgerEntryDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.ledgerEntryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun dailySeedsCapSumsOnlyPositiveHabitRewardSeedsForDate() = runBlocking {
        dao.insert(entry(delta = 20, date = DAY, key = "positive"))
        dao.insert(entry(delta = -7, date = DAY, key = "negative"))
        dao.insert(entry(delta = 99, reason = "PURCHASE", date = DAY, key = "other-reason"))
        dao.insert(entry(currency = "XP", delta = 30, date = DAY, key = "xp"))
        dao.insert(entry(delta = 40, date = DAY.plusDays(1), key = "other-day"))

        assertEquals("daily cap ignores negative, other-source, other-currency, and other-date rows", 20L, dao.sumPositiveHabitSeeds(DAY))
    }

    @Test
    fun highestStageForHabitDateReturnsHighestRewardStage() = runBlocking {
        dao.insert(entry(stage = "MINIMUM", key = "minimum"))
        dao.insert(entry(stage = "DONE", key = "done"))

        assertEquals("DONE outranks MINIMUM", "DONE", dao.highestStageForHabitDate(HABIT, DAY))
        assertNull("no stage exists for another habit", dao.highestStageForHabitDate("other", DAY))
    }

    @Test
    fun stageRowsForSavedBasisReturnsOrderedRowsWithPersistedBasis() = runBlocking {
        dao.insert(entry(currency = "SEEDS", stage = "MINIMUM", difficulty = "HARD", streak = 4, comeback = true, entitlement = 18, key = "minimum-seeds"))
        dao.insert(entry(currency = "XP", stage = "MINIMUM", difficulty = "HARD", streak = 4, comeback = true, entitlement = 10, key = "minimum-xp"))
        dao.insert(entry(stage = "DONE", difficulty = "HARD", streak = 4, comeback = true, entitlement = 30, key = "done-seeds"))

        val rows = dao.stageRowsForSavedBasis(HABIT, DAY)
        assertEquals("saved basis returns each stage currency row", 3, rows.size)
        assertEquals("first row keeps saved difficulty", "HARD", rows.first().difficulty)
        assertEquals("first row keeps saved streak", 4, rows.first().streakLength)
        assertEquals("first row keeps saved comeback", true, rows.first().comebackApplied)
        assertEquals("first row keeps entitlement", 18L, rows.first().stageEntitlementAmount)
    }

    @Test
    fun pityUsesLatestFirstDoneRowByIdIncludingZeroDelta() = runBlocking {
        dao.insert(entry(delta = 5, stage = "DONE", date = DAY.plusDays(5), createdAt = 900, pity = 8, key = "first"))
        val latestId = dao.insert(entry(delta = 0, stage = "DONE", date = DAY, createdAt = 100, pity = 0, key = "latest-id"))
        val laterMinimumSeedsId = dao.insert(
            entry(delta = 0, stage = "MINIMUM", date = DAY.plusDays(2), createdAt = 50, pity = 30, key = "later-minimum-seeds")
        )
        val laterXpId = dao.insert(
            entry(currency = "XP", delta = 0, stage = "DONE", date = DAY.plusDays(3), createdAt = 25, pity = 44, key = "later-xp")
        )

        val row = dao.latestFirstDoneRowForPity()
        assertNotNull("latest first-DONE row exists", row)
        assertEquals("pity ordering uses auto id rather than date or timestamp", latestId, row!!.id)
        assertNotEquals("later MINIMUM Seeds row must be ignored", laterMinimumSeedsId, row.id)
        assertNotEquals("later XP row must be ignored", laterXpId, row.id)
        assertEquals("zero-delta first-DONE row still resets pity", 0, row.pityCounterAfter)
        assertEquals("zero-delta row is retained", 0L, row.delta)
    }

    @Test
    fun weeklyQuotaReturnsDistinctPaidDatesExcludingCurrentDate() = runBlocking {
        dao.insert(entry(currency = "SEEDS", stage = "MINIMUM", date = DAY, key = "day1-min"))
        dao.insert(entry(currency = "SEEDS", stage = "DONE", date = DAY, key = "day1-done"))
        dao.insert(entry(currency = "SEEDS", stage = "DONE", date = DAY.plusDays(1), delta = 0, key = "day2"))
        dao.insert(entry(currency = "XP", stage = "DONE", date = DAY.plusDays(2), key = "xp"))
        dao.insert(entry(currency = "SEEDS", stage = "DONE", date = DAY.plusDays(3), habitId = "other-habit", key = "other-habit"))
        dao.insert(entry(currency = "SEEDS", stage = "DONE", reason = "PURCHASE", date = DAY.plusDays(4), key = "other-reason"))
        dao.insert(entry(currency = "SEEDS", stage = "DONE", date = DAY.plusDays(8), key = "outside-week"))

        assertEquals(
            "weekly quota counts distinct first-grant dates and excludes current date",
            listOf(DAY.plusDays(1)),
            dao.distinctPaidDatesForHabitInRangeExcludingDate(HABIT, DAY, DAY.plusDays(6), DAY)
        )
    }

    @Test
    fun balancesSumAllDeltasByCurrencyAndXpTotal() = runBlocking {
        dao.insert(entry(delta = 12, key = "seeds-earned"))
        dao.insert(entry(delta = -5, reason = "SPEND", key = "seeds-spent"))
        dao.insert(entry(currency = "XP", delta = 150, key = "xp-earned"))
        dao.insert(entry(currency = "XP", delta = 10, reason = "BONUS", key = "xp-bonus"))

        assertEquals("Seeds balance is net ledger total", 7L, dao.balanceForCurrency("SEEDS"))
        assertEquals("XP balance is net ledger total", 160L, dao.balanceForCurrency("XP"))
        assertEquals("XP total sums XP currency rows", 160L, dao.totalXp())
    }

    private fun entry(
        currency: String = "SEEDS",
        delta: Long = 10,
        reason: String = "HABIT_REWARD",
        habitId: String = HABIT,
        date: LocalDate = DAY,
        createdAt: Long = 500,
        stage: String? = "DONE",
        difficulty: String? = "MEDIUM",
        streak: Int? = 2,
        comeback: Boolean? = false,
        entitlement: Long? = 15,
        pity: Int? = 0,
        key: String
    ) = LedgerEntryEntity(
        currency = currency,
        delta = delta,
        reason = reason,
        habitId = habitId,
        logicalDate = date,
        createdAt = Instant.ofEpochMilli(createdAt),
        idempotencyKey = key,
        rewardStage = stage,
        difficulty = difficulty,
        streakLength = streak,
        comebackApplied = comeback,
        stageEntitlementAmount = entitlement,
        pityCounterAfter = pity
    )

    private companion object {
        val DAY: LocalDate = LocalDate.parse("2025-05-12")
        const val HABIT = "habit-1"
    }
}
