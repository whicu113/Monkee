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
        CompletionEntity::class,
        LedgerEntryEntity::class,
        DropRecordEntity::class,
        WorldIslandEntity::class,
        WorldTileEntity::class,
        WorldItemEntity::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(HabitConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitGroupDao(): HabitGroupDao
    abstract fun completionDao(): CompletionDao
    abstract fun ledgerEntryDao(): LedgerEntryDao
    abstract fun dropRecordDao(): DropRecordDao
    abstract fun worldDao(): WorldDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ledger_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `currency` TEXT NOT NULL, `delta` INTEGER NOT NULL, `reason` TEXT NOT NULL, `habitId` TEXT, `logicalDate` TEXT, `createdAt` INTEGER NOT NULL, `idempotencyKey` TEXT NOT NULL, `rewardStage` TEXT, `difficulty` TEXT, `streakLength` INTEGER, `comebackApplied` INTEGER, `stageEntitlementAmount` INTEGER, `pityCounterAfter` INTEGER)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_ledger_entries_idempotencyKey` ON `ledger_entries` (`idempotencyKey`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ledger_entries_reason_currency_logicalDate` ON `ledger_entries` (`reason`, `currency`, `logicalDate`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ledger_entries_habitId_logicalDate` ON `ledger_entries` (`habitId`, `logicalDate`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `drop_records` (`id` TEXT NOT NULL, `rarity` TEXT NOT NULL, `habitId` TEXT NOT NULL, `logicalDate` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_drop_records_habitId_logicalDate` ON `drop_records` (`habitId`, `logicalDate`)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `world_islands` (`id` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `world_tiles` (`islandId` TEXT NOT NULL, `x` INTEGER NOT NULL, `y` INTEGER NOT NULL, `terrain` TEXT NOT NULL, PRIMARY KEY(`islandId`, `x`, `y`), FOREIGN KEY(`islandId`) REFERENCES `world_islands`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_world_tiles_islandId` ON `world_tiles` (`islandId`)"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `world_items` (`id` TEXT NOT NULL, `islandId` TEXT NOT NULL, `type` TEXT NOT NULL, `x` INTEGER NOT NULL, `y` INTEGER NOT NULL, `rotation` INTEGER NOT NULL, `flipped` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`islandId`) REFERENCES `world_islands`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_world_items_islandId` ON `world_items` (`islandId`)"
                )
            }
        }
    }
}
