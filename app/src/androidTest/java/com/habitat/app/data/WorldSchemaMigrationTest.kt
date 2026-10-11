package com.habitat.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.habitat.app.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorldSchemaMigrationTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migration4To5PreservesExistingUserDataAndCreatesWorldSchema() {
        helper.createDatabase(TEST_DATABASE, 4).apply {
            execSQL(
                "INSERT INTO habits (id, name, emoji, color, cue, fullVersion, minVersion, identity, schedule, reminderTime, windowStart, windowEnd, difficulty, groupId, sortOrder, archived, createdAt) VALUES ('existing-habit', 'Walk', '🚶', '#4CAF50', '', '', '', '', 'v1:DAILY', NULL, NULL, NULL, 'EASY', NULL, 0, 0, 1715000000000)"
            )
            execSQL(
                "INSERT INTO completions (id, habitId, logicalDate, state, completedAt, reason, note) VALUES ('existing-completion', 'existing-habit', '2025-05-12', 'DONE', 1715000000000, NULL, NULL)"
            )
            execSQL(
                "INSERT INTO ledger_entries (currency, delta, reason, habitId, logicalDate, createdAt, idempotencyKey) VALUES ('SEEDS', 12, 'HABIT_REWARD', 'existing-habit', '2025-05-12', 1715000000000, 'existing|SEEDS')"
            )
            execSQL(
                "INSERT INTO drop_records (id, rarity, habitId, logicalDate, createdAt) VALUES ('existing-drop', 'COMMON', 'existing-habit', '2025-05-12', 1715000000000)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            5,
            true,
            AppDatabase.MIGRATION_4_5
        )
        try {
            assertEquals("migration preserves Habit", 1, scalarInt(db, "SELECT COUNT(*) FROM habits WHERE id = 'existing-habit'"))
            assertEquals("migration preserves Completion", 1, scalarInt(db, "SELECT COUNT(*) FROM completions WHERE id = 'existing-completion'"))
            assertEquals("migration preserves LedgerEntry", 1, scalarInt(db, "SELECT COUNT(*) FROM ledger_entries WHERE idempotencyKey = 'existing|SEEDS'"))
            assertEquals("migration preserves DropRecord", 1, scalarInt(db, "SELECT COUNT(*) FROM drop_records WHERE id = 'existing-drop'"))
            assertEquals(
                "migration creates both world tables",
                2,
                scalarInt(db, "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN ('world_islands', 'world_tiles')")
            )
            assertEquals(
                "migration creates the world tile foreign-key index",
                1,
                scalarInt(db, "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'index_world_tiles_islandId'")
            )
            assertEquals("migration does not eagerly seed world rows", 0, scalarInt(db, "SELECT COUNT(*) FROM world_islands"))
        } finally {
            db.close()
            InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DATABASE)
        }
    }

    @Test
    fun migration5To6PreservesExistingDataAndCreatesEmptyItemCollection() {
        helper.createDatabase(TEST_DATABASE_5_TO_6, 5).apply {
            execSQL(
                "INSERT INTO habits (id, name, emoji, color, cue, fullVersion, minVersion, identity, schedule, reminderTime, windowStart, windowEnd, difficulty, groupId, sortOrder, archived, createdAt) VALUES ('existing-habit', 'Walk', '🚶', '#4CAF50', '', '', '', '', 'v1:DAILY', NULL, NULL, NULL, 'EASY', NULL, 0, 0, 1715000000000)"
            )
            execSQL(
                "INSERT INTO completions (id, habitId, logicalDate, state, completedAt, reason, note) VALUES ('existing-completion', 'existing-habit', '2025-05-12', 'DONE', 1715000000000, NULL, NULL)"
            )
            execSQL(
                "INSERT INTO ledger_entries (currency, delta, reason, habitId, logicalDate, createdAt, idempotencyKey) VALUES ('SEEDS', 12, 'HABIT_REWARD', 'existing-habit', '2025-05-12', 1715000000000, 'existing|SEEDS')"
            )
            execSQL(
                "INSERT INTO drop_records (id, rarity, habitId, logicalDate, createdAt) VALUES ('existing-drop', 'COMMON', 'existing-habit', '2025-05-12', 1715000000000)"
            )
            execSQL("INSERT INTO world_islands (id, width, height) VALUES ('existing-island', 8, 8)")
            execSQL("INSERT INTO world_tiles (islandId, x, y, terrain) VALUES ('existing-island', 0, 0, 'GRASS')")
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DATABASE_5_TO_6,
            6,
            true,
            AppDatabase.MIGRATION_5_6
        )
        try {
            assertEquals("v5 Habit survives", 1, scalarInt(db, "SELECT COUNT(*) FROM habits WHERE id = 'existing-habit'"))
            assertEquals("v5 Completion survives", 1, scalarInt(db, "SELECT COUNT(*) FROM completions WHERE id = 'existing-completion'"))
            assertEquals("v5 LedgerEntry survives", 1, scalarInt(db, "SELECT COUNT(*) FROM ledger_entries WHERE idempotencyKey = 'existing|SEEDS'"))
            assertEquals("v5 DropRecord survives", 1, scalarInt(db, "SELECT COUNT(*) FROM drop_records WHERE id = 'existing-drop'"))
            assertEquals("v5 island survives", 1, scalarInt(db, "SELECT COUNT(*) FROM world_islands WHERE id = 'existing-island'"))
            assertEquals(
                "v5 terrain survives unchanged",
                1,
                scalarInt(db, "SELECT COUNT(*) FROM world_tiles WHERE islandId = 'existing-island' AND x = 0 AND y = 0 AND terrain = 'GRASS'")
            )
            assertEquals("new item table begins empty", 0, scalarInt(db, "SELECT COUNT(*) FROM world_items"))
            assertEquals(
                "migration creates the world item island index",
                1,
                scalarInt(db, "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'index_world_items_islandId'")
            )
            db.query("PRAGMA foreign_key_list('world_items')").use { cursor ->
                assertTrue("world item foreign key exists", cursor.moveToFirst())
                assertEquals("world item foreign key references islands", "world_islands", cursor.getString(cursor.getColumnIndexOrThrow("table")))
                assertEquals("deleting an island cascades to its items", "CASCADE", cursor.getString(cursor.getColumnIndexOrThrow("on_delete")))
            }
        } finally {
            db.close()
            InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DATABASE_5_TO_6)
        }
    }

    private fun scalarInt(db: SupportSQLiteDatabase, sql: String): Int =
        db.query(sql).use { cursor ->
            assertTrue("$sql returns a row", cursor.moveToFirst())
            cursor.getInt(0)
        }

    private companion object {
        const val TEST_DATABASE = "world-schema-migration-test"
        const val TEST_DATABASE_5_TO_6 = "world-items-schema-migration-test"
    }
}
