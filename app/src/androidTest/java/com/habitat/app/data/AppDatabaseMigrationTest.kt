package com.habitat.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.habitat.app.data.local.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Migration test for Room schema migrations 1->2 and 2->3.
 * SPEC Section 9.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2() {
        var db = helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO habits (id, name, emoji, color, cue, fullVersion, minVersion, identity, schedule, windowStart, windowEnd, difficulty, groupId, sortOrder, archived, createdAt) VALUES ('h1', 'Run', '🏃', '#4CAF50', '', '', '', '', 'v1:DAILY', NULL, NULL, 'EASY', NULL, 0, 0, 1715000000000)"
            )
            close()
        }

        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)

        val cursor = db.query("SELECT * FROM habits WHERE id = 'h1'")
        assert(cursor.moveToFirst())
        cursor.close()
    }

    @Test
    fun migrate2To3() {
        var db = helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO habits (id, name, emoji, color, cue, fullVersion, minVersion, identity, schedule, windowStart, windowEnd, difficulty, groupId, sortOrder, archived, createdAt) VALUES ('h1', 'Read', '📚', '#4CAF50', '', '', '', '', 'v1:DAILY', NULL, NULL, 'EASY', NULL, 0, 0, 1715000000000)"
            )
            close()
        }

        db = helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3)

        val cursor = db.query("SELECT reminderTime FROM habits WHERE id = 'h1'")
        assert(cursor.moveToFirst())
        assert(cursor.isNull(0))
        cursor.close()
    }
}
