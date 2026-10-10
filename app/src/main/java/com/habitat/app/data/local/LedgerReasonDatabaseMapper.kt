package com.habitat.app.data.local

import com.habitat.app.domain.LedgerReason

internal object LedgerReasonDatabaseMapper {
    fun toDatabaseValue(reason: LedgerReason): String = when (reason) {
        LedgerReason.HABIT_REWARD -> "HABIT_REWARD"
    }

    fun fromDatabaseValue(value: String): LedgerReason = when (value) {
        "HABIT_REWARD" -> LedgerReason.HABIT_REWARD
        else -> throw IllegalArgumentException("Unknown ledger reason: $value")
    }
}
