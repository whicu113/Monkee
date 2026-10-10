package com.habitat.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "drop_records",
    indices = [Index(value = ["habitId", "logicalDate"], unique = true)]
)
data class DropRecordEntity(
    @PrimaryKey val id: String,
    val rarity: String,
    val habitId: String,
    val logicalDate: LocalDate,
    val createdAt: Instant
)
