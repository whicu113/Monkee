package com.example.monkee.domain.repository

import com.example.monkee.domain.model.Habit
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Habit CRUD and querying operations.
 * SPEC Section 4 & 12 (M1).
 */
interface HabitRepository {
    fun getActiveHabits(): Flow<List<Habit>>
    fun getAllHabits(): Flow<List<Habit>>
    suspend fun getHabitById(id: String): Habit?
    suspend fun saveHabit(habit: Habit)
    suspend fun archiveHabit(id: String)
    suspend fun deleteHabit(id: String)
}
