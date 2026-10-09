package com.habitat.app.domain.usecase

import java.time.LocalDate

/**
 * Domain interface for reminder-related actions triggered after a completion is logged.
 * Implemented in platform/ to dismiss active notifications, cancel pending snoozes, and re-arm.
 * SPEC Section 8.1.
 */
interface CompletionReminderHandler {
    suspend fun onCompletionLogged(habitId: String, logicalDate: LocalDate)
}
