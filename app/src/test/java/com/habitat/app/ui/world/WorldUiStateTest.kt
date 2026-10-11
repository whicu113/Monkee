package com.habitat.app.ui.world

import com.habitat.app.domain.model.createStarterIsland
import org.junit.Assert.assertEquals
import org.junit.Test

class WorldUiStateTest {

    @Test
    fun noSavedIslandKeepsWorldScreenLoading() {
        assertEquals("empty repository emits loading state", WorldUiState.Loading, worldUiStateFor(null))
    }

    @Test
    fun savedIslandBecomesReadOnlyContentState() {
        val savedIsland = createStarterIsland()
        assertEquals(
            "repository island becomes the displayed world",
            WorldUiState.Content(savedIsland),
            worldUiStateFor(savedIsland)
        )
    }
}
