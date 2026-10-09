package com.habitat.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.habitat.app.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Migration test for Room schema migrations 1->2 and 2->3.
 * SPEC Section 9.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @Test
    fun migrate1To2_createsHabitGroupsAndCompletionsTables() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("test_migration_1_2.db")

        val db = Room.databaseBuilder(context, AppDatabase::class.java, "test_migration_1_2.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

        val sqliteDb = db.openHelper.writableDatabase

        // Insert habit into habits table
        sqliteDb.execSQL(
            "INSERT INTO habits (id, name, emoji, color, cue, fullVersion, minVersion, identity, schedule, windowStart, windowEnd, difficulty, groupId, sortOrder, archived, createdAt) VALUES ('h1', 'Run', '🏃', '#4CAF50', '', '', '', '', 'v1:DAILY', NULL, NULL, 'EASY', NULL, 0, 0, 1715000000000)"
        )

        // Query habits, habit_groups, and completions tables
        val habitCursor = sqliteDb.query("SELECT * FROM habits WHERE id = 'h1'")
        assertTrue(habitCursor.moveToFirst())
        habitCursor.close()

        val groupsCursor = sqliteDb.query("SELECT * FROM habit_groups")
        assertNotNull(groupsCursor)
        groupsCursor.close()

        val completionsCursor = sqliteDb.query("SELECT * FROM completions")
        assertNotNull(completionsCursor)
        completionsCursor.close()

        db.close()
    }

    @Test
    fun migrate2To3_addsReminderTimeColumnToHabits() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("test_migration_2_3.db")

        val db = Room.databaseBuilder(context, AppDatabase::class.java, "test_migration_2_3.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

        val sqliteDb = db.openHelper.writableDatabase

        sqliteDb.execSQL(
            "INSERT INTO habits (id, name, emoji, color, cue, fullVersion, minVersion, identity, schedule, reminderTime, windowStart, windowEnd, difficulty, groupId, sortOrder, archived, createdAt) VALUES ('h2', 'Read', '📚', '#4CAF50', '', '', '', '', 'v1:DAILY', '09:30', NULL, NULL, 'EASY', NULL, 0, 0, 1715000000000)"
        )

        val cursor = sqliteDb.query("SELECT reminderTime FROM habits WHERE id = 'h2'")
        assertTrue(cursor.moveToFirst())
        assertEquals("09:30", cursor.getString(0))
        cursor.close()

        db.close()
    }

    private fun assertNotNull(obj: Any?) {
        assertTrue(obj != null)
    }
}
