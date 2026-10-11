package com.habitat.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "world_islands")
data class WorldIslandEntity(
    @PrimaryKey val id: String,
    val width: Int,
    val height: Int
)

@Entity(
    tableName = "world_tiles",
    primaryKeys = ["islandId", "x", "y"],
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
data class WorldTileEntity(
    val islandId: String,
    val x: Int,
    val y: Int,
    val terrain: String
)

data class WorldIslandWithTiles(
    @Embedded val island: WorldIslandEntity,
    @Relation(parentColumn = "id", entityColumn = "islandId")
    val tiles: List<WorldTileEntity>
)

data class WorldIslandWithBuildContents(
    @Embedded val island: WorldIslandEntity,
    @Relation(parentColumn = "id", entityColumn = "islandId")
    val tiles: List<WorldTileEntity>,
    @Relation(parentColumn = "id", entityColumn = "islandId")
    val items: List<WorldItemEntity>
)
