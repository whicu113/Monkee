package com.habitat.app.data.repository

import com.habitat.app.data.local.HabitDao
import com.habitat.app.data.local.toDomainModel
import com.habitat.app.data.local.toEntity
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitRepositoryImpl @Inject constructor(
    private val habitDao: HabitDao
) : HabitRepository {

    override fun getActiveHabits(): Flow<List<Habit>> {
        return habitDao.getActiveHabits().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getAllHabits(): Flow<List<Habit>> {
        return habitDao.getAllHabits().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getHabitById(id: String): Habit? {
        return habitDao.getHabitById(id)?.toDomainModel()
    }

    override suspend fun saveHabit(habit: Habit) {
        habitDao.insertHabit(habit.toEntity())
    }

    override suspend fun archiveHabit(id: String) {
        habitDao.archiveHabit(id)
    }

    override suspend fun deleteHabit(id: String) {
        habitDao.deleteHabit(id)
    }
}
