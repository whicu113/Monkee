package com.habitat.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habitat.app.data.local.AppDatabase
import com.habitat.app.data.local.DropRecordEntity
import com.habitat.app.data.local.LedgerEntryEntity
import com.habitat.app.data.local.LedgerReasonDatabaseMapper
import com.habitat.app.data.local.RoomRewardTransactionRunner
import com.habitat.app.data.repository.CompletionRepositoryImpl
import com.habitat.app.data.repository.HabitRepositoryImpl
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.LedgerKeyConflictException
import com.habitat.app.domain.LedgerReason
import com.habitat.app.domain.RewardTransactionRunner
import com.habitat.app.domain.logic.DropRarity
import com.habitat.app.domain.logic.RewardCurrency
import com.habitat.app.domain.logic.RewardRandom
import com.habitat.app.domain.logic.RewardStage
import com.habitat.app.domain.logic.rewardIdempotencyKey
import com.habitat.app.domain.model.AppSettings
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.Difficulty
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.ScheduleType
import com.habitat.app.domain.repository.SettingsRepository
import com.habitat.app.domain.usecase.CompletionReminderHandler
import com.habitat.app.domain.usecase.LogCompletionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.ArrayDeque

@RunWith(AndroidJUnit4::class)
class RewardLoggingIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var clock: AppClock
    private lateinit var settings: TestSettingsRepository
    private lateinit var reminders: RecordingCompletionReminder
    private var widgetUpdates = 0
    private val today = LocalDate.parse("2025-05-12")
    private val now = Instant.parse("2025-05-12T12:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        clock = object : AppClock {
            override fun instant() = now
            override fun zoneId() = ZoneId.of("UTC")
        }
        settings = TestSettingsRepository(AppSettings())
        reminders = RecordingCompletionReminder()
        widgetUpdates = 0
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun doneUncheckDonePaysOnce() = runBlocking {
        val useCase = useCase(random = ScriptedRandom(99))
        saveHabit(Habit(id = "done-uncheck", name = "Read", createdAt = now))

        assertTrue("first DONE log is accepted", useCase.executeLog("done-uncheck", CompletionState.DONE, today))
        useCase.executeRemove("done-uncheck", today)
        assertTrue("rechecked DONE remains accepted", useCase.executeLog("done-uncheck", CompletionState.DONE, today))

        assertEquals("one Seeds stage row is retained", 1, stageRows("done-uncheck", today, RewardCurrency.SEEDS).size)
        assertEquals("one XP stage row is retained", 1, stageRows("done-uncheck", today, RewardCurrency.XP).size)
        assertEquals("uncheck then recheck does not duplicate Seeds", 11L, database.ledgerEntryDao().sumPositiveHabitSeeds(today, rewardReason))
    }

    @Test
    fun doneUncheckMinimumPaysNothing() = runBlocking {
        val useCase = useCase(random = ScriptedRandom(99))
        saveHabit(Habit(id = "done-uncheck-min", name = "Read", createdAt = now))
        useCase.executeLog("done-uncheck-min", CompletionState.DONE, today)
        useCase.executeRemove("done-uncheck-min", today)

        useCase.executeLogWithResult("done-uncheck-min", CompletionState.MINIMUM, today)

        assertEquals("minimum cannot add Seeds after the DONE entitlement", 1, stageRows("done-uncheck-min", today, RewardCurrency.SEEDS).size)
        assertEquals("minimum cannot add XP after the DONE entitlement", 1, stageRows("done-uncheck-min", today, RewardCurrency.XP).size)
    }

    @Test
    fun minimumToDoneUpgradeOnQuotaFillingDayInheritsEligibility() = runBlocking {
        val habitId = "quota-upgrade"
        val useCase = useCase(random = ScriptedRandom(99))
        saveHabit(Habit(id = habitId, name = "Read", schedule = ScheduleType.NPerWeek(2), createdAt = today.minusDays(7).atStartOfDay(ZoneId.of("UTC")).toInstant()))
        insertPaidSeeds(habitId, today.minusDays(3), "quota-first")

        val minimum = useCase.executeLogWithResult(habitId, CompletionState.MINIMUM, today)
        assertTrue("minimum is the second paid date allowed by weekly quota", minimum is com.habitat.app.domain.HabitRewardResult.Granted)
        insertPaidSeeds(habitId, today.minusDays(2), "quota-filling-second")

        val upgrade = useCase.executeLogWithResult(habitId, CompletionState.DONE, today)
        assertTrue("DONE upgrade inherits first-grant eligibility after quota fills", upgrade is com.habitat.app.domain.HabitRewardResult.Granted)
        assertEquals("upgrade writes a DONE Seeds row", 1, stageRows(habitId, today, RewardCurrency.SEEDS).count { it.rewardStage == RewardStage.DONE.name })
    }

    @Test
    fun backfillAndUnscheduledCompletionsWriteNoRewardRows() = runBlocking {
        val useCase = useCase()
        val daily = "backfill-row"
        val unscheduled = "off-schedule-row"
        saveHabit(Habit(id = daily, name = "Daily", createdAt = now))
        saveHabit(Habit(id = unscheduled, name = "Friday", schedule = ScheduleType.Weekdays(setOf(DayOfWeek.FRIDAY)), createdAt = now))

        val backfill = useCase.executeLogWithResult(daily, CompletionState.DONE, today.minusDays(1))
        val offDay = useCase.executeLogWithResult(unscheduled, CompletionState.DONE, today)

        assertEquals("backfill receives BACKFILL reason", com.habitat.app.domain.HabitRewardResult.NoReward(com.habitat.app.domain.RewardNoRewardReason.BACKFILL), backfill)
        assertEquals("off-schedule date receives NOT_SCHEDULED reason", com.habitat.app.domain.HabitRewardResult.NoReward(com.habitat.app.domain.RewardNoRewardReason.NOT_SCHEDULED), offDay)
        assertNotNull("backfill completion remains logged", database.completionDao().getCompletion(daily, today.minusDays(1)))
        assertNotNull("unscheduled completion remains logged", database.completionDao().getCompletion(unscheduled, today))
        assertEquals("ineligible logs write no ledger rows", 0L, tableCount("ledger_entries"))
        assertEquals("ineligible logs create no drops", 0L, tableCount("drop_records"))
    }

    @Test
    fun dropRecordExistsOnlyWhenDropRollSucceeds() = runBlocking {
        val useCase = useCase(random = ScriptedRandom(0, 92, 99))
        saveHabit(Habit(id = "drop-hit", name = "Hit", createdAt = now))
        saveHabit(Habit(id = "drop-miss", name = "Miss", createdAt = now))

        val hit = useCase.executeLogWithResult("drop-hit", CompletionState.DONE, today) as com.habitat.app.domain.HabitRewardResult.Granted
        useCase.executeLogWithResult("drop-miss", CompletionState.DONE, today)

        assertEquals("chance hit creates a DropRecord", DropRarity.RARE, hit.drop)
        assertEquals("only the successful roll has a DropRecord", 1L, tableCount("drop_records"))
        var duplicateRejected = false
        try {
            database.dropRecordDao().insert(dropRecord("duplicate-drop-2", "drop-hit"))
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            duplicateRejected = true
        }
        assertTrue("DropRecord unique habit/date constraint remains enforced", duplicateRejected)
    }

    @Test
    fun concurrentSameHabitDateLogsPayOnce() = runBlocking {
        val useCase = useCase(random = ScriptedRandom(99, 99))
        saveHabit(Habit(id = "concurrent-same", name = "Same", createdAt = now))

        val results = (1..2).map {
            async(Dispatchers.IO) { useCase.executeLogWithResult("concurrent-same", CompletionState.DONE, today) }
        }.awaitAll()

        assertEquals("both taps complete without a second grant", 2, results.size)
        assertEquals("concurrent logs leave one Seeds stage row", 1, stageRows("concurrent-same", today, RewardCurrency.SEEDS).size)
        assertEquals("concurrent logs leave one XP stage row", 1, stageRows("concurrent-same", today, RewardCurrency.XP).size)
    }

    @Test
    fun concurrentDifferentHabitsStayWithinDailySeedsCap() = runBlocking {
        val useCase = useCase(random = ScriptedRandom(*IntArray(80) { 99 }))
        val habits = (0 until 30).map { i -> Habit(id = "concurrent-$i", name = "Habit $i", createdAt = now) }
        habits.forEach { saveHabit(it) }

        habits.map { habit ->
            async(Dispatchers.IO) { useCase.executeLogWithResult(habit.id, CompletionState.DONE, today) }
        }.awaitAll()

        assertEquals("separate habits share the 300 Seeds daily cap", 300L, database.ledgerEntryDao().sumPositiveHabitSeeds(today, rewardReason))
    }

    @Test
    fun forcedLedgerKeyConflictRetriesWholeTransactionOnce() = runBlocking {
        val habitId = "forced-conflict"
        saveHabit(Habit(id = habitId, name = "Retry", createdAt = now))
        val countingRunner = ConflictOnceRunner(RoomRewardTransactionRunner(database))
        val useCase = useCase(random = ScriptedRandom(99), runner = countingRunner)

        val result = useCase.executeLogWithResult(habitId, CompletionState.DONE, today)

        assertTrue("retry completes the granted transaction", result is com.habitat.app.domain.HabitRewardResult.Granted)
        assertEquals("the runner is called for first attempt and one retry", 2, countingRunner.calls)
        assertNotNull("retry commits the Completion row", database.completionDao().getCompletion(habitId, today))
        assertEquals("retry commits one Seeds row", 1, stageRows(habitId, today, RewardCurrency.SEEDS).size)
    }

    private fun useCase(
        random: RewardRandom = ScriptedRandom(99),
        runner: RewardTransactionRunner = RoomRewardTransactionRunner(database)
    ) = LogCompletionUseCase(
        completionRepository = CompletionRepositoryImpl(database.completionDao()),
        clock = clock,
        reminderHandler = reminders,
        widgetUpdater = com.habitat.app.domain.WidgetUpdater { widgetUpdates++ },
        settingsRepository = settings,
        transactionRunner = runner,
        rewardRandom = random
    )

    private suspend fun saveHabit(habit: Habit) {
        HabitRepositoryImpl(database.habitDao()).saveHabit(habit)
    }

    private suspend fun insertPaidSeeds(habitId: String, date: LocalDate, key: String) {
        database.ledgerEntryDao().insert(
            LedgerEntryEntity(
                currency = RewardCurrency.SEEDS.name,
                delta = 1,
                reason = rewardReason,
                habitId = habitId,
                logicalDate = date,
                createdAt = now,
                idempotencyKey = key,
                rewardStage = RewardStage.DONE.name,
                difficulty = Difficulty.EASY.name,
                streakLength = 1,
                comebackApplied = false,
                stageEntitlementAmount = 1,
                pityCounterAfter = 0
            )
        )
    }

    private suspend fun stageRows(habitId: String, date: LocalDate, currency: RewardCurrency) =
        database.ledgerEntryDao().stageRowsForSavedBasis(habitId, date, rewardReason).filter { it.currency == currency.name }

    private fun dropRecord(id: String, habitId: String) = DropRecordEntity(
        id = id, rarity = DropRarity.COMMON.name, habitId = habitId, logicalDate = today, createdAt = now
    )

    private fun tableCount(table: String): Long = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
        cursor.moveToFirst()
        cursor.getLong(0)
    }

    private val rewardReason: String
        get() = LedgerReasonDatabaseMapper.toDatabaseValue(LedgerReason.HABIT_REWARD)

    private class ScriptedRandom(vararg values: Int) : RewardRandom {
        private val rolls = ArrayDeque(values.toList())
        override fun nextInt(boundExclusive: Int): Int = (rolls.pollFirst() ?: boundExclusive - 1).coerceIn(0, boundExclusive - 1)
    }

    private class TestSettingsRepository(var settings: AppSettings) : SettingsRepository {
        override fun getSettings(): Flow<AppSettings> = flowOf(settings)
        override suspend fun updateSettings(settings: AppSettings) { this.settings = settings }
        override suspend fun getPostedHabitIds(logicalDateString: String) = emptySet<String>()
        override suspend fun addPostedHabitId(logicalDateString: String, habitId: String) = Unit
        override suspend fun clearPostedHabitIds(logicalDateString: String) = Unit
        override suspend fun pruneOldPostedHabitIds(cutoffDateString: String) = Unit
    }

    private class RecordingCompletionReminder : CompletionReminderHandler {
        override suspend fun onCompletionLogged(habitId: String, logicalDate: LocalDate) = Unit
    }

    private class ConflictOnceRunner(private val delegate: RewardTransactionRunner) : RewardTransactionRunner {
        var calls = 0
        override suspend fun <T> inTransaction(block: suspend com.habitat.app.domain.RewardTransactionScope.() -> T): T {
            calls++
            if (calls == 1) throw LedgerKeyConflictException("forced-first-attempt", IllegalStateException("test conflict"))
            return delegate.inTransaction(block)
        }
    }
}
