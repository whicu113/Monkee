package com.habitat.app.ui.world

import com.habitat.app.domain.model.BuildWorld
import com.habitat.app.domain.model.PlacedWorldItem
import com.habitat.app.domain.model.TileCoordinate
import com.habitat.app.domain.model.WorldIsland

/** Pure UI model for terrain and placed-item rendering on the saved World screen. */
data class WorldRenderModel(
    val island: WorldIsland,
    val items: List<WorldRenderItem> = emptyList()
)

/** One saved placement and the island cells occupied by its oriented footprint. */
data class WorldRenderItem(
    val placement: PlacedWorldItem,
    val occupiedTiles: Set<TileCoordinate>
)

/**
 * Creates a render model for [world]. Item projection is intentionally deferred to the rendering
 * implementation step; the existing read-only screen continues to display the terrain only.
 */
internal fun worldRenderModelFor(world: BuildWorld): WorldRenderModel =
    WorldRenderModel(island = world.island)
