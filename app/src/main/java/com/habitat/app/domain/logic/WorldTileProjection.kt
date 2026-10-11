package com.habitat.app.domain.logic

import com.habitat.app.domain.model.WorldIsland
import com.habitat.app.domain.model.WorldTerrain

data class WorldPoint(val x: Float, val y: Float)

data class ProjectedWorldTile(
    val x: Int,
    val y: Int,
    val terrain: WorldTerrain,
    val top: WorldPoint,
    val right: WorldPoint,
    val bottom: WorldPoint,
    val left: WorldPoint
)

/** Projects saved isometric terrain cells into a viewport without Android or Compose types. SPEC 7.1. */
fun projectWorldTiles(
    island: WorldIsland,
    viewportWidth: Float,
    viewportHeight: Float
): List<ProjectedWorldTile> {
    require(viewportWidth > 0f && viewportHeight > 0f)

    val diagonalCount = island.width + island.height
    val tileWidth = minOf(
        viewportWidth * 2f / diagonalCount,
        viewportHeight * 4f / diagonalCount
    )
    val tileHeight = tileWidth / 2f
    val mapWidth = diagonalCount * tileWidth / 2f
    val mapHeight = diagonalCount * tileHeight / 2f
    val originX = (viewportWidth - mapWidth) / 2f
    val originY = (viewportHeight - mapHeight) / 2f

    return island.tiles
        .sortedWith(compareBy({ it.x + it.y }, { it.x }))
        .map { tile ->
            val centerX = originX + (tile.x - tile.y + island.height) * tileWidth / 2f
            val centerY = originY + (tile.x + tile.y + 1) * tileHeight / 2f
            ProjectedWorldTile(
                x = tile.x,
                y = tile.y,
                terrain = tile.terrain,
                top = WorldPoint(centerX, centerY - tileHeight / 2f),
                right = WorldPoint(centerX + tileWidth / 2f, centerY),
                bottom = WorldPoint(centerX, centerY + tileHeight / 2f),
                left = WorldPoint(centerX - tileWidth / 2f, centerY)
            )
        }
}
