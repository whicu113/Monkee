package com.habitat.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for habit groups.
 * SPEC Section 9.
 */
@Dao
interface HabitGroupDao {

    @Query("SELECT * FROM habit_groups ORDER BY sortOrder ASC")
    fun getAllGroups(): Flow<List<HabitGroupEntity>>

    @Query("SELECT * FROM habit_groups WHERE id = :id")
    suspend fun getGroupById(id: String): HabitGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: HabitGroupEntity): Long

    @Update
    suspend fun updateGroup(group: HabitGroupEntity): Int

    @Query("DELETE FROM habit_groups WHERE id = :id")
    suspend fun deleteGroup(id: String): Int
}
