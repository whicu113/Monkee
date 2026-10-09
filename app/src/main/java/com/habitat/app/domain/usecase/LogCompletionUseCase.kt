package com.habitat.app.domain.usecase

import com.habitat.app.domain.AppClock
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.LoggedCompletion
import com.habitat.app.domain.repository.CompletionRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Domain UseCase for logging or removing completions.
 * Enforces trailing 7-day backfill rule, blocks future dates, and ensures idempotency.
 * SPEC Section 4.4 & 8.1.
 */
class LogCompletionUseCase @Inject constructor(
    private val completionRepository: CompletionRepository,
    private val clock: AppClock
) {

    /**
     * Logs or updates a completion entry for [habitId] on [logicalDate].
     * Blocked if [logicalDate] is in the future or older than 7 logical days.
     */
    suspend fun executeLog(
        habitId: String,
        state: CompletionState,
        logicalDate: LocalDate = clock.logicalDate()
    ): Boolean {
        val today = clock.logicalDate()
        if (logicalDate > today || logicalDate < today.minusDays(7)) {
            return false
        }

        completionRepository.logCompletion(
            LoggedCompletion(
                habitId = habitId,
                logicalDate = logicalDate,
                state = state
            )
        )
        return true
    }

    /**
     * Removes a completion entry for [habitId] on [logicalDate] (uncheck).
     */
    suspend fun executeRemove(
        habitId: String,
        logicalDate: LocalDate = clock.logicalDate()
    ) {
        completionRepository.removeCompletion(habitId, logicalDate)
    }
}
