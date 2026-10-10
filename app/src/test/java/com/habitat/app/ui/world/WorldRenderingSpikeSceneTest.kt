package com.habitat.app.ui.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldRenderingSpikeSceneTest {
    @Test
    fun `scene contains two hundred unique tiles within the isometric viewport bounds`() {
        val tiles = createWorldSpikeTiles()
        val failures = mutableListOf<String>()
        val expectedWidth = WORLD_SPIKE_TILE_WIDTH_DP * (WORLD_SPIKE_COLUMNS + WORLD_SPIKE_ROWS) / 2f
        val expectedHeight = WORLD_SPIKE_TILE_HEIGHT_DP * (WORLD_SPIKE_COLUMNS + WORLD_SPIKE_ROWS) / 2f

        if (tiles.size != 200) {
            failures += "scene tile count: expected 200, got ${tiles.size}"
        }
        if (tiles.map { it.id }.toSet().size != 200) {
            failures += "scene tile ids: expected 200 unique ids, got ${tiles.map { it.id }.toSet().size}"
        }
        if (tiles.any { it.leftDp < 0f || it.topDp < 0f }) {
            failures += "scene origin: tile position fell above or left of zero"
        }
        if (tiles.any { it.leftDp + WORLD_SPIKE_TILE_WIDTH_DP > expectedWidth || it.topDp + WORLD_SPIKE_TILE_HEIGHT_DP > expectedHeight }) {
            failures += "scene bounds: a tile extends beyond ${expectedWidth}x${expectedHeight}dp"
        }
        if (tiles.map { it.colorIndex }.any { it !in 0..3 }) {
            failures += "scene palette: color index fell outside 0..3"
        }

        assertTrue("Isometric scene rows: ${failures.joinToString("; ")}", failures.isEmpty())
    }

    @Test
    fun `all two hundred tiles remain fully visible across the measurement pan extremes`() {
        data class Row(val name: String, val panX: Float, val panY: Float)
        val rows = listOf(
            Row("pan right and down", 8f, 4f),
            Row("pan left and up", -8f, -4f)
        )
        val tiles = createWorldSpikeTiles()
        val viewportWidth = 381f
        val viewportHeight = 200f
        val sceneWidth = WORLD_SPIKE_TILE_WIDTH_DP * (WORLD_SPIKE_COLUMNS + WORLD_SPIKE_ROWS) / 2f
        val sceneHeight = WORLD_SPIKE_TILE_HEIGHT_DP * (WORLD_SPIKE_COLUMNS + WORLD_SPIKE_ROWS) / 2f
        val originX = (viewportWidth - sceneWidth) / 2f
        val originY = (viewportHeight - sceneHeight) / 2f
        val failures = mutableListOf<String>()

        rows.forEach { row ->
            val visible = tiles.count {
                isWorldSpikeTileFullyVisible(
                    tile = it,
                    viewportWidthDp = viewportWidth,
                    viewportHeightDp = viewportHeight,
                    originX = originX,
                    originY = originY,
                    panX = row.panX,
                    panY = row.panY
                )
            }
            if (visible != 200) {
                failures += "${row.name}: expected 200 fully visible tiles, got $visible"
            }
        }

        assertTrue("Visible tile rows: ${failures.joinToString("; ")}", failures.isEmpty())
    }

    @Test
    fun `draw counter reports unique tiles per frame and the minimum count`() {
        val counter = WorldSpikeFrameDrawCounter()
        repeat(WORLD_SPIKE_TILE_COUNT) { tileId -> counter.record(1L, tileId) }
        repeat(WORLD_SPIKE_TILE_COUNT - 1) { tileId -> counter.record(2L, tileId) }
        counter.record(3L, 0)
        val snapshot = counter.snapshot()
        val failures = mutableListOf<String>()

        if (snapshot.completedFrames != 2) {
            failures += "completed frame count: expected 2, got ${snapshot.completedFrames}"
        }
        if (snapshot.minimumTilesDrawn != WORLD_SPIKE_TILE_COUNT - 1) {
            failures += "minimum drawn tiles: expected 199, got ${snapshot.minimumTilesDrawn}"
        }
        if (snapshot.latestTilesDrawn != WORLD_SPIKE_TILE_COUNT - 1) {
            failures += "latest complete frame: expected 199 unique tiles, got ${snapshot.latestTilesDrawn}"
        }

        assertEquals("Draw counter rows: ${failures.joinToString("; ")}", emptyList<String>(), failures)
    }
}
