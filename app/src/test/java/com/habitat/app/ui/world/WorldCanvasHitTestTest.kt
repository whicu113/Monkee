package com.habitat.app.ui.world

import com.habitat.app.domain.logic.ProjectedWorldTile
import com.habitat.app.domain.logic.WorldPoint
import com.habitat.app.domain.model.BuiltInPlaceableCatalog
import com.habitat.app.domain.model.BuildWorld
import com.habitat.app.domain.model.PlacedWorldItem
import com.habitat.app.domain.model.TileCoordinate
import com.habitat.app.domain.model.WorldIsland
import com.habitat.app.domain.model.WorldTerrain
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldCanvasHitTestTest {

    @Test
    fun hitTestTableReportsEveryNamedGeometryAndOccupancyRow() {
        val failures = mutableListOf<String>()
        val singleTile = projectedTile(x = 3, y = 4)
        val occupancyCases = listOf(
            "multi-cell first footprint cell" to Pair(
                item("horizontal", 2, 2, BuiltInPlaceableCatalog.TWO_TILE_LOG_ID),
                TileCoordinate(2, 2)
            ),
            "multi-cell second footprint cell" to Pair(
                item("horizontal-second", 2, 2, BuiltInPlaceableCatalog.TWO_TILE_LOG_ID),
                TileCoordinate(3, 2)
            ),
            "rotated footprint cell" to Pair(
                item("rotated", 2, 2, BuiltInPlaceableCatalog.TWO_TILE_LOG_ID, rotation = 1),
                TileCoordinate(2, 3)
            ),
            "flipped multi-cell footprint cell" to Pair(
                item("flipped", 2, 2, BuiltInPlaceableCatalog.TWO_TILE_LOG_ID, flipped = true),
                TileCoordinate(3, 2)
            )
        )

        val rows = listOf<Pair<String, () -> Unit>>(
            "tile center" to {
                val tile = hitTestWorldTile(center(singleTile), listOf(singleTile))
                expect(failures, "tile center", tile == TileCoordinate(3, 4), "expected tile (3,4), got $tile")
            },
            "diamond edge narrowly inside" to {
                val tile = hitTestWorldTile(WorldPoint(9.8f, 0f), listOf(diamondTile()))
                expect(failures, "diamond edge narrowly inside", tile == TileCoordinate(1, 1), "inside point was not assigned to the diamond")
            },
            "diamond edge narrowly outside" to {
                val tile = hitTestWorldTile(WorldPoint(10.2f, 0f), listOf(diamondTile()))
                expect(failures, "diamond edge narrowly outside", tile == null, "outside point was assigned to $tile")
            },
            "outside island" to {
                val tile = hitTestWorldTile(WorldPoint(1000f, 1000f), listOf(singleTile))
                expect(failures, "outside island", tile == null, "outside point was assigned to $tile")
            },
            "overlapping edge tie uses first projected tile" to {
                val front = diamondTile(x = 4, y = 5)
                val back = diamondTile(x = 8, y = 9)
                val tiedTiles = listOf(front, back).map { it.copy(top = WorldPoint(0f, -1f), right = WorldPoint(1f, 0f), bottom = WorldPoint(0f, 1f), left = WorldPoint(-1f, 0f)) }
                val tile = hitTestWorldTile(WorldPoint(0f, 0f), tiedTiles)
                expect(failures, "overlapping edge tie uses first projected tile", tile == TileCoordinate(4, 5), "tie selected $tile instead of the first projected tile")
            }
        ) + occupancyCases.map { (row, pair) ->
            row to {
                val (placed, coordinate) = pair
                val world = BuildWorld(island = island(), items = listOf(placed))
                val renderModel = worldRenderModelFor(world)
                val selected = hitTestPlacedWorldItem(coordinate, renderModel)
                expect(failures, row, selected?.id == placed.id, "occupied cell $coordinate selected ${selected?.id}")
            }
        }

        rows.forEach { (row, check) ->
            try {
                check()
            } catch (failure: Throwable) {
                failures += "$row: unexpected ${failure::class.simpleName}: ${failure.message}"
            }
        }
        assertTrue("Hit-test rows failed (${failures.size}):\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    private fun expect(failures: MutableList<String>, row: String, condition: Boolean, message: String) {
        if (!condition) failures += "$row: $message"
    }

    private fun center(tile: ProjectedWorldTile) = WorldPoint(
        x = (tile.left.x + tile.right.x) / 2f,
        y = (tile.top.y + tile.bottom.y) / 2f
    )

    private fun diamondTile(x: Int = 1, y: Int = 1) = ProjectedWorldTile(
        x = x,
        y = y,
        terrain = WorldTerrain.GRASS,
        top = WorldPoint(0f, -5f),
        right = WorldPoint(10f, 0f),
        bottom = WorldPoint(0f, 5f),
        left = WorldPoint(-10f, 0f)
    )

    private fun projectedTile(x: Int, y: Int) = diamondTile(x, y)

    private fun island() = WorldIsland(
        id = "hit-test-island",
        width = 8,
        height = 8,
        tiles = emptyList()
    )

    private fun item(
        id: String,
        x: Int,
        y: Int,
        definitionId: String,
        rotation: Int = 0,
        flipped: Boolean = false
    ) = PlacedWorldItem(id, definitionId, TileCoordinate(x, y), rotation, flipped)
}
