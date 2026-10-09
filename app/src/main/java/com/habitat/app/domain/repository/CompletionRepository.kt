package com.habitat.app.domain.repository

import com.habitat.app.domain.model.LoggedCompletion
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface CompletionRepository {
    fun getCompletionsForHabit(habitId: String): Flow<List<LoggedCompletion>>
    fun getAllCompletions(): Flow<List<LoggedCompletion>>
    suspend fun logCompletion(completion: LoggedCompletion)
    suspend fun removeCompletion(habitId: String, logicalDate: LocalDate)
}
