package com.habitat.app.domain.usecase

import com.habitat.app.domain.AppClock
import com.habitat.app.domain.HabitRewardResult
import com.habitat.app.domain.RewardNoRewardReason
import com.habitat.app.domain.RewardTransactionRunner
import com.habitat.app.domain.WidgetUpdater
import com.habitat.app.domain.logic.RewardRandom
import com.habitat.app.domain.updateSafely
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.LoggedCompletion
import com.habitat.app.domain.repository.CompletionRepository
import com.habitat.app.domain.repository.SettingsRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Domain UseCase for logging or removing completions.
 * Enforces trailing 7-day backfill rule, blocks future dates, and ensures idempotency.
 * SPEC Section 4.4 & 8.1.
 */
class LogCompletionUseCase @Inject constructor(
    private val completionRepository: CompletionRepository,
    private val clock: AppClock,
    private val reminderHandler: CompletionReminderHandler,
    private val widgetUpdater: WidgetUpdater,
    private val settingsRepository: SettingsRepository,
    private val transactionRunner: RewardTransactionRunner,
    private val rewardRandom: RewardRandom
) : WidgetCompletionLogger {

    /** Reward-aware API; the integration is intentionally behavior-preserving until its next change. */
    suspend fun executeLogWithResult(
        habitId: String,
        state: CompletionState,
        logicalDate: LocalDate
    ): HabitRewardResult {
        val logged = executeLog(habitId, state, logicalDate)
        if (!logged) return HabitRewardResult.NoReward(RewardNoRewardReason.BACKFILL)
        return HabitRewardResult.Granted(
            seeds = 0,
            xp = 0,
            drop = null,
            comebackApplied = false,
            capApplied = 0
        )
    }

    /**
     * Logs or updates a completion entry for [habitId] on [logicalDate].
     * Blocked if [logicalDate] is in the future or older than 7 logical days.
     * Invokes [reminderHandler.onCompletionLogged] only after a successful new log (not on rejected or duplicate taps).
     */
    override suspend fun executeLog(
        habitId: String,
        state: CompletionState,
        logicalDate: LocalDate
    ): Boolean {
        val today = clock.logicalDate()
        if (logicalDate > today || logicalDate < today.minusDays(7)) {
            return false
        }

        val existing = completionRepository.getCompletion(habitId, logicalDate)
        if (existing?.state == state) {
            // Duplicate tap: one row, no error, handler not called twice
            return true
        }

        completionRepository.logCompletion(
            LoggedCompletion(
                habitId = habitId,
                logicalDate = logicalDate,
                state = state
            )
        )
        reminderHandler.onCompletionLogged(habitId, logicalDate)
        return true
    }

    suspend fun executeLog(habitId: String, state: CompletionState): Boolean =
        executeLog(habitId, state, clock.logicalDate())

    /**
     * Removes a completion entry for [habitId] on [logicalDate] (uncheck).
     */
    suspend fun executeRemove(
        habitId: String,
        logicalDate: LocalDate = clock.logicalDate()
    ) {
        completionRepository.removeCompletion(habitId, logicalDate)
        widgetUpdater.updateSafely()
    }
}
