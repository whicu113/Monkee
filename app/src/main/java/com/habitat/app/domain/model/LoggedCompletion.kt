package com.habitat.app.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Domain model representing a user-logged completion entry.
 * SPEC Section 4.4 & 9.
 */
data class LoggedCompletion(
    val id: String = UUID.randomUUID().toString(),
    val habitId: String,
    val logicalDate: LocalDate,
    val state: CompletionState,
    val completedAt: Instant = Instant.now(),
    val reason: String? = null,
    val note: String? = null
)
