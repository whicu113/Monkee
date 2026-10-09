package com.habitat.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.habitat.app.data.local.converters.HabitConverters

@Database(
    entities = [
        HabitEntity::class,
        HabitGroupEntity::class,
        CompletionEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(HabitConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitGroupDao(): HabitGroupDao
    abstract fun completionDao(): CompletionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `habit_groups` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `completions` (`id` TEXT NOT NULL, `habitId` TEXT NOT NULL, `logicalDate` TEXT NOT NULL, `state` TEXT NOT NULL, `completedAt` INTEGER NOT NULL, `reason` TEXT, `note` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_completions_habitId_logicalDate` ON `completions` (`habitId`, `logicalDate`)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `habits` ADD COLUMN `reminderTime` TEXT DEFAULT NULL"
                )
            }
        }
    }
}
