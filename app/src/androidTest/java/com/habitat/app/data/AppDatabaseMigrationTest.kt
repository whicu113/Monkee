package com.habitat.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.habitat.app.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Migration test for Room schema migrations 1->2 and 2->3.
 * SPEC Section 9.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private fun createDb(name: String, version: Int): SupportSQLiteDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS habits (id TEXT NOT NULL, name TEXT NOT NULL, emoji TEXT NOT NULL, color TEXT NOT NULL, cue TEXT NOT NULL, fullVersion TEXT NOT NULL, minVersion TEXT NOT NULL, identity TEXT NOT NULL, schedule TEXT NOT NULL, windowStart TEXT, windowEnd TEXT, difficulty TEXT NOT NULL, groupId TEXT, sortOrder INTEGER NOT NULL, archived INTEGER NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))"
                    )
                    if (version >= 2) {
                        AppDatabase.MIGRATION_1_2.migrate(db)
                    }
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun migrate1To2_createsHabitGroupsAndCompletionsTables() {
        val db = createDb("migration-test-1-2.db", 1)

        db.execSQL(
            "INSERT INTO habits (id, name, emoji, color, cue, fullVersion, minVersion, identity, schedule, windowStart, windowEnd, difficulty, groupId, sortOrder, archived, createdAt) VALUES ('h1', 'Run', '🏃', '#4CAF50', '', '', '', '', 'v1:DAILY', NULL, NULL, 'EASY', NULL, 0, 0, 1715000000000)"
        )

        // Apply MIGRATION_1_2
        AppDatabase.MIGRATION_1_2.migrate(db)

        val habitCursor = db.query("SELECT * FROM habits WHERE id = 'h1'")
        assertTrue(habitCursor.moveToFirst())
        habitCursor.close()

        val groupsCursor = db.query("SELECT * FROM habit_groups")
        assertNotNull(groupsCursor)
        groupsCursor.close()

        val completionsCursor = db.query("SELECT * FROM completions")
        assertNotNull(completionsCursor)
        completionsCursor.close()

        db.close()
    }

    @Test
    fun migrate2To3_addsReminderTimeColumnAsNullAndPreservesData() {
        val db = createDb("migration-test-2-3.db", 2)

        db.execSQL(
            "INSERT INTO habits (id, name, emoji, color, cue, fullVersion, minVersion, identity, schedule, windowStart, windowEnd, difficulty, groupId, sortOrder, archived, createdAt) VALUES ('h2', 'Read', '📚', '#4CAF50', '', '', '', '', 'v1:DAILY', NULL, NULL, 'EASY', NULL, 0, 0, 1715000000000)"
        )

        // Apply MIGRATION_2_3
        AppDatabase.MIGRATION_2_3.migrate(db)

        val cursor = db.query("SELECT reminderTime, name FROM habits WHERE id = 'h2'")
        assertTrue(cursor.moveToFirst())
        val reminderTimeIndex = cursor.getColumnIndex("reminderTime")
        val nameIndex = cursor.getColumnIndex("name")

        assertTrue(cursor.isNull(reminderTimeIndex))
        assertEquals("Read", cursor.getString(nameIndex))
        cursor.close()

        db.close()
    }
}
