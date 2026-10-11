package com.habitat.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "world_items",
    foreignKeys = [
        ForeignKey(
            entity = WorldIslandEntity::class,
            parentColumns = ["id"],
            childColumns = ["islandId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["islandId"])]
)
data class WorldItemEntity(
    @PrimaryKey val id: String,
    val islandId: String,
    val type: String,
    val x: Int,
    val y: Int,
    val rotation: Int,
    val flipped: Boolean
)
