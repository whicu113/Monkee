package com.habitat.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy

@Dao
interface DropRecordDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: DropRecordEntity)
}
