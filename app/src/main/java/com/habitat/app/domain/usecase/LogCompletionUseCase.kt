package com.habitat.app.domain.usecase

import com.habitat.app.domain.AppClock
import com.habitat.app.domain.HabitRewardResult
import com.habitat.app.domain.LedgerKeyConflictException
import com.habitat.app.domain.LedgerReason
import com.habitat.app.domain.RewardDropRecord
import com.habitat.app.domain.RewardLedgerEntry
import com.habitat.app.domain.RewardNoRewardReason
import com.habitat.app.domain.RewardTransactionRunner
import com.habitat.app.domain.RewardTransactionScope
import com.habitat.app.domain.WidgetUpdater
import com.habitat.app.domain.logic.RewardAmounts
import com.habitat.app.domain.logic.RewardBasis
import com.habitat.app.domain.logic.RewardCurrency
import com.habitat.app.domain.logic.RewardRandom
import com.habitat.app.domain.logic.RewardStage
import com.habitat.app.domain.logic.ReplayEngine
import com.habitat.app.domain.logic.calculateHabitReward
import com.habitat.app.domain.logic.rewardIdempotencyKey
import com.habitat.app.domain.logic.ScheduleEvaluator
import com.habitat.app.domain.evaluateRewardEligibility
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.LoggedCompletion
import com.habitat.app.domain.repository.CompletionRepository
import com.habitat.app.domain.repository.SettingsRepository
import com.habitat.app.domain.updateSafely
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

/** Logs a completion and its eligible rewards atomically. */
class LogCompletionUseCase @Inject constructor(
    private val completionRepository: CompletionRepository,
    private val clock: AppClock,
    private val reminderHandler: CompletionReminderHandler,
    private val widgetUpdater: WidgetUpdater,
    private val settingsRepository: SettingsRepository,
    private val transactionRunner: RewardTransactionRunner,
    private val rewardRandom: RewardRandom
) : WidgetCompletionLogger {

    override suspend fun executeLog(
        habitId: String,
        state: CompletionState,
        logicalDate: LocalDate
    ): Boolean = executeLogInternal(habitId, state, logicalDate).accepted

    suspend fun executeLogWithResult(
        habitId: String,
        state: CompletionState,
        logicalDate: LocalDate
    ): HabitRewardResult = executeLogInternal(habitId, state, logicalDate).result

    suspend fun executeLog(habitId: String, state: CompletionState): Boolean {
        val settings = settingsRepository.getSettings().first()
        val logicalDate = clock.logicalDate(settings.dayBoundaryHour)
        return executeLogInternal(habitId, state, logicalDate, settings).accepted
    }

    private suspend fun executeLogInternal(
        habitId: String,
        state: CompletionState,
        logicalDate: LocalDate,
        settingsOverride: com.habitat.app.domain.model.AppSettings? = null
    ): LogOutcome {
        // Settings are captured once, before entering the transaction.
        val settings = settingsOverride ?: settingsRepository.getSettings().first()
        val today = clock.logicalDate(settings.dayBoundaryHour)
        if (logicalDate > today || logicalDate < today.minusDays(7)) {
            return LogOutcome(accepted = false, result = HabitRewardResult.NoReward(RewardNoRewardReason.BACKFILL))
        }

        val outcome = try {
            transactionRunner.inTransaction {
                applyLog(this, habitId, state, logicalDate, today, settings)
            }
        } catch (_: LedgerKeyConflictException) {
            // Retry the whole transaction once so the second pass observes the winning ledger rows.
            transactionRunner.inTransaction {
                applyLog(this, habitId, state, logicalDate, today, settings)
            }
        }

        if (outcome.completionChanged) {
            try {
                reminderHandler.onCompletionLogged(habitId, logicalDate)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                LOGGER.log(Level.SEVERE, "Completion reminder reconciliation failed", exception)
            }
            widgetUpdater.updateSafely()
        }
        return LogOutcome(accepted = true, result = outcome.result)
    }

    private suspend fun applyLog(
        scope: RewardTransactionScope,
        habitId: String,
        state: CompletionState,
        logicalDate: LocalDate,
        today: LocalDate,
        settings: com.habitat.app.domain.model.AppSettings
    ): TransactionOutcome {
        val existingCompletion = scope.completions.get(habitId, logicalDate)
        val completionChanged = existingCompletion?.state != state
        val completion = if (completionChanged) {
            LoggedCompletion(
                id = existingCompletion?.id ?: UUID.randomUUID().toString(),
                habitId = habitId,
                logicalDate = logicalDate,
                state = state,
                completedAt = clock.instant(),
                reason = existingCompletion?.reason,
                note = existingCompletion?.note
            )
        } else existingCompletion

        val habit = scope.habits.getById(habitId)
        val stageRows = scope.ledger.stageRowsForSavedBasis(habitId, logicalDate)
        val highestStage = stageRows.mapNotNull { it.rewardStage }.maxByOrNull { it.ordinal }

        // The comeback replay sees history before this write; streak replay below sees it after.
        val beforeCompletions = scope.completions.forHabit(habitId)
        val comebackReplay = habit?.let {
            ReplayEngine.evaluate(
                habit = it,
                loggedCompletions = beforeCompletions,
                currentLogicalDate = today,
                strictStreaks = settings.strictStreaks,
                autoUseRestTokens = settings.autoUseRestTokens,
                startOfWeekDay = settings.startOfWeekDay
            )
        }
        val comeback = comebackReplay?.let { ReplayEngine.isComebackBonus(it.occurrenceStates, logicalDate) } ?: false

        if (completionChanged) scope.completions.put(requireNotNull(completion))

        if (state == CompletionState.SKIPPED) {
            return TransactionOutcome(HabitRewardResult.NoReward(RewardNoRewardReason.SKIPPED), completionChanged)
        }

        val stage = if (state == CompletionState.DONE) RewardStage.DONE else RewardStage.MINIMUM
        if (highestStage != null && stage.ordinal <= highestStage.ordinal) {
            return TransactionOutcome(HabitRewardResult.NoReward(RewardNoRewardReason.ALREADY_REWARDED), completionChanged)
        }

        if (logicalDate != today) {
            return TransactionOutcome(HabitRewardResult.NoReward(RewardNoRewardReason.BACKFILL), completionChanged)
        }

        if (habit == null) {
            return TransactionOutcome(HabitRewardResult.NoReward(RewardNoRewardReason.UNKNOWN_SCHEDULE), completionChanged)
        }

        val paidDates = if (habit.schedule is com.habitat.app.domain.model.ScheduleType.NPerWeek) {
            val start = ScheduleEvaluator.startOfWeek(logicalDate, settings.startOfWeekDay)
            scope.ledger.distinctPaidDatesInRangeExcludingDate(habitId, start, start.plusDays(6), logicalDate).toSet()
        } else emptySet()
        val eligibility = evaluateRewardEligibility(
            schedule = habit.schedule,
            logicalDate = logicalDate,
            today = today,
            habitCreatedDate = habit.createdAt.atZone(clock.zoneId()).toLocalDate(),
            startOfWeekDay = settings.startOfWeekDay,
            paidLogicalDatesInWeek = paidDates,
            eligibilityAlreadyGranted = stageRows.isNotEmpty()
        )
        if (eligibility != null) return TransactionOutcome(HabitRewardResult.NoReward(eligibility), completionChanged)

        val afterCompletions = scope.completions.forHabit(habitId)
        val streak = ReplayEngine.evaluate(
            habit = habit,
            loggedCompletions = afterCompletions,
            currentLogicalDate = today,
            strictStreaks = settings.strictStreaks,
            autoUseRestTokens = settings.autoUseRestTokens,
            startOfWeekDay = settings.startOfWeekDay
        ).currentStreak

        val basis = stageRows.firstOrNull()?.let { first ->
            RewardBasis(
                difficulty = first.difficulty ?: habit.difficulty,
                streakLength = first.streakLength ?: streak,
                comeback = first.comebackApplied ?: comeback
            )
        } ?: RewardBasis(habit.difficulty, streak, comeback)
        val grantedByStage = stageRows.groupBy { it.rewardStage }.mapNotNull { (savedStage, rows) ->
            savedStage?.let { saved ->
                val savedBasis = RewardBasis(
                    difficulty = rows.firstNotNullOfOrNull { it.difficulty } ?: basis.difficulty,
                    streakLength = rows.firstNotNullOfOrNull { it.streakLength } ?: basis.streakLength,
                    comeback = rows.firstNotNullOfOrNull { it.comebackApplied } ?: basis.comeback
                )
                val seeds = rows.filter { it.currency == RewardCurrency.SEEDS }.sumOf { it.stageEntitlementAmount ?: 0L }.toInt()
                val xp = rows.filter { it.currency == RewardCurrency.XP }.sumOf { it.stageEntitlementAmount ?: 0L }.toInt()
                saved to RewardAmounts(seeds, xp, savedBasis)
            }
        }.toMap()
        val seedsAlreadyEarned = scope.ledger.sumPositiveHabitSeeds(today).toInt()
        val pityCounter = scope.ledger.latestFirstDoneRowForPity()?.pityCounterAfter ?: 0
        val calculation = calculateHabitReward(
            state = state,
            difficulty = basis.difficulty,
            streakLength = basis.streakLength,
            comeback = basis.comeback,
            grantedByStage = grantedByStage,
            seedsEarnedToday = seedsAlreadyEarned,
            rng = rewardRandom,
            pityCounter = pityCounter
        )
        val entitlement = calculation.grantedAmounts ?: RewardAmounts(0, 0, basis)
        val priorEntitlement = grantedByStage[highestStage] ?: RewardAmounts(0, 0, basis)
        val capApplied = (entitlement.seeds - priorEntitlement.seeds - calculation.seedsGranted).coerceAtLeast(0)

        val createdAt = clock.instant()
        scope.ledger.insert(
            RewardLedgerEntry(
                currency = RewardCurrency.SEEDS,
                delta = calculation.seedsGranted.toLong(),
                reason = LedgerReason.HABIT_REWARD,
                habitId = habitId,
                logicalDate = logicalDate,
                createdAt = createdAt,
                idempotencyKey = rewardIdempotencyKey(habitId, logicalDate, stage, RewardCurrency.SEEDS),
                rewardStage = stage,
                difficulty = basis.difficulty,
                streakLength = basis.streakLength,
                comebackApplied = basis.comeback,
                stageEntitlementAmount = entitlement.seeds.toLong(),
                pityCounterAfter = calculation.pityCounterAfter
            )
        )
        scope.ledger.insert(
            RewardLedgerEntry(
                currency = RewardCurrency.XP,
                delta = calculation.xpGranted.toLong(),
                reason = LedgerReason.HABIT_REWARD,
                habitId = habitId,
                logicalDate = logicalDate,
                createdAt = createdAt,
                idempotencyKey = rewardIdempotencyKey(habitId, logicalDate, stage, RewardCurrency.XP),
                rewardStage = stage,
                difficulty = basis.difficulty,
                streakLength = basis.streakLength,
                comebackApplied = basis.comeback,
                stageEntitlementAmount = entitlement.xp.toLong(),
                pityCounterAfter = null
            )
        )
        calculation.drop?.let { rarity ->
            scope.drops.insert(RewardDropRecord(UUID.randomUUID().toString(), rarity, habitId, logicalDate, createdAt))
        }

        return TransactionOutcome(
            result = HabitRewardResult.Granted(
                seeds = calculation.seedsGranted,
                xp = calculation.xpGranted,
                drop = calculation.drop,
                comebackApplied = basis.comeback,
                capApplied = capApplied
            ),
            completionChanged = completionChanged
        )
    }

    suspend fun executeRemove(habitId: String, logicalDate: LocalDate = clock.logicalDate()) {
        completionRepository.removeCompletion(habitId, logicalDate)
        widgetUpdater.updateSafely()
    }

    private data class LogOutcome(val accepted: Boolean, val result: HabitRewardResult)
    private data class TransactionOutcome(val result: HabitRewardResult, val completionChanged: Boolean)

    private companion object {
        val LOGGER: Logger = Logger.getLogger("LogCompletionUseCase")
    }
}
