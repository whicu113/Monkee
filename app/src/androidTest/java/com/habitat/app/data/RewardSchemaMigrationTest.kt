package com.habitat.app.data

import android.database.sqlite.SQLiteConstraintException
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
class RewardSchemaMigrationTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migration3To4PreservesRowsAndCreatesRewardSchema() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                "INSERT INTO habits (id, name, emoji, color, cue, fullVersion, minVersion, identity, schedule, reminderTime, windowStart, windowEnd, difficulty, groupId, sortOrder, archived, createdAt) VALUES ('habit-existing', 'Walk', '🚶', '#4CAF50', '', '', '', '', 'v1:DAILY', NULL, NULL, NULL, 'EASY', NULL, 0, 0, 1715000000000)"
            )
            execSQL(
                "INSERT INTO completions (id, habitId, logicalDate, state, completedAt, reason, note) VALUES ('completion-existing', 'habit-existing', '2025-05-12', 'DONE', 1715000000000, NULL, NULL)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            4,
            true,
            AppDatabase.MIGRATION_3_4
        )
        assertEquals("existing habit survives", 1, scalarInt(db, "SELECT COUNT(*) FROM habits WHERE id = 'habit-existing'"))
        assertEquals(
            "existing completion survives",
            1,
            scalarInt(db, "SELECT COUNT(*) FROM completions WHERE id = 'completion-existing'")
        )
        assertEquals(
            "new reward tables exist",
            2,
            scalarInt(db, "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN ('ledger_entries', 'drop_records')")
        )
        assertEquals(
            "reward indexes exist",
            4,
            scalarInt(
                db,
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name IN ('index_ledger_entries_idempotencyKey', 'index_ledger_entries_reason_currency_logicalDate', 'index_ledger_entries_habitId_logicalDate', 'index_drop_records_habitId_logicalDate')"
            )
        )

        db.execSQL(
            "INSERT INTO ledger_entries (currency, delta, reason, habitId, logicalDate, createdAt, idempotencyKey) VALUES ('SEEDS', 10, 'HABIT_REWARD', 'habit-existing', '2025-05-12', 1715000000000, 'habit-existing|2025-05-12|DONE|SEEDS')"
        )
        var duplicateRejected = false
        try {
            db.execSQL(
                "INSERT INTO ledger_entries (currency, delta, reason, habitId, logicalDate, createdAt, idempotencyKey) VALUES ('SEEDS', 10, 'HABIT_REWARD', 'habit-existing', '2025-05-12', 1715000000001, 'habit-existing|2025-05-12|DONE|SEEDS')"
            )
        } catch (_: SQLiteConstraintException) {
            duplicateRejected = true
        }
        assertTrue("duplicate idempotency key rejected", duplicateRejected)
        db.close()
    }

    private fun scalarInt(db: SupportSQLiteDatabase, sql: String): Int =
        db.query(sql).use { cursor ->
            assertTrue("query returns a row", cursor.moveToFirst())
            cursor.getInt(0)
        }

    private companion object {
        const val TEST_DB = "reward-schema-migration-test"
    }
}
