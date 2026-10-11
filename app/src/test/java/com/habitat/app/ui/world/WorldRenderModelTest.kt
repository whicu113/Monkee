package com.habitat.app.ui.world

import com.habitat.app.domain.model.BuildWorld
import com.habitat.app.domain.model.BuiltInPlaceableCatalog
import com.habitat.app.domain.model.PlacedWorldItem
import com.habitat.app.domain.model.TileCoordinate
import com.habitat.app.domain.model.WorldIsland
import com.habitat.app.domain.model.WorldTerrain
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldRenderModelTest {

    @Test
    fun renderModelTableReportsEveryNamedContentRow() {
        val failures = mutableListOf<String>()
        val fiftyItems = (0 until 50).map { index ->
            item("stone-$index", x = index % 8, y = index / 8)
        }
        val log = item("log", 2, 2, BuiltInPlaceableCatalog.TWO_TILE_LOG_ID)
        val rotatedLog = log.copy(id = "rotated-log", rotationQuarterTurns = 1)
        val flippedLog = log.copy(id = "flipped-log", flipped = true)
        val rows = listOf(
            "50 one-cell items are all represented" to {
                val model = worldRenderModelFor(BuildWorld(island(), fiftyItems))
                expect(failures, "50 one-cell items are all represented", model.items.size == 50, "expected 50 render items, got ${model.items.size}")
                expect(failures, "50 one-cell items are all represented", model.items.map { it.placement.id }.toSet() == fiftyItems.map { it.id }.toSet(), "one or more placement IDs are missing")
                expect(failures, "50 one-cell items are all represented", model.items.all { it.occupiedTiles.size == 1 }, "a one-cell item has an unexpected footprint")
            },
            "multi-cell footprint is represented" to {
                val model = worldRenderModelFor(BuildWorld(island(), listOf(log)))
                expect(failures, "multi-cell footprint is represented", model.items.singleOrNull()?.occupiedTiles == setOf(TileCoordinate(2, 2), TileCoordinate(3, 2)), "log occupied cells were ${model.items.singleOrNull()?.occupiedTiles}")
            },
            "rotation transforms occupied footprint" to {
                val model = worldRenderModelFor(BuildWorld(island(), listOf(rotatedLog)))
                expect(failures, "rotation transforms occupied footprint", model.items.singleOrNull()?.occupiedTiles == setOf(TileCoordinate(2, 2), TileCoordinate(2, 3)), "rotated log occupied cells were ${model.items.singleOrNull()?.occupiedTiles}")
            },
            "flip retains oriented footprint and flag" to {
                val model = worldRenderModelFor(BuildWorld(island(), listOf(flippedLog)))
                expect(failures, "flip retains oriented footprint and flag", model.items.singleOrNull()?.placement?.flipped == true, "flip state was not retained")
                expect(failures, "flip retains oriented footprint and flag", model.items.singleOrNull()?.occupiedTiles == setOf(TileCoordinate(2, 2), TileCoordinate(3, 2)), "flipped log occupied cells were ${model.items.singleOrNull()?.occupiedTiles}")
            },
            "empty world has no rendered placements" to {
                val model = worldRenderModelFor(BuildWorld(island()))
                expect(failures, "empty world has no rendered placements", model.items.isEmpty(), "empty world produced ${model.items.size} render items")
                expect(failures, "empty world has no rendered placements", model.island == island(), "island was not retained")
            }
        )

        rows.forEach { (row, check) ->
            try {
                check()
            } catch (failure: Throwable) {
                failures += "$row: unexpected ${failure::class.simpleName}: ${failure.message}"
            }
        }
        assertTrue("Render-model rows failed (${failures.size}):\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    private fun expect(failures: MutableList<String>, row: String, condition: Boolean, message: String) {
        if (!condition) failures += "$row: $message"
    }

    private fun item(
        id: String,
        x: Int,
        y: Int,
        definitionId: String = BuiltInPlaceableCatalog.SMALL_STONE_ID
    ) = PlacedWorldItem(id, definitionId, TileCoordinate(x, y))

    private fun island() = WorldIsland(
        id = "render-island",
        width = 8,
        height = 8,
        tiles = emptyList()
    )
}
