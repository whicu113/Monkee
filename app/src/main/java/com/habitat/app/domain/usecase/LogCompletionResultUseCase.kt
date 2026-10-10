package com.habitat.app.domain.usecase

import com.habitat.app.domain.HabitRewardResult
import com.habitat.app.domain.model.CompletionState
import java.time.LocalDate

/** Returns the reward outcome for an already accepted completion log. */
interface LogCompletionResultUseCase {
    suspend fun executeLogWithResult(
        habitId: String,
        state: CompletionState,
        logicalDate: LocalDate
    ): HabitRewardResult
}
