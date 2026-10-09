package com.habitat.app.data.repository

import com.habitat.app.data.local.CompletionDao
import com.habitat.app.data.local.toDomainModel
import com.habitat.app.data.local.toEntity
import com.habitat.app.domain.model.LoggedCompletion
import com.habitat.app.domain.repository.CompletionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompletionRepositoryImpl @Inject constructor(
    private val completionDao: CompletionDao
) : CompletionRepository {

    override fun getCompletionsForHabit(habitId: String): Flow<List<LoggedCompletion>> {
        return completionDao.getCompletionsForHabit(habitId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getAllCompletions(): Flow<List<LoggedCompletion>> {
        return completionDao.getAllCompletions().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun logCompletion(completion: LoggedCompletion) {
        completionDao.insertOrUpdate(completion.toEntity())
    }

    override suspend fun removeCompletion(habitId: String, logicalDate: LocalDate) {
        completionDao.deleteCompletion(habitId, logicalDate)
    }

    override suspend fun getCompletion(habitId: String, logicalDate: LocalDate): LoggedCompletion? {
        return completionDao.getCompletion(habitId, logicalDate)?.toDomainModel()
    }
}
