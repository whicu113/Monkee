package com.habitat.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldDao {
    @Transaction
    @Query("SELECT * FROM world_islands WHERE id = :islandId")
    fun observeIslandWithTiles(islandId: String): Flow<WorldIslandWithTiles?>

    @Transaction
    @Query("SELECT * FROM world_islands WHERE id = :islandId")
    fun observeIslandWithBuildContents(islandId: String): Flow<WorldIslandWithBuildContents?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIslandIfAbsent(island: WorldIslandEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTilesIfAbsent(tiles: List<WorldTileEntity>)

    @Query("DELETE FROM world_items WHERE islandId = :islandId")
    suspend fun deleteItemsForIsland(islandId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorldItems(items: List<WorldItemEntity>)

    @Query("SELECT COUNT(*) FROM world_islands WHERE id = :islandId")
    suspend fun islandCount(islandId: String): Int

    @Query("SELECT COUNT(*) FROM world_tiles WHERE islandId = :islandId")
    suspend fun tileCount(islandId: String): Int
}
