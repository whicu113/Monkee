package com.habitat.app.ui.world

import com.habitat.app.domain.model.BuildWorld
import com.habitat.app.domain.model.PlacedWorldItem
import com.habitat.app.domain.model.TileCoordinate
import com.habitat.app.domain.model.WorldIsland
import com.habitat.app.domain.model.BuiltInPlaceableCatalog
import com.habitat.app.domain.model.transformedFootprintOffsets

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
 * Projects saved items to the island cells occupied by their transformed footprints (SPEC 7.1).
 * Unknown definitions remain represented as items with no hit-testable cells.
 */
internal fun worldRenderModelFor(world: BuildWorld): WorldRenderModel =
    WorldRenderModel(
        island = world.island,
        items = world.items.map { item ->
            val definition = BuiltInPlaceableCatalog.definitions[item.definitionId]
            val occupiedTiles = definition
                ?.let { transformedFootprintOffsets(item, it) }
                .orEmpty()
                .mapNotNullTo(linkedSetOf()) { offset ->
                    val x = item.anchor.x.toLong() + offset.x
                    val y = item.anchor.y.toLong() + offset.y
                    if (x in 0 until world.island.width.toLong() && y in 0 until world.island.height.toLong()) {
                        TileCoordinate(x.toInt(), y.toInt())
                    } else {
                        null
                    }
                }
            WorldRenderItem(placement = item, occupiedTiles = occupiedTiles)
        }
    )
