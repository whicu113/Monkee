package com.habitat.app.ui.world

import com.habitat.app.domain.model.BuiltInPlaceableCatalog
import com.habitat.app.domain.model.PlacedWorldItem
import com.habitat.app.domain.model.TileCoordinate
import com.habitat.app.domain.model.WorldIsland
import com.habitat.app.domain.model.WorldTerrain
import com.habitat.app.domain.model.WorldTile
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldAccessibilityModelTest {

    @Test
    fun accessibleTileRowsAreCompleteAndExposeDescriptionFields() {
        val island = WorldIsland(
            id = "accessibility-island",
            width = 3,
            height = 2,
            tiles = listOf(
                WorldTile(0, 0, WorldTerrain.GRASS),
                WorldTile(1, 0, WorldTerrain.SAND),
                WorldTile(2, 0, WorldTerrain.DEEP_WATER),
                WorldTile(0, 1, WorldTerrain.DIRT),
                WorldTile(1, 1, WorldTerrain.SHALLOW_WATER),
                WorldTile(2, 1, WorldTerrain.GRASS)
            )
        )
        val log = PlacedWorldItem(
            id = "log",
            definitionId = BuiltInPlaceableCatalog.TWO_TILE_LOG_ID,
            anchor = TileCoordinate(1, 1)
        )
        val stone = PlacedWorldItem(
            id = "stone",
            definitionId = BuiltInPlaceableCatalog.SMALL_STONE_ID,
            anchor = TileCoordinate(2, 0)
        )
        val model = WorldRenderModel(
            island = island,
            items = listOf(
                WorldRenderItem(log, setOf(TileCoordinate(1, 1), TileCoordinate(2, 1))),
                WorldRenderItem(stone, setOf(TileCoordinate(2, 0)))
            )
        )
        val tiles = accessibleWorldTiles(model)
        val rows = listOf(
            TileCase("empty upper-left", 0, 0, 1, 1, WorldTerrain.GRASS, null),
            TileCase("empty sand tile", 1, 0, 1, 2, WorldTerrain.SAND, null),
            TileCase("occupied stone tile", 2, 0, 1, 3, WorldTerrain.DEEP_WATER, AccessibleWorldItemKind.STONE),
            TileCase("empty lower-left", 0, 1, 2, 1, WorldTerrain.DIRT, null),
            TileCase("occupied log anchor", 1, 1, 2, 2, WorldTerrain.SHALLOW_WATER, AccessibleWorldItemKind.LOG),
            TileCase("occupied log footprint", 2, 1, 2, 3, WorldTerrain.GRASS, AccessibleWorldItemKind.LOG)
        )

        val failures = mutableListOf<String>()
        if (tiles.size != island.tiles.size) {
            failures += "every island tile: expected ${island.tiles.size} entries, was ${tiles.size}"
        }
        rows.forEach { row ->
            val tile = tiles.firstOrNull { it.coordinate == TileCoordinate(row.x, row.y) }
            if (tile == null) {
                failures += "${row.name}: tile is missing"
            } else {
                if (tile.row != row.row || tile.column != row.column) {
                    failures += "${row.name}: expected row ${row.row}, column ${row.column}; was row ${tile.row}, column ${tile.column}"
                }
                if (tile.terrain != row.terrain) {
                    failures += "${row.name}: expected terrain ${row.terrain}, was ${tile.terrain}"
                }
                if (tile.occupiedBy != row.occupiedBy) {
                    failures += "${row.name}: expected occupancy ${row.occupiedBy}, was ${tile.occupiedBy}"
                }
            }
        }
        val expectedOrder = listOf(
            TileCoordinate(0, 0), TileCoordinate(1, 0), TileCoordinate(2, 0),
            TileCoordinate(0, 1), TileCoordinate(1, 1), TileCoordinate(2, 1)
        )
        if (tiles.map { it.coordinate } != expectedOrder) {
            failures += "row-major tile order: expected $expectedOrder, was ${tiles.map { it.coordinate }}"
        }
        assertTrue(failures.joinToString(separator = "\n"), failures.isEmpty())
    }

    @Test
    fun accessibleItemRowsSortByAnchorAndUseOneBasedCoordinates() {
        val island = WorldIsland(
            id = "item-list-island",
            width = 3,
            height = 3,
            tiles = buildList {
                for (y in 0..2) for (x in 0..2) add(WorldTile(x, y, WorldTerrain.GRASS))
            }
        )
        val items = listOf(
            PlacedWorldItem("later-row", BuiltInPlaceableCatalog.SMALL_STONE_ID, TileCoordinate(0, 2)),
            PlacedWorldItem("same-row-right", BuiltInPlaceableCatalog.TWO_TILE_LOG_ID, TileCoordinate(2, 0)),
            PlacedWorldItem("same-row-left", BuiltInPlaceableCatalog.SMALL_STONE_ID, TileCoordinate(0, 0)),
            PlacedWorldItem("unknown-kind", "future-definition", TileCoordinate(1, 1))
        )
        val model = WorldRenderModel(
            island = island,
            items = items.map { WorldRenderItem(it, emptySet()) }
        )
        val accessibleItems = accessibleWorldItems(model)
        val rows = listOf(
            ItemCase("first row left item", "same-row-left", AccessibleWorldItemKind.STONE, 1, 1),
            ItemCase("first row right item", "same-row-right", AccessibleWorldItemKind.LOG, 1, 3),
            ItemCase("unknown item kind", "unknown-kind", AccessibleWorldItemKind.UNKNOWN, 2, 2),
            ItemCase("later row item", "later-row", AccessibleWorldItemKind.STONE, 3, 1)
        )

        val failures = mutableListOf<String>()
        if (accessibleItems.size != items.size) {
            failures += "all placed items: expected ${items.size} entries, was ${accessibleItems.size}"
        }
        rows.forEachIndexed { index, row ->
            val item = accessibleItems.getOrNull(index)
            if (item == null) {
                failures += "${row.name}: row is missing"
            } else {
                if (item.id != row.id) failures += "${row.name}: expected id ${row.id}, was ${item.id}"
                if (item.kind != row.kind) failures += "${row.name}: expected kind ${row.kind}, was ${item.kind}"
                if (item.row != row.row || item.column != row.column) {
                    failures += "${row.name}: expected row ${row.row}, column ${row.column}; was row ${item.row}, column ${item.column}"
                }
            }
        }
        assertTrue(failures.joinToString(separator = "\n"), failures.isEmpty())
    }

    private data class TileCase(
        val name: String,
        val x: Int,
        val y: Int,
        val row: Int,
        val column: Int,
        val terrain: WorldTerrain,
        val occupiedBy: AccessibleWorldItemKind?
    )

    private data class ItemCase(
        val name: String,
        val id: String,
        val kind: AccessibleWorldItemKind,
        val row: Int,
        val column: Int
    )
}
