package com.habitat.app.domain.model

import java.time.Instant
import java.time.LocalTime
import java.util.UUID

/**
 * Domain representation of a Habit.
 * SPEC Section 4.1 & 8.1.
 */
data class Habit(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val emoji: String = "🌱",
    val color: String = "#4CAF50",
    val cue: String = "",
    val fullVersion: String = "",
    val minVersion: String = "",
    val identity: String = "",
    val schedule: ScheduleType = ScheduleType.Daily,
    val reminderTime: LocalTime? = null,
    val windowStart: LocalTime? = null,
    val windowEnd: LocalTime? = null,
    val difficulty: Difficulty = Difficulty.EASY,
    val groupId: String? = null,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val createdAt: Instant = Instant.now()
) {
    init {
        require(name.isNotBlank()) { "Habit name cannot be blank" }
    }
}
