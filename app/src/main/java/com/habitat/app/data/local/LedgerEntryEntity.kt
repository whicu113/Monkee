package com.habitat.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "ledger_entries",
    indices = [
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["reason", "currency", "logicalDate"]),
        Index(value = ["habitId", "logicalDate"])
    ]
)
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val currency: String,
    val delta: Long,
    val reason: String,
    val habitId: String?,
    val logicalDate: LocalDate?,
    val createdAt: Instant,
    val idempotencyKey: String,
    val rewardStage: String?,
    val difficulty: String?,
    val streakLength: Int?,
    val comebackApplied: Boolean?,
    val stageEntitlementAmount: Long?,
    val pityCounterAfter: Int?
)
