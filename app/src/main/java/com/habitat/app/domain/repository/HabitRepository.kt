package com.habitat.app.domain.repository

import com.habitat.app.domain.model.Habit
import kotlinx.coroutines.flow.Flow

interface HabitRepository {
    fun getActiveHabits(): Flow<List<Habit>>
    fun getAllHabits(): Flow<List<Habit>>
    suspend fun getHabitById(id: String): Habit?
    suspend fun saveHabit(habit: Habit)
    suspend fun archiveHabit(id: String)
    suspend fun deleteHabit(id: String)
}
