package com.habitat.app.ui.world

import androidx.lifecycle.ViewModelStore
import com.habitat.app.domain.model.BuildWorld
import com.habitat.app.domain.model.WorldIsland
import com.habitat.app.domain.model.createStarterIsland
import com.habitat.app.domain.repository.WorldRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorldViewModelFreshProcessTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun freshViewModelStartsInactiveAfterProcessDeath() = runTest(dispatcher) {
        val island = createStarterIsland()
        val repository = ReadOnlyWorldRepository(island)
        val viewModel = WorldViewModel(repository)
        val store = ViewModelStore().apply { put("fresh-world", viewModel) }
        try {
            assertEquals("fresh process starts outside Build mode", WorldBuildUiState.Inactive, viewModel.buildModeState.value)
            advanceUntilIdle()
            assertEquals("fresh process opens saved World read-only", WorldUiState.Content(island), viewModel.uiState.value)
        } finally {
            store.clear()
        }
    }

    private class ReadOnlyWorldRepository(private val island: WorldIsland) : WorldRepository {
        override fun observeStarterIsland(): Flow<WorldIsland?> = flowOf(island)
        override fun observeStarterBuildWorld(): Flow<BuildWorld?> = flowOf(BuildWorld(island))
        override suspend fun ensureStarterIsland() = Unit
        override suspend fun saveBuildWorld(world: BuildWorld) = Unit
    }
}
