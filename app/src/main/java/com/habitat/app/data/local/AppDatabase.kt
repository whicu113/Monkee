package com.habitat.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.habitat.app.data.local.converters.HabitConverters

@Database(
    entities = [
        HabitEntity::class,
        HabitGroupEntity::class,
        CompletionEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(HabitConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitGroupDao(): HabitGroupDao
    abstract fun completionDao(): CompletionDao
}
