package com.habitat.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.habitat.app.domain.model.HabitGroup

/**
 * Room database entity for HabitGroup.
 * SPEC Section 9.
 */
@Entity(tableName = "habit_groups")
data class HabitGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int
)

fun HabitGroupEntity.toDomainModel(): HabitGroup = HabitGroup(
    id = id,
    name = name,
    sortOrder = sortOrder
)

fun HabitGroup.toEntity(): HabitGroupEntity = HabitGroupEntity(
    id = id,
    name = name,
    sortOrder = sortOrder
)
