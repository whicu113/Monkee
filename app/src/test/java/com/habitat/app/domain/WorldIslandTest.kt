package com.habitat.app.domain

import com.habitat.app.domain.logic.projectWorldTiles
import com.habitat.app.domain.model.WorldTerrain
import com.habitat.app.domain.model.createStarterIsland
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldIslandTest {

    @Test
    fun starterIslandUsesApprovedSizeAndExistingGrassTerrain() {
        val row = "approved 8x8 starter"
        val island = createStarterIsland()

        assertEquals("$row: width", 8, island.width)
        assertEquals("$row: height", 8, island.height)
        assertEquals("$row: complete tile grid", 64, island.tiles.size)
        assertEquals("$row: unique coordinates", 64, island.tiles.map { it.x to it.y }.distinct().size)
        assertTrue("$row: terrain is an existing SPEC type", island.tiles.all { it.terrain == WorldTerrain.GRASS })
    }

    @Test
    fun isometricProjectionDrawsEverySavedTileInsideViewport() {
        val row = "saved 8x8 terrain projection"
        val projected = projectWorldTiles(createStarterIsland(), viewportWidth = 800f, viewportHeight = 600f)

        assertEquals("$row: one projection per saved tile", 64, projected.size)
        assertEquals("$row: unique projected tiles", 64, projected.map { it.x to it.y }.distinct().size)
        projected.forEachIndexed { index, tile ->
            assertTrue("$row: tile $index top is in viewport", tile.top.x in 0f..800f && tile.top.y in 0f..600f)
            assertTrue("$row: tile $index right is in viewport", tile.right.x in 0f..800f && tile.right.y in 0f..600f)
            assertTrue("$row: tile $index bottom is in viewport", tile.bottom.x in 0f..800f && tile.bottom.y in 0f..600f)
            assertTrue("$row: tile $index left is in viewport", tile.left.x in 0f..800f && tile.left.y in 0f..600f)
        }
    }
}
