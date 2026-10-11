package com.habitat.app.domain.model

import com.habitat.app.domain.GameConfig

/** Terrain types listed for the world map in SPEC 7.1. */
enum class WorldTerrain {
    DEEP_WATER,
    SHALLOW_WATER,
    SAND,
    DIRT,
    GRASS
}

data class WorldTile(
    val x: Int,
    val y: Int,
    val terrain: WorldTerrain
)

data class WorldIsland(
    val id: String,
    val width: Int,
    val height: Int,
    val tiles: List<WorldTile>
) {
    init {
        require(id.isNotBlank())
        require(width > 0 && height > 0)
        require(tiles.all { it.x in 0 until width && it.y in 0 until height })
        require(tiles.map { it.x to it.y }.distinct().size == tiles.size)
    }
}

/** Creates the initial persisted island using the approved size and SPEC terrain. */
fun createStarterIsland(): WorldIsland = WorldIsland(
    id = GameConfig.STARTER_ISLAND_ID,
    width = GameConfig.STARTER_ISLAND_WIDTH,
    height = GameConfig.STARTER_ISLAND_HEIGHT,
    tiles = buildList(GameConfig.STARTER_ISLAND_WIDTH * GameConfig.STARTER_ISLAND_HEIGHT) {
        for (y in 0 until GameConfig.STARTER_ISLAND_HEIGHT) {
            for (x in 0 until GameConfig.STARTER_ISLAND_WIDTH) {
                add(WorldTile(x = x, y = y, terrain = WorldTerrain.GRASS))
            }
        }
    }
)
