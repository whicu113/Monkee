package com.habitat.app.domain

import com.habitat.app.domain.logic.DropRarity
import com.habitat.app.domain.logic.RewardCurrency
import com.habitat.app.domain.logic.RewardRandom
import com.habitat.app.domain.logic.RewardStage
import com.habitat.app.domain.logic.ReplayEngine
import com.habitat.app.domain.model.AppSettings
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.Difficulty
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.LoggedCompletion
import com.habitat.app.domain.model.OccurrenceState
import com.habitat.app.domain.model.ScheduleType
import com.habitat.app.domain.repository.CompletionRepository
import com.habitat.app.domain.repository.SettingsRepository
import com.habitat.app.domain.usecase.CompletionReminderHandler
import com.habitat.app.domain.usecase.LogCompletionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class RewardingLogCompletionUseCaseTest {
    private lateinit var fixture: RewardLogFixture

    @Before
    fun setUp() {
        fixture = RewardLogFixture()
    }

    @Test
    fun firstDoneGrantWritesCompletionAndBothCurrencyRows() = runTest {
        val result = fixture.useCase.executeLogWithResult(HABIT_ID, CompletionState.DONE, TODAY)

        val granted = result as HabitRewardResult.Granted
        assertTrue("first DONE grant pays Seeds", granted.seeds > 0)
        assertEquals("first DONE grant pays habit XP", 10, granted.xp)
        assertEquals("first DONE grant writes Seeds and XP rows", 2, fixture.runner.ledgerRows.size)
        assertEquals("completion commits with the reward", CompletionState.DONE, fixture.runner.completion(HABIT_ID, TODAY)?.state)
        assertEquals("new completion invokes reminder once", 1, fixture.reminder.logged.size)
        assertEquals("new completion refreshes widget once", 1, fixture.widgetUpdates)
    }

    @Test
    fun skippedThenDonePaysFullDoneGrant() = runTest {
        fixture.useCase.executeLogWithResult(HABIT_ID, CompletionState.SKIPPED, TODAY)
        val result = fixture.useCase.executeLogWithResult(HABIT_ID, CompletionState.DONE, TODAY)
        val freshDone = RewardLogFixture().useCase.executeLogWithResult(HABIT_ID, CompletionState.DONE, TODAY)

        val granted = result as HabitRewardResult.Granted
        val freshGranted = freshDone as HabitRewardResult.Granted
        assertEquals("SKIPPED creates no ledger rows", 2, fixture.runner.ledgerRows.size)
        assertEquals("SKIPPED to DONE pays full DONE Seeds grant", freshGranted.seeds, granted.seeds)
        assertEquals("SKIPPED to DONE pays full XP entitlement", 10, granted.xp)
    }

    @Test
    fun minimumThenDonePaysEntitlementDeltaWithOneDoneDropRollAndPityMove() = runTest {
        fixture.runner.addPityRow(pityAfter = 5)
        val minimum = fixture.useCase.executeLogWithResult(HABIT_ID, CompletionState.MINIMUM, TODAY) as HabitRewardResult.Granted
        val beforeUpgradePity = fixture.runner.latestPityCounter()
        fixture.random.script(0, 99)
        val freshDone = RewardLogFixture().useCase.executeLogWithResult(HABIT_ID, CompletionState.DONE, TODAY) as HabitRewardResult.Granted

        val done = fixture.useCase.executeLogWithResult(HABIT_ID, CompletionState.DONE, TODAY) as HabitRewardResult.Granted

        assertEquals("MINIMUM grants XP once", 10, minimum.xp)
        assertEquals("MINIMUM to DONE has no XP delta", 0, done.xp)
        assertEquals("upgrade calculates Seeds from DONE entitlement difference", freshDone.seeds - minimum.seeds, done.seeds)
        assertEquals("upgrade is the first DONE grant and rolls once", 2, fixture.random.calls)
        assertEquals("upgrade drop resets the prior pity counter once", 0, fixture.runner.latestPityCounter())
        assertTrue("upgrade's first DONE grant records the drop", done.drop != null)
        assertEquals("pre-upgrade pity counter fixture", 5, beforeUpgradePity)
    }

    @Test
    fun firstCompletionAfterMissedGetsComebackBonus() = runTest {
        val habit = fixture.runner.habit.copy(createdAt = TODAY.minusDays(2).atStartOfDay(ZoneId.of("UTC")).toInstant())
        fixture.runner.habits[HABIT_ID] = habit
        val before = ReplayEngine.evaluate(habit, emptyList(), TODAY)
        assertEquals("comeback setup has a missed prior occurrence", OccurrenceState.MISSED, before.occurrenceStates[TODAY.minusDays(1)])

        val result = fixture.useCase.executeLogWithResult(HABIT_ID, CompletionState.DONE, TODAY) as HabitRewardResult.Granted

        assertTrue("MISSED immediately before first completion applies comeback", result.comebackApplied)
    }

    @Test
    fun firstCompletionAfterProtectedDoesNotGetComebackBonus() = runTest {
        val habit = fixture.runner.habit.copy(createdAt = TODAY.minusDays(9).atStartOfDay(ZoneId.of("UTC")).toInstant())
        fixture.runner.habits[HABIT_ID] = habit
        fixture.seedEarlier(CompletionState.DONE, TODAY.minusDays(8))
        fixture.seedEarlier(CompletionState.DONE, TODAY.minusDays(7))
        fixture.seedEarlier(CompletionState.DONE, TODAY.minusDays(6))
        fixture.seedEarlier(CompletionState.DONE, TODAY.minusDays(5))
        fixture.seedEarlier(CompletionState.DONE, TODAY.minusDays(4))
        fixture.seedEarlier(CompletionState.DONE, TODAY.minusDays(3))
        fixture.seedEarlier(CompletionState.DONE, TODAY.minusDays(2))
        val before = ReplayEngine.evaluate(habit, fixture.runner.completions.values.toList(), TODAY)
        assertEquals("seventh prior completion protects the following miss", OccurrenceState.PROTECTED, before.occurrenceStates[TODAY.minusDays(1)])

        val result = fixture.useCase.executeLogWithResult(HABIT_ID, CompletionState.DONE, TODAY) as HabitRewardResult.Granted

        assertFalse("PROTECTED after rest-token use is not a comeback miss", result.comebackApplied)
    }

    @Test
    fun thirdOccurrenceAfterMissedDoesNotGetComebackBonus() = runTest {
        val habit = fixture.runner.habit.copy(createdAt = TODAY.minusDays(4).atStartOfDay(ZoneId.of("UTC")).toInstant())
        fixture.runner.habits[HABIT_ID] = habit
        fixture.seedEarlier(CompletionState.DONE, TODAY.minusDays(2))
        fixture.seedEarlier(CompletionState.DONE, TODAY.minusDays(1))

        val result = fixture.useCase.executeLogWithResult(HABIT_ID, CompletionState.DONE, TODAY) as HabitRewardResult.Granted

        assertFalse("MISSED outside the previous two occurrences does not apply comeback", result.comebackApplied)
    }

    @Test
    fun minimumToDoneUpgradeKeepsFirstGrantComebackBasis() = runTest {
        fixture.runner.habits[HABIT_ID] = fixture.runner.habit.copy(createdAt = TODAY.minusDays(2).atStartOfDay(ZoneId.of("UTC")).toInstant())
        val first = fixture.useCase.executeLogWithResult(HABIT_ID, CompletionState.MINIMUM, TODAY) as HabitRewardResult.Granted
        fixture.runner.completions[HABIT_ID to TODAY.minusDays(1)] = fixture.completion(TODAY.minusDays(1), CompletionState.DONE)
        fixture.runner.completions[HABIT_ID to TODAY.minusDays(2)] = fixture.completion(TODAY.minusDays(2), CompletionState.DONE)

        val upgrade = fixture.useCase.executeLogWithResult(HABIT_ID, CompletionState.DONE, TODAY) as HabitRewardResult.Granted

        assertTrue("first grant stored comeback basis", first.comebackApplied)
        assertTrue("upgrade reuses first grant comeback basis", upgrade.comebackApplied)
    }

    @Test
    fun legacySameStateWithoutLedgerRowsReceivesFirstGrantOnce() = runTest {
        fixture.runner.completions[HABIT_ID to TODAY] = fixture.completion(TODAY, CompletionState.DONE)

        val first = fixture.useCase.executeLogWithResult(HABIT_ID, CompletionState.DONE, TODAY)
        val retry = fixture.useCase.executeLogWithResult(HABIT_ID, CompletionState.DONE, TODAY)

        assertTrue("legacy same-state row earns once", first is HabitRewardResult.Granted)
        assertEquals("reward-only retry does not grant twice", HabitRewardResult.NoReward(RewardNoRewardReason.ALREADY_REWARDED), retry)
        assertEquals("reward-only re-log does not invoke reminder", 0, fixture.reminder.logged.size)
        assertEquals("reward-only re-log does not refresh widget", 0, fixture.widgetUpdates)
    }

    @Test
    fun backfillLogsCompletionWithoutLedgerOrPityMovement() = runTest {
        fixture.random.script(0, 0)
        val result = fixture.useCase.executeLogWithResult(HABIT_ID, CompletionState.DONE, TODAY.minusDays(1))

        assertEquals("backfill is unrewarded", HabitRewardResult.NoReward(RewardNoRewardReason.BACKFILL), result)
        assertTrue("backfill writes no ledger rows", fixture.runner.ledgerRows.isEmpty())
        assertNull("backfill creates no drop record", fixture.runner.dropRecords.firstOrNull())
        assertEquals("backfill does not move pity", 0, fixture.runner.latestPityCounter())
    }

    @Test
    fun unscheduledDayLogsWithoutRewardsAndScheduledDayCanEarn() = runTest {
        fixture.runner.habits[HABIT_ID] = fixture.runner.habit.copy(schedule = ScheduleType.Weekdays(setOf(DayOfWeek.MONDAY)))
        val offDay = LocalDate.of(2025, 5, 11)
        fixture.clock = FixedClock(offDay.atTime(12, 0).atZone(ZoneId.of("UTC")).toInstant())

        val skipped = fixture.useCase.executeLogWithResult(HABIT_ID, CompletionState.DONE, offDay)
        assertEquals("unscheduled date remains logged but earns nothing", HabitRewardResult.NoReward(RewardNoRewardReason.NOT_SCHEDULED), skipped)
        assertEquals("unscheduled completion persists", CompletionState.DONE, fixture.runner.completion(HABIT_ID, offDay)?.state)
        assertTrue("unscheduled date writes no ledger", fixture.runner.ledgerRows.isEmpty())

        val scheduled = LocalDate.of(2025, 5, 12)
        fixture.clock = FixedClock(scheduled.atTime(12, 0).atZone(ZoneId.of("UTC")).toInstant())
        val reward = fixture.useCase.executeLogWithResult(HABIT_ID, CompletionState.DONE, scheduled)
        assertTrue("next scheduled date earns", reward is HabitRewardResult.Granted)
    }

    @Test
    fun unknownScheduleLogsWithoutRewards() = runTest {
        fixture.runner.habits[HABIT_ID] = fixture.runner.habit.copy(schedule = ScheduleType.Unknown("v99"))

        val result = fixture.useCase.executeLogWithResult(HABIT_ID, CompletionState.DONE, TODAY)

        assertEquals("unknown schedule has its own no-reward reason", HabitRewardResult.NoReward(RewardNoRewardReason.UNKNOWN_SCHEDULE), result)
        assertTrue("unknown schedule writes no ledger rows", fixture.runner.ledgerRows.isEmpty())
    }

    @Test
    fun weeklyQuotaBlocksExtraNPerWeekReward() = runTest {
        fixture.runner.habits[HABIT_ID] = fixture.runner.habit.copy(schedule = ScheduleType.NPerWeek(1))
        fixture.runner.addPaidDate(TODAY.minusDays(1))

        val result = fixture.useCase.executeLogWithResult(HABIT_ID, CompletionState.DONE, TODAY)

        assertEquals("N_PER_WEEK paid-date quota is enforced", HabitRewardResult.NoReward(RewardNoRewardReason.WEEKLY_GOAL_REACHED), result)
        assertEquals("quota rejection writes no rows", 1, fixture.runner.ledgerRows.size)
    }

    @Test
    fun dailySeedsCapReducesGrantAndZeroGrantStillWritesRows() = runTest {
        fixture.runner.addPositiveSeeds(TODAY, 299)
        val reduced = fixture.useCase.executeLogWithResult(HABIT_ID, CompletionState.DONE, TODAY) as HabitRewardResult.Granted
        val fullGrant = RewardLogFixture().useCase.executeLogWithResult(HABIT_ID, CompletionState.DONE, TODAY) as HabitRewardResult.Granted
        assertEquals("daily cap limits Seeds grant", 1, reduced.seeds)
        assertEquals("daily cap amount is reported", fullGrant.seeds - reduced.seeds, reduced.capApplied)

        fixture.runner.addPositiveSeeds(TODAY, 1)
        val nextHabitId = "habit-cap-zero"
        fixture.runner.habits[nextHabitId] = fixture.runner.habit.copy(id = nextHabitId)
        val zero = fixture.useCase.executeLogWithResult(nextHabitId, CompletionState.DONE, TODAY) as HabitRewardResult.Granted
        assertEquals("cap exhaustion grants zero Seeds", 0, zero.seeds)
        assertEquals("zero-Seed grant still pays XP", 10, zero.xp)
        assertTrue("zero-Seed first grant still writes stage rows", fixture.runner.stageRows(nextHabitId, TODAY).isNotEmpty())
        assertNotNull("zero-Seed first DONE grant stores pity counter", fixture.runner.latestPityCounterOrNull())
    }

    @Test
    fun habitXpRemainsUncappedAfterLargePriorLedgerXp() = runTest {
        fixture.runner.addPriorXp(10_000, "prior-habit-xp")
        fixture.runner.addPriorXp(150, null)

        val result = fixture.useCase.executeLogWithResult(HABIT_ID, CompletionState.DONE, TODAY) as HabitRewardResult.Granted

        assertEquals("habit XP has no daily cap after 10,150 prior XP", 10, result.xp)
    }

    @Test
    fun completionAtTwoAmUsesPreviousLogicalDateAndWeek() = runTest {
        fixture.settings.value = AppSettings(dayBoundaryHour = 4, startOfWeekDay = DayOfWeek.MONDAY)
        fixture.clock = FixedClock(Instant.parse("2025-05-12T02:00:00Z"))
        fixture.runner.habits[HABIT_ID] = fixture.runner.habit.copy(schedule = ScheduleType.NPerWeek(1))

        val result = fixture.useCase.executeLogWithResult(HABIT_ID, CompletionState.DONE, LocalDate.of(2025, 5, 11))

        assertTrue("02:00 is evaluated on the prior logical date", result is HabitRewardResult.Granted)
        assertEquals("completion persists against prior logical date", CompletionState.DONE, fixture.runner.completion(HABIT_ID, LocalDate.of(2025, 5, 11))?.state)
        assertEquals("settings are read once", 1, fixture.settings.readCount)
    }

    @Test
    fun pityAtThirtySkipsChanceRoll() = runTest {
        fixture.runner.addPityRow(pityAfter = 30)
        fixture.random.script(99)

        val result = fixture.useCase.executeLogWithResult(HABIT_ID, CompletionState.DONE, TODAY) as HabitRewardResult.Granted

        assertEquals("pity guarantee consumes rarity roll only", 1, fixture.random.calls)
        assertEquals("pity guarantee produces rarity", DropRarity.LEGENDARY, result.drop)
    }

    private companion object {
        const val HABIT_ID = "reward-habit"
        val TODAY = LocalDate.of(2025, 5, 10)
    }
}

internal class RewardLogFixture(initialClock: AppClock = FixedClock(Instant.parse("2025-05-10T12:00:00Z"))) {
    val repository = FakeRewardCompletionRepository()
    val runner = FakeRewardTransactionRunner(repository)
    val settings = FakeSettingsRepository()
    val random = ScriptedRewardRandom()
    val reminder = RecordingReminder()
    var widgetUpdates = 0
    var clock: AppClock = initialClock
    val useCase: LogCompletionUseCase
        get() = createUseCase()

    fun createUseCase(
        reminderHandler: CompletionReminderHandler = reminder,
        widgetUpdater: WidgetUpdater = WidgetUpdater { widgetUpdates++ }
    ) = LogCompletionUseCase(
            completionRepository = repository,
            clock = clock,
            reminderHandler = reminderHandler,
            widgetUpdater = widgetUpdater,
            settingsRepository = settings,
            transactionRunner = runner,
            rewardRandom = random
        )

    fun completion(date: LocalDate, state: CompletionState) = LoggedCompletion(
        habitId = "reward-habit", logicalDate = date, state = state,
        completedAt = date.atTime(LocalTime.NOON).atZone(ZoneId.of("UTC")).toInstant()
    )

    suspend fun seedEarlier(state: CompletionState, date: LocalDate) {
        runner.completions["reward-habit" to date] = completion(date, state)
    }
}

internal class FixedClock(private val now: Instant) : AppClock {
    override fun instant() = now
    override fun zoneId() = ZoneId.of("UTC")
}

internal class ScriptedRewardRandom : RewardRandom {
    private val script = ArrayDeque<Int>()
    var calls = 0
        private set

    fun script(vararg values: Int) { script.clear(); script.addAll(values.toList()); calls = 0 }
    override fun nextInt(boundExclusive: Int): Int {
        calls++
        return (script.removeFirstOrNull() ?: 99).coerceIn(0, boundExclusive - 1)
    }
}

internal class RecordingReminder : CompletionReminderHandler {
    val logged = mutableListOf<Pair<String, LocalDate>>()
    override suspend fun onCompletionLogged(habitId: String, logicalDate: LocalDate) { logged += habitId to logicalDate }
}

internal class FakeSettingsRepository : SettingsRepository {
    var value = AppSettings()
    var readCount = 0
    override fun getSettings(): Flow<AppSettings> = flow { readCount++; emit(value) }
    override suspend fun updateSettings(settings: AppSettings) { value = settings }
    override suspend fun getPostedHabitIds(logicalDateString: String) = emptySet<String>()
    override suspend fun addPostedHabitId(logicalDateString: String, habitId: String) = Unit
    override suspend fun clearPostedHabitIds(logicalDateString: String) = Unit
    override suspend fun pruneOldPostedHabitIds(cutoffDateString: String) = Unit
}

internal class FakeRewardCompletionRepository : CompletionRepository {
    private val rows = MutableStateFlow<Map<Pair<String, LocalDate>, LoggedCompletion>>(emptyMap())
    override fun getCompletionsForHabit(habitId: String): Flow<List<LoggedCompletion>> = rows.map { all -> all.values.filter { it.habitId == habitId } }
    override fun getAllCompletions(): Flow<List<LoggedCompletion>> = rows.map { it.values.toList() }
    override suspend fun logCompletion(completion: LoggedCompletion) { rows.value = rows.value + ((completion.habitId to completion.logicalDate) to completion) }
    override suspend fun removeCompletion(habitId: String, logicalDate: LocalDate) { rows.value = rows.value - (habitId to logicalDate) }
    override suspend fun getCompletion(habitId: String, logicalDate: LocalDate) = rows.value[habitId to logicalDate]
}

internal class FakeRewardTransactionRunner(private val repository: CompletionRepository) : RewardTransactionRunner {
    val habits = mutableMapOf<String, Habit>()
    val completions = mutableMapOf<Pair<String, LocalDate>, LoggedCompletion>()
    val ledgerRows = mutableListOf<RewardLedgerEntry>()
    val dropRecords = mutableListOf<RewardDropRecord>()
    var habit = Habit(id = "reward-habit", name = "Read", schedule = ScheduleType.Daily, createdAt = Instant.parse("2025-01-01T00:00:00Z"))
    private var nextId = 1L

    init { habits[habit.id] = habit }
    fun completion(habitId: String, date: LocalDate) = completions[habitId to date]
    suspend fun addPaidDate(date: LocalDate) {
        val row = RewardLedgerEntry(currency = RewardCurrency.SEEDS, delta = 0, reason = LedgerReason.HABIT_REWARD,
            habitId = "reward-habit", logicalDate = date, createdAt = Instant.EPOCH, idempotencyKey = "seed-$date",
            rewardStage = RewardStage.DONE, difficulty = Difficulty.EASY, streakLength = 1, comebackApplied = false,
            stageEntitlementAmount = 10, pityCounterAfter = 0)
        ledgerRows += row
    }
    suspend fun addPositiveSeeds(date: LocalDate, amount: Int) {
        ledgerRows += RewardLedgerEntry(currency = RewardCurrency.SEEDS, delta = amount.toLong(), reason = LedgerReason.HABIT_REWARD,
            habitId = "cap-source", logicalDate = date, createdAt = Instant.EPOCH, idempotencyKey = "cap-$amount-${ledgerRows.size}",
            rewardStage = RewardStage.DONE, difficulty = Difficulty.EASY, streakLength = 1, comebackApplied = false,
            stageEntitlementAmount = amount.toLong(), pityCounterAfter = 0)
    }
    suspend fun addPriorXp(amount: Long, habitId: String?) {
        ledgerRows += RewardLedgerEntry(currency = RewardCurrency.XP, delta = amount, reason = LedgerReason.HABIT_REWARD,
            habitId = habitId, logicalDate = TODAY, createdAt = Instant.EPOCH, idempotencyKey = "prior-xp-${ledgerRows.size}",
            rewardStage = RewardStage.DONE, difficulty = Difficulty.EASY, streakLength = 1, comebackApplied = false,
            stageEntitlementAmount = amount, pityCounterAfter = null)
    }
    suspend fun addPityRow(pityAfter: Int) {
        ledgerRows += RewardLedgerEntry(currency = RewardCurrency.SEEDS, delta = 0, reason = LedgerReason.HABIT_REWARD,
            habitId = "old", logicalDate = TODAY, createdAt = Instant.EPOCH, idempotencyKey = "pity-$pityAfter",
            rewardStage = RewardStage.DONE, difficulty = Difficulty.EASY, streakLength = 1, comebackApplied = false,
            stageEntitlementAmount = 10, pityCounterAfter = pityAfter)
    }
    fun latestPityCounter() = latestPityCounterOrNull() ?: 0
    fun latestPityCounterOrNull() = ledgerRows.lastOrNull { it.currency == RewardCurrency.SEEDS && it.rewardStage == RewardStage.DONE }?.pityCounterAfter
    fun stageRows(habitId: String, date: LocalDate) = ledgerRows.filter { it.habitId == habitId && it.logicalDate == date }

    override suspend fun <T> inTransaction(block: suspend RewardTransactionScope.() -> T): T {
        val scope = object : RewardTransactionScope {
            override val habits = object : RewardHabitTransactionPort {
                override suspend fun getById(habitId: String) = this@FakeRewardTransactionRunner.habits[habitId]
            }
            override val completions = object : RewardCompletionTransactionPort {
                override suspend fun get(habitId: String, logicalDate: LocalDate) = this@FakeRewardTransactionRunner.completions[habitId to logicalDate]
                    ?: repository.getCompletion(habitId, logicalDate)
                override suspend fun forHabit(habitId: String) = (this@FakeRewardTransactionRunner.completions.values.filter { it.habitId == habitId } + repository.getCompletionsForHabit(habitId).first()).distinctBy { it.logicalDate }
                override suspend fun put(completion: LoggedCompletion) {
                    this@FakeRewardTransactionRunner.completions[completion.habitId to completion.logicalDate] = completion
                    repository.logCompletion(completion)
                }
                override suspend fun remove(habitId: String, logicalDate: LocalDate) { this@FakeRewardTransactionRunner.completions.remove(habitId to logicalDate) }
            }
            override val ledger = object : RewardLedgerTransactionPort {
                override suspend fun insert(entry: RewardLedgerEntry): Long { ledgerRows += entry.copy(id = nextId++, logicalDate = entry.logicalDate ?: LocalDate.MIN); return nextId - 1 }
                override suspend fun sumPositiveHabitSeeds(logicalDate: LocalDate) = ledgerRows.filter { it.currency == RewardCurrency.SEEDS && it.logicalDate == logicalDate && it.delta > 0 }.sumOf { it.delta }
                override suspend fun highestStage(habitId: String, logicalDate: LocalDate) = stageRows(habitId, logicalDate).mapNotNull { it.rewardStage }.maxByOrNull { it.ordinal }
                override suspend fun stageRowsForSavedBasis(habitId: String, logicalDate: LocalDate) = stageRows(habitId, logicalDate)
                override suspend fun latestFirstDoneRowForPity() = ledgerRows.lastOrNull { it.currency == RewardCurrency.SEEDS && it.rewardStage == RewardStage.DONE }
                override suspend fun distinctPaidDatesInRangeExcludingDate(habitId: String, startDate: LocalDate, endDate: LocalDate, excludedDate: LocalDate) = ledgerRows.asSequence().filter { it.currency == RewardCurrency.SEEDS && it.habitId == habitId && it.rewardStage in setOf(RewardStage.MINIMUM, RewardStage.DONE) }.mapNotNull { row -> row.logicalDate?.takeIf { it in startDate..endDate && it != excludedDate } }.distinct().toList()
                override suspend fun balance(currency: RewardCurrency) = ledgerRows.filter { it.currency == currency }.sumOf { it.delta }
                override suspend fun totalXp() = balance(RewardCurrency.XP)
            }
            override val drops = object : RewardDropTransactionPort {
                override suspend fun insert(record: RewardDropRecord) { dropRecords += record }
            }
        }
        return scope.block()
    }

    companion object { val TODAY = LocalDate.of(2025, 5, 10) }
}
