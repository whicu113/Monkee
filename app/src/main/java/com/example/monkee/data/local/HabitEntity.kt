package com.example.monkee.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.monkee.domain.model.Difficulty
import com.example.monkee.domain.model.Habit
import com.example.monkee.domain.model.ScheduleType
import java.time.Instant
import java.time.LocalTime

/**
 * Room database entity for Habit.
 * SPEC Section 9.
 */
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val color: String,
    val cue: String,
    val fullVersion: String,
    val minVersion: String,
    val identity: String,
    val schedule: ScheduleType,
    val windowStart: LocalTime?,
    val windowEnd: LocalTime?,
    val difficulty: Difficulty,
    val groupId: String?,
    val sortOrder: Int,
    val archived: Boolean,
    val createdAt: Instant
)

fun HabitEntity.toDomainModel(): Habit = Habit(
    id = id,
    name = name,
    emoji = emoji,
    color = color,
    cue = cue,
    fullVersion = fullVersion,
    minVersion = minVersion,
    identity = identity,
    schedule = schedule,
    windowStart = windowStart,
    windowEnd = windowEnd,
    difficulty = difficulty,
    groupId = groupId,
    sortOrder = sortOrder,
    archived = archived,
    createdAt = createdAt
)

fun Habit.toEntity(): HabitEntity = HabitEntity(
    id = id,
    name = name,
    emoji = emoji,
    color = color,
    cue = cue,
    fullVersion = fullVersion,
    minVersion = minVersion,
    identity = identity,
    schedule = schedule,
    windowStart = windowStart,
    windowEnd = windowEnd,
    difficulty = difficulty,
    groupId = groupId,
    sortOrder = sortOrder,
    archived = archived,
    createdAt = createdAt
)
