package com.habitat.app.data

import com.habitat.app.data.local.LedgerReasonDatabaseMapper
import com.habitat.app.domain.LedgerReason
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerReasonTest {

    @Test
    fun `habit reward reason maps to and from database value`() {
        assertEquals(
            "habit reward maps to persisted value",
            "HABIT_REWARD",
            LedgerReasonDatabaseMapper.toDatabaseValue(LedgerReason.HABIT_REWARD)
        )
        assertEquals(
            "persisted habit reward maps to domain enum",
            LedgerReason.HABIT_REWARD,
            LedgerReasonDatabaseMapper.fromDatabaseValue("HABIT_REWARD")
        )
    }
}
