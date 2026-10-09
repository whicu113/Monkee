package com.habitat.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Data Access Object for completions.
 * SPEC Section 9.
 */
@Dao
interface CompletionDao {

    @Query("SELECT * FROM completions WHERE habitId = :habitId ORDER BY logicalDate ASC")
    fun getCompletionsForHabit(habitId: String): Flow<List<CompletionEntity>>

    @Query("SELECT * FROM completions ORDER BY logicalDate ASC")
    fun getAllCompletions(): Flow<List<CompletionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(completion: CompletionEntity): Long

    @Query("DELETE FROM completions WHERE habitId = :habitId AND logicalDate = :logicalDate")
    suspend fun deleteCompletion(habitId: String, logicalDate: LocalDate): Int

    @Query("SELECT * FROM completions WHERE habitId = :habitId AND logicalDate = :logicalDate")
    suspend fun getCompletion(habitId: String, logicalDate: LocalDate): CompletionEntity?
}
