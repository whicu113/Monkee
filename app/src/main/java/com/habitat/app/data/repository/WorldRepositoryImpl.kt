package com.habitat.app.data.repository

import androidx.room.withTransaction
import com.habitat.app.data.local.AppDatabase
import com.habitat.app.data.local.WorldDao
import com.habitat.app.data.local.WorldIslandEntity
import com.habitat.app.data.local.WorldIslandWithBuildContents
import com.habitat.app.data.local.WorldIslandWithTiles
import com.habitat.app.data.local.WorldItemEntity
import com.habitat.app.data.local.WorldTileEntity
import com.habitat.app.domain.model.BuildWorld
import com.habitat.app.domain.model.PlacedWorldItem
import com.habitat.app.domain.model.TileCoordinate
import com.habitat.app.domain.model.WorldIsland
import com.habitat.app.domain.model.WorldTerrain
import com.habitat.app.domain.model.WorldTile
import com.habitat.app.domain.model.createStarterIsland
import com.habitat.app.domain.repository.WorldRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorldRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val worldDao: WorldDao
) : WorldRepository {

    override fun observeStarterIsland(): Flow<WorldIsland?> =
        worldDao.observeIslandWithTiles(com.habitat.app.domain.GameConfig.STARTER_ISLAND_ID)
            .map { it?.toDomainModel() }

    override fun observeStarterBuildWorld(): Flow<BuildWorld?> =
        worldDao.observeIslandWithBuildContents(com.habitat.app.domain.GameConfig.STARTER_ISLAND_ID)
            .map { it?.toBuildWorld() }

    override suspend fun ensureStarterIsland() {
        val starterIsland = createStarterIsland()
        database.withTransaction {
            val insertedId = worldDao.insertIslandIfAbsent(
                WorldIslandEntity(
                    id = starterIsland.id,
                    width = starterIsland.width,
                    height = starterIsland.height
                )
            )
            if (insertedId != -1L) {
                worldDao.insertTilesIfAbsent(starterIsland.tiles.map { tile ->
                    WorldTileEntity(
                        islandId = starterIsland.id,
                        x = tile.x,
                        y = tile.y,
                        terrain = tile.terrain.name
                    )
                })
            }
        }
    }

    override suspend fun saveBuildWorld(world: BuildWorld) {
        database.withTransaction {
            check(worldDao.islandCount(world.island.id) == 1) {
                "Cannot save placed items for an island that is not persisted"
            }
            worldDao.deleteItemsForIsland(world.island.id)
            if (world.items.isNotEmpty()) {
                worldDao.insertWorldItems(world.items.map { item -> item.toEntity(world.island.id) })
            }
        }
    }
}

private fun WorldIslandWithTiles.toDomainModel() = WorldIsland(
    id = island.id,
    width = island.width,
    height = island.height,
    tiles = tiles.map { tile ->
        WorldTile(
            x = tile.x,
            y = tile.y,
            terrain = WorldTerrain.valueOf(tile.terrain)
        )
    }.sortedWith(compareBy({ it.y }, { it.x }))
)

private fun WorldIslandWithBuildContents.toBuildWorld() = BuildWorld(
    island = WorldIsland(
        id = island.id,
        width = island.width,
        height = island.height,
        tiles = tiles.map { tile ->
            WorldTile(
                x = tile.x,
                y = tile.y,
                terrain = WorldTerrain.valueOf(tile.terrain)
            )
        }.sortedWith(compareBy({ it.y }, { it.x }))
    ),
    items = items.map { item ->
        PlacedWorldItem(
            id = item.id,
            definitionId = item.type,
            anchor = TileCoordinate(item.x, item.y),
            rotationQuarterTurns = item.rotation,
            flipped = item.flipped
        )
    }.sortedBy { it.id }
)

private fun PlacedWorldItem.toEntity(islandId: String) = WorldItemEntity(
    id = id,
    islandId = islandId,
    type = definitionId,
    x = anchor.x,
    y = anchor.y,
    rotation = rotationQuarterTurns,
    flipped = flipped
)
