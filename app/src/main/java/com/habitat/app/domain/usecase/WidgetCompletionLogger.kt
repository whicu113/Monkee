package com.habitat.app.domain.usecase

import com.habitat.app.domain.model.CompletionState
import java.time.LocalDate

/** Small use-case port used by widget action orchestration and its unit tests. */
fun interface WidgetCompletionLogger {
    suspend fun executeLog(habitId: String, state: CompletionState, logicalDate: LocalDate): Boolean
}
