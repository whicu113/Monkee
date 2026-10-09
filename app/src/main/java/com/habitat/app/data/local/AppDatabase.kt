package com.habitat.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.habitat.app.data.local.converters.HabitConverters

@Database(
    entities = [
        HabitEntity::class,
        HabitGroupEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(HabitConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitGroupDao(): HabitGroupDao
}
