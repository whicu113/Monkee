package com.habitat.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.LoggedCompletion
import java.time.Instant
import java.time.LocalDate

/**
 * Room database entity for user-logged completions.
 * SPEC Section 9. Unique constraint on (habitId, logicalDate).
 */
@Entity(
    tableName = "completions",
    indices = [
        Index(value = ["habitId", "logicalDate"], unique = true)
    ]
)
data class CompletionEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val logicalDate: LocalDate,
    val state: CompletionState,
    val completedAt: Instant,
    val reason: String?,
    val note: String?
)

fun CompletionEntity.toDomainModel(): LoggedCompletion = LoggedCompletion(
    id = id,
    habitId = habitId,
    logicalDate = logicalDate,
    state = state,
    completedAt = completedAt,
    reason = reason,
    note = note
)

fun LoggedCompletion.toEntity(): CompletionEntity = CompletionEntity(
    id = id,
    habitId = habitId,
    logicalDate = logicalDate,
    state = state,
    completedAt = completedAt,
    reason = reason,
    note = note
)
