package com.habitat.app.ui.world

import com.habitat.app.domain.model.BuiltInPlaceableCatalog
import com.habitat.app.domain.model.TileCoordinate
import com.habitat.app.domain.model.WorldTerrain

/** Stable presentation kind so Android resources can provide localized names. */
enum class AccessibleWorldItemKind {
    STONE,
    LOG,
    UNKNOWN
}

enum class WorldBuildContentMode {
    CANVAS,
    TILES,
    ITEMS
}

/** One accessible island cell, with user-facing row and column numbers starting at one. */
data class AccessibleWorldTile(
    val coordinate: TileCoordinate,
    val row: Int,
    val column: Int,
    val terrain: WorldTerrain,
    val occupiedBy: AccessibleWorldItemKind?
)

/** One accessible placed item, ordered by its anchor from top to bottom and left to right. */
data class AccessibleWorldItem(
    val id: String,
    val coordinate: TileCoordinate,
    val kind: AccessibleWorldItemKind,
    val row: Int,
    val column: Int
)

/** Builds all island cells in stable row-major order for the accessible tile picker. */
fun accessibleWorldTiles(renderModel: WorldRenderModel): List<AccessibleWorldTile> {
    val occupants = renderModel.items
        .flatMap { item -> item.occupiedTiles.map { coordinate -> coordinate to item.placement.definitionId } }
        .toMap()

    return renderModel.island.tiles
        .sortedWith(compareBy({ it.y }, { it.x }))
        .map { tile ->
            val coordinate = TileCoordinate(tile.x, tile.y)
            AccessibleWorldTile(
                coordinate = coordinate,
                row = tile.y + 1,
                column = tile.x + 1,
                terrain = tile.terrain,
                occupiedBy = occupants[coordinate]?.let(::accessibleItemKind)
            )
        }
}

/** Builds placed-item rows in stable anchor row/column order. */
fun accessibleWorldItems(renderModel: WorldRenderModel): List<AccessibleWorldItem> =
    renderModel.items
        .sortedWith(
            compareBy<WorldRenderItem>({ it.placement.anchor.y }, { it.placement.anchor.x }, { it.placement.id })
        )
        .map { item ->
            AccessibleWorldItem(
                id = item.placement.id,
                coordinate = item.placement.anchor,
                kind = accessibleItemKind(item.placement.definitionId),
                row = item.placement.anchor.y + 1,
                column = item.placement.anchor.x + 1
            )
        }

private fun accessibleItemKind(definitionId: String): AccessibleWorldItemKind = when (definitionId) {
    BuiltInPlaceableCatalog.SMALL_STONE_ID -> AccessibleWorldItemKind.STONE
    BuiltInPlaceableCatalog.TWO_TILE_LOG_ID -> AccessibleWorldItemKind.LOG
    else -> AccessibleWorldItemKind.UNKNOWN
}
